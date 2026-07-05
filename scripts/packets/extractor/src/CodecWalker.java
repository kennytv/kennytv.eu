import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Recursively interprets live StreamCodec objects into wire-format description nodes.
 * Unknown/opaque codecs fall back to {@link DecodeTracer}.
 */
public class CodecWalker {

    static final Map<String, Object> TYPES = new LinkedHashMap<>();
    static final Map<Object, String> CODEC_TYPE_NAMES = new IdentityHashMap<>();
    static final Set<String> UNKNOWN_SHAPES = new HashSet<>();
    static final int MAX_DEPTH = 40;

    /** Walk a packet root codec. */
    static Map<String, Object> walkRoot(StreamCodec<?, ?> codec, Class<?> packetClass) {
        return walk(codec, packetClass, new IdentityHashMap<>(), 0, true);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> walk(Object codec, Class<?> expected, IdentityHashMap<Object, String> walking, int depth, boolean isRoot) {
        if (depth > MAX_DEPTH) return node("opaque", "note", "max depth exceeded");

        String prior = walking.get(codec);
        if (prior != null) return node("ref", "ref", prior);

        String label = PacketExtractor.CODEC_CONSTANT_NAMES.get(codec);
        String typeName = label != null ? typeNameFor(label) : null;

        // Already emitted as a shared type?
        if (!isRoot && CODEC_TYPE_NAMES.containsKey(codec)) {
            return node("ref", "ref", CODEC_TYPE_NAMES.get(codec));
        }

        walking.put(codec, typeName != null ? typeName : (expected != null ? expected.getSimpleName() : "recursive type"));
        Map<String, Object> result;
        try {
            result = walkShape(codec, expected, walking, depth);
        } finally {
            walking.remove(codec);
        }

        // Promote labeled, non-trivial structures to the shared types table and return a ref.
        if (!isRoot && typeName != null && !isTrivial(result)) {
            if (!TYPES.containsKey(typeName)) {
                TYPES.put(typeName, result);
            }
            CODEC_TYPE_NAMES.put(codec, typeName);
            Map<String, Object> ref = node("ref", "ref", typeName);
            if (expected != null) ref.put("java", expected.getSimpleName());
            return ref;
        }
        return result;
    }

    static boolean isTrivial(Map<String, Object> n) {
        String kind = (String) n.get("kind");
        return switch (kind) {
            case "value", "unit", "registry", "enum", "ref", "opaque" -> true;
            case "optional", "list" -> {
                Object inner = n.containsKey("inner") ? n.get("inner") : n.get("elem");
                yield inner instanceof Map<?, ?> m && isTrivial((Map<String, Object>) m);
            }
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> walkShape(Object codec, Class<?> expected, IdentityHashMap<Object, String> walking, int depth) {
        Map<String, Object> special = specialCodec(PacketExtractor.CODEC_CONSTANT_NAMES.get(codec), walking, depth);
        if (special != null) return special;

        Map<String, Field> fields = capturedFields(codec);
        String cls = codec.getClass().getName();
        String kind = ShapeRegistry.kindOf(codec);

        try {
            // composite(codec1..N, getter1..N, constructor) — one anonymous class per arity,
            // identified by its captured-field pattern
            if (fields.containsKey("val$codec1")) {
                return walkComposite(codec, fields, expected, walking, depth);
            }

            // Exact-class identification first (captured-field names can collide across shapes)
            if (kind != null) {
                switch (kind) {
                    case "unit" -> {
                        return node("unit");
                    }
                    case "map" -> {
                        Map<String, Object> inner = walk(get(fields, "this$0", codec), null, walking, depth + 1, false);
                        if (expected != null && !inner.containsKey("java")) inner.put("java", expected.getSimpleName());
                        return inner;
                    }
                    case "mapStream" -> {
                        return walk(get(fields, "this$0", codec), expected, walking, depth + 1, false);
                    }
                    case "dispatch" -> {
                        return walkDispatch(codec, fields, walking, depth);
                    }
                    case "optional" -> {
                        Map<String, Object> n = node("optional");
                        n.put("inner", walk(get(fields, "val$original", codec), null, walking, depth + 1, false));
                        return n;
                    }
                    case "collection" -> {
                        Map<String, Object> n = node("list");
                        n.put("elem", walk(get(fields, "val$elementCodec", codec), null, walking, depth + 1, false));
                        addLimit(n, fields, codec);
                        return n;
                    }
                    case "mapCodec" -> {
                        Map<String, Object> n = node("map");
                        n.put("key", walk(get(fields, "val$keyCodec", codec), null, walking, depth + 1, false));
                        n.put("value", walk(get(fields, "val$valueCodec", codec), null, walking, depth + 1, false));
                        addLimit(n, fields, codec);
                        return n;
                    }
                    case "either" -> {
                        Map<String, Object> n = node("either");
                        n.put("left", walk(get(fields, "val$leftCodec", codec), null, walking, depth + 1, false));
                        n.put("right", walk(get(fields, "val$rightCodec", codec), null, walking, depth + 1, false));
                        return n;
                    }
                    case "registry", "holder", "holderSet" -> {
                        return walkRegistryCodec(codec, fields, walking, depth);
                    }
                    case "idMapper" -> {
                        return walkIdMapper(codec, fields);
                    }
                    case "lengthPrefixed" -> {
                        Map<String, Object> n = node("prefixed");
                        n.put("inner", walk(get(fields, "val$original", codec), expected, walking, depth + 1, false));
                        addLimit(n, fields, codec);
                        return n;
                    }
                    case "parsedCodec" -> {
                        // fromCodec(ops, codec) / fromCodecWithRegistries: wire format is the inner codec
                        String innerField = fields.containsKey("val$original") ? "val$original" : "val$tagCodec";
                        Map<String, Object> inner = walk(get(fields, innerField, codec), null, walking, depth + 1, false);
                        if (expected != null) inner.put("java", expected.getSimpleName());
                        return inner;
                    }
                    case "string" -> {
                        Map<String, Object> n = node("value", "wire", "String");
                        Field f = fields.get("val$maxStringLength");
                        if (f != null) {
                            f.setAccessible(true);
                            Object v = f.get(codec);
                            if (v instanceof Integer i && i != Integer.MAX_VALUE) n.put("limit", i);
                        }
                        return n;
                    }
                    case "byteArray" -> {
                        Map<String, Object> n = node("value", "wire", "Byte Array");
                        addLimit(n, fields, codec);
                        return n;
                    }
                    case "nbt" -> {
                        return node("value", "wire", "NBT");
                    }
                    case "optionalNbt" -> {
                        return node("value", "wire", "NBT", "note", "empty = TAG_End");
                    }
                    case "json" -> {
                        return node("value", "wire", "JSON (String)");
                    }
                    default -> { /* of/ofMember: fall through to tracing */ }
                }
            } else {
                // Unregistered shape (new combinator in a future version): captured-field heuristics
                if (fields.containsKey("val$instance")) {
                    return node("unit");
                }
                if (fields.containsKey("val$to") && fields.containsKey("val$from") && fields.containsKey("this$0")) {
                    Map<String, Object> inner = walk(get(fields, "this$0", codec), null, walking, depth + 1, false);
                    if (expected != null && !inner.containsKey("java")) inner.put("java", expected.getSimpleName());
                    return inner;
                }
                if (fields.containsKey("val$type") && fields.containsKey("val$codec") && fields.containsKey("this$0")) {
                    return walkDispatch(codec, fields, walking, depth);
                }
                if (fields.containsKey("val$elementCodec")) {
                    Map<String, Object> n = node("list");
                    n.put("elem", walk(get(fields, "val$elementCodec", codec), null, walking, depth + 1, false));
                    addLimit(n, fields, codec);
                    return n;
                }
                if (fields.containsKey("val$keyCodec") && fields.containsKey("val$valueCodec")) {
                    Map<String, Object> n = node("map");
                    n.put("key", walk(get(fields, "val$keyCodec", codec), null, walking, depth + 1, false));
                    n.put("value", walk(get(fields, "val$valueCodec", codec), null, walking, depth + 1, false));
                    addLimit(n, fields, codec);
                    return n;
                }
                if (fields.containsKey("val$leftCodec") && fields.containsKey("val$rightCodec")) {
                    Map<String, Object> n = node("either");
                    n.put("left", walk(get(fields, "val$leftCodec", codec), null, walking, depth + 1, false));
                    n.put("right", walk(get(fields, "val$rightCodec", codec), null, walking, depth + 1, false));
                    return n;
                }
                if (fields.containsKey("val$registryKey")) {
                    return walkRegistryCodec(codec, fields, walking, depth);
                }
                if (fields.containsKey("val$byId")) {
                    return walkIdMapper(codec, fields);
                }
            }
        } catch (ReflectiveOperationException e) {
            return node("opaque", "note", "reflection failed: " + e);
        }

        // Unknown or intentionally opaque (of/ofMember, custom lambdas...) -> trace it.
        if (kind == null
                && !cls.startsWith("net.minecraft.network.codec.StreamCodec$")
                && !cls.startsWith("net.minecraft.network.codec.ByteBufCodecs")
                && UNKNOWN_SHAPES.add(shapeSignature(codec))) {
            System.err.println("[walker] tracing unknown shape: " + shapeSignature(codec));
        }
        return traceNode(codec, expected);
    }

    /* ── composite ──────────────────────────────────────── */

    @SuppressWarnings("unchecked")
    static Map<String, Object> walkComposite(Object codec, Map<String, Field> fields, Class<?> expected,
                                             IdentityHashMap<Object, String> walking, int depth) throws ReflectiveOperationException {
        List<Object> subCodecs = new ArrayList<>();
        for (int i = 1; fields.containsKey("val$codec" + i); i++) {
            subCodecs.add(get(fields, "val$codec" + i, codec));
        }

        Class<?> produced = expected;
        if (produced == null) {
            // discover the produced class by decoding once
            DecodeTracer.TraceOutcome outcome = DecodeTracer.trace((StreamCodec<?, ?>) codec);
            if (outcome.ok() && outcome.result() != null) produced = outcome.result().getClass();
        }

        String[] names = componentNames(produced, subCodecs.size());
        Class<?>[] javaTypes = componentTypes(produced, subCodecs.size());

        List<Object> fieldNodes = new ArrayList<>();
        for (int i = 0; i < subCodecs.size(); i++) {
            Map<String, Object> child = walk(subCodecs.get(i), javaTypes != null ? javaTypes[i] : null, walking, depth + 1, false);
            if (names != null) child.put("name", names[i]);
            if (javaTypes != null && !child.containsKey("java")) child.put("java", javaTypes[i].getSimpleName());
            fieldNodes.add(child);
        }
        Map<String, Object> n = node("container");
        if (produced != null) n.put("java", produced.getSimpleName());
        n.put("fields", fieldNodes);
        return n;
    }

    static String[] componentNames(Class<?> produced, int count) {
        if (produced == null) return null;
        if (produced.isRecord()) {
            RecordComponent[] rc = produced.getRecordComponents();
            if (rc.length == count) {
                String[] names = new String[count];
                for (int i = 0; i < count; i++) names[i] = rc[i].getName();
                return names;
            }
        }
        Constructor<?> ctor = matchingConstructor(produced, count);
        if (ctor != null) {
            Parameter[] params = ctor.getParameters();
            String[] names = new String[count];
            for (int i = 0; i < count; i++) names[i] = params[i].getName();
            return names;
        }
        return null;
    }

    static Class<?>[] componentTypes(Class<?> produced, int count) {
        if (produced == null) return null;
        if (produced.isRecord()) {
            RecordComponent[] rc = produced.getRecordComponents();
            if (rc.length == count) {
                Class<?>[] types = new Class<?>[count];
                for (int i = 0; i < count; i++) types[i] = rc[i].getType();
                return types;
            }
        }
        Constructor<?> ctor = matchingConstructor(produced, count);
        if (ctor != null) {
            Class<?>[] types = new Class<?>[count];
            Parameter[] params = ctor.getParameters();
            for (int i = 0; i < count; i++) types[i] = params[i].getType();
            return types;
        }
        return null;
    }

    static Constructor<?> matchingConstructor(Class<?> cls, int paramCount) {
        Constructor<?> match = null;
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (c.getParameterCount() == paramCount) {
                if (match != null) return match; // ambiguous: use first
                match = c;
            }
        }
        return match;
    }

    /* ── dispatch ───────────────────────────────────────── */

    record KeyEntry(Object key, String label) {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> walkDispatch(Object codec, Map<String, Field> fields,
                                            IdentityHashMap<Object, String> walking, int depth) throws ReflectiveOperationException {
        Object keyCodec = get(fields, "this$0", codec);
        Function<Object, Object> codecFn = (Function<Object, Object>) get(fields, "val$codec", codec);

        Map<String, Object> n = node("dispatch");
        n.put("key", walk(keyCodec, null, walking, depth + 1, false));

        List<KeyEntry> keys = enumerateKeys(keyCodec);
        if (keys == null) {
            n.put("note", "variants could not be enumerated");
            return n;
        }

        List<Object> variants = new ArrayList<>();
        for (KeyEntry entry : keys) {
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("key", entry.label());
            Map<String, Object> body;
            try {
                Object variantCodec = codecFn.apply(entry.key());
                body = variantCodec == null
                        ? node("opaque", "note", "no codec for key")
                        : walk(variantCodec, null, walking, depth + 1, false);
            } catch (Throwable t) {
                body = node("opaque", "note", "variant failed: " + t.getClass().getSimpleName());
            }
            variant.put("body", body);
            variants.add(variant);
        }
        n.put("variants", mergeVariants(variants));
        return n;
    }

    /** Enumerate the possible key values of a dispatch key codec. */
    @SuppressWarnings("unchecked")
    static List<KeyEntry> enumerateKeys(Object keyCodec) throws ReflectiveOperationException {
        Map<String, Field> fields = capturedFields(keyCodec);

        // Registry-backed keys: iterate the id map directly.
        if (fields.containsKey("val$registryKey") && fields.containsKey("val$mapExtractor")) {
            ResourceKey<? extends Registry<?>> key = (ResourceKey<? extends Registry<?>>) get(fields, "val$registryKey", keyCodec);
            Function<Object, Object> extractor = (Function<Object, Object>) get(fields, "val$mapExtractor", keyCodec);
            Registry<Object> registry = (Registry<Object>) DecodeTracer.registryAccess.lookupOrThrow((ResourceKey) key);
            IdMap<Object> idMap = (IdMap<Object>) extractor.apply(registry);
            List<KeyEntry> keys = new ArrayList<>();
            for (int i = 0; i < idMap.size(); i++) {
                Object value = idMap.byId(i);
                if (value == null) continue;
                keys.add(new KeyEntry(value, registryEntryLabel(registry, value)));
            }
            return keys;
        }

        // Mapped key codecs (e.g. VAR_INT.map(Type::byId, Type::id)): probe decode with increasing ids.
        List<KeyEntry> keys = new ArrayList<>();
        Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 1024; i++) {
            DecodeTracer.TraceOutcome outcome = DecodeTracer.runScripted((StreamCodec<?, ?>) keyCodec, i);
            if (!outcome.ok() || outcome.result() == null) break;
            Object key = outcome.result();
            if (!seen.add(key)) break; // wrapped around (clamp/wrap id maps)
            keys.add(new KeyEntry(key, keyLabel(key)));
        }
        return keys.isEmpty() ? null : keys;
    }

    static String registryEntryLabel(Registry<Object> registry, Object value) {
        try {
            if (value instanceof Holder<?> holder) {
                return holder.unwrapKey().map(k -> k.identifier().toString()).orElseGet(() -> keyLabel(holder.value()));
            }
            var key = registry.getKey(value);
            if (key != null) return key.toString();
        } catch (Throwable ignored) {
        }
        return keyLabel(value);
    }

    static String keyLabel(Object key) {
        if (key instanceof Enum<?> e) return e.name();
        if (key instanceof StringRepresentable sr) return sr.getSerializedName();
        if (key instanceof Holder<?> h) {
            return h.unwrapKey().map(k -> k.identifier().toString()).orElse(String.valueOf(h.value()));
        }
        String s = String.valueOf(key);
        return s.length() > 60 ? key.getClass().getSimpleName() : s;
    }

    /* ── registry & id-mapped leaf codecs ───────────────── */

    @SuppressWarnings("unchecked")
    static Map<String, Object> walkRegistryCodec(Object codec, Map<String, Field> fields,
                                                 IdentityHashMap<Object, String> walking, int depth) throws ReflectiveOperationException {
        ResourceKey<?> key = (ResourceKey<?>) get(fields, "val$registryKey", codec);
        String registryName = key.identifier().toString();

        // holder(registryKey, directCodec): id 0 = inline value, else registry id + 1
        if (fields.containsKey("val$directCodec")) {
            Map<String, Object> n = node("holder", "registry", registryName);
            n.put("direct", walk(get(fields, "val$directCodec", codec), null, walking, depth + 1, false));
            return n;
        }
        // holderSet(registryKey): tag name or list of ids
        if (codec.getClass().getName().contains("ByteBufCodecs") && hasInstanceField(codec, "holderCodec")) {
            return node("holderSet", "registry", registryName);
        }
        return node("registry", "registry", registryName);
    }

    static Map<String, Object> walkIdMapper(Object codec, Map<String, Field> fields) {
        // Probe a few ids to find out what this maps to.
        List<String> values = new ArrayList<>();
        Class<?> valueClass = null;
        Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < 512; i++) {
            DecodeTracer.TraceOutcome outcome = DecodeTracer.runScripted((StreamCodec<?, ?>) codec, i);
            if (!outcome.ok() || outcome.result() == null) break;
            Object v = outcome.result();
            if (!seen.add(v)) break;
            if (valueClass == null) valueClass = v.getClass();
            values.add(keyLabel(v));
        }
        if (values.isEmpty()) {
            return node("value", "wire", "VarInt", "note", "id-mapped");
        }
        Map<String, Object> n = node("enum", "wire", "VarInt");
        if (valueClass != null) {
            Class<?> display = valueClass.isEnum() || valueClass.getSuperclass() == null ? valueClass : valueClass;
            if (valueClass.isAnonymousClass() && valueClass.getSuperclass().isEnum()) display = valueClass.getSuperclass();
            n.put("java", display.getSimpleName());
        }
        n.put("values", values);
        return n;
    }

    /* ── well-known codecs with hand-modeled wire formats ── */

    /**
     * ItemStack and DataComponentPatch use custom count-guarded encoders whose full structure
     * (per-component-type value codecs) is worth modeling explicitly.
     */
    static Map<String, Object> specialCodec(String label, IdentityHashMap<Object, String> walking, int depth) {
        if (label == null) return null;
        return switch (label) {
            case "ItemStack.STREAM_CODEC", "ItemStack.OPTIONAL_STREAM_CODEC" -> itemStackNode(false, walking, depth);
            case "ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC" -> itemStackNode(true, walking, depth);
            case "DataComponentPatch.STREAM_CODEC" -> dataComponentPatchNode(false, walking, depth);
            case "DataComponentPatch.DELIMITED_STREAM_CODEC" -> dataComponentPatchNode(true, walking, depth);
            // manual loop packets whose bit-packed/sentinel formats tracing cannot express
            case "ClientboundSetEntityDataPacket.STREAM_CODEC" -> setEntityDataNode();
            case "ClientboundSetEquipmentPacket.STREAM_CODEC" -> setEquipmentNode(walking, depth);
            case "ClientboundSectionBlocksUpdatePacket.STREAM_CODEC" -> sectionBlocksUpdateNode();
            case "Vec3.LP_STREAM_CODEC" -> lpVec3Node();
            default -> null;
        };
    }

    /** LpVec3: bit-packed quantized vector — tracing shows the reads but not the packing. */
    static Map<String, Object> lpVec3Node() {
        Map<String, Object> n = node("container", "java", "Vec3",
                "note", "low-precision vector, quantized into a 48-bit pack");
        List<Object> fields = new ArrayList<>();
        fields.add(node("value", "wire", "Unsigned Byte", "name", "packedLow",
                "note", "0 = zero vector, nothing follows. Bits 0-1: scale, bit 2: extended scale follows"));
        fields.add(node("value", "wire", "Unsigned Byte", "name", "packedMid"));
        fields.add(node("value", "wire", "Unsigned Int", "name", "packedHigh",
                "note", "the three bytes+int form a 48-bit pack (low to high); bits 3-17, 18-32, 33-47 are x, y, z as 15-bit values in [-1, 1], each multiplied by the scale"));
        fields.add(node("value", "wire", "VarInt", "name", "extendedScale",
                "note", "only when bit 2 of the first byte is set: scale |= value << 2"));
        n.put("fields", fields);
        return n;
    }

    static Map<String, Object> setEntityDataNode() {
        Map<String, Object> n = node("container", "java", "ClientboundSetEntityDataPacket");
        List<Object> fields = new ArrayList<>();
        fields.add(node("value", "wire", "VarInt", "name", "id", "java", "int"));

        Map<String, Object> entry = node("container", "name", "packedItems",
                "note", "repeated until index = 255");
        List<Object> entryFields = new ArrayList<>();
        entryFields.add(node("value", "wire", "Unsigned Byte", "name", "index",
                "note", "255 = end of list, nothing follows"));
        entryFields.add(node("value", "wire", "VarInt", "name", "serializerType",
                "note", "entity data serializer id", "link", "/entity-data", "linkText", "see entity data"));
        entryFields.add(node("opaque", "name", "value", "note", "format depends on the serializer"));
        entry.put("fields", entryFields);
        fields.add(entry);
        n.put("fields", fields);
        return n;
    }

    static Map<String, Object> setEquipmentNode(IdentityHashMap<Object, String> walking, int depth) {
        Map<String, Object> n = node("container", "java", "ClientboundSetEquipmentPacket");
        List<Object> fields = new ArrayList<>();
        fields.add(node("value", "wire", "VarInt", "name", "entity", "java", "int"));

        Map<String, Object> entry = node("container", "name", "slots",
                "note", "repeated while the slot byte has its high bit set");
        List<Object> entryFields = new ArrayList<>();
        Map<String, Object> slot = node("value", "wire", "Byte", "name", "slot",
                "note", "bits 0-6 = slot id, bit 7 = another entry follows");
        try {
            List<String> names = new ArrayList<>();
            for (var constant : net.minecraft.world.entity.EquipmentSlot.values()) names.add(constant.name());
            slot.put("values", names);
            slot.put("java", "EquipmentSlot");
        } catch (Throwable ignored) {
        }
        entryFields.add(slot);
        entryFields.add(node("ref", "ref", ensureType("ItemStack (optional)",
                () -> itemStackNode(false, walking, depth + 1)), "name", "item"));
        entry.put("fields", entryFields);
        fields.add(entry);
        n.put("fields", fields);
        return n;
    }

    static Map<String, Object> sectionBlocksUpdateNode() {
        Map<String, Object> n = node("container", "java", "ClientboundSectionBlocksUpdatePacket");
        List<Object> fields = new ArrayList<>();
        fields.add(node("value", "wire", "Long", "name", "sectionPos", "java", "SectionPos",
                "note", "packed section coordinates: x (22 bits) | z (22 bits) | y (20 bits)"));
        fields.add(node("value", "wire", "VarInt", "name", "count"));
        fields.add(node("value", "wire", "VarLong", "name", "blocks",
                "note", "repeated 'count' times; packed: block state id << 12 | (x << 8 | z << 4 | y) within the section"));
        n.put("fields", fields);
        return n;
    }

    static Map<String, Object> itemStackNode(boolean delimited, IdentityHashMap<Object, String> walking, int depth) {
        Map<String, Object> n = node("container", "java", "ItemStack");
        List<Object> fields = new ArrayList<>();
        fields.add(node("value", "wire", "VarInt", "name", "count", "note", "0 or less = empty ItemStack, nothing follows"));
        fields.add(node("registry", "registry", "minecraft:item", "name", "item"));
        String patchType = delimited ? "DataComponentPatch (delimited)" : "DataComponentPatch";
        fields.add(node("ref", "ref", ensureType(patchType, () -> dataComponentPatchNode(delimited, walking, depth + 1)), "name", "components"));
        n.put("fields", fields);
        return n;
    }

    static Map<String, Object> dataComponentPatchNode(boolean delimited, IdentityHashMap<Object, String> walking, int depth) {
        Map<String, Object> n = node("container", "java", "DataComponentPatch");
        List<Object> fields = new ArrayList<>();
        fields.add(node("value", "wire", "VarInt", "name", "added"));
        fields.add(node("value", "wire", "VarInt", "name", "removed"));

        Map<String, Object> components = node("dispatch",
                "name", "addedComponents",
                "note", "repeated 'added' times");
        components.put("key", node("registry", "registry", "minecraft:data_component_type"));
        components.put("variants", dataComponentVariants(delimited, walking, depth));
        fields.add(components);

        fields.add(node("registry", "registry", "minecraft:data_component_type",
                "name", "removedComponents", "note", "repeated 'removed' times"));
        n.put("fields", fields);
        return n;
    }

    @SuppressWarnings("unchecked")
    static List<Object> dataComponentVariants(boolean delimited, IdentityHashMap<Object, String> walking, int depth) {
        List<Object> variants = new ArrayList<>();
        try {
            net.minecraft.core.Registry<net.minecraft.core.component.DataComponentType<?>> registry =
                    DecodeTracer.registryAccess.lookupOrThrow(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE);
            for (net.minecraft.core.component.DataComponentType<?> type : registry) {
                Map<String, Object> variant = new LinkedHashMap<>();
                variant.put("key", String.valueOf(registry.getKey(type)));
                Map<String, Object> body;
                try {
                    Object valueCodec = type.streamCodec();
                    body = valueCodec == null
                            ? node("opaque", "note", "not network-synchronized")
                            : walk(valueCodec, null, walking, depth + 1, false);
                    if (delimited) {
                        Map<String, Object> prefixed = node("prefixed");
                        prefixed.put("inner", body);
                        body = prefixed;
                    }
                } catch (Throwable t) {
                    body = node("opaque", "note", "failed: " + t.getClass().getSimpleName());
                }
                variant.put("body", body);
                variants.add(variant);
            }
        } catch (Throwable t) {
            System.err.println("[walker] data component enumeration failed: " + t);
        }
        return mergeVariants(variants);
    }

    /** Ensure a type referenced from a trace collapse exists — hand-modeled or walked from its constant. */
    static String ensureTraceType(String name) {
        return switch (name) {
            case "ItemStack (optional)" -> ensureType(name, () -> itemStackNode(false, new IdentityHashMap<>(), 0));
            case "DataComponentPatch" -> ensureType(name, () -> dataComponentPatchNode(false, new IdentityHashMap<>(), 0));
            default -> {
                Object codec = PacketExtractor.CODEC_BY_LABEL.get(name + ".STREAM_CODEC");
                if (codec != null) {
                    yield ensureType(name, () -> walkShape(codec, null, new IdentityHashMap<>(), 0));
                }
                yield name;
            }
        };
    }

    /** Register a manually built shared type and return its name. */
    static String ensureType(String name, java.util.function.Supplier<Map<String, Object>> builder) {
        if (!TYPES.containsKey(name)) {
            TYPES.put(name, node("opaque", "note", "building")); // recursion guard (components can contain ItemStacks)
            TYPES.put(name, builder.get());
        }
        return name;
    }

    /** Merge variants with identical bodies into one entry with a combined key. */
    @SuppressWarnings("unchecked")
    static List<Object> mergeVariants(List<Object> variants) {
        Map<String, Map<String, Object>> byBody = new LinkedHashMap<>();
        for (Object v : variants) {
            Map<String, Object> variant = (Map<String, Object>) v;
            String bodyKey = String.valueOf(variant.get("body"));
            Map<String, Object> existing = byBody.get(bodyKey);
            if (existing == null) {
                byBody.put(bodyKey, new LinkedHashMap<>(variant));
            } else {
                existing.put("key", existing.get("key") + ", " + variant.get("key"));
            }
        }
        return new ArrayList<>(byBody.values());
    }

    /* ── tracing fallback ───────────────────────────────── */

    static Map<String, Object> traceNode(Object codec, Class<?> expected) {
        DecodeTracer.TraceOutcome outcome = DecodeTracer.trace((StreamCodec<?, ?>) codec);
        Class<?> produced = expected;
        if (produced == null && outcome.result() != null) produced = outcome.result().getClass();

        if (!outcome.ok()) {
            Map<String, Object> n = node("opaque");
            n.put("note", "decode failed: " + summarizeFailure(outcome.failure()));
            if (!outcome.events().isEmpty()) {
                Map<String, Object> partial = DecodeTracer.buildTree(outcome.events(), produced);
                n.put("fields", partial.get("fields"));
                n.put("partial", true);
            }
            return n;
        }

        Map<String, Object> tree = DecodeTracer.traceTreeWithBranches((StreamCodec<?, ?>) codec, produced, outcome);
        if (produced != null) tree.put("java", produced.getSimpleName());
        if (!DecodeTracer.hasEnumBranching(outcome.events())) {
            DecodeTracer.applyStringNames(tree, outcome);
        }
        DecodeTracer.dedupeInnerNames(tree);

        // collapse single-leaf traces to plain values, keeping the field's name and java type
        Object fieldList = tree.get("fields");
        if (fieldList instanceof List<?> l && l.size() == 1 && l.getFirst() instanceof Map<?, ?> only
                && "value".equals(((Map<String, Object>) only).get("kind"))) {
            Map<String, Object> leaf = new LinkedHashMap<>((Map<String, Object>) only);
            if (!leaf.containsKey("java") && produced != null) leaf.put("java", produced.getSimpleName());
            return leaf;
        }
        if (fieldList instanceof List<?> l && l.isEmpty()) {
            return node("unit");
        }
        return tree;
    }

    static String summarizeFailure(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg != null ? ": " + (msg.length() > 100 ? msg.substring(0, 100) : msg) : "");
    }

    /* ── helpers ────────────────────────────────────────── */

    static Map<String, Field> capturedFields(Object codec) {
        Map<String, Field> out = new HashMap<>();
        for (Field f : codec.getClass().getDeclaredFields()) {
            out.put(f.getName(), f);
        }
        return out;
    }

    static Object get(Map<String, Field> fields, String name, Object obj) throws ReflectiveOperationException {
        Field f = fields.get(name);
        if (f == null) {
            throw new NoSuchFieldException(name + " on " + shapeSignature(obj));
        }
        f.setAccessible(true);
        return f.get(obj);
    }

    static boolean hasInstanceField(Object obj, String name) {
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (f.getName().equals(name)) return true;
        }
        return false;
    }

    static void addLimit(Map<String, Object> n, Map<String, Field> fields, Object codec) throws ReflectiveOperationException {
        Field f = fields.get("val$maxSize");
        if (f == null) return;
        f.setAccessible(true);
        Object v = f.get(codec);
        if (v instanceof Integer i && i != Integer.MAX_VALUE) n.put("limit", i);
    }

    static String shapeSignature(Object codec) {
        StringBuilder sb = new StringBuilder(codec.getClass().getName()).append(" {");
        for (Field f : codec.getClass().getDeclaredFields()) sb.append(f.getName()).append(',');
        return sb.append('}').toString();
    }

    /** "ItemStack.STREAM_CODEC" -> "ItemStack"; "ComponentSerialization.TRUSTED_STREAM_CODEC" -> "ComponentSerialization (trusted)". */
    static String typeNameFor(String label) {
        int dot = label.lastIndexOf('.');
        String owner = label.substring(0, dot);
        String field = label.substring(dot + 1);
        String suffix = field
                .replaceAll("STREAM_CODEC$", "")
                .replaceAll("CODEC$", "")
                .replaceAll("_+$", "")
                .replaceAll("^_+", "");
        if (owner.equals("ByteBufCodecs")) {
            return prettyConstant(field);
        }
        if (suffix.isEmpty()) return owner;
        return owner + " (" + prettyConstant(suffix).toLowerCase(java.util.Locale.ROOT) + ")";
    }

    static String prettyConstant(String constant) {
        String[] parts = constant.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        String s = sb.toString();
        return switch (s) {
            case "Var Int" -> "VarInt";
            case "Var Long" -> "VarLong";
            case "String Utf8" -> "String";
            case "Bool" -> "Boolean";
            default -> s;
        };
    }

    static Map<String, Object> node(String kind, Object... kv) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("kind", kind);
        for (int i = 0; i < kv.length; i += 2) {
            n.put((String) kv[i], kv[i + 1]);
        }
        return n;
    }
}
