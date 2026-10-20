import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Names traced reads by observation instead of declaration order: replay the decode with one
 * read answered differently and diff the two decoded object graphs. The single field that
 * changed is where that read went — wire order and field order no longer matter
 * (set_experience writes level before total but declares them the other way round). The same
 * replays map id reads to the constants they select (ClientIntent's ids 1..3).
 */
public class PerturbNaming {

    /** The trace and event index a tree leaf was built from. */
    record Source(DecodeTracer.TraceOutcome outcome, int event) {}

    static final Map<Map<String, Object>, Source> SOURCES = new IdentityHashMap<>();

    /** One step from a decoded object to a value: a field, or an element/optional unwrap (field null). */
    record Step(Field field, int index) {}

    static void register(Map<String, Object> leaf, DecodeTracer.TraceOutcome outcome, int event) {
        if (outcome != null) SOURCES.put(leaf, new Source(outcome, event));
    }

    /** A list built from a count read inherits the count's source (for its limit). */
    static void transfer(Object from, Map<String, Object> to) {
        Source src = from instanceof Map<?, ?> m ? SOURCES.get(m) : null;
        if (src != null) SOURCES.put(to, src);
    }

    /** Name every registered leaf in the tree whose replay pins it to exactly one field. */
    static void apply(Map<String, Object> tree) {
        applyLeaves(tree);
        // structured values (spliced refs, optionals, lists) and reads touching several
        // fields: name after the deepest field every difference lies under
        DecodeTracer.forEachNode(tree, node -> {
            Source src = SOURCES.get(node);
            if (src == null || node.get("name") != null || node.get("x") != null
                    || !src.outcome().ok() || src.outcome().result() == null) {
                return;
            }
            // deepest field that can hold the node's value (a spliced ItemStack's own count
            // field changes too, but the ItemStack sits in the field above)
            List<Field> common = commonFields(src.outcome(), src.event());
            for (int i = common.size() - 1; i >= 0; i--) {
                if (holds(common.get(i).getType(), node)) {
                    node.put("name", common.get(i).getName());
                    node.putIfAbsent("java", common.get(i).getType().getSimpleName());
                    break;
                }
            }
        });
    }

    /** The fields on the common prefix of all difference paths a replay causes, outermost first. */
    static List<Field> commonFields(DecodeTracer.TraceOutcome outcome, int event) {
        Object base = outcome.result();
        for (long candidate : new long[]{2, 0, 5}) {
            DecodeTracer.TraceOutcome replay = outcome.perturbed(event, candidate, false);
            if (!replay.ok() || replay.result() == null) continue;
            List<List<Step>> diffs = new ArrayList<>();
            diff(base, replay.result(), new ArrayList<>(), diffs, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0, 64);
            if (diffs.isEmpty()) continue;
            int common = diffs.getFirst().size();
            for (List<Step> path : diffs) {
                int i = 0;
                while (i < common && i < path.size() && path.get(i).equals(diffs.getFirst().get(i))) i++;
                common = i;
            }
            List<Field> fields = new ArrayList<>();
            for (int i = 0; i < common; i++) {
                if (diffs.getFirst().get(i).field() != null) fields.add(diffs.getFirst().get(i).field());
            }
            return fields;
        }
        return List.of();
    }

    /**
     * Last resort for reads into locals the decoded object keeps no field for: the local's
     * own name in the reader's bytecode (BlockHitResult's clickX, stop_sound's flags).
     */
    static void applyLocals(Map<String, Object> tree) {
        DecodeTracer.forEachNode(tree, node -> {
            Source src = SOURCES.get(node);
            if (src == null || node.get("name") != null) return;
            List<DecodeTracer.Frame> ctx = src.outcome().events().get(src.event()).context();
            int at = -1;
            for (int i = 0; i < ctx.size(); i++) {
                if (DecodeTracer.isCallerFrame(ctx.get(i))) at = i;
            }
            if (at < 0) return;
            DecodeTracer.Frame caller = ctx.get(at);
            // a read inside a container helper (readEither's Boolean, readOptional's flag) is
            // part of the container the local holds, not the local itself
            if (at + 1 < ctx.size() && DecodeTracer.isBufFrame(ctx.get(at + 1))
                    && DecodeTracer.COLLECTION_HELPERS.containsKey(ctx.get(at + 1).methodName())) {
                return;
            }
            CallSiteCodecs.Local local = CallSiteCodecs.localStoredAt(caller.className(), caller.methodName(), caller.line());
            if (local == null || local.name().equals("this")) return;
            node.put("name", local.name());
        });
    }

    /** Whether a field of this type can hold what the node decodes. */
    static boolean holds(Class<?> type, Map<String, Object> node) {
        String kind = (String) node.get("kind");
        return switch (kind) {
            case "ref" -> {
                Class<?> produced = CodecWalker.TYPE_CLASSES.get(String.valueOf(node.get("ref")));
                yield produced == null ? !type.isPrimitive() : CodecWalker.boxed(type).isAssignableFrom(produced);
            }
            case "optional" -> type == Optional.class || !type.isPrimitive();
            case "list" -> Collection.class.isAssignableFrom(type) || type.isArray();
            case "map" -> Map.class.isAssignableFrom(type);
            case "value" -> {
                String wire = node.get("wire") instanceof String w ? w : "";
                // a fixed-width number only fills an object field as an enum/constant id;
                // otherwise it is a flags byte steering the object (boss_event's Operation)
                boolean crossToObject = !DecodeTracer.bucketOf(wire).equals(DecodeTracer.bucketOf(type));
                if (crossToObject && !wire.equals("VarInt") && !type.isEnum()
                        && (type.isInterface() || constantNames(type).isEmpty())) yield false;
                yield DecodeTracer.bucketsCompatible(DecodeTracer.bucketOf(wire), DecodeTracer.bucketOf(type))
                        && (wire.isEmpty() || DecodeTracer.affinityOk(wire, type));
            }
            default -> !type.isPrimitive();
        };
    }

    /**
     * "0 = absent" ids (OPTIONAL_UNSIGNED_INT, optional block states): replaying the read with 0
     * empties the value and with 2 fills it — 1 when the encoding adds one.
     */
    static void applyPresenceNotes(Map<String, Object> tree) {
        DecodeTracer.forEachNode(tree, node -> {
            Source src = SOURCES.get(node);
            if (src == null || node.get("note") != null || !"VarInt".equals(node.get("wire"))) return;
            DecodeTracer.TraceOutcome zero = src.outcome().perturbed(src.event(), 0, false);
            DecodeTracer.TraceOutcome two = src.outcome().perturbed(src.event(), 2, false);
            if (!zero.ok() || !two.ok()) return;
            List<List<Step>> diffs = new ArrayList<>();
            diff(zero.result(), two.result(), new ArrayList<>(), diffs, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0, 1);
            if (diffs.size() != 1) return;
            Object empty = read(zero.result(), diffs.getFirst());
            Object full = read(two.result(), diffs.getFirst());
            if (!isAbsent(empty) || isAbsent(full)) return;
            Object inner = full instanceof java.util.OptionalInt oi ? (Object) oi.getAsInt() : full;
            node.put("note", Integer.valueOf(1).equals(inner) ? "0 = absent, otherwise value + 1" : "0 = absent");
        });
    }

    /**
     * Booleans steering what follows: replay with the Boolean flipped. Reads that vanish are
     * conditional on it (player_look_at's entity/toAnchor behind atEntity); an Either group
     * whose reads are replaced by others gets its second branch.
     */
    @SuppressWarnings("unchecked")
    static void applyBooleanBranches(Map<String, Object> tree) {
        List<List<Object>> fieldLists = new ArrayList<>();
        DecodeTracer.forEachNode(tree, n -> {
            if (n.get("fields") instanceof List<?> l) fieldLists.add((List<Object>) l);
        });
        for (List<Object> fields : fieldLists) {
            for (int i = 0; i < fields.size(); i++) {
                Map<String, Object> node = DecodeTracer.asMap(fields.get(i));
                if (node == null) continue;
                if ("group".equals(node.get("kind")) && "Either".equals(node.get("context"))) {
                    Map<String, Object> either = completeEither(node);
                    if (either != null) fields.set(i, either);
                    continue;
                }
                if (!"Boolean".equals(node.get("wire"))) continue;
                Source src = SOURCES.get(node);
                if (src == null) continue;
                Branch branch = flip(src);
                if (branch == null || !branch.replayMiddle().isEmpty() || branch.baseMiddle().isEmpty()) continue;
                java.util.Set<Integer> gated = new java.util.HashSet<>();
                for (DecodeTracer.Unit u : branch.baseMiddle()) gated.add(u.firstEvent);
                String cond = "only when " + (node.get("name") != null ? node.get("name") : "the preceding Boolean")
                        + " is " + (src.outcome().events().get(src.event()).value() != 0);
                for (int j = i + 1; j < fields.size(); j++) {
                    Map<String, Object> sibling = DecodeTracer.asMap(fields.get(j));
                    Source s = sibling == null ? null : SOURCES.get(sibling);
                    if (s != null && s.outcome() == src.outcome() && gated.contains(s.event())) {
                        sibling.put("note", sibling.get("note") == null ? cond : cond + "; " + sibling.get("note"));
                    }
                }
            }
        }
    }

    record Branch(List<DecodeTracer.Unit> baseMiddle, List<DecodeTracer.Unit> replayMiddle) {}

    /** The reads that differ between the decode and its replay with the Boolean flipped. */
    static Branch flip(Source src) {
        DecodeTracer.TraceOutcome replay = src.outcome().perturbed(src.event(), 0, false);
        if (!replay.ok()) return null;
        List<DecodeTracer.Unit> base = DecodeTracer.toUnits(src.outcome().events(), null);
        List<DecodeTracer.Unit> other = DecodeTracer.toUnits(replay.events(), null);
        List<List<DecodeTracer.Unit>> both = List.of(base, other);
        int prefix = DecodeTracer.commonPrefix(both);
        int suffix = DecodeTracer.commonSuffix(both, prefix);
        return new Branch(base.subList(prefix, base.size() - suffix), other.subList(prefix, other.size() - suffix));
    }

    /** An Either group traced down one branch: its Boolean's replay supplies the other. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> completeEither(Map<String, Object> group) {
        List<Object> fields = (List<Object>) group.get("fields");
        if (fields.size() < 2) return null;
        Map<String, Object> flag = DecodeTracer.asMap(fields.getFirst());
        Source src = flag == null ? null : SOURCES.get(flag);
        if (src == null || !"Boolean".equals(flag.get("wire"))) return null;
        Branch branch = flip(src);
        if (branch == null || branch.replayMiddle().isEmpty()) return null;
        Map<String, Object> taken = DecodeTracer.wrap(new ArrayList<>(fields.subList(1, fields.size())));
        Map<String, Object> other = innermost(DecodeTracer.treeFromUnits(branch.replayMiddle(), null));
        boolean tookLeft = src.outcome().events().get(src.event()).value() != 0;
        Map<String, Object> either = new java.util.LinkedHashMap<>();
        either.put("kind", "either");
        if (group.get("name") != null) either.put("name", group.get("name"));
        if (group.get("java") != null) either.put("java", group.get("java"));
        either.put("left", tookLeft ? taken : other);
        either.put("right", tookLeft ? other : taken);
        return either;
    }

    /** Strip the wrapper groups a sub-trace's frames add around its reads. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> innermost(Map<String, Object> tree) {
        Map<String, Object> n = tree;
        while (n.get("fields") instanceof List<?> l && l.size() == 1 && DecodeTracer.asMap(l.getFirst()) != null
                && DecodeTracer.asMap(l.getFirst()).get("fields") instanceof List<?>) {
            n = DecodeTracer.asMap(l.getFirst());
        }
        if (n.get("fields") instanceof List<?> l && "group".equals(n.get("kind")) && "Either".equals(n.get("context"))) {
            // the replay's own Either group: drop its Boolean, keep the branch's reads
            List<Object> reads = new ArrayList<>((List<Object>) l);
            if (!reads.isEmpty() && "Boolean".equals(DecodeTracer.asMap(reads.getFirst()).get("wire"))) reads.removeFirst();
            return DecodeTracer.wrap(reads);
        }
        return n.get("fields") instanceof List<?> l ? DecodeTracer.wrap(new ArrayList<>((List<Object>) l)) : n;
    }

    static boolean isAbsent(Object v) {
        return v == null || (v instanceof Optional<?> o && o.isEmpty())
                || (v instanceof java.util.OptionalInt oi && oi.isEmpty());
    }

    static void applyLeaves(Map<String, Object> tree) {
        DecodeTracer.forEachNode(tree, node -> {
            Source src = SOURCES.get(node);
            if (src == null || !src.outcome().ok() || src.outcome().result() == null) return;
            String kind = (String) node.get("kind");
            Object wireValue = node.get("wire");
            if (!node.containsKey("limit") && ("list".equals(kind) || "map".equals(kind)
                    || "String".equals(wireValue) || "Byte Array".equals(wireValue))) {
                Integer limit = probeLimit(src);
                // 32767 is readUtf()'s default, not worth repeating on every string
                // <= 0: a limit relative to the readable bytes (readByteArray()), not a constant
                if (limit != null && limit > 0 && limit != 32767) node.put("limit", limit);
            }
            if (!"value".equals(kind) && !"enum".equals(kind) && !"registry".equals(kind) && !"holder".equals(kind)) return;
            List<Step> path = locate(src);
            if (path == null || path.isEmpty() || path.getLast().field() == null) return;
            String wire = node.get("wire") instanceof String w ? w : "";
            if (node.get("x") instanceof Integer x && x > 1) {
                // several same-line reads merged (Vec3's three doubles): the replay hit the
                // first component; the merged node is the object holding all of them
                Field holder = path.size() >= 2 ? path.get(path.size() - 2).field() : null;
                if (holder == null) return;
                node.put("name", holder.getName());
                node.put("java", holder.getType().getSimpleName());
                return;
            }
            Field field = path.getLast().field();
            if (!DecodeTracer.bucketsCompatible(DecodeTracer.bucketOf(wire), DecodeTracer.bucketOf(field.getType()))
                    || (!wire.isEmpty() && !DecodeTracer.affinityOk(wire, field.getType()))) {
                return;
            }
            node.put("name", field.getName());
            // an enum list already pins the type (boss_event's OperationType behind an
            // Operation-typed field) — the field's declared type would only blur it
            if (!node.containsKey("values")) node.put("java", field.getType().getSimpleName());
            if ("value".equals(kind) && !node.containsKey("values")) {
                List<String> ids = probeIds(src, path, field.getType());
                if (ids != null) node.put("values", ids);
            }
        });
    }

    /**
     * The top-level field of the decoded object that a read feeds — however deep or structural
     * the change it causes (a list count, an optional flag): every difference must sit below
     * the same field. Composite codecs use it to name sub-codecs of non-record classes.
     */
    static Field topField(DecodeTracer.TraceOutcome outcome, int event) {
        Object base = outcome.result();
        for (long candidate : new long[]{2, 0, 5}) {
            DecodeTracer.TraceOutcome replay = outcome.perturbed(event, candidate, false);
            if (!replay.ok() || replay.result() == null) continue;
            List<List<Step>> diffs = new ArrayList<>();
            diff(base, replay.result(), new ArrayList<>(), diffs, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0, 64);
            if (diffs.isEmpty()) continue;
            Field top = null;
            for (List<Step> path : diffs) {
                if (path.isEmpty() || path.getFirst().field() == null) return null;
                if (top != null && top != path.getFirst().field()) return null;
                top = path.getFirst().field();
            }
            return top;
        }
        return null;
    }

    static final java.util.regex.Pattern[] LIMIT_MESSAGES = {
            // Utf8String: "... longer than maximum allowed (1000000 > 48)" — bytes, 3 per char
            java.util.regex.Pattern.compile("longer than maximum allowed \\(\\d+ > (\\d+)\\)"),
            java.util.regex.Pattern.compile("exceeded max size of: (\\d+)"),
            java.util.regex.Pattern.compile("bigger than allowed (\\d+)"),
    };

    /** A reader's size limit, read off its rejection of an oversized length or count. */
    static Integer probeLimit(Source src) {
        DecodeTracer.TraceOutcome replay = src.outcome().perturbed(src.event(), 1_000_000, false);
        if (replay.ok()) return null;
        for (Throwable t = replay.failure(); t != null; t = t.getCause() == t ? null : t.getCause()) {
            String msg = t.getMessage();
            if (msg == null) continue;
            for (int i = 0; i < LIMIT_MESSAGES.length; i++) {
                var m = LIMIT_MESSAGES[i].matcher(msg);
                if (m.find()) {
                    int limit = Integer.parseInt(m.group(1));
                    return i == 0 ? limit / 3 : limit;
                }
            }
        }
        return null;
    }

    /** The path of the one field a read lands in, or null when replays disagree or touch several. */
    static List<Step> locate(Source src) {
        Object base = src.outcome().result();
        for (long candidate : new long[]{2, 0, 5}) {
            DecodeTracer.TraceOutcome replay = src.outcome().perturbed(src.event(), candidate, false);
            if (!replay.ok() || replay.result() == null) continue;
            // a read that changes what is read next gates fields; it is not one itself
            if (structuralReads(replay.events()) != structuralReads(src.outcome().events())) return null;
            List<List<Step>> diffs = new ArrayList<>();
            diff(base, replay.result(), new ArrayList<>(), diffs, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), 0, 1);
            if (diffs.size() > 1) return null; // the read steers structure (a count, a switch)
            if (diffs.size() == 1) {
                // a value appearing or vanishing means the read gates the field (stop_sound's
                // flags byte), it is not the field itself
                List<Step> path = diffs.getFirst();
                return read(base, path) != null && read(replay.result(), path) != null ? path : null;
            }
        }
        return null;
    }

    /**
     * For a read landing in an enum (or constant-holding) field: replay with ids 0.. and record
     * which constant each id selects. Ordinal-contiguous results list plain names; anything
     * else ("STATUS=1") spells out the id.
     */
    static List<String> probeIds(Source src, List<Step> path, Class<?> type) {
        if (!type.isEnum() && constantNames(type).isEmpty()) return null;
        Map<String, Integer> firstId = new LinkedHashMap<>();
        int misses = 0;
        for (int v = 0; v < 512 && misses < 32; v++) {
            DecodeTracer.TraceOutcome replay = src.outcome().perturbed(src.event(), v, true);
            Object value = replay.ok() ? read(replay.result(), path) : null;
            String label = value instanceof Enum<?> e ? e.name() : value == null ? null : constantNames(type).get(value);
            if (label == null || firstId.containsKey(label)) {
                misses++;
                continue;
            }
            misses = 0;
            firstId.put(label, v);
        }
        if (firstId.size() < 2) return null;
        List<String> out = new ArrayList<>();
        boolean ordinal = type.isEnum();
        Object[] constants = type.isEnum() ? type.getEnumConstants() : new Object[0];
        int i = 0;
        for (var entry : firstId.entrySet()) {
            if (entry.getValue() != i || i >= constants.length || !((Enum<?>) constants[i]).name().equals(entry.getKey())) {
                ordinal = false;
            }
            i++;
        }
        for (var entry : firstId.entrySet()) out.add(ordinal ? entry.getKey() : entry.getKey() + "=" + entry.getValue());
        return out;
    }

    /** Reads that shape what follows — a string's content bytes only follow its own length. */
    static int structuralReads(List<DecodeTracer.Event> events) {
        int n = 0;
        for (DecodeTracer.Event ev : events) {
            String op = ev.op();
            if (!op.equals("stringContent") && !op.equals("readCharSequence") && !op.equals("skipBytes")) n++;
        }
        return n;
    }

    static Object read(Object root, List<Step> path) {
        Object cur = root;
        try {
            for (Step step : path) {
                if (cur == null) return null;
                if (step.field() != null) {
                    cur = step.field().get(cur);
                } else if (cur instanceof Optional<?> o) {
                    cur = o.orElse(null);
                } else if (cur instanceof List<?> l) {
                    cur = step.index() < l.size() ? l.get(step.index()) : null;
                } else if (cur instanceof Object[] arr) {
                    cur = step.index() < arr.length ? arr[step.index()] : null;
                } else {
                    return null;
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return cur;
    }

    /* ── object graph diff ──────────────────────────────── */

    static final Map<Class<?>, List<Field>> FIELDS = new HashMap<>();

    /** Collect differing leaf paths between two decoded graphs; stops once more than {@code limit} are found. */
    static void diff(Object a, Object b, List<Step> path, List<List<Step>> out, Set<Object> seen, int depth, int limit) {
        if (out.size() > limit || a == b) return;
        if (a == null || b == null || a.getClass() != b.getClass()) {
            out.add(new ArrayList<>(path));
            return;
        }
        if (isValue(a)) {
            if (!Objects.deepEquals(a, b)) out.add(new ArrayList<>(path));
            return;
        }
        if (depth > 8 || !seen.add(a)) return;
        // table constants (GameEvent.Type.CHANGE_GAME_MODE) are values, not structures
        if (constantNames(a.getClass()).containsKey(a) && constantNames(b.getClass()).containsKey(b)) {
            out.add(new ArrayList<>(path));
            return;
        }
        if (a instanceof Optional<?> oa) {
            path.add(new Step(null, 0));
            diff(oa.orElse(null), ((Optional<?>) b).orElse(null), path, out, seen, depth + 1, limit);
            path.removeLast();
            return;
        }
        if (a instanceof List<?> la) {
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) {
                out.add(new ArrayList<>(path));
                return;
            }
            for (int i = 0; i < la.size() && out.size() <= limit; i++) {
                path.add(new Step(null, i));
                diff(la.get(i), lb.get(i), path, out, seen, depth + 1, limit);
                path.removeLast();
            }
            return;
        }
        if (a instanceof Collection<?> || a instanceof Map<?, ?> || a.getClass().isArray()) {
            if (!Objects.deepEquals(a, b)) out.add(new ArrayList<>(path));
            return;
        }
        for (Field f : fields(a.getClass())) {
            try {
                path.add(new Step(f, -1));
                diff(f.get(a), f.get(b), path, out, seen, depth + 1, limit);
                path.removeLast();
            } catch (Throwable ignored) {
                path.removeLast();
            }
            if (out.size() > limit) return;
        }
    }

    /** Leaves of the diff: values compared by equality, never descended into. */
    static boolean isValue(Object o) {
        Class<?> c = o.getClass();
        String cn = c.getName();
        if (c.isEnum() || (c.getSuperclass() != null && c.getSuperclass().isEnum())) return true;
        if (cn.startsWith("java.") && !(o instanceof Collection<?>) && !(o instanceof Map<?, ?>) && !(o instanceof Optional<?>)) return true;
        if (cn.startsWith("io.netty.") || cn.startsWith("com.mojang.") || cn.startsWith("com.google.")) return true;
        if (o instanceof net.minecraft.core.Holder<?> || o instanceof net.minecraft.resources.Identifier
                || o instanceof net.minecraft.resources.ResourceKey<?> || o instanceof net.minecraft.network.chat.Component
                || o instanceof net.minecraft.nbt.Tag || o instanceof net.minecraft.world.level.block.state.StateHolder<?, ?>) {
            return true;
        }
        // registered singletons (Block, EntityType, ...) and big engine objects compare by identity
        return fields(c).size() > 24 || registered(o);
    }

    static final Map<Class<?>, Map<Object, String>> CONSTANTS = new HashMap<>();

    /** A class's own static final instances by identity, named after their fields. */
    static Map<Object, String> constantNames(Class<?> c) {
        return CONSTANTS.computeIfAbsent(c, cls -> {
            Map<Object, String> out = new IdentityHashMap<>();
            if (cls.isEnum() || cls.getName().startsWith("java.")) return out;
            // GameEvent.Type's instances live in ClientboundGameEventPacket, its outer class
            for (Class<?> owner : new Class<?>[]{cls, cls.getDeclaringClass()}) {
                if (owner == null) continue;
                try {
                    for (Field f : owner.getDeclaredFields()) {
                        int m = f.getModifiers();
                        if (!Modifier.isStatic(m) || !Modifier.isFinal(m) || f.getType() != cls) continue;
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v != null) out.putIfAbsent(v, f.getName());
                    }
                } catch (Throwable ignored) {
                }
            }
            return out;
        });
    }

    /** Any static final field values of a holder class, named by field (EntityDataSerializers.BYTE). */
    static Map<Object, String> constantNamesIn(Class<?> holder) {
        Map<Object, String> out = new IdentityHashMap<>();
        for (Field f : holder.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isFinal(m) || f.getType().isPrimitive()) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null) out.putIfAbsent(v, f.getName());
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    static final Map<Class<?>, Boolean> REGISTERED = new HashMap<>();

    static boolean registered(Object o) {
        return REGISTERED.computeIfAbsent(o.getClass(), c -> {
            if (c.isRecord()) return false;
            if (DecodeTracer.registryList == null) DecodeTracer.registryIdOf(o); // initializes the list
            return DecodeTracer.lookupInRegistries(o) != null;
        });
    }

    static List<Field> fields(Class<?> c) {
        return FIELDS.computeIfAbsent(c, cls -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> k = cls; k != null && k != Object.class && !k.getName().startsWith("java."); k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    try {
                        f.setAccessible(true);
                        out.add(f);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return out;
        });
    }
}
