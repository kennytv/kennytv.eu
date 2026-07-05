import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Learns the anonymous-class names of codec combinators by instantiating samples at runtime,
 * so codecs (and stack frames) can be identified exactly instead of by captured-field heuristics.
 */
public class ShapeRegistry {

    static final Map<String, String> CLASS_KINDS = new HashMap<>();

    /** Kinds that should appear as structural groups in traced output. */
    static final Map<String, String> TRACE_GROUP_LABELS = Map.of(
            "mapCodec", "Map",
            "collection", "List",
            "optional", "Optional",
            "either", "Either",
            "dispatch", "Dispatch",
            "lengthPrefixed", "Length-prefixed",
            "constant", "Composite"
    );

    static void init() {
        StreamCodec<Object, Object> dummy = StreamCodec.unit(new Object());
        register("unit", dummy);
        register("of", StreamCodec.of((b, v) -> {}, b -> new Object()));
        register("ofMember", StreamCodec.ofMember((v, b) -> {}, b -> new Object()));
        register("map", dummy.map(x -> x, x -> x));
        register("mapStream", dummy.mapStream(b -> b));
        register("dispatch", dummy.dispatch(x -> x, k -> dummy));
        register("optional", ByteBufCodecs.optional(ByteBufCodecs.BOOL));
        register("collection", ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.BOOL));
        register("mapCodec", ByteBufCodecs.map(HashMap::new, ByteBufCodecs.BOOL, ByteBufCodecs.BOOL));
        register("either", ByteBufCodecs.either(ByteBufCodecs.BOOL, ByteBufCodecs.BOOL));
        register("idMapper", ByteBufCodecs.idMapper(i -> i, x -> 0));
        register("registry", ByteBufCodecs.registry(Registries.ITEM));
        register("holder", ByteBufCodecs.holder(Registries.SOUND_EVENT, StreamCodec.unit(null)));
        register("holderSet", ByteBufCodecs.holderSet(Registries.ITEM));
        // primitive constants (VAR_INT, BOOL, ...) are their own anonymous classes. Only
        // classes declared in ByteBufCodecs itself: constants built from generic combinators
        // (GAME_PROFILE is a composite) must keep their combinator class meaning. "constant"
        // walks like "of" (trace fallback) but its frames form groups, so a hand-written
        // multi-read constant (PROPERTY_MAP) keeps its nesting in traced output.
        for (java.lang.reflect.Field f : ByteBufCodecs.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && StreamCodec.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value != null && value.getClass().getName().startsWith(ByteBufCodecs.class.getName() + "$")) {
                        register("constant", value);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        register("string", ByteBufCodecs.stringUtf8(1));
        register("byteArray", ByteBufCodecs.byteArray(1));
        register("nbt", ByteBufCodecs.tagCodec(NbtAccounter::unlimitedHeap));
        register("nbt", ByteBufCodecs.compoundTagCodec(NbtAccounter::unlimitedHeap));
        register("optionalNbt", ByteBufCodecs.optionalTagCodec(NbtAccounter::unlimitedHeap));
        // fromCodec(ops, codec): decodes an inner wire codec (NBT tag, JSON, ...) and parses it
        register("parsedCodec", ByteBufCodecs.fromCodec(Codec.BOOL));
        register("parsedCodec", ByteBufCodecs.fromCodecWithRegistries(Codec.BOOL));
        register("json", ByteBufCodecs.lenientJson(1));
        try {
            register("lengthPrefixed", ByteBufCodecs.BOOL.apply(ByteBufCodecs.lengthPrefixed(1)));
            register("lengthPrefixed", ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.registryFriendlyLengthPrefixed(1)));
        } catch (Throwable t) {
            System.err.println("[shapes] lengthPrefixed registration failed: " + t);
        }
        System.err.println("[shapes] registered " + CLASS_KINDS.size() + " combinator classes");
    }

    static void register(String kind, Object sample) {
        if (sample != null) CLASS_KINDS.putIfAbsent(sample.getClass().getName(), kind);
    }

    static String kindOf(Object codec) {
        return CLASS_KINDS.get(codec.getClass().getName());
    }

    static String kindOfClassName(String className) {
        return CLASS_KINDS.get(className);
    }
}
