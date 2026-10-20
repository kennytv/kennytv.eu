import io.netty.buffer.FabricatingByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes a StreamCodec against a fabricated buffer and reconstructs the wire format
 * from the recorded reads and their call stacks.
 */
public class DecodeTracer {

    /** One intercepted read. */
    record Event(String op, int byteLength, long value, List<Frame> context, String wire) {}

    /** A stack frame reduced to what we group on. */
    record Frame(String className, String methodName, int line) {
        String key() { return className + "#" + methodName + "#" + line; }
        String simpleClassName() {
            String n = className;
            int lambda = n.indexOf("$$Lambda");
            if (lambda >= 0) n = n.substring(0, lambda);
            int dot = n.lastIndexOf('.');
            if (dot >= 0) n = n.substring(dot + 1);
            return n;
        }
    }

    static class AbortDecode extends RuntimeException {
        AbortDecode(String msg) { super(msg, null, false, false); }
    }

    /** Result of a trace: the reconstructed node plus the decoded object (for naming). */
    record TraceOutcome(Object result, List<Event> events, Throwable failure, List<String> fabricatedStrings,
                        StreamCodec<?, ?> codec, Policy template) {
        boolean ok() { return failure == null; }

        /** Replay this decode with the read that became {@code eventIndex} given another value. */
        TraceOutcome perturbed(int eventIndex, long value, boolean literal) {
            Policy p = template.copy();
            p.perturbEvent = eventIndex;
            p.perturbValue = value;
            p.perturbLiteral = literal;
            return run(codec, p);
        }
    }

    static RegistryAccess registryAccess;

    /* ── fabrication policy ─────────────────────────────── */

    /** Network-NBT bytes of the compound {text:"a"} — a valid Component and harmless generic compound. */
    static final byte[] NBT_TEXT_COMPOUND = {10, 8, 0, 4, 't', 'e', 'x', 't', 0, 1, 'a', 0};

    static byte[] rsaPublicKeyDer;

    static byte[] rsaPublicKeyDer() {
        if (rsaPublicKeyDer == null) {
            try {
                var gen = java.security.KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                rsaPublicKeyDer = gen.generateKeyPair().getPublic().getEncoded();
            } catch (Exception e) {
                rsaPublicKeyDer = new byte[0];
            }
        }
        return rsaPublicKeyDer;
    }

    static class Policy implements FabricatingByteBuf.Fabricator {
        final int varintValue;
        final boolean booleansTrue;
        final List<Event> events = new ArrayList<>();
        final Map<String, Integer> callSiteCounts = new HashMap<>();
        final java.util.ArrayDeque<Integer> varintScript = new java.util.ArrayDeque<>();
        /** Unique contents given to each fabricated string, for name recovery from the decoded object. */
        final List<String> fabricatedStrings = new ArrayList<>();
        /** NBT planted for stream-based readers: {text:"a"} parses as a Component and a plain compound. */
        boolean nbtTextCompound = true;
        /** Value for the first varint read under a readEnum frame (branch exploration). */
        int enumChoice = -1;
        private boolean enumChoiceUsed;
        /** Value for the first plain byte read (method/action switch exploration). */
        int byteChoice = -1;
        private boolean byteChoiceUsed;
        /** Single bit to set in the first EnumSet read (action-set exploration). */
        int enumSetBit = -1;
        private boolean enumSetUsed;
        /** Read (by event index) to answer with {@link #perturbValue} instead (field-name probing). */
        int perturbEvent = -1;
        long perturbValue;
        /** Fixed-width reads take {@link #perturbValue} as-is (id probing) instead of a float-safe pattern. */
        boolean perturbLiteral;
        /** Byte arrays carry a valid RSA key (ByteBufCodecs.PUBLIC_KEY maps a plain byte array). */
        boolean keyArrays;
        private int pendingVarint = -1; // multi-byte varint in progress
        private int lastVarintValue;
        private boolean lastVarintPerturbed;

        Policy(int varintValue, boolean booleansTrue) {
            this.varintValue = varintValue;
            this.booleansTrue = booleansTrue;
        }

        /** A fresh policy with the same configuration (scripts included), for replays. */
        Policy copy() {
            Policy p = new Policy(varintValue, booleansTrue);
            p.nbtTextCompound = nbtTextCompound;
            p.enumChoice = enumChoice;
            p.byteChoice = byteChoice;
            p.enumSetBit = enumSetBit;
            p.varintScript.addAll(varintScript);
            p.keyArrays = keyArrays;
            return p;
        }

        boolean perturbingNow() {
            return perturbEvent >= 0 && events.size() == perturbEvent;
        }

        private byte nextVarintByte(StackTraceElement[] stack) {
            int value;
            if (pendingVarint >= 0) {
                value = pendingVarint;
            } else {
                boolean enumRead = hasFrameMethod(stack, "readEnum");
                lastVarintPerturbed = false;
                if (perturbingNow()) {
                    // consume what the base run would have, so later reads stay aligned
                    if (enumChoice >= 0 && !enumChoiceUsed && enumRead) enumChoiceUsed = true;
                    else if (!enumRead && !hasFrame(stack, "Utf8String", "read") && !varintScript.isEmpty()) varintScript.poll();
                    value = (int) perturbValue;
                    lastVarintPerturbed = true;
                } else if (enumChoice >= 0 && !enumChoiceUsed && enumRead) {
                    enumChoiceUsed = true;
                    value = enumChoice;
                } else if (hasFrameMethod(stack, "readPublicKey") || hasFrame(stack, ".Crypt", "byteToPublicKey")
                        || (keyArrays && hasFrameMethod(stack, "readByteArray"))) {
                    value = rsaPublicKeyDer().length;
                } else if (hasFrame(stack, "Utf8String", "read")) {
                    value = 2; // two chars: room for a unique marker per string
                } else if (!enumRead && !varintScript.isEmpty()) {
                    // enum reads never consume the script — it targets dispatch keys/ids only
                    value = varintScript.poll();
                } else {
                    value = varintValue;
                }
                lastVarintValue = value;
            }
            if ((value & ~0x7F) != 0) {
                pendingVarint = value >>> 7;
                return (byte) ((value & 0x7F) | 0x80);
            }
            pendingVarint = -1;
            return (byte) value;
        }

        @Override
        public void fabricate(byte[] out, StackTraceElement[] stack) {
            String ctx = stackContextKind(stack);
            if (perturbingNow() && !ctx.equals("varint")) {
                fabricateDefault(out, stack, ctx);
                if (ctx.equals("boolean")) {
                    out[0] = (byte) (out[0] == 0 ? 1 : 0);
                } else if (ctx.equals("other")) {
                    // distinct from the 0 fill: 5 for bytes/shorts, 3.0 as float/double bits
                    long bits = perturbLiteral ? perturbValue
                            : out.length == 4 ? 0x40400000L : out.length == 8 ? 0x4008000000000000L : perturbValue;
                    for (int i = 0; i < out.length; i++) out[i] = (byte) (bits >>> (8 * (out.length - 1 - i)));
                }
                return;
            }
            fabricateDefault(out, stack, ctx);
        }

        private void fabricateDefault(byte[] out, StackTraceElement[] stack, String ctx) {
            switch (ctx) {
                case "varint" -> out[0] = nextVarintByte(stack);
                case "boolean" -> {
                    // per-callsite decay: true once, then false (terminates while(readBoolean()) loops)
                    String site = siteKey(stack);
                    int seen = callSiteCounts.merge(site, 1, Integer::sum);
                    out[0] = (byte) ((booleansTrue && seen <= 1) ? 1 : 0);
                }
                case "nbtRootType" -> out[0] = 10; // CompoundTag
                case "nbt" -> java.util.Arrays.fill(out, (byte) 0); // end tags / empty
                case "string" -> java.util.Arrays.fill(out, (byte) 'a');
                case "enumSet" -> {
                    if (enumSetBit >= 0 && !enumSetUsed) {
                        enumSetUsed = true;
                        java.util.Arrays.fill(out, (byte) 0);
                        if (enumSetBit / 8 < out.length) out[enumSetBit / 8] = (byte) (1 << (enumSetBit % 8));
                    } else {
                        java.util.Arrays.fill(out, (byte) 0xFF); // all flags set
                    }
                }
                default -> {
                    java.util.Arrays.fill(out, (byte) 0);
                    if (byteChoice >= 0 && !byteChoiceUsed) {
                        byteChoiceUsed = true;
                        out[0] = (byte) byteChoice;
                        return;
                    }
                    // Sentinel-loop breaker: a plain byte read hit repeatedly at the same call
                    // site is likely scanning for a terminator (entity data uses 255).
                    String site = siteKey(stack);
                    int seen = callSiteCounts.merge("byte:" + site, 1, Integer::sum);
                    if (seen >= 3) out[0] = (byte) 0xFF;
                }
            }
        }

        @Override
        public byte[] extraContent(byte[] justFilled, StackTraceElement[] stack) {
            // Some readers check readableBytes() against the just-read length before consuming
            // content (strings, long arrays), so the content must exist eagerly.
            if (pendingVarint >= 0) return null; // multi-byte varint still in progress
            if (!"varint".equals(stackContextKind(stack))) return null;
            int v = lastVarintValue;
            if (v <= 0) return null;
            // a perturbed length probes the reader's limit check — never materialize it
            if (lastVarintPerturbed && v > 4096) return null;
            if (hasFrame(stack, "Utf8String", "read")) {
                // unique marker so the field name can be recovered from the decoded object
                int index = fabricatedStrings.size();
                byte[] content = new byte[v];
                java.util.Arrays.fill(content, (byte) 'a');
                content[0] = (byte) ('a' + (index / 26) % 26);
                if (v > 1) content[1] = (byte) ('a' + index % 26);
                fabricatedStrings.add(new String(content, java.nio.charset.StandardCharsets.US_ASCII));
                return content;
            }
            if (hasFrameMethod(stack, "readPublicKey") || hasFrame(stack, ".Crypt", "byteToPublicKey")
                    || (keyArrays && hasFrameMethod(stack, "readByteArray"))) {
                return rsaPublicKeyDer();
            }
            if (hasFrameMethod(stack, "readLongArray")) return new byte[v * 8];
            if (hasFrameMethod(stack, "readByteArray")) return new byte[v];
            return null;
        }

        @Override
        public void onRead(String op, int byteLength, long value, StackTraceElement[] stack) {
            List<Frame> context = trimStack(stack);
            events.add(new Event(op, byteLength, value, context, null));
        }

        @Override
        public void checkBudget(int totalOps) {
            if (totalOps > 40_000) throw new AbortDecode("read budget exceeded");
        }

        @Override
        public byte[] advanceReserve(StackTraceElement[] stack) {
            // NBT is read through a ByteBufInputStream that snapshots readableBytes() at
            // construction; make an empty compound tag (type 10, end marker 0) available.
            boolean streamCtor = false, nbtCaller = false;
            for (StackTraceElement e : stack) {
                if (e.getClassName().endsWith("ByteBufInputStream") && e.getMethodName().equals("<init>")) streamCtor = true;
                if (e.getClassName().contains(".nbt.") || e.getMethodName().startsWith("readNbt")) nbtCaller = true;
            }
            if (!streamCtor || !nbtCaller) return null;
            return nbtTextCompound ? NBT_TEXT_COMPOUND : new byte[]{10, 0};
        }

        static String siteKey(StackTraceElement[] stack) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement e : stack) {
                String cn = e.getClassName();
                if (cn.startsWith("net.minecraft") || cn.contains("$$Lambda")) {
                    sb.append(e.getClassName()).append('#').append(e.getMethodName()).append('#').append(e.getLineNumber()).append(';');
                }
            }
            return sb.toString();
        }
    }

    /** Classify what kind of value the pending read wants, from the call stack. */
    static String stackContextKind(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            String cn = e.getClassName();
            String mn = e.getMethodName();
            if (cn.endsWith(".VarInt") || cn.endsWith(".VarLong")) return "varint";
            if (mn.equals("readBoolean")) return "boolean";
            if (mn.equals("readEnumSet") || mn.equals("readFixedBitSet")) return "enumSet";
            if (cn.endsWith("Utf8String")) return "string";
            if (cn.contains(".nbt.")) {
                // The root tag id read comes through NbtIo.readAnyTag/readUnnamedTag directly.
                for (StackTraceElement e2 : stack) {
                    String m2 = e2.getMethodName();
                    if (e2.getClassName().endsWith(".NbtIo") && (m2.equals("readAnyTag") || m2.equals("readUnnamedTag") || m2.equals("readAnyTagOrNull"))) {
                        return isRootTagRead(stack) ? "nbtRootType" : "nbt";
                    }
                }
                return "nbt";
            }
        }
        return "other";
    }

    /** The tag-type byte read directly by NbtIo (not inside CompoundTag/ListTag loading) is the root type. */
    static boolean isRootTagRead(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            String cn = e.getClassName();
            if (cn.contains(".nbt.") && (cn.endsWith("CompoundTag") || cn.endsWith("ListTag") || cn.contains("Tag$"))) {
                return false;
            }
        }
        return true;
    }

    static boolean hasFrame(StackTraceElement[] stack, String classSuffix, String method) {
        for (StackTraceElement e : stack) {
            if (e.getClassName().endsWith(classSuffix) && e.getMethodName().equals(method)) return true;
        }
        return false;
    }

    static boolean hasUtf8Frame(List<Frame> context) {
        for (Frame f : context) {
            if (f.simpleClassName().equals("Utf8String") && f.methodName().equals("read")) return true;
        }
        return false;
    }

    static boolean hasFrameMethod(StackTraceElement[] stack, String method) {
        for (StackTraceElement e : stack) {
            if (e.getClassName().startsWith("net.minecraft") && e.getMethodName().equals(method)) return true;
        }
        return false;
    }

    /* ── stack trimming ─────────────────────────────────── */

    /**
     * Reduce a raw stack to the frames between the codec decode entry (exclusive) and the
     * buffer layer (exclusive), ordered outermost-first.
     */
    static List<Frame> trimStack(StackTraceElement[] stack) {
        List<Frame> frames = new ArrayList<>();
        for (StackTraceElement e : stack) {
            String cn = e.getClassName();
            if (cn.equals(DecodeTracer.class.getName())) break; // stop at our decode entry
            if (cn.startsWith("io.netty.")) continue;
            if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")) continue;
            if (cn.startsWith("com.google.") || cn.startsWith("it.unimi.")) continue;
            frames.add(new Frame(cn, e.getMethodName(), e.getLineNumber()));
        }
        // currently innermost-first; reverse to outermost-first
        java.util.Collections.reverse(frames);
        return frames;
    }

    /* ── decode driving ─────────────────────────────────── */

    static TraceOutcome runTrace(StreamCodec<?, ?> codec, int varintValue, boolean booleansTrue) {
        return run(codec, new Policy(varintValue, booleansTrue));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static TraceOutcome run(StreamCodec<?, ?> codec, Policy policy) {
        Policy template = policy.copy();
        FabricatingByteBuf fab = new FabricatingByteBuf(policy);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(fab, registryAccess);
        TraceOutcome out;
        try {
            Object result = decodeCall((StreamCodec) codec, buf);
            out = new TraceOutcome(result, policy.events, null, policy.fabricatedStrings, codec, template);
        } catch (Throwable t) {
            out = new TraceOutcome(null, policy.events, t, policy.fabricatedStrings, codec, template);
        }
        OUTCOME_BY_EVENTS.put(policy.events, out);
        if (!out.ok() && !template.keyArrays && mentionsPublicKey(out.failure())) {
            // the key went through a mapped byte array the stack cannot identify: retry with
            // every byte array holding a valid key
            Policy retry = template.copy();
            retry.keyArrays = true;
            return run(codec, retry);
        }
        return out;
    }

    static boolean mentionsPublicKey(Throwable t) {
        for (; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(java.util.Locale.ROOT).contains("public key")) return true;
        }
        return false;
    }

    /** Which trace an event list came from — tree leaves keep a pointer back for replays. */
    static final Map<List<Event>, TraceOutcome> OUTCOME_BY_EVENTS = new java.util.IdentityHashMap<>();

    /** Separate frame so trimStack can find the decode entry boundary. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object decodeCall(StreamCodec codec, Object buf) {
        return codec.decode(buf);
    }

    /** Try presence-maximizing policy first, then fall back. */
    static TraceOutcome trace(StreamCodec<?, ?> codec) {
        TraceOutcome first = null;
        int[][] policies = {{1, 1, 1}, {0, 0, 1}, {1, 1, 0}, {0, 0, 0}, {0, 1, 1}};
        for (int[] p : policies) {
            Policy policy = new Policy(p[0], p[1] == 1);
            policy.nbtTextCompound = p[2] == 1;
            TraceOutcome t = run(codec, policy);
            if (t.ok()) return t;
            if (first == null) first = t;
        }
        return first; // report first failure, with partial events
    }

    /** Decode with a fixed value for the first varint read (id/key probing). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static TraceOutcome runScripted(StreamCodec<?, ?> codec, int firstVarint) {
        Policy policy = new Policy(0, false);
        policy.varintScript.add(firstVarint);
        return run(codec, policy);
    }

    /* ── enum branch exploration ────────────────────────── */

    static TraceOutcome runTraceWithEnum(StreamCodec<?, ?> codec, int enumChoice) {
        Policy policy = new Policy(1, true);
        policy.enumChoice = enumChoice;
        TraceOutcome t = run(codec, policy);
        if (t.ok()) return t;
        Policy fallback = new Policy(1, true);
        fallback.enumChoice = enumChoice;
        fallback.nbtTextCompound = false;
        TraceOutcome t2 = run(codec, fallback);
        return t2.ok() ? t2 : t;
    }

    /**
     * Build the tree for a successful trace; if the decode passed through readEnum (an
     * enum-switched body, e.g. boss bar operations), explore every enum constant and emit
     * the differing tails as variants.
     */
    static boolean hasEnumBranching(List<Event> events) {
        for (Event ev : events) {
            for (Frame f : ev.context()) {
                if (f.methodName().equals("readEnum")) return true;
            }
        }
        return false;
    }

    static Map<String, Object> traceTreeWithBranches(StreamCodec<?, ?> codec, Class<?> produced, TraceOutcome base) {
        if (hasEnumBranching(base.events())) {
            Map<String, Object> enumTree = exploreEnumBranches(codec, produced, base);
            if (enumTree != null) return enumTree;
        }
        if (hasContext(base.events(), "enumSet")) {
            Map<String, Object> enumSetTree = exploreEnumSetBranches(codec, produced, base);
            if (enumSetTree != null) return enumSetTree;
        }
        if (hasPlainByteRead(base.events())) {
            Map<String, Object> byteTree = exploreByteBranches(codec, produced, base);
            if (byteTree != null) return byteTree;
        }
        Map<String, Object> dispatched = exploreRegistryDispatch(codec, produced, base);
        if (dispatched != null) return dispatched;
        return buildTree(base.events(), produced);
    }

    static boolean hasContext(List<Event> events, String kind) {
        for (Event ev : events) {
            if (ev.op().equals("readBytes") || ev.op().equals("readByte")) {
                for (Frame f : ev.context()) {
                    if (f.methodName().equals("readEnumSet") || f.methodName().equals("readFixedBitSet")) return true;
                }
            }
        }
        return false;
    }

    static boolean hasPlainByteRead(List<Event> events) {
        for (Event ev : events) {
            if (!ev.op().equals("readByte")) continue;
            boolean plain = true;
            for (Frame f : ev.context()) {
                String cn = f.className();
                String mn = f.methodName();
                if (cn.contains(".nbt.") || cn.endsWith(".VarInt") || cn.endsWith(".VarLong")
                        || cn.endsWith("Utf8String") || mn.equals("readBoolean")
                        || mn.equals("readEnumSet") || mn.equals("readFixedBitSet")) {
                    plain = false;
                    break;
                }
            }
            if (plain) return true;
        }
        return false;
    }

    /**
     * Manual switches on a plain byte (set_objective/set_player_team 'method'): re-decode
     * with the first plain byte scripted to 0..4 and emit differing bodies as variants.
     */
    static Map<String, Object> exploreByteBranches(StreamCodec<?, ?> codec, Class<?> produced, TraceOutcome base) {
        List<TraceOutcome> outs = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int v = 0; v <= 4; v++) {
            Policy policy = new Policy(1, true);
            policy.byteChoice = v;
            TraceOutcome out = run(codec, policy);
            if (!out.ok()) break;
            outs.add(out);
            keys.add(String.valueOf(v));
        }
        if (outs.size() <= 1) return null;
        boolean bitmask = constantFamily(produced) == null && isTwoBitMask(outs, produced);
        if (bitmask) {
            // 0x01 and 0x02 each add their own reads, 3 adds both, 4 adds nothing: presence
            // flags, not a switch — and 4 was only ever a probe
            outs = outs.subList(0, 4);
            keys = List.of("0", "1 (0x01)", "2 (0x02)", "3 (0x01 | 0x02)");
        }
        Map<String, Object> root = assembleBranches(outs, produced, keys.toArray(new String[0]),
                bitmask ? "fields present per flag combination" : "switch on the preceding byte");
        if (root == null) return null;
        if (bitmask) {
            Map<String, Object> keyNode = lastByteBeforeDispatch(root);
            if (keyNode != null) {
                keyNode.put("name", "flags");
                keyNode.remove("java");
                keyNode.put("note", "0x01 and 0x02 each mark an optional field as present");
            }
        } else {
            relabelByteSwitch(root, produced);
        }
        return root;
    }

    /** Probe bodies 0..4 behave like two independent presence bits. */
    static boolean isTwoBitMask(List<TraceOutcome> outs, Class<?> produced) {
        if (outs.size() != 5) return false;
        List<List<Unit>> ul = new ArrayList<>();
        for (TraceOutcome out : outs) ul.add(toUnits(out.events(), produced));
        int prefix = commonPrefix(ul);
        int tail = commonSuffix(ul, prefix);
        List<List<String>> bodies = new ArrayList<>();
        for (List<Unit> u : ul) {
            List<String> wires = new ArrayList<>();
            for (Unit unit : u.subList(prefix, u.size() - tail)) wires.add(unit.wire);
            bodies.add(wires);
        }
        List<String> both = new ArrayList<>(bodies.get(1));
        both.addAll(bodies.get(2));
        return bodies.get(0).isEmpty() && bodies.get(4).isEmpty()
                && !bodies.get(1).isEmpty() && !bodies.get(2).isEmpty() && both.equals(bodies.get(3));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> lastByteBeforeDispatch(Map<String, Object> root) {
        Map<String, Object> keyNode = null;
        for (Object f : (List<Object>) root.get("fields")) {
            Map<String, Object> node = asMap(f);
            if (node == null) continue;
            if ("dispatch".equals(node.get("kind"))) return keyNode;
            if ("value".equals(node.get("kind")) && ("Byte".equals(node.get("wire")) || "Unsigned Byte".equals(node.get("wire")))) {
                keyNode = node;
            }
        }
        return null;
    }

    /**
     * The probe tried 0..4 blindly; the packet class's own int constants say which values
     * exist. A family of powers of two (HAS_SOURCE=1, HAS_SOUND=2) is a bitmask: the byte is
     * named flags and only combinations of its bits remain. Otherwise (METHOD_ADD=0, ...) the
     * keys are the constants and probe values without one are dropped.
     */
    @SuppressWarnings("unchecked")
    static void relabelByteSwitch(Map<String, Object> root, Class<?> produced) {
        Map<Integer, String> family = constantFamily(produced);
        if (family == null) return;
        boolean bitmask = !family.containsKey(0) && family.keySet().stream().allMatch(v -> Integer.bitCount(v) == 1);
        int allBits = family.keySet().stream().mapToInt(Integer::intValue).reduce(0, (a, b) -> a | b);
        Map<String, Object> dispatch = null;
        Map<String, Object> keyNode = null;
        for (Object f : (List<Object>) root.get("fields")) {
            Map<String, Object> node = asMap(f);
            if (node == null) continue;
            if ("dispatch".equals(node.get("kind"))) {
                dispatch = node;
                break;
            }
            if ("value".equals(node.get("kind")) && ("Byte".equals(node.get("wire")) || "Unsigned Byte".equals(node.get("wire")))) {
                keyNode = node;
            }
        }
        if (dispatch == null) return;
        List<Object> kept = new ArrayList<>();
        for (Object v : (List<Object>) dispatch.get("variants")) {
            Map<String, Object> variant = asMap(v);
            List<String> labels = new ArrayList<>();
            for (String k : String.valueOf(variant.get("key")).split(", ")) {
                int value;
                try {
                    value = Integer.parseInt(k);
                } catch (NumberFormatException e) {
                    labels.add(k);
                    continue;
                }
                if (bitmask) {
                    if ((value & ~allBits) != 0) continue;
                    List<String> bits = new ArrayList<>();
                    for (var e : family.entrySet()) if ((value & e.getKey()) != 0) bits.add(e.getValue());
                    labels.add(value + (bits.isEmpty() ? "" : " (" + String.join(" | ", bits) + ")"));
                } else if (family.containsKey(value)) {
                    labels.add(family.get(value) + "=" + value);
                }
            }
            if (labels.isEmpty()) continue;
            variant.put("key", String.join(", ", labels));
            kept.add(variant);
        }
        dispatch.put("variants", kept);
        if (keyNode != null) {
            if (bitmask) {
                keyNode.putIfAbsent("name", "flags");
                List<String> bits = new ArrayList<>();
                for (var e : family.entrySet()) bits.add(String.format("0x%02x: %s", e.getKey(), e.getValue()));
                keyNode.put("note", String.join(", ", bits));
                dispatch.put("note", "fields present per flag combination");
            } else {
                keyNode.put("values", family.entrySet().stream().map(e -> e.getValue() + "=" + e.getKey()).toList());
            }
        }
    }

    /** The class's static final int constants in 0..255 sharing one name prefix (METHOD_*, HAS_*). */
    static Map<Integer, String> constantFamily(Class<?> cls) {
        if (cls == null) return null;
        Map<String, Map<Integer, String>> byPrefix = new LinkedHashMap<>();
        for (Field f : cls.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isFinal(m) || (f.getType() != int.class && f.getType() != byte.class)) continue;
            try {
                f.setAccessible(true);
                int v = ((Number) f.get(null)).intValue();
                if (v < 0 || v > 255) continue;
                int us = f.getName().indexOf('_');
                String prefix = us > 0 ? f.getName().substring(0, us) : f.getName();
                byPrefix.computeIfAbsent(prefix, k -> new java.util.TreeMap<>()).putIfAbsent(v, f.getName());
            } catch (Throwable ignored) {
            }
        }
        Map<Integer, String> best = null;
        for (Map<Integer, String> family : byPrefix.values()) {
            if (family.size() >= 2 && (best == null || family.size() > best.size())) best = family;
        }
        return best;
    }

    /**
     * EnumSet-driven sections (player_info_update): one decode per action bit; the set's
     * single element names the variant.
     */
    static Map<String, Object> exploreEnumSetBranches(StreamCodec<?, ?> codec, Class<?> produced, TraceOutcome base) {
        List<TraceOutcome> outs = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int bit = 0; bit < 24; bit++) {
            Policy policy = new Policy(1, true);
            policy.enumSetBit = bit;
            TraceOutcome out = run(codec, policy);
            if (!out.ok()) continue;
            String label = singleEnumSetElement(out.result());
            if (label == null) continue; // bit beyond the enum's size
            outs.add(out);
            keys.add(label);
        }
        if (outs.size() <= 1) return null;
        Map<String, Object> root = assembleBranches(outs, produced, keys.toArray(new String[0]), "one section per action in the set, in ordinal order");
        Class<?> enumClass = enumSetClass(outs.getFirst().result());
        if (root != null && enumClass != null) {
            forEachNode(root, n -> {
                if ("EnumSet".equals(n.get("wire")) && !n.containsKey("values")) {
                    List<String> names = new ArrayList<>();
                    for (Object c : enumClass.getEnumConstants()) names.add(((Enum<?>) c).name());
                    n.put("values", names);
                    n.put("java", "EnumSet<" + enumClass.getSimpleName() + ">");
                    n.put("note", "bit n set = constant n is in the set");
                }
            });
        }
        return root;
    }

    /** The enum class of the decoded object's single-element EnumSet field. */
    static Class<?> enumSetClass(Object result) {
        if (result == null) return null;
        for (Field f : result.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !java.util.EnumSet.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                if (f.get(result) instanceof java.util.EnumSet<?> set && !set.isEmpty()) {
                    return ((Enum<?>) set.iterator().next()).getDeclaringClass();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Name of the single element of the decoded packet's EnumSet field, if exactly one. */
    static String singleEnumSetElement(Object result) {
        if (result == null) return null;
        for (Field f : result.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !java.util.EnumSet.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                if (f.get(result) instanceof java.util.EnumSet<?> set && set.size() == 1) {
                    return set.iterator().next().name();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Shared prefix/variants/tail assembly for scripted-branch explorations. */
    static Map<String, Object> assembleBranches(List<TraceOutcome> outs, Class<?> produced, String[] keys, String note) {
        List<List<Unit>> unitLists = new ArrayList<>();
        for (TraceOutcome out : outs) unitLists.add(toUnits(out.events(), produced));
        int prefix = commonPrefix(unitLists);
        int tail = commonSuffix(unitLists, prefix);

        Map<String, Object> root = treeFromUnits(unitLists.getFirst().subList(0, prefix), produced);
        VariantLabeling labeling = variantLabels(outs);
        String[] labels = labeling != null && labeling.enumClass() != null ? labeling.labels() : keys;
        List<Object> variants = buildVariantBodies(unitLists, prefix, tail, outs,
                new VariantLabeling(labels, labeling != null ? labeling.enumClass() : null,
                        labeling != null ? labeling.fieldName() : null, labeling != null ? labeling.field() : null), true);
        if (variants == null) return null;

        Map<String, Object> dispatch = new LinkedHashMap<>();
        dispatch.put("kind", "dispatch");
        dispatch.put("note", note);
        dispatch.put("variants", variants);
        @SuppressWarnings("unchecked")
        List<Object> rootFields = (List<Object>) root.get("fields");

        // When every variant body is a lone list and the prefix ends in a list, the sections
        // decode once per element of THAT list (player_info_update's per-entry action data):
        // the dispatch belongs inside the list element, after the shared element prefix.
        Map<String, Object> host = rootFields.isEmpty() ? null : asMap(rootFields.getLast());
        boolean folded = host != null && "list".equals(host.get("kind")) && unwrapElementLists(variants);
        stripPrefixNames(rootFields, variants, labeling != null ? labeling.fieldName() : null);
        if (folded) {
            List<Object> elemFields = new ArrayList<>();
            Map<String, Object> elem = asMap(host.get("elem"));
            if (elem != null && "container".equals(elem.get("kind")) && elem.get("fields") instanceof List<?> ef) {
                elemFields.addAll((List<Object>) ef);
            } else if (elem != null) {
                elemFields.add(elem);
            }
            elemFields.add(dispatch);
            Map<String, Object> container = new LinkedHashMap<>();
            container.put("kind", "container");
            container.put("fields", elemFields);
            host.put("elem", container);
        } else {
            rootFields.add(dispatch);
        }
        appendTail(rootFields, unitLists.getFirst(), tail, produced);
        return root;
    }

    /**
     * A field name can only appear once in a packet: a variant-body node named like a field
     * the prefix already consumed got its name from a garbage assignment — and so did its
     * siblings from the same matching run, so the whole fields list is cleared. The
     * switched-on field's own name is exempt: the variant value group legitimately shares
     * it with the prefix key row (boss_event's operation, level_particles' particle).
     */
    static void stripPrefixNames(List<Object> prefixFields, List<Object> variants, String switchedField) {
        java.util.Set<Object> taken = new java.util.HashSet<>();
        for (Object f : prefixFields) {
            Object name = asMap(f) == null ? null : asMap(f).get("name");
            if (name != null && !name.equals(switchedField)) taken.add(name);
        }
        if (taken.isEmpty()) return;
        for (Object v : variants) {
            Map<String, Object> body = asMap(asMap(v).get("body"));
            if (body != null) stripTakenNames(body, taken);
        }
    }

    @SuppressWarnings("unchecked")
    static void stripTakenNames(Map<String, Object> node, java.util.Set<Object> taken) {
        if (node.get("fields") instanceof List<?> l) {
            boolean collided = false;
            for (Object f : l) {
                Map<String, Object> child = asMap(f);
                if (child != null && taken.contains(child.get("name"))) {
                    collided = true;
                    break;
                }
            }
            for (Object f : l) {
                Map<String, Object> child = asMap(f);
                if (child == null) continue;
                if (collided) {
                    child.remove("name");
                    child.remove("java");
                    child.remove("values");
                }
                stripTakenNames(child, taken);
            }
        }
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct", "body"}) {
            if (node.get(key) instanceof Map<?, ?> m) stripTakenNames((Map<String, Object>) m, taken);
        }
        if (node.get("variants") instanceof List<?> vl) {
            for (Object v : vl) {
                if (v instanceof Map<?, ?> m) stripTakenNames((Map<String, Object>) m, taken);
            }
        }
    }

    /**
     * If each variant body consists of exactly one list node, replace the bodies with the
     * lists' element content and return true; leaves the bodies untouched otherwise.
     */
    @SuppressWarnings("unchecked")
    static boolean unwrapElementLists(List<Object> variants) {
        for (Object v : variants) {
            Map<String, Object> body = asMap(asMap(v).get("body"));
            if (!(body.get("fields") instanceof List<?> f) || f.size() != 1) return false;
            Map<String, Object> only = asMap(f.getFirst());
            if (only == null || !"list".equals(only.get("kind"))) return false;
        }
        for (Object v : variants) {
            Map<String, Object> body = asMap(asMap(v).get("body"));
            Map<String, Object> list = asMap(((List<?>) body.get("fields")).getFirst());
            Map<String, Object> elem = asMap(list.get("elem"));
            body.put("fields", elem != null && "container".equals(elem.get("kind")) && elem.get("fields") instanceof List<?> ef
                    ? new ArrayList<>((List<Object>) ef)
                    : new ArrayList<>(List.of(elem != null ? elem : list)));
        }
        return true;
    }

    static Map<String, Object> exploreEnumBranches(StreamCodec<?, ?> codec, Class<?> produced, TraceOutcome base) {
        List<TraceOutcome> outs = new ArrayList<>();
        for (int e = 0; e < 64; e++) {
            TraceOutcome out = runTraceWithEnum(codec, e);
            if (!out.ok()) break;
            outs.add(out);
        }
        if (outs.size() <= 1) return null;

        List<List<Unit>> unitLists = new ArrayList<>();
        for (TraceOutcome out : outs) unitLists.add(toUnits(out.events(), produced));
        int prefix = commonPrefix(unitLists);
        int tail = commonSuffix(unitLists, prefix);

        Map<String, Object> root = treeFromUnits(unitLists.getFirst().subList(0, prefix), produced);
        VariantLabeling labeling = variantLabels(outs);
        List<Object> variants = buildVariantBodies(unitLists, prefix, tail, outs, labeling, true);
        if (variants == null) return null; // no switched body / all identical

        @SuppressWarnings("unchecked")
        List<Object> rootFields = (List<Object>) root.get("fields");

        // Enrich the switched-on enum row with its name and constants
        if (labeling != null && labeling.enumClass() != null) {
            for (int i = rootFields.size() - 1; i >= 0; i--) {
                @SuppressWarnings("unchecked")
                Map<String, Object> node = (Map<String, Object>) rootFields.get(i);
                if ("value".equals(node.get("kind")) && "Enum".equals(node.get("wire"))) {
                    if (labeling.fieldName() != null) node.putIfAbsent("name", labeling.fieldName());
                    attachEnumValues(node, labeling.enumClass());
                    break;
                }
            }
        }

        stripPrefixNames(rootFields, variants, labeling != null ? labeling.fieldName() : null);

        Map<String, Object> dispatch = new LinkedHashMap<>();
        dispatch.put("kind", "dispatch");
        dispatch.put("note", labeling != null && labeling.fieldName() != null
                ? "switch on " + labeling.fieldName()
                : "switch on the preceding enum");
        dispatch.put("variants", variants);
        rootFields.add(dispatch);
        appendTail(rootFields, unitLists.getFirst(), tail, produced);
        return root;
    }

    /** Common trailing units across all variants continue after the dispatch at top level. */
    @SuppressWarnings("unchecked")
    static void appendTail(List<Object> rootFields, List<Unit> units, int tail, Class<?> produced) {
        if (tail <= 0) return;
        Map<String, Object> tailTree = treeFromUnits(units.subList(units.size() - tail, units.size()), null);
        if (tailTree.get("fields") instanceof List<?> l) {
            nameTailByFieldType((List<Object>) l, rootFields, produced);
            for (Object field : l) rootFields.add(field);
        }
    }

    /**
     * A multi-read tail with no naming context is usually one remaining packet field decoded
     * by an inline composite (player_chat's trailing ChatType.Bound): when exactly one
     * still-unclaimed field's type names every tail node, adopt that type's field names.
     */
    static void nameTailByFieldType(List<Object> tailNodes, List<Object> prefixFields, Class<?> produced) {
        if (produced == null || tailNodes.size() < 2 || hasAnyName(tailNodes)) return;
        java.util.Set<Object> claimed = new java.util.HashSet<>();
        for (Object f : prefixFields) {
            Map<String, Object> node = asMap(f);
            if (node != null && node.get("name") != null) claimed.add(node.get("name"));
        }
        Class<?> match = null;
        for (Field f : instanceFields(produced)) {
            if (claimed.contains(f.getName()) || !fullNameMatch(tailNodes, f.getType())) continue;
            if (match != null) return; // ambiguous
            match = f.getType();
        }
        if (match != null) nameChildren(tailNodes, match);
    }

    /** Whether naming against this class assigns a name to every node (checked on a copy). */
    static boolean fullNameMatch(List<Object> nodes, Class<?> cls) {
        List<Object> copy = new ArrayList<>();
        for (Object n : nodes) copy.add(copyTree(n));
        nameChildren(copy, cls);
        for (Object n : copy) {
            if (asMap(n) == null || asMap(n).get("name") == null) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    static Object copyTree(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : ((Map<String, Object>) m).entrySet()) out.put(e.getKey(), copyTree(e.getValue()));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(copyTree(o));
            return out;
        }
        return value;
    }

    static int commonSuffix(List<List<Unit>> unitLists, int prefix) {
        int minLen = Integer.MAX_VALUE;
        for (List<Unit> ul : unitLists) minLen = Math.min(minLen, ul.size());
        int maxTail = minLen - prefix;
        int tail = 0;
        outer:
        while (tail < maxTail) {
            List<Unit> firstList = unitLists.getFirst();
            Unit ref = firstList.get(firstList.size() - 1 - tail);
            for (List<Unit> ul : unitLists) {
                Unit u = ul.get(ul.size() - 1 - tail);
                if (!u.invocationKey.equals(ref.invocationKey) || !u.wire.equals(ref.wire)) break outer;
            }
            tail++;
        }
        return tail;
    }

    /**
     * Assemble per-variant suffix trees, naming their fields via the class of the value that
     * distinguishes the variant (e.g. DustParticleOptions). Null when no variant has a body
     * or all bodies are identical (no real branching).
     */
    static List<Object> buildVariantBodies(List<List<Unit>> unitLists, int prefix, int tail, List<TraceOutcome> outs,
                                           VariantLabeling labeling, boolean packetReads) {
        List<Object> variants = new ArrayList<>();
        boolean anyBody = false;
        for (int i = 0; i < unitLists.size(); i++) {
            List<Unit> all = unitLists.get(i);
            List<Unit> suffix = all.subList(Math.min(prefix, all.size()), all.size() - tail);
            Map<String, Object> body = treeFromUnits(suffix, null);
            body.put("kind", "container");
            unwrapDispatchShell(body);
            if (body.get("fields") instanceof List<?> l) {
                @SuppressWarnings("unchecked")
                List<Object> fields = (List<Object>) l;
                VariantValue value = variantValueClass(outs.get(i), labeling);
                Map<String, Object> valueRef = value == null ? null : refProducing(fields, value.cls());
                if (valueRef != null) {
                    // a spliced shared type decodes the whole value (set_player_team's Parameters)
                    if (value.fieldName() != null) valueRef.putIfAbsent("name", value.fieldName());
                    if (packetReads) {
                        Class<?> packetClass = outs.get(i).result() == null ? null : outs.get(i).result().getClass();
                        nameUnclaimedVariantFields(fields, packetClass, null);
                    }
                } else if (value != null) {
                    // a single structured group in the body IS the variant's value field
                    Map<String, Object> soleGroup = soleStructuredChild(fields);
                    if (soleGroup != null) {
                        if (value.fieldName() != null) soleGroup.putIfAbsent("name", value.fieldName());
                        soleGroup.putIfAbsent("java", value.cls().getSimpleName());
                        // a Dispatch group's children are the key and the dispatched body,
                        // not the value's fields — matching them fabricates names
                        if (!"Dispatch".equals(soleGroup.get("context"))
                                && soleGroup.get("fields") instanceof List<?> groupFields) {
                            nameChildren((List<Object>) groupFields, value.cls());
                        }
                        // reads next to the value group belong to the value class first
                        // (vibration's arrivalInTicks), else — for switches the packet
                        // itself decodes — to the packet (set_player_team's players)
                        if (!nameUnclaimedVariantFields(fields, value.cls(), soleGroup) && packetReads) {
                            Class<?> packetClass = outs.get(i).result() == null ? null : outs.get(i).result().getClass();
                            nameUnclaimedVariantFields(fields, packetClass, null);
                        }
                        // the value's own group as the body's only member is a redundant level
                        if (fields.size() == 1 && fields.getFirst() == soleGroup
                                && soleGroup.get("fields") instanceof List<?> inner && !"Dispatch".equals(soleGroup.get("context"))) {
                            body.put("java", soleGroup.get("java"));
                            body.put("fields", new ArrayList<>((List<Object>) inner));
                        }
                    } else {
                        nameChildren(fields, value.cls());
                    }
                } else {
                    // fields read directly by the packet (set_objective displayName etc.)
                    Class<?> packetClass = outs.get(i).result() == null ? null : outs.get(i).result().getClass();
                    if (packetClass != null && !hasAnyName(fields)) {
                        if (fields.size() >= 2) {
                            nameChildren(fields, packetClass);
                        } else {
                            // single reads are too ambiguous for ordered matching; name only
                            // when affinity forces a unique field (stop_sound source/name)
                            List<Field> unique = uniqueAffinityMatch(fields, instanceFields(packetClass), false);
                            if (unique != null) applyNames(fields, unique);
                        }
                    }
                }
            }
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("key", labeling != null ? labeling.labels()[i] : "case " + i);
            variant.put("body", body);
            if (!((List<?>) body.get("fields")).isEmpty()) anyBody = true;
            variants.add(variant);
        }
        if (!anyBody) return null;
        // merged only after replay naming (mergeTracedVariants): bodies that look alike now may
        // still name different fields (number parsers' min vs max)
        return CodecWalker.mergeVariants(copyVariants(variants)).size() <= 1 ? null : variants;
    }

    @SuppressWarnings("unchecked")
    static List<Object> copyVariants(List<Object> variants) {
        List<Object> out = new ArrayList<>();
        for (Object v : variants) out.add(copyTree(v));
        return out;
    }

    /** Merge identical variant bodies throughout a finished traced tree. */
    @SuppressWarnings("unchecked")
    static void mergeTracedVariants(Map<String, Object> tree) {
        forEachNode(tree, n -> {
            if ("dispatch".equals(n.get("kind")) && n.get("variants") instanceof List<?> l) {
                n.put("variants", CodecWalker.mergeVariants((List<Object>) l));
            }
        });
    }

    static boolean hasAnyName(List<Object> fields) {
        for (Object f : fields) {
            if (f instanceof Map<?, ?> m && m.get("name") != null) return true;
        }
        return false;
    }

    /**
     * Names a variant body's still-unnamed reads against the given class's fields that no
     * named node claimed yet; names inside {@code alsoClaimed} count as taken too.
     * Returns whether names were assigned.
     */
    static boolean nameUnclaimedVariantFields(List<Object> fields, Class<?> cls, Map<String, Object> alsoClaimed) {
        if (cls == null) return false;
        List<Object> unnamed = new ArrayList<>();
        java.util.Set<Object> claimed = new java.util.HashSet<>();
        for (Object f : fields) {
            Map<String, Object> node = asMap(f);
            if (node == null) continue;
            if (node.get("name") != null) claimed.add(node.get("name"));
            else unnamed.add(f);
        }
        if (alsoClaimed != null && alsoClaimed.get("fields") instanceof List<?> l) {
            for (Object f : l) {
                Map<String, Object> node = asMap(f);
                if (node != null && node.get("name") != null) claimed.add(node.get("name"));
            }
        }
        if (unnamed.isEmpty()) return false;
        List<Field> open = new ArrayList<>();
        for (Field f : instanceFields(cls)) {
            if (!claimed.contains(f.getName())) open.add(f);
        }
        List<Field> match = uniqueAffinityMatch(unnamed, open, false);
        if (match == null) return false;
        applyNames(unnamed, match);
        return true;
    }

    record VariantValue(Class<?> cls, String fieldName) {}

    /** The one ref child whose shared type decodes to (a subtype of) {@code cls}. */
    static Map<String, Object> refProducing(List<Object> fields, Class<?> cls) {
        Map<String, Object> found = null;
        for (Object f : fields) {
            Map<String, Object> node = asMap(f);
            if (node == null || !"ref".equals(node.get("kind"))) continue;
            Class<?> produced = CodecWalker.TYPE_CLASSES.get(String.valueOf(node.get("ref")));
            if (produced == null || !cls.isAssignableFrom(produced)) continue;
            if (found != null) return null;
            found = node;
        }
        return found;
    }

    /** The single group/container child among plain values, if any. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> soleStructuredChild(List<Object> fields) {
        Map<String, Object> found = null;
        for (Object f : fields) {
            Map<String, Object> node = (Map<String, Object>) f;
            String kind = (String) node.get("kind");
            if (("group".equals(kind) || "container".equals(kind)) && node.get("fields") instanceof List<?>) {
                if (found != null) return null; // ambiguous
                found = node;
            }
        }
        return found;
    }

    /**
     * The class whose fields name a variant body: the labeling field's value, or any field
     * holding an instance of an inner class of the packet (e.g. SetPlayerTeamPacket.Parameters).
     */
    static VariantValue variantValueClass(TraceOutcome out, VariantLabeling labeling) {
        Object result = out.result();
        if (result == null) return null;
        try {
            if (labeling != null && labeling.field() != null) {
                Object value = labeling.field().get(result);
                if (value instanceof java.util.Optional<?> opt) value = opt.orElse(null);
                // a switch key (set_objective's method int) is not the variant's value
                if (value != null && !value.getClass().getName().startsWith("java.") && !(value instanceof Enum<?>)) {
                    return new VariantValue(value.getClass(), labeling.field().getName());
                }
            }
            for (Field f : result.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object value = f.get(result);
                if (value instanceof java.util.Optional<?> opt) value = opt.orElse(null);
                if (value != null && value.getClass().getEnclosingClass() == result.getClass()) {
                    return new VariantValue(value.getClass(), f.getName());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Variant bodies decode inside the dispatch codec — its group wrapper is redundant there. */
    @SuppressWarnings("unchecked")
    static void unwrapDispatchShell(Map<String, Object> body) {
        while (body.get("fields") instanceof List<?> l && l.size() == 1
                && l.getFirst() instanceof Map<?, ?> only
                && "group".equals(((Map<String, Object>) only).get("kind"))
                && "Dispatch".equals(((Map<String, Object>) only).get("context"))) {
            body.put("fields", ((Map<String, Object>) only).get("fields"));
        }
    }

    /* ── registry-dispatch exploration inside manual packets ── */

    /**
     * A traced packet that passes through a dispatch codec (e.g. level_particles' particle
     * options) samples only one variant. Re-decode with every registry id scripted into the
     * dispatch key read and emit the differing tails as variants.
     */
    static Map<String, Object> exploreRegistryDispatch(StreamCodec<?, ?> codec, Class<?> produced, TraceOutcome base) {
        int before = varintInvocationsBeforeDispatchKey(base.events());
        if (before < 0) return null;

        List<TraceOutcome> outs = new ArrayList<>();
        for (int k = 0; k < 512; k++) {
            Policy policy = new Policy(1, true);
            for (int i = 0; i < before; i++) policy.varintScript.add(1);
            policy.varintScript.add(k);
            TraceOutcome out = run(codec, policy);
            if (!out.ok()) break;
            outs.add(out);
        }
        if (outs.size() <= 1) return null;

        List<List<Unit>> unitLists = new ArrayList<>();
        for (TraceOutcome out : outs) unitLists.add(toUnits(out.events(), produced));
        int prefix = commonPrefix(unitLists);
        int tail = commonSuffix(unitLists, prefix);

        Map<String, Object> root = treeFromUnits(unitLists.getFirst().subList(0, prefix), produced);
        VariantLabeling labeling = dispatchLabels(outs);
        // registry-dispatch bodies decode the dispatched value only — never packet fields
        List<Object> variants = buildVariantBodies(unitLists, prefix, tail, outs, labeling, false);
        if (variants == null) return null;

        Map<String, Object> dispatch = new LinkedHashMap<>();
        dispatch.put("kind", "dispatch");
        dispatch.put("note", "switch on the preceding registry id");
        dispatch.put("variants", variants);
        @SuppressWarnings("unchecked")
        List<Object> rootFields = (List<Object>) root.get("fields");
        stripPrefixNames(rootFields, variants, labeling != null ? labeling.fieldName() : null);

        // If the key sits inside an optional (Optional<NumberFormat> etc.), keep the
        // dispatch inside it rather than as a detached sibling.
        Map<String, Object> host = rootFields.isEmpty() ? null : asMap(rootFields.getLast());
        if (host != null && "optional".equals(host.get("kind"))
                && host.get("inner") instanceof Map<?, ?> inner
                && "value".equals(((Map<String, Object>) inner).get("kind"))) {
            Map<String, Object> container = new LinkedHashMap<>();
            container.put("kind", "container");
            List<Object> containerFields = new ArrayList<>();
            containerFields.add(inner);
            containerFields.add(dispatch);
            container.put("fields", containerFields);
            host.put("inner", container);
        } else {
            rootFields.add(dispatch);
        }
        appendTail(rootFields, unitLists.getFirst(), tail, produced);
        return root;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    /**
     * Number of scripted varint reads before the first dispatch-key read, or -1 when the
     * trace never passes a dispatch codec. String lengths, public keys and enum reads take
     * their values from context, not the script, and are not counted.
     */
    static int varintInvocationsBeforeDispatchKey(List<Event> events) {
        int count = 0;
        for (Event ev : events) {
            boolean varintRead = false, scripted = true, dispatchKey = false;
            for (Frame f : ev.context()) {
                if (f.simpleClassName().equals("VarInt") && f.methodName().equals("read")) varintRead = true;
                if (f.simpleClassName().equals("Utf8String") || f.methodName().equals("readPublicKey")
                        || f.methodName().equals("readEnum") || f.methodName().equals("byteToPublicKey")) {
                    scripted = false;
                }
                if ("dispatch".equals(ShapeRegistry.kindOfClassName(f.className()))) dispatchKey = true;
            }
            if (!varintRead) continue;
            if (dispatchKey) return count;
            if (scripted) count++;
        }
        return -1;
    }

    static int commonPrefix(List<List<Unit>> unitLists) {
        int prefix = 0;
        outer:
        while (prefix < unitLists.getFirst().size()) {
            Unit ref = unitLists.getFirst().get(prefix);
            for (List<Unit> ul : unitLists) {
                if (prefix >= ul.size() || !ul.get(prefix).invocationKey.equals(ref.invocationKey)
                        || !ul.get(prefix).wire.equals(ref.wire)) {
                    break outer;
                }
            }
            prefix++;
        }
        return prefix;
    }

    /** Label variants by looking up the differing field's value (or a value it exposes) in the registries. */
    static VariantLabeling dispatchLabels(List<TraceOutcome> outs) {
        Object first = outs.getFirst().result();
        if (first == null) return null;
        for (Field f : first.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                String[] labels = new String[outs.size()];
                java.util.Set<String> distinct = new java.util.HashSet<>();
                for (int i = 0; i < outs.size(); i++) {
                    Object v = outs.get(i).result() == null ? null : f.get(outs.get(i).result());
                    String label = v == null ? null : registryIdOf(v);
                    if (label == null) {
                        labels = null;
                        break;
                    }
                    labels[i] = label;
                    distinct.add(label);
                }
                if (labels != null && distinct.size() == outs.size()) {
                    return new VariantLabeling(labels, null, f.getName(), f);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static List<net.minecraft.core.Registry<?>> registryList;
    static final Map<Class<?>, java.lang.reflect.Method> REGISTRY_KEY_METHODS = new HashMap<>();

    /** Registry id of a value — directly, or via a no-arg accessor returning a registered object (e.g. ParticleOptions#getType). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String registryIdOf(Object value) {
        if (value instanceof java.util.Optional<?> opt) {
            value = opt.orElse(null);
            if (value == null) return null;
        }
        if (registryList == null) {
            registryList = new ArrayList<>();
            registryAccess.registries().forEach(e -> registryList.add(e.value()));
        }
        String direct = lookupInRegistries(value);
        if (direct != null) return direct;

        java.lang.reflect.Method cached = REGISTRY_KEY_METHODS.get(value.getClass());
        if (cached != null) {
            try {
                return lookupInRegistries(cached.invoke(value));
            } catch (Throwable ignored) {
                return null;
            }
        }
        for (java.lang.reflect.Method m : value.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || m.getReturnType().isPrimitive()) continue;
            if (!m.getDeclaringClass().getName().startsWith("net.minecraft")) continue;
            try {
                m.setAccessible(true); // the declaring class may be package-private
                Object inner = m.invoke(value);
                if (inner == null || inner == value) continue;
                String id = lookupInRegistries(inner);
                if (id != null) {
                    REGISTRY_KEY_METHODS.put(value.getClass(), m);
                    return id;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String lookupInRegistries(Object value) {
        for (net.minecraft.core.Registry<?> registry : registryList) {
            try {
                // getResourceKey, not getKey: defaulted registries answer getKey with their
                // default entry (minecraft:air) for foreign objects
                var key = ((net.minecraft.core.Registry) registry).getResourceKey(value);
                if (key.isPresent()) return ((net.minecraft.resources.ResourceKey<?>) key.get()).identifier().toString();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    record VariantLabeling(String[] labels, Class<?> enumClass, String fieldName, Field field) {}

    /**
     * Label variants by the field of the decoded packet whose value differs across runs —
     * by enum constant name if it is (or contains) an enum, else by value class.
     */
    static VariantLabeling variantLabels(List<TraceOutcome> outs) {
        Object first = outs.getFirst().result();
        if (first == null) return null;
        VariantLabeling byClass = null;
        // the whole hierarchy: subclass-per-variant values keep their kind in a superclass
        // field (TrackedWaypoint.type)
        for (Field f : instanceFields(first.getClass())) {
            try {
                f.setAccessible(true);
                String[] labels = new String[outs.size()];
                java.util.Set<String> distinct = new java.util.HashSet<>();
                Class<?> enumClass = null;
                for (int i = 0; i < outs.size(); i++) {
                    Object v = outs.get(i).result() == null ? null : f.get(outs.get(i).result());
                    if (v instanceof java.util.Optional<?> opt) v = opt.orElse(null);
                    Enum<?> e = v instanceof Enum<?> direct ? direct : innerEnum(v);
                    String label;
                    if (e != null) {
                        label = e.name();
                        enumClass = e.getDeclaringClass();
                    } else {
                        label = v == null ? "null" : classLabel(v);
                    }
                    if (label.isEmpty()) label = "case " + i;
                    labels[i] = label;
                    distinct.add(label);
                }
                if (distinct.size() == outs.size()) {
                    VariantLabeling labeling = new VariantLabeling(labels, enumClass, f.getName(), f);
                    if (enumClass != null) return labeling; // enum names beat class names
                    if (byClass == null) byClass = labeling;
                }
            } catch (Throwable ignored) {
            }
        }
        // polymorphic values (boss_event's AddOperation, ...) usually expose their kind
        // through an enum accessor on the shared supertype (Operation#getType)
        if (byClass != null && byClass.field() != null) {
            VariantLabeling byAccessor = accessorLabels(outs, byClass.field());
            if (byAccessor != null) return byAccessor;
        }
        if (byClass == null) {
            VariantLabeling byAccessor = accessorLabels(outs, null);
            if (byAccessor != null) return byAccessor;
            // subclass-per-variant packets (TrackedWaypoint): the decoded objects' own
            // classes label the variants when no single field distinguishes them
            String[] labels = new String[outs.size()];
            java.util.Set<String> distinct = new java.util.HashSet<>();
            for (int i = 0; i < outs.size(); i++) {
                Object r = outs.get(i).result();
                if (r == null || (labels[i] = classLabel(r)).isEmpty()) return null;
                distinct.add(labels[i]);
            }
            if (distinct.size() == outs.size()) return new VariantLabeling(labels, null, null, null);
        }
        return byClass;
    }

    /**
     * Labels from a no-arg enum accessor declared on a supertype shared by every variant's
     * value (the field's value, or the decoded object itself when {@code field} is null),
     * when it tells all variants apart.
     */
    static VariantLabeling accessorLabels(List<TraceOutcome> outs, Field field) {
        List<Object> values = new ArrayList<>();
        try {
            for (TraceOutcome out : outs) {
                Object v = out.result() == null ? null : field == null ? out.result() : field.get(out.result());
                if (v instanceof java.util.Optional<?> opt) v = opt.orElse(null);
                if (v == null) return null;
                values.add(v);
            }
        } catch (Throwable t) {
            return null;
        }
        for (java.lang.reflect.Method m : values.getFirst().getClass().getMethods()) {
            if (m.getParameterCount() != 0 || !m.getReturnType().isEnum()
                    || !m.getDeclaringClass().getName().startsWith("net.minecraft")) {
                continue;
            }
            try {
                // every value's class implements the accessor (Operation#getType lives on each
                // private implementation, so match by name rather than declaring class)
                String[] labels = new String[values.size()];
                java.util.Set<String> distinct = new java.util.HashSet<>();
                for (int i = 0; i < values.size(); i++) {
                    java.lang.reflect.Method own = values.get(i).getClass().getMethod(m.getName());
                    if (own.getReturnType() != m.getReturnType()) throw new NoSuchMethodException();
                    own.setAccessible(true);
                    labels[i] = ((Enum<?>) own.invoke(values.get(i))).name();
                    distinct.add(labels[i]);
                }
                if (distinct.size() == values.size()) {
                    return new VariantLabeling(labels, m.getReturnType(), field != null ? field.getName() : null, field);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * A value's display label: its class name, except anonymous singletons (boss_event's
     * REMOVE_OPERATION) have none — probe a no-arg enum accessor (getType) for a constant.
     */
    static String classLabel(Object value) {
        String simple = value.getClass().getSimpleName();
        if (!simple.isEmpty()) return simple;
        for (java.lang.reflect.Method m : value.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || !m.getReturnType().isEnum()
                    || !m.getDeclaringClass().getName().startsWith("net.minecraft")) {
                continue;
            }
            try {
                m.setAccessible(true);
                if (m.invoke(value) instanceof Enum<?> e) return e.name();
            } catch (Throwable ignored) {
            }
        }
        return simple;
    }

    /** The single enum among an object's fields (e.g. FilterMask.type), if any. */
    static Enum<?> innerEnum(Object value) {
        if (value == null || value.getClass().getName().startsWith("java.")) return null;
        Enum<?> found = null;
        for (Field f : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !f.getType().isEnum()) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(value);
                if (v instanceof Enum<?> e) {
                    if (found != null) return null; // ambiguous
                    found = e;
                }
            } catch (Throwable ignored) {
            }
        }
        return found;
    }

    /* ── hand patches ───────────────────────────────────── */

    /**
     * Targeted fixes for flag/conditional reads whose meaning tracing cannot recover: the
     * fabricated buffer answers flag reads with 0, hiding fields behind flag bits, and
     * packed bytes have no matching Java field to name them. Misses are reported loudly so
     * a format change in a future version surfaces during generation.
     */
    static void applyHandPatches(Map<String, Object> tree, Class<?> produced) {
        if (produced == null) return;
        switch (PacketExtractor.nestedName(produced)) {
            case "DisplayInfo" -> patchDisplayInfo(tree);
            case "ClientboundBossEventPacket" -> patchFlagBytes(tree, 2,
                    "0x01: darken screen, 0x02: play boss music, 0x04: create world fog");
            case "ServerboundSetStructureBlockPacket" -> patchStructureBlock(tree);
            case "ServerboundMovePlayerPacket.Pos", "ServerboundMovePlayerPacket.PosRot",
                 "ServerboundMovePlayerPacket.Rot", "ServerboundMovePlayerPacket.StatusOnly" ->
                    patchFlagByte(tree, "0x01: on ground, 0x02: horizontal collision");
            case "ServerboundSetCommandBlockPacket" ->
                    patchFlagByte(tree, "0x01: track output, 0x02: conditional, 0x04: automatic");
            case "ClientboundPlayerAbilitiesPacket" ->
                    patchFlagByte(tree, "0x01: invulnerable, 0x02: flying, 0x04: may fly, 0x08: instabuild (creative)");
            case "ServerboundPlayerAbilitiesPacket" ->
                    patchFlagByte(tree, "0x02: flying; other bits are ignored");
            case "ClientboundDamageEventPacket" -> patchDamageEvent(tree);
            case "ClientboundPlayerInfoUpdatePacket" -> patchPlayerInfo(tree);
            case "DiscardedPayload" -> patchDiscardedPayload(tree);
            case "ClientboundMoveEntityPacket.Pos", "ClientboundMoveEntityPacket.PosRot" -> patchMoveEntity(tree);
            case "ServerboundCustomQueryAnswerPacket" -> patchCustomQueryAnswer(tree);
            default -> { }
        }
    }

    /**
     * move_entity_pos(_rot): the second VarInt packs on-ground and a step count, and VecDelta
     * reads either one short triple (no steps) or a ticks+triple per step.
     */
    @SuppressWarnings("unchecked")
    static void patchMoveEntity(Map<String, Object> tree) {
        List<Object> fields = (List<Object>) tree.get("fields");
        int deltaAt = -1;
        boolean hasVecDelta = false;
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> n = asMap(fields.get(i));
            hasVecDelta |= "VecDelta".equals(n.get("context"));
            if ("group".equals(n.get("kind")) && "VecDelta".equals(n.get("context"))
                    && n.get("fields") instanceof List<?> l && l.size() == 3) {
                deltaAt = i;
            }
        }
        if (!hasVecDelta) return; // before 26.3: three plain shorts, nothing packed
        if (deltaAt < 1 || !"VarInt".equals(asMap(fields.get(deltaAt - 1)).get("wire"))) {
            System.err.println("[patch] move-entity patch missed the VecDelta");
            return;
        }
        Map<String, Object> properties = asMap(fields.get(deltaAt - 1));
        properties.put("name", "properties");
        properties.put("java", "int");
        properties.put("note", "0x01: on ground; properties >> 1 = step count");
        List<Object> triple = (List<Object>) asMap(fields.get(deltaAt)).get("fields");

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("kind", "container");
        step.put("name", "steps");
        step.put("java", "DeltaStep");
        step.put("note", "repeated step count times");
        List<Object> stepFields = new ArrayList<>();
        stepFields.add(new LinkedHashMap<>(Map.of("kind", "value", "wire", "VarInt", "name", "ticks", "java", "int")));
        for (Object t : triple) stepFields.add(copyTree(t));
        step.put("fields", stepFields);

        Map<String, Object> dispatch = new LinkedHashMap<>();
        dispatch.put("kind", "dispatch");
        dispatch.put("name", "delta");
        dispatch.put("java", "VecDelta");
        dispatch.put("note", "switch on the step count; deltas are in 1/4096 blocks");
        dispatch.put("variants", List.of(
                Map.of("key", "0 (linear)", "body", new LinkedHashMap<>(Map.of("kind", "container", "java", "Linear", "fields", triple))),
                Map.of("key", "1 or more (stepped)", "body", new LinkedHashMap<>(Map.of("kind", "container", "java", "Stepped", "fields", List.of(step))))));
        fields.set(deltaAt, dispatch);
    }

    /**
     * custom_query_answer: the writer sends the payload with writeNullable, but vanilla's reader
     * skips the rest of the packet unparsed, so tracing never sees the presence Boolean.
     */
    @SuppressWarnings("unchecked")
    static void patchCustomQueryAnswer(Map<String, Object> tree) {
        List<Object> fields = (List<Object>) tree.get("fields");
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> node = asMap(fields.get(i));
            if (!"Raw Bytes".equals(node.get("wire"))) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "value");
            data.put("wire", "Raw Bytes");
            data.put("note", "payload data, the rest of the packet (at most 1048576 bytes)");
            Map<String, Object> optional = new LinkedHashMap<>();
            optional.put("kind", "optional");
            optional.put("name", "payload");
            optional.put("note", "written with writeNullable (vanilla clients send false); "
                    + "the vanilla server skips everything after the transaction id unparsed");
            optional.put("inner", data);
            fields.set(i, optional);
            return;
        }
        System.err.println("[patch] custom-query-answer patch missed the payload");
    }

    /** player_info_update ADD_PLAYER: the game profile's name and property map, read into locals. */
    static void patchPlayerInfo(Map<String, Object> tree) {
        boolean[] done = {false};
        forEachNode(tree, n -> {
            if (!"group".equals(n.get("kind"))
                    || !"ClientboundPlayerInfoUpdatePacket$Action".equals(n.get("context"))
                    || !(n.get("fields") instanceof List<?> l) || l.size() != 2) {
                return;
            }
            Map<String, Object> name = asMap(l.get(0));
            Map<String, Object> properties = asMap(l.get(1));
            if (name == null || !"String".equals(name.get("wire"))
                    || properties == null || !"ref".equals(properties.get("kind"))) {
                return;
            }
            name.put("name", "name");
            properties.put("name", "properties");
            properties.put("java", "PropertyMap");
            done[0] = true;
        });
        if (!done[0]) System.err.println("[patch] player-info patch missed the ADD_PLAYER profile");
    }

    /** Fallback custom payload: an id and the remainder of the packet as data. */
    static void patchDiscardedPayload(Map<String, Object> tree) {
        Map<String, Object> group = tree.get("fields") instanceof List<?> l && l.size() == 1 ? asMap(l.getFirst()) : null;
        if (group != null && group.get("fields") instanceof List<?> gl && gl.size() == 2
                && "Identifier".equals(asMap(gl.get(0)).get("wire"))) {
            asMap(gl.get(0)).put("name", "id");
            asMap(gl.get(1)).put("name", "data");
            asMap(gl.get(1)).put("note", "the rest of the packet");
            // the group carried the packet's sole field name by first-fit — flatten it
            tree.put("fields", gl);
            return;
        }
        System.err.println("[patch] discarded-payload patch missed");
    }

    /** Names the packet's single boolean-packed byte and documents its bits. */
    static void patchFlagByte(Map<String, Object> tree, String note) {
        int[] seen = {0};
        forEachNode(tree, n -> {
            if ("value".equals(n.get("kind"))
                    && ("Byte".equals(n.get("wire")) || "Unsigned Byte".equals(n.get("wire")))
                    && n.get("x") == null) {
                if (seen[0]++ == 0) {
                    // the byte packs several booleans: no single field (abilities, isFlying) is it
                    n.put("name", "flags");
                    n.remove("java");
                    n.put("note", note);
                }
            }
        });
        if (seen[0] != 1) {
            System.err.println("[patch] flag-byte patch expected 1 byte, saw " + seen[0]);
        }
    }

    /** damage_event: the two optional-entity-id reads and the optional source position. */
    @SuppressWarnings("unchecked")
    static void patchDamageEvent(Map<String, Object> tree) {
        if (!(tree.get("fields") instanceof List<?> l)) return;
        List<Object> fields = (List<Object>) l;
        int groupAt = -1;
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> node = asMap(fields.get(i));
            if (node != null && "group".equals(node.get("kind"))
                    && "ClientboundDamageEventPacket.readOptionalEntityId".equals(node.get("context"))
                    && node.get("fields") instanceof List<?> gl && gl.size() == 2) {
                String[] names = {"sourceCauseId", "sourceDirectId"};
                for (int j = 0; j < 2; j++) {
                    Map<String, Object> child = asMap(gl.get(j));
                    child.put("name", names[j]);
                    child.put("java", "int");
                    child.put("note", "0 = absent, otherwise entity id + 1");
                }
                groupAt = i;
                break;
            }
        }
        boolean position = false;
        for (Object f : fields) {
            Map<String, Object> node = asMap(f);
            if (node != null && "optional".equals(node.get("kind")) && node.get("name") == null) {
                node.put("name", "sourcePosition");
                node.put("java", "Vec3");
                position = true;
            }
        }
        if (groupAt >= 0) {
            // the "sourcePosition"-named wrapper group was a first-fit guess — inline its reads
            List<Object> children = (List<Object>) asMap(fields.get(groupAt)).get("fields");
            fields.remove(groupAt);
            fields.addAll(groupAt, children);
        }
        if (groupAt < 0 || !position) {
            System.err.println("[patch] damage-event patch incomplete (group=" + (groupAt >= 0) + ", position=" + position + ")");
        }
    }

    /** DisplayInfo.fromNetwork: a flag int packing three booleans, the first gating a background identifier. */
    @SuppressWarnings("unchecked")
    static void patchDisplayInfo(Map<String, Object> tree) {
        List<Object> fields = (List<Object>) tree.get("fields");
        for (int i = 0; i < fields.size(); i++) {
            Map<String, Object> node = asMap(fields.get(i));
            if (node == null || !"value".equals(node.get("kind")) || !"Int".equals(node.get("wire")) || node.get("name") != null) continue;
            node.put("name", "flags");
            node.put("java", "int");
            node.put("note", "0x01: a background follows, 0x02: show toast, 0x04: hidden");
            Map<String, Object> background = new LinkedHashMap<>();
            background.put("kind", "value");
            background.put("wire", "Identifier");
            background.put("name", "background");
            background.put("note", "only present when flags & 0x01");
            fields.add(i + 1, background);
            return;
        }
        System.err.println("[patch] MISSED DisplayInfo flags");
    }

    /** Boolean-packed bytes read into several fields (boss_event's darkenScreen/playMusic/createWorldFog). */
    static void patchFlagBytes(Map<String, Object> tree, int expected, String note) {
        int[] patched = {0};
        forEachNode(tree, n -> {
            if ("value".equals(n.get("kind")) && "Unsigned Byte".equals(n.get("wire"))
                    && n.get("name") == null && n.get("x") == null) {
                n.put("name", "flags");
                n.put("note", note);
                patched[0]++;
            }
        });
        if (patched[0] != expected) {
            System.err.println("[patch] flag-byte patch expected " + expected + " nodes, patched " + patched[0]);
        }
    }

    /** set_structure_block: two byte-triple vectors and a trailing packed flags byte. */
    @SuppressWarnings("unchecked")
    static void patchStructureBlock(Map<String, Object> tree) {
        if (!(tree.get("fields") instanceof List<?> l)) return;
        int vecs = 0;
        boolean flags = false;
        for (Object f : (List<Object>) l) {
            Map<String, Object> node = asMap(f);
            if (node == null || !"value".equals(node.get("kind")) || !"Byte".equals(node.get("wire"))) continue;
            if (Integer.valueOf(3).equals(node.get("x"))) {
                node.putIfAbsent("name", vecs == 0 ? "offset" : "size");
                node.put("note", vecs == 0 ? "x, y, z as one byte each, clamped to -48..48"
                        : "x, y, z as one byte each, clamped to 0..48");
                vecs++;
            } else if (node.get("x") == null) {
                node.putIfAbsent("name", "flags");
                node.put("note", "0x01: ignore entities, 0x02: show air, 0x04: show bounding box, 0x08: strict");
                flags = true;
            }
        }
        if (vecs != 2 || !flags) {
            System.err.println("[patch] structure-block patch incomplete (vecs=" + vecs + ", flags=" + flags + ")");
        }
    }

    /** Depth-first visit of every node map in a tree, including variant bodies. */
    @SuppressWarnings("unchecked")
    static void forEachNode(Map<String, Object> node, java.util.function.Consumer<Map<String, Object>> visitor) {
        visitor.accept(node);
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct", "body"}) {
            if (node.get(key) instanceof Map<?, ?> m) forEachNode((Map<String, Object>) m, visitor);
        }
        if (node.get("fields") instanceof List<?> l) {
            for (Object child : l) {
                if (child instanceof Map<?, ?> m) forEachNode((Map<String, Object>) m, visitor);
            }
        }
        if (node.get("variants") instanceof List<?> l) {
            for (Object v : l) {
                if (v instanceof Map<?, ?> m) forEachNode((Map<String, Object>) m, visitor);
            }
        }
    }

    /* ── string-marker name recovery ────────────────────── */

    /**
     * Names for string fields read into local variables (no constructor frame to name them):
     * every fabricated string got unique content, so matching the decoded object's String
     * fields back to those markers recovers the declared field names.
     */
    static void applyStringNames(Map<String, Object> tree, TraceOutcome outcome) {
        if (outcome.result() == null || outcome.fabricatedStrings().isEmpty()) return;
        Map<String, String> valueToField = stringFieldNames(outcome.result());
        if (valueToField.isEmpty()) return;
        List<Map<String, Object>> stringNodes = new ArrayList<>();
        collectStringNodes(tree, stringNodes);
        List<String> strings = outcome.fabricatedStrings();
        for (int i = 0; i < stringNodes.size() && i < strings.size(); i++) {
            Map<String, Object> n = stringNodes.get(i);
            if (n.containsKey("name")) continue;
            String field = valueToField.get(strings.get(i));
            if (field != null) n.put("name", field);
        }
    }

    /** An optional's inner value re-labeled with the optional's own name is redundant. */
    @SuppressWarnings("unchecked")
    static void dedupeInnerNames(Map<String, Object> node) {
        if ("optional".equals(node.get("kind")) && node.get("name") != null
                && node.get("inner") instanceof Map<?, ?> inner
                && node.get("name").equals(inner.get("name"))) {
            ((Map<String, Object>) inner).remove("name");
        }
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct", "body"}) {
            if (node.get(key) instanceof Map<?, ?> m) dedupeInnerNames((Map<String, Object>) m);
        }
        if (node.get("fields") instanceof List<?> l) {
            for (Object child : l) dedupeInnerNames((Map<String, Object>) child);
        }
        if (node.get("variants") instanceof List<?> l) {
            for (Object v : l) dedupeInnerNames((Map<String, Object>) v);
        }
    }

    @SuppressWarnings("unchecked")
    static void collectStringNodes(Map<String, Object> node, List<Map<String, Object>> out) {
        if ("value".equals(node.get("kind")) && "String".equals(node.get("wire"))) out.add(node);
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct"}) {
            if (node.get(key) instanceof Map<?, ?> m) collectStringNodes((Map<String, Object>) m, out);
        }
        if (node.get("fields") instanceof List<?> l) {
            for (Object child : l) collectStringNodes((Map<String, Object>) child, out);
        }
    }

    /** BFS over the decoded object graph: String field value -> field name (shallowest wins). */
    static Map<String, String> stringFieldNames(Object root) {
        Map<String, String> out = new HashMap<>();
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Object> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        int budget = 800;
        while (!queue.isEmpty() && budget-- > 0) {
            Object obj = queue.poll();
            if (obj == null || !seen.add(obj)) continue;
            if (obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj instanceof Character) continue;
            Class<?> cls = obj.getClass();
            if (cls.isEnum()) continue;
            // Only iterate trusted container types — game iterables (HolderSet etc.) can have
            // side effects or throw (e.g. unbound tags).
            String cn = cls.getName();
            boolean trustedContainer = cn.startsWith("java.") || cn.startsWith("com.google.common.")
                    || cn.startsWith("it.unimi.") || cn.startsWith("com.mojang.");
            try {
                if (trustedContainer && obj instanceof com.google.common.collect.Multimap<?, ?> mm) {
                    for (var entry : mm.entries()) {
                        queue.add(entry.getKey());
                        queue.add(entry.getValue());
                    }
                    continue;
                }
                if (trustedContainer && obj instanceof java.util.Map<?, ?> m) {
                    queue.addAll(m.keySet());
                    queue.addAll(m.values());
                    continue;
                }
                if (trustedContainer && obj instanceof Iterable<?> it) {
                    for (Object o : it) queue.add(o);
                    continue;
                }
                if (obj instanceof Object[] arr) {
                    java.util.Collections.addAll(queue, arr);
                    continue;
                }
            } catch (Throwable ignored) {
                continue;
            }
            if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.") || cls.isArray()) continue;
            // identifiers store their parsed parts as strings; those names would mislead
            boolean harvest = !cn.equals("net.minecraft.resources.Identifier") && !cn.startsWith("net.minecraft.resources.ResourceKey");
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v instanceof String s) {
                            if (harvest) out.putIfAbsent(s, f.getName());
                        } else if (v != null) {
                            queue.add(v);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return out;
    }

    /* ── wire-type labeling ─────────────────────────────── */

    static final Map<String, String> OP_WIRE = Map.ofEntries(
            Map.entry("readBoolean", "Boolean"),
            Map.entry("readByte", "Byte"),
            Map.entry("readShort", "Short"),
            Map.entry("readMedium", "Medium"),
            Map.entry("readInt", "Int"),
            Map.entry("readLong", "Long"),
            Map.entry("readChar", "Char"),
            Map.entry("readFloat", "Float"),
            Map.entry("readDouble", "Double"),
            Map.entry("readBytes", "Raw Bytes"),
            Map.entry("skipBytes", "Raw Bytes"),
            Map.entry("stringContent", "String Bytes"),
            Map.entry("readCharSequence", "String Bytes")
    );

    /** Buffer helpers that contain other reads and should become groups, not value labels. */
    static final Map<String, String> COLLECTION_HELPERS = Map.of(
            "readList", "List",
            "readCollection", "List",
            "readMap", "Map",
            "readOptional", "Optional",
            "readNullable", "Optional", // same wire concept: Boolean presence flag + value
            "readEither", "Either");

    /** Known wire-helper frames: (class-suffix match, method prefix) -> label. Order matters: innermost wins. */
    static String helperLabel(Frame f) {
        String cls = f.simpleClassName();
        String m = f.methodName();
        if (cls.equals("VarInt") && m.equals("read")) return "VarInt";
        if (cls.equals("VarLong") && m.equals("read")) return "VarLong";
        if (cls.equals("Utf8String") && m.equals("read")) return "String";
        if ((cls.equals("FriendlyByteBuf") || cls.equals("RegistryFriendlyByteBuf")) && m.startsWith("read")) {
            if (COLLECTION_HELPERS.containsKey(m)) return null; // structural, not a value
            return prettifyReadMethod(m);
        }
        return null;
    }

    static final Map<String, String> HELPER_SPECIALS = Map.ofEntries(
            Map.entry("Var Int", "VarInt"),
            Map.entry("Var Long", "VarLong"),
            Map.entry("Utf", "String"),
            Map.entry("Nbt", "NBT"),
            Map.entry("Block Pos", "BlockPos"),
            Map.entry("Chunk Pos", "ChunkPos"),
            Map.entry("Section Pos", "SectionPos"),
            Map.entry("Global Pos", "GlobalPos"),
            Map.entry("Bit Set", "BitSet"),
            Map.entry("Fixed Bit Set", "BitSet (fixed)"),
            Map.entry("Enum Set", "EnumSet"),
            Map.entry("Container Id", "Container ID"),
            Map.entry("Json With Codec", "JSON"),
            Map.entry("With Codec", "NBT"),
            Map.entry("Resource Location", "Identifier"),
            Map.entry("Instant", "Long (Instant)"),
            Map.entry("Block Hit Result", "BlockHitResult"),
            Map.entry("Int Id List", "VarInt List"),
            Map.entry("By Id", "VarInt"));

    static String prettifyReadMethod(String m) {
        String base = m.substring(4); // strip "read"
        // camel case -> spaced words, keep acronyms
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(base.charAt(i - 1))) sb.append(' ');
            sb.append(c);
        }
        String s = sb.toString();
        return HELPER_SPECIALS.getOrDefault(s, s);
    }

    /* ── tree building ──────────────────────────────────── */

    /**
     * Build a node tree out of a flat event list.
     * Each event: context frames (outermost-first). We locate the outermost helper frame;
     * frames before it are structural context; the helper labels the wire read.
     */
    static Map<String, Object> buildTree(List<Event> events, Class<?> rootClass) {
        return treeFromUnits(toUnits(events, rootClass), rootClass);
    }

    /* ── call-site codec splicing ───────────────────────── */

    /** Codecs spliced into traced trees; a unit's wire "codec:<i>" indexes this list. */
    static final List<Object> SPLICED = new ArrayList<>();
    static final Map<Object, Integer> SPLICE_IDS = new java.util.IdentityHashMap<>();
    static final Map<Object, Map<String, Object>> SPLICE_NODES = new java.util.IdentityHashMap<>();
    /** Codecs whose splice node is being walked right now (recursive types trace themselves). */
    static final java.util.Set<Object> SPLICING = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    static final Map<String, Boolean> CODEC_CLASSES = new HashMap<>();

    record Splice(int frameIndex, int id) {}

    /**
     * The outermost codec a game-class reader invokes on this event's path, resolved from the
     * bytecode at the call site ({@code SoundEvent.STREAM_CODEC.decode(input)}); the reads
     * below it are that codec's business and get the walker's exact node instead.
     */
    static Splice spliceFor(List<Frame> ctx) {
        for (int i = 0; i < ctx.size(); i++) {
            Frame caller = ctx.get(i);
            if (!isCallerFrame(caller)) continue;
            int j = i + 1;
            while (j < ctx.size() && isBufFrame(ctx.get(j))) j++;
            if (j >= ctx.size() || !isCodecDecodeFrame(ctx.get(j))) continue;
            String codecClass = ctx.get(j).className();
            if (codecClass.equals(caller.className())) continue; // bridge decode(Object) -> decode(B)
            Object match = null;
            boolean ambiguous = false;
            for (Object candidate : CallSiteCodecs.codecsAt(caller.className(), caller.methodName(), caller.line())) {
                if (!candidate.getClass().getName().equals(codecClass)) continue;
                if (match != null && match != candidate) ambiguous = true;
                match = candidate;
            }
            if (ambiguous) continue;
            if (match == null && Boolean.getBoolean("extractor.debugSplice")) {
                System.err.println("[splice] no match at " + caller.key() + " for " + codecClass + " among "
                        + CallSiteCodecs.codecsAt(caller.className(), caller.methodName(), caller.line()).stream()
                        .map(c -> c.getClass().getName()).toList());
            }
            // unresolvable (built at runtime: entryCodec(actions)) — codecs nested inside it may
            // still resolve at their own call sites
            if (match == null || SPLICING.contains(match)) continue;
            Integer id = SPLICE_IDS.get(match);
            if (id == null) {
                id = SPLICED.size();
                SPLICED.add(match);
                SPLICE_IDS.put(match, id);
            }
            return new Splice(j, id);
        }
        return null;
    }

    static boolean isCallerFrame(Frame f) {
        String cn = f.className();
        if (!cn.startsWith("net.minecraft.") || cn.startsWith("net.minecraft.network.codec.")
                || cn.contains(".nbt.") || cn.contains("$$Lambda")) {
            return false;
        }
        String simple = f.simpleClassName();
        return !simple.equals("FriendlyByteBuf") && !simple.equals("RegistryFriendlyByteBuf")
                && !simple.equals("VarInt") && !simple.equals("VarLong") && !simple.equals("Utf8String");
    }

    static boolean isBufFrame(Frame f) {
        String simple = f.simpleClassName();
        return simple.equals("FriendlyByteBuf") || simple.equals("RegistryFriendlyByteBuf");
    }

    static boolean isCodecDecodeFrame(Frame f) {
        if (!f.methodName().equals("decode")) return false;
        return CODEC_CLASSES.computeIfAbsent(f.className(), cn -> {
            try {
                return StreamCodec.class.isAssignableFrom(Class.forName(cn, false, DecodeTracer.class.getClassLoader()));
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /** The walker's node for a spliced codec — a fresh copy, since naming mutates trees. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> splicedNode(int id) {
        Object codec = SPLICED.get(id);
        Map<String, Object> node = SPLICE_NODES.get(codec);
        if (node == null) {
            SPLICING.add(codec);
            try {
                node = CodecWalker.walk(codec, null, new java.util.IdentityHashMap<>(), 0, false);
            } finally {
                SPLICING.remove(codec);
            }
            SPLICE_NODES.put(codec, node);
        }
        return (Map<String, Object>) copyTree(node);
    }

    /** Convert events into semantic units, merging events of the same helper invocation. */
    static List<Unit> toUnits(List<Event> events, Class<?> rootClass) {
        List<Unit> units = new ArrayList<>();
        Unit current = null;
        for (int evIndex = 0; evIndex < events.size(); evIndex++) {
            Event ev = events.get(evIndex);
            int helperIdx = -1;
            String wire = null;
            for (int i = 0; i < ev.context().size(); i++) {
                String label = helperLabel(ev.context().get(i));
                if (label != null) {
                    helperIdx = i;
                    wire = label;
                    break; // outermost helper
                }
            }
            Splice splice = spliceFor(ev.context());
            if (splice != null && (helperIdx < 0 || splice.frameIndex() < helperIdx)) {
                List<Frame> path = ev.context().subList(0, splice.frameIndex());
                String spliceWire = "codec:" + splice.id();
                String key = pathKey(path) + "@" + spliceWire;
                if (current != null && current.invocationKey.equals(key)) {
                    current.bytes += ev.byteLength();
                    continue;
                }
                current = new Unit(structuralPath(path, rootClass), key, spliceWire, ev.byteLength());
                current.events = events;
                current.firstEvent = evIndex;
                units.add(current);
                continue;
            }
            List<Frame> path;
            String invocationKey;
            if (helperIdx >= 0) {
                path = ev.context().subList(0, helperIdx);
                // identity of a helper invocation: caller path (with lines) + helper method
                // (without its own line — a helper's internals span multiple lines)
                Frame helper = ev.context().get(helperIdx);
                invocationKey = pathKey(path) + "@" + helper.className() + "#" + helper.methodName();
            } else {
                path = ev.context();
                wire = OP_WIRE.getOrDefault(ev.op(), ev.op());
                invocationKey = pathKey(ev.context()) + "@" + ev.op();
            }
            // NBT internals: collapse everything below the first nbt frame into one "NBT" unit
            int nbtIdx = -1;
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i).className().contains(".nbt.")) { nbtIdx = i; break; }
            }
            if (nbtIdx >= 0) {
                path = path.subList(0, nbtIdx);
                wire = "NBT";
                invocationKey = pathKey(path) + "@nbt";
            }

            // Reads inside well-known codecs (ItemStack, DataComponentPatch) collapse into a
            // reference to their hand-modeled shared type.
            String refType = null;
            for (int i = 0; i < path.size(); i++) {
                refType = TRACE_REF_OWNERS.getOrDefault(path.get(i).className(),
                        TRACE_REF_OWNERS.get(outerClassName(path.get(i).className())));
                if (refType != null) {
                    path = path.subList(0, i);
                    wire = "ref:" + refType;
                    invocationKey = pathKey(path) + "@" + wire;
                    break;
                }
            }

            // A completed string followed by a new length read at the same call site is a new
            // string (loops like sign lines read several strings from one line of code).
            boolean newString = current != null && current.stringContentSeen
                    && ev.op().equals("readByte") && hasUtf8Frame(ev.context());

            // A finished varint (last byte without continuation bit) followed by another read
            // at the same site is a second varint: this(input.readVarInt(), input.readVarInt())
            boolean newVarint = current != null && current.varintDone
                    && (wire.equals("VarInt") || wire.equals("VarLong"));

            // Same helper invocation (identified by caller path incl. lines) -> same unit.
            if (current != null && !newString && !newVarint
                    && current.invocationKey.equals(invocationKey) && current.wire.equals(wire)) {
                current.bytes += ev.byteLength();
                current.varintDone = varintEnds(ev, wire);
                if (ev.op().equals("stringContent") || ev.op().equals("readCharSequence")) {
                    current.stringContentSeen = true;
                }
                continue;
            }
            current = new Unit(structuralPath(path, rootClass), invocationKey, wire, ev.byteLength());
            current.events = events;
            current.firstEvent = evIndex;
            current.stringContentSeen = ev.op().equals("stringContent") || ev.op().equals("readCharSequence");
            current.varintDone = varintEnds(ev, wire);
            for (Frame f : ev.context()) {
                if (f.methodName().equals("readCount") && f.simpleClassName().equals("ByteBufCodecs")) {
                    current.countRead = true;
                    break;
                }
            }
            units.add(current);
        }
        mergeSplitByteArrays(units);
        return units;
    }

    static boolean varintEnds(Event ev, String wire) {
        return (wire.equals("VarInt") || wire.equals("VarLong")) && ev.op().equals("readByte") && (ev.value() & 0x80) == 0;
    }

    /**
     * A VarInt immediately followed by a raw-bytes read from the same method is a
     * length-prefixed byte array split across two statements (readVarInt size + readBytes,
     * e.g. the chunk buffer) — the same wire shape readByteArray labels in one call.
     */
    static void mergeSplitByteArrays(List<Unit> units) {
        for (int i = 0; i + 1 < units.size(); i++) {
            Unit a = units.get(i), b = units.get(i + 1);
            if (!a.wire.equals("VarInt") || !(b.wire.equals("Bytes") || b.wire.equals("Raw Bytes"))) continue;
            if (!sameCallSite(a.path, b.path)) continue;
            Unit merged = new Unit(a.path, a.invocationKey + "+bytes", "Byte Array", a.bytes + b.bytes);
            merged.events = a.events;
            merged.firstEvent = a.firstEvent;
            units.set(i, merged);
            units.remove(i + 1);
        }
    }

    /** Same frame chain (class+method), with only the innermost line allowed to differ. */
    static boolean sameCallSite(List<Frame> a, List<Frame> b) {
        if (a.size() != b.size() || a.isEmpty()) return false;
        for (int i = 0; i < a.size(); i++) {
            Frame fa = a.get(i), fb = b.get(i);
            if (!fa.className().equals(fb.className()) || !fa.methodName().equals(fb.methodName())) return false;
            if (i < a.size() - 1 && fa.line() != fb.line()) return false;
        }
        return true;
    }

    static Map<String, Object> treeFromUnits(List<Unit> units, Class<?> rootClass) {
        // Assemble tree by structural path
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("kind", "traced");
        List<Object> rootChildren = new ArrayList<>();
        root.put("fields", rootChildren);

        List<GroupLevel> stack = new ArrayList<>();
        for (Unit u : units) {
            // find common prefix depth with open groups
            int common = 0;
            while (common < stack.size() && common < u.path.size()
                    && stack.get(common).frameKey.equals(levelKey(u.path, common))) {
                common++;
            }
            while (stack.size() > common) stack.removeLast();
            while (stack.size() < u.path.size()) {
                Frame f = u.path.get(stack.size());
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("kind", "group");
                group.put("context", groupLabel(f));
                // constructors AND static readers (Advancement.read, DisplayInfo.fromNetwork)
                // produce instances of their own class — usable for field naming. Lambdas do
                // not: a collection-element lambda's reads have nothing to do with the fields
                // of the class that happens to declare it (login's levels elements).
                if (!f.className().contains(".codec.") && !f.simpleClassName().startsWith("FriendlyByteBuf")
                        && !f.simpleClassName().startsWith("RegistryFriendlyByteBuf")
                        && !f.methodName().startsWith("lambda")) {
                    group.put("_cls", f.className());
                }
                List<Object> children = new ArrayList<>();
                group.put("fields", children);
                currentChildren(stack, rootChildren).add(group);
                stack.add(new GroupLevel(levelKey(u.path, stack.size()), children));
            }
            Map<String, Object> leaf = new LinkedHashMap<>();
            if (u.wire.startsWith("codec:")) {
                leaf = splicedNode(Integer.parseInt(u.wire.substring(6)));
            } else if (u.wire.startsWith("ref:")) {
                String refName = u.wire.substring(4);
                leaf.put("kind", "ref");
                leaf.put("ref", CodecWalker.ensureTraceType(refName));
            } else {
                leaf.put("kind", "value");
                leaf.put("wire", u.wire);
                if (u.countRead) leaf.put("_count", true);
                // several same-line reads of a fixed-size primitive merge into one unit; surface the count
                int prim = primitiveSize(u.wire);
                if (prim > 0 && u.bytes > prim && u.bytes % prim == 0) {
                    leaf.put("x", u.bytes / prim);
                }
            }
            if (u.events != null) PerturbNaming.register(leaf, OUTCOME_BY_EVENTS.get(u.events), u.firstEvent);
            currentChildren(stack, rootChildren).add(leaf);
        }

        // attach field names where group child counts match the owning class's fields
        nameChildren(rootChildren, rootClass);
        nameGroupsRecursively(rootChildren);
        simplifyTraced(root);
        applyElemClasses(root);
        stripCountMarkers(root);
        return root;
    }

    /** Remove count markers that no list conversion consumed — internal only, not output. */
    @SuppressWarnings("unchecked")
    static void stripCountMarkers(Map<String, Object> node) {
        node.remove("_count");
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct", "body"}) {
            if (node.get(key) instanceof Map<?, ?> m) stripCountMarkers((Map<String, Object>) m);
        }
        if (node.get("fields") instanceof List<?> l) {
            for (Object child : l) stripCountMarkers((Map<String, Object>) child);
        }
        if (node.get("variants") instanceof List<?> l) {
            for (Object v : l) stripCountMarkers((Map<String, Object>) v);
        }
    }

    /* ── traced-group simplification ────────────────────── */

    /**
     * Rewrites traced container groups into the walker's node vocabulary: the Boolean presence
     * flag of optionals and the VarInt count of lists/maps are implied by the container kind,
     * and anonymous codec-class groups with a single child are just wrappers.
     */
    @SuppressWarnings("unchecked")
    static void simplifyTraced(Map<String, Object> node) {
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct"}) {
            if (node.get(key) instanceof Map<?, ?> m) {
                Map<String, Object> child = (Map<String, Object>) m;
                simplifyTraced(child);
                node.put(key, transformGroup(child));
            }
        }
        if (node.get("fields") instanceof List<?> l) {
            List<Object> fields = (List<Object>) l;
            for (int i = 0; i < fields.size(); i++) {
                Map<String, Object> child = (Map<String, Object>) fields.get(i);
                simplifyTraced(child);
                fields.set(i, transformGroup(child));
            }
            // a sole composite-codec wrapper adds no structure — hoist its members
            if (fields.size() == 1 && fields.getFirst() instanceof Map<?, ?> only
                    && "group".equals(only.get("kind")) && "Composite".equals(only.get("context"))
                    && only.get("fields") instanceof List<?> innerFields) {
                node.put("fields", innerFields);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> transformGroup(Map<String, Object> node) {
        if (!"group".equals(node.get("kind"))) return node;
        String context = (String) node.get("context");
        List<Object> fields = (List<Object>) node.get("fields");
        if (context == null || fields == null) return node;

        switch (context) {
            case "Optional" -> {
                dropLeading(fields, "Boolean");
                Map<String, Object> n = replaced(node, "optional", "inner", wrap(fields));
                // the sole inner value carrying the optional's own name is redundant
                if (n.get("inner") instanceof Map<?, ?> inner && n.get("name") != null
                        && n.get("name").equals(inner.get("name"))) {
                    ((Map<String, Object>) inner).remove("name");
                }
                return n;
            }
            case "List" -> {
                Object count = fields.isEmpty() ? null : fields.getFirst();
                dropLeading(fields, "VarInt");
                Map<String, Object> list = replaced(node, "list", "elem", wrap(fields));
                if (fields.isEmpty() || fields.getFirst() != count) PerturbNaming.transfer(count, list);
                return list;
            }
            case "Map" -> {
                Object count = fields.isEmpty() ? null : fields.getFirst();
                dropLeading(fields, "VarInt");
                if (fields.isEmpty()) return replaced(node, "map", "key", wrap(fields));
                Map<String, Object> n = replaced(node, "map", "key", (Map<String, Object>) fields.getFirst());
                n.put("value", wrap(fields.subList(1, fields.size())));
                if (fields.getFirst() != count) PerturbNaming.transfer(count, n);
                return n;
            }
            case "Length-prefixed" -> {
                dropLeading(fields, "VarInt");
                return replaced(node, "prefixed", "inner", wrap(fields));
            }
            case "Either" -> {
                // the fabricated Boolean always took one branch; the other stays unseen
                node.putIfAbsent("note",
                        "the Boolean picks one of two encodings; only the branch the tracer took is shown");
                return node;
            }
            default -> {
                // a hand-written codec that opens with an element count (ByteBufCodecs.readCount)
                // is a collection: the reads after the count are one element
                if (fields.size() >= 2 && fields.getFirst() instanceof Map<?, ?> first
                        && Boolean.TRUE.equals(first.get("_count")) && first.get("name") == null) {
                    fields.removeFirst();
                    Map<String, Object> list = replaced(node, "list", "elem", wrap(fields));
                    PerturbNaming.transfer(first, list);
                    return list;
                }
                // a group with a single read is just a wrapper — inline it
                if (fields.size() == 1) {
                    Map<String, Object> child = (Map<String, Object>) fields.getFirst();
                    if (node.get("name") != null) child.putIfAbsent("name", node.get("name"));
                    if (node.get("java") != null) child.putIfAbsent("java", node.get("java"));
                    return child;
                }
                return node;
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void dropLeading(List<Object> fields, String wire) {
        // never drop a group's only read: in variant bodies the count lives in the shared
        // prefix and the single read IS the content
        if (fields.size() >= 2 && fields.getFirst() instanceof Map<?, ?> m
                && "value".equals(m.get("kind")) && wire.equals(m.get("wire")) && m.get("name") == null) {
            fields.removeFirst();
        }
    }

    static Map<String, Object> wrap(List<Object> fields) {
        if (fields.isEmpty()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("kind", "opaque");
            n.put("note", "content not captured");
            return n;
        }
        if (fields.size() == 1) {
            @SuppressWarnings("unchecked")
            Map<String, Object> only = (Map<String, Object>) fields.getFirst();
            return only;
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("kind", "container");
        n.put("fields", new ArrayList<>(fields));
        return n;
    }

    /** Turn a group node into a different kind, keeping name/java/note (+ element class stash). */
    static Map<String, Object> replaced(Map<String, Object> group, String kind, String childKey, Map<String, Object> child) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("kind", kind);
        if (group.get("name") != null) n.put("name", group.get("name"));
        if (group.get("java") != null) n.put("java", group.get("java"));
        if (group.get("note") != null) n.put("note", group.get("note"));
        if (group.get("_elemCls") != null) n.put("_elemCls", group.get("_elemCls"));
        n.put(childKey, child);
        return n;
    }

    /** Anonymous codec classes whose reads collapse into refs to hand-modeled types. */
    static final Map<String, String> TRACE_REF_OWNERS = Map.of(
            "net.minecraft.world.item.ItemStack", "ItemStack (optional)",
            "net.minecraft.world.item.ItemStackTemplate", "ItemStackTemplate",
            "net.minecraft.core.component.DataComponentPatch", "DataComponentPatch",
            // LpVec3.read takes a zero-vector early exit on the fabricated buffer, so tracing
            // sees a lone byte — collapse to the hand-modeled type instead
            "net.minecraft.network.LpVec3", "Vec3 (lp)",
            // exact nested class: id + 1, full signature only for id 0 (older packets call
            // Packed.read directly rather than through the codec)
            "net.minecraft.network.chat.MessageSignature$Packed", "MessageSignature.Packed");

    static String outerClassName(String className) {
        int inner = className.indexOf('$');
        return inner >= 0 ? className.substring(0, inner) : className;
    }

    static int primitiveSize(String wire) {
        return switch (wire) {
            case "Byte", "Unsigned Byte", "Boolean", "Bytes" -> 1;
            case "Short", "Unsigned Short", "Char" -> 2;
            case "Int", "Float" -> 4;
            case "Long", "Double" -> 8;
            default -> 0;
        };
    }

    /**
     * Group identity when nesting: class+method for game classes (so consecutive reads in one
     * constructor share a group even across lines), class+method+line for codec-combinator
     * frames (distinct combinator instances of the same anonymous class stay separate).
     */
    /**
     * Group identity at path level {@code i}: the frame's class+method (lines within a
     * constructor must merge), plus the parent frame's line, which distinguishes two separate
     * invocations of the same helper/codec from adjacent call sites.
     */
    static String levelKey(List<Frame> path, int i) {
        Frame f = path.get(i);
        String parentLine = i > 0 ? "@" + path.get(i - 1).line() : "";
        return f.className() + "#" + f.methodName() + parentLine;
    }

    @SuppressWarnings("unchecked")
    static void nameGroupsRecursively(List<Object> children) {
        for (Object child : children) {
            Map<String, Object> node = (Map<String, Object>) child;
            Object cls = node.remove("_cls");
            Object nested = node.get("fields");
            if (nested instanceof List<?> l) {
                // post-order: inner (more specific) names win, outer naming fills the gaps
                nameGroupsRecursively((List<Object>) l);
                if (cls != null) {
                    try {
                        nameChildren((List<Object>) l, Class.forName((String) cls, false, DecodeTracer.class.getClassLoader()));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    record GroupLevel(String frameKey, List<Object> children) {}

    /** A semantic read unit: one helper invocation (possibly several primitive reads). */
    static class Unit {
        final List<Frame> path;
        final String invocationKey;
        final String wire;
        int bytes;
        boolean stringContentSeen;
        boolean countRead;
        boolean varintDone;
        /** Trace and index of the unit's first event, for replay-based naming. */
        List<Event> events;
        int firstEvent;

        Unit(List<Frame> path, String invocationKey, String wire, int bytes) {
            this.path = path;
            this.invocationKey = invocationKey;
            this.wire = wire;
            this.bytes = bytes;
        }
    }

    static List<Object> currentChildren(List<GroupLevel> stack, List<Object> rootChildren) {
        return stack.isEmpty() ? rootChildren : stack.getLast().children();
    }

    static String pathKey(List<Frame> frames) {
        StringBuilder sb = new StringBuilder();
        for (Frame f : frames) sb.append(f.key()).append(';');
        return sb.toString();
    }

    /**
     * Structural frames only: constructors/reader methods of game classes, plus codec-combinator
     * frames that represent meaningful containers (Map, List, Optional, Either, Dispatch).
     */
    static List<Frame> structuralPath(List<Frame> path, Class<?> rootClass) {
        List<Frame> out = new ArrayList<>();
        String prevContainerKey = null;
        for (Frame f : path) {
            String cn = f.className();
            if (cn.contains("$$Lambda")) continue;
            String simple = f.simpleClassName();
            if (simple.equals("FriendlyByteBuf") || simple.equals("RegistryFriendlyByteBuf")) {
                String label = COLLECTION_HELPERS.get(f.methodName());
                if (label == null) continue;
                // collapse readList -> readCollection chains into one group
                if (prevContainerKey != null && prevContainerKey.equals("buf:" + label)) continue;
                prevContainerKey = "buf:" + label;
                out.add(f);
                continue;
            }
            if (cn.contains(".codec.")) {
                String kind = ShapeRegistry.kindOfClassName(cn);
                // registered containers (Map/List/...) and unregistered combinators (composite
                // arities) form groups; other registered kinds (of, registry, ...) are invisible
                if (kind != null && !ShapeRegistry.TRACE_GROUP_LABELS.containsKey(kind)) continue;
                if (cn.equals(prevContainerKey)) continue; // collapse decode->decode inner frames
                prevContainerKey = cn;
                out.add(f);
                continue;
            }
            prevContainerKey = null;
            // collapse bridge-method chains (decode(Object) -> decode(Buf)) and alike
            if (!out.isEmpty() && out.getLast().className().equals(cn)
                    && out.getLast().methodName().equals(f.methodName())) {
                continue;
            }
            out.add(f);
        }
        // The traced class's own reader frame is the root itself, not a group.
        if (!out.isEmpty() && rootClass != null && out.getFirst().className().equals(rootClass.getName())) {
            out.removeFirst();
        }
        return out;
    }

    static String groupLabel(Frame f) {
        if (f.className().contains(".codec.")) {
            String kind = ShapeRegistry.kindOfClassName(f.className());
            return kind == null ? "Composite" : ShapeRegistry.TRACE_GROUP_LABELS.getOrDefault(kind, "Composite");
        }
        String cls = f.simpleClassName();
        String m = f.methodName();
        if (COLLECTION_HELPERS.containsKey(m) && (cls.equals("FriendlyByteBuf") || cls.equals("RegistryFriendlyByteBuf"))) {
            return COLLECTION_HELPERS.get(m);
        }
        if (m.equals("<init>") || m.equals("read") || m.equals("decode") || m.startsWith("lambda")) return cls;
        return cls + "." + m;
    }

    /**
     * Attach field names to a group's children. Wire order is not always declaration order,
     * so names are only assigned when the types line up. Tried in order:
     * 1. instance fields (including superclasses) when counts match — positional, then bucketed;
     * 2. a constructor with a matching parameter count (subclass packets like MoveEntity.Rot
     *    keep their fields in the parent but read via their own constructor);
     * 3. ordered subset matching against the fields (packed flag bytes etc. stay unnamed).
     */
    /** Non-static fields of a class and its non-JDK superclasses, parent fields first. */
    static List<Field> instanceFields(Class<?> cls) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("java.")) break;
            List<Field> own = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) {
                // synthetic fields (an inner class's this$0) are no wire data
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) own.add(f);
            }
            fields.addAll(0, own); // parent fields first
        }
        return fields;
    }

    static void nameChildren(List<Object> children, Class<?> cls) {
        if (cls == null || children.isEmpty()) return;
        // JDK types and enums never describe wire data — their instance fields are
        // implementation internals (String.hashIsZero, EnumSet.universe, Enum.name,
        // an enum constant's captured codecs), and matching against them fabricates names
        if (cls.isEnum() || cls.getName().startsWith("java.") || cls.getName().startsWith("jdk.")
                || cls.getName().startsWith("sun.") || cls.getName().startsWith("com.sun.")) {
            return;
        }
        List<Field> fields = instanceFields(cls);

        if (Boolean.getBoolean("extractor.debugNames") && fields.size() <= 6) {
            System.err.println("[names] cls=" + cls.getSimpleName()
                    + " children=" + children.stream().map(c -> ((Map<String, Object>) c).get("kind") + "/" + childWire(c)
                            + "/" + ((Map<String, Object>) c).get("context")).toList()
                    + " fields=" + fields.stream().map(f -> f.getName() + ":" + f.getType().getSimpleName()).toList());
        }
        if (fields.size() == children.size()) {
            // positional with type verification
            List<Field> assignment = new ArrayList<>();
            boolean positionalOk = true;
            for (int i = 0; i < children.size(); i++) {
                String wire = childWire(children.get(i));
                Class<?> type = fields.get(i).getType();
                if (!bucketsCompatible(bucketOf(wire), bucketOf(type)) || !affinityOk(wire, type)) {
                    positionalOk = false;
                    break;
                }
                assignment.add(fields.get(i));
            }
            if (positionalOk) {
                applyNames(children, assignment);
                return;
            }
            List<Field> bucketed = bucketMatch(children, fields);
            if (bucketed != null) {
                applyNames(children, bucketed);
                return;
            }
            // wire order may differ from declaration order (stop_sound writes source before
            // name) — accept an out-of-order assignment when affinity forces it uniquely
            List<Field> unique = uniqueAffinityMatch(children, fields, true);
            if (unique != null) {
                applyNames(children, unique);
                return;
            }
        }

        // constructor parameters (skip buffer-reading constructors). Merged repeated reads
        // (Vec3's three doubles as one x=3 node) never stand for one parameter.
        boolean merged = false;
        for (Object c : children) merged |= isMergedRead(asMap(c));
        for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
            if (merged || ctor.getParameterCount() != children.size()) continue;
            java.lang.reflect.Parameter[] params = ctor.getParameters();
            // libraries compiled without parameter names (joml) only offer arg0, arg1, ...
            if (params.length > 0 && !params[0].isNamePresent()) continue;
            // conversion constructors (BlockPos(Vec3i)) name a value after its own supertype
            if (params.length == 1 && params[0].getType().isAssignableFrom(cls)) continue;
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                String wire = childWire(children.get(i));
                if (io.netty.buffer.ByteBuf.class.isAssignableFrom(params[i].getType())
                        || !bucketsCompatible(bucketOf(wire), bucketOf(params[i].getType()))
                        || !affinityOk(wire, params[i].getType())) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            for (int i = 0; i < children.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) children.get(i);
                child.putIfAbsent("name", params[i].getName());
                child.putIfAbsent("java", params[i].getType().getSimpleName());
                attachEnumValues(child, params[i].getType());
            }
            return;
        }

        // count mismatch (skipped fields, extra reads): order-preserving partial matching
        if (fields.size() != children.size()) {
            // a lone read has no order to preserve — first-fit would guess among the fields
            // (ResourceKey's two Identifiers), so only accept a forced-unique match
            if (children.size() == 1) {
                List<Field> unique = uniqueAffinityMatch(children, fields, false);
                if (unique != null) applyNames(children, unique);
                return;
            }
            List<Field> partial = orderPreservingMatch(children, fields);
            if (partial == null) {
                partial = new ArrayList<>(java.util.Collections.nCopies(children.size(), (Field) null));
            }
            // second chance: units the forward scan missed may match a unique unused field
            // that declaration order put earlier (e.g. 'method' declared before 'name')
            java.util.Set<Field> used = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Field f : partial) {
                if (f != null) used.add(f);
            }
            boolean any = false;
            for (int i = 0; i < children.size(); i++) {
                if (partial.get(i) != null) {
                    any = true;
                    continue;
                }
                String wire = childWire(children.get(i));
                String bucket = bucketOf(wire);
                for (Field f : fields) {
                    if (used.contains(f)) continue;
                    String fieldBucket = bucketOf(f.getType());
                    boolean compatible = bucket.equals(fieldBucket)
                            || (fieldBucket.equals("object") && wireMayBeObject(wire));
                    if (compatible && affinityOk(wire, f.getType())) {
                        partial.set(i, f);
                        used.add(f);
                        any = true;
                        break;
                    }
                }
            }
            if (any) applyNames(children, partial);
        }
    }

    /**
     * Assign each unit the first compatible field at or after the last assigned one; fields
     * that fit nothing are skipped, units that fit nothing stay unnamed. Order is preserved,
     * which is what manual readers guarantee.
     */
    static List<Field> orderPreservingMatch(List<Object> children, List<Field> fields) {
        List<Field> assignment = new ArrayList<>();
        int pointer = 0;
        int matched = 0;
        for (Object child : children) {
            String wire = childWire(child);
            String bucket = bucketOf(wire);
            Field assigned = null;
            for (int i = pointer; i < fields.size(); i++) {
                Field f = fields.get(i);
                String fieldBucket = bucketOf(f.getType());
                // when scanning ahead, only VarInts (registry ids) may claim object fields —
                // a plain byte skipping ahead to an object field is almost always wrong
                boolean compatible = bucket.equals(fieldBucket)
                        || (fieldBucket.equals("object") && wireMayBeObject(wire));
                if (compatible && affinityOk(wire, f.getType())) {
                    assigned = f;
                    pointer = i + 1;
                    break;
                }
            }
            assignment.add(assigned);
            if (assigned != null) matched++;
        }
        return matched > 0 ? assignment : null;
    }

    /** Wires that may decode into object fields: registry/id reads and everything object-like. */
    static boolean wireMayBeObject(String wire) {
        return switch (bucketOf(wire)) {
            case "object" -> true;
            case "int" -> wire.equals("VarInt") || wire.equals("Optional VarInt");
            case "long" -> wire.equals("VarLong") || wire.equals("Long") || wire.equals("Long (Instant)");
            default -> false;
        };
    }

    /** A merged repeated read (x=N), possibly inside a single-read wrapper group. */
    static boolean isMergedRead(Map<String, Object> node) {
        while (node != null && "group".equals(node.get("kind")) && node.get("fields") instanceof List<?> l && l.size() == 1) {
            node = asMap(l.getFirst());
        }
        return node != null && node.get("x") instanceof Integer;
    }

    /**
     * In-order matching within type buckets (equal counts only); int/long wires may fill
     * object fields (registry ids, instants) when their own bucket runs dry.
     */
    static List<Field> bucketMatch(List<Object> children, List<Field> fields) {
        Map<String, java.util.ArrayDeque<Field>> buckets = new HashMap<>();
        for (Field f : fields) {
            buckets.computeIfAbsent(bucketOf(f.getType()), k -> new java.util.ArrayDeque<>()).add(f);
        }
        List<Field> assignment = new ArrayList<>();
        for (Object child : children) {
            String wire = childWire(child);
            String bucket = bucketOf(wire);
            java.util.ArrayDeque<Field> q = buckets.get(bucket);
            Field f = q == null ? null : q.poll();
            if (f == null && wireMayBeObject(wire)) {
                java.util.ArrayDeque<Field> objects = buckets.get("object");
                f = objects == null ? null : objects.poll();
            }
            // a missing or incompatible field means the order assumption is wrong
            if (f == null || !affinityOk(wire, f.getType())) return null;
            assignment.add(f);
        }
        return assignment;
    }

    /**
     * Assignment by elimination, ignoring order: each child's candidates are the compatible
     * fields; children with exactly one candidate claim it, which may make other children
     * unique in turn. Succeeds only when every child resolves — a forced, unambiguous match.
     * With {@code requireAll}, every field must be claimed too (equal-count matching).
     */
    static List<Field> uniqueAffinityMatch(List<Object> children, List<Field> fields, boolean requireAll) {
        List<List<Field>> candidates = new ArrayList<>();
        for (Object child : children) {
            String wire = childWire(child);
            String bucket = bucketOf(wire);
            // exact-bucket fields outrank object fields an int/long wire could also fill
            // (VibrationParticleOption's arrivalInTicks:int wins over destination:PositionSource)
            List<Field> same = new ArrayList<>(), cross = new ArrayList<>();
            for (Field f : fields) {
                if (!affinityOk(wire, f.getType())) continue;
                String fieldBucket = bucketOf(f.getType());
                if (bucket.equals(fieldBucket)) same.add(f);
                else if (bucketsCompatible(bucket, fieldBucket)) cross.add(f);
            }
            List<Field> fits = same.isEmpty() ? cross : same;
            if (fits.isEmpty()) return null;
            candidates.add(fits);
        }
        List<Field> assignment = new ArrayList<>(java.util.Collections.nCopies(children.size(), (Field) null));
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int i = 0; i < candidates.size(); i++) {
                if (assignment.get(i) != null || candidates.get(i).size() != 1) continue;
                Field f = candidates.get(i).getFirst();
                assignment.set(i, f);
                for (int j = 0; j < candidates.size(); j++) {
                    if (j != i) candidates.get(j).remove(f);
                }
                progress = true;
            }
        }
        for (int i = 0; i < assignment.size(); i++) {
            if (assignment.get(i) == null) return null; // ambiguous or exhausted
        }
        if (requireAll && new java.util.HashSet<>(assignment).size() != fields.size()) return null;
        return assignment;
    }

    /**
     * Wire types with an unambiguous Java counterpart must land on a matching field;
     * otherwise the in-order assumption produced a wrong assignment.
     */
    static boolean affinityOk(String wire, Class<?> type) {
        // an EnumSet field is always a single fixed bitset on the wire
        if (java.util.EnumSet.class.isAssignableFrom(type)) return wire.equals("EnumSet");
        // collections on the wire are lists/maps — a scalar read never fills them
        if (java.util.Collection.class.isAssignableFrom(type) || java.util.Map.class.isAssignableFrom(type)) {
            return wire.equals("List") || wire.equals("Map") || wire.equals("VarInt List")
                    || wire.isEmpty() || wire.startsWith("ref:");
        }
        String simple = type.getSimpleName();
        if (wire.startsWith("ref:")) {
            // a type reference may only name a field of that type (or a super-interface)
            Class<?> produced = CodecWalker.TYPE_CLASSES.get(wire.substring(4));
            if (produced != null && !type.isPrimitive()) return type.isAssignableFrom(produced);
            String base = wire.substring(4);
            int paren = base.indexOf(" (");
            if (paren >= 0) base = base.substring(0, paren);
            return simple.equals(base) || type.isInterface() || Modifier.isAbstract(type.getModifiers());
        }
        return switch (wire) {
            // enum-switched payloads are often stored in interface-typed fields (BossEvent.Operation)
            case "Enum" -> type.isEnum() || type.isInterface() || Modifier.isAbstract(type.getModifiers());
            case "BlockPos" -> simple.equals("BlockPos");
            case "ChunkPos" -> simple.equals("ChunkPos");
            case "UUID" -> type == java.util.UUID.class;
            // strings are also parsed into identifiers, by-name enums (JointType) and line arrays
            case "String" -> type == String.class || type == String[].class || type.isEnum()
                    || net.minecraft.util.StringRepresentable.class.isAssignableFrom(type)
                    || simple.equals("Identifier") || simple.equals("ResourceKey");
            case "Identifier" -> simple.equals("Identifier") || simple.equals("ResourceKey");
            case "NBT" -> simple.equals("Tag") || simple.equals("CompoundTag") || simple.equals("Component");
            case "BitSet", "BitSet (fixed)" -> simple.equals("BitSet");
            case "EnumSet" -> simple.equals("EnumSet");
            // collection-typed fields were already accepted above; arrays are the only other fit
            case "List", "VarInt List" -> type.isArray();
            case "Map" -> false;
            default -> true;
        };
    }

    @SuppressWarnings("unchecked")
    static void applyNames(List<Object> children, List<Field> assignment) {
        for (int i = 0; i < children.size(); i++) {
            Field f = assignment.get(i);
            if (f == null) continue;
            Map<String, Object> child = (Map<String, Object>) children.get(i);
            child.putIfAbsent("name", f.getName());
            child.putIfAbsent("java", f.getType().getSimpleName());
            attachEnumValues(child, f.getType());
            // a group named after a structured field is that type's encoding — name its members
            if (child.get("fields") instanceof List<?> nested && !hasAnyName((List<Object>) nested)) {
                nameChildren((List<Object>) nested, f.getType());
            }
            // a collection field's declared element type names the element's structure;
            // groups become list nodes only later, so stash the class for the post pass.
            // Only for groups: post-simplification namings (variant bodies) have no
            // consumer, and naming finished elements by first-fit fabricates names.
            Class<?> elemClass = genericArg(f);
            if (elemClass != null && java.util.Collection.class.isAssignableFrom(f.getType())
                    && "group".equals(child.get("kind"))) {
                child.put("_elemCls", elemClass.getName());
            }
        }
    }

    /** After container simplification, name list elements from their field's generic type. */
    @SuppressWarnings("unchecked")
    static void applyElemClasses(Map<String, Object> node) {
        Object cls = node.remove("_elemCls");
        if (cls != null && node.get("elem") instanceof Map<?, ?> elem
                && ((Map<String, Object>) elem).get("fields") instanceof List<?> elemFields) {
            try {
                nameChildren((List<Object>) elemFields, Class.forName((String) cls, false, DecodeTracer.class.getClassLoader()));
            } catch (Throwable ignored) {
            }
        }
        for (String key : new String[]{"inner", "elem", "key", "value", "left", "right", "direct"}) {
            if (node.get(key) instanceof Map<?, ?> m) applyElemClasses((Map<String, Object>) m);
        }
        if (node.get("fields") instanceof List<?> l) {
            for (Object child : l) applyElemClasses((Map<String, Object>) child);
        }
    }

    /** The raw class of a field's first generic type argument (List&lt;X&gt; -> X). */
    static Class<?> genericArg(Field f) {
        if (f.getGenericType() instanceof java.lang.reflect.ParameterizedType pt
                && pt.getActualTypeArguments().length > 0) {
            java.lang.reflect.Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) return c;
            if (arg instanceof java.lang.reflect.ParameterizedType inner && inner.getRawType() instanceof Class<?> c) return c;
        }
        return null;
    }

    /** For enum-typed fields read as plain "Enum" (or by-name "String") values, list the constants. */
    static void attachEnumValues(Map<String, Object> child, Class<?> type) {
        if (!type.isEnum() || child.containsKey("values")) return;
        boolean byOrdinal = "Enum".equals(child.get("wire"));
        boolean byName = "String".equals(child.get("wire"));
        if (!byOrdinal && !byName) return;
        List<String> names = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            if (byName && constant instanceof net.minecraft.util.StringRepresentable sr) {
                names.add(sr.getSerializedName());
            } else {
                names.add(((Enum<?>) constant).name());
            }
        }
        child.put("values", names);
        child.put("java", type.getSimpleName());
        if (byName) child.put("note", "enum by name");
    }

    /** int/long wires (varints, registry ids, instants) may map to object-typed fields. */
    static boolean bucketsCompatible(String wireBucket, String fieldBucket) {
        if (wireBucket.equals(fieldBucket)) return true;
        return fieldBucket.equals("object") && (wireBucket.equals("int") || wireBucket.equals("long"));
    }

    @SuppressWarnings("unchecked")
    static String childWire(Object child) {
        Map<String, Object> node = (Map<String, Object>) child;
        if ("ref".equals(node.get("kind")) && node.get("ref") instanceof String ref) return "ref:" + ref;
        String kind = (String) node.get("kind");
        Object context = node.get("context");
        // collections keep a marker wire so affinity can route them to collection-typed fields
        if ("list".equals(kind) || ("group".equals(kind) && "List".equals(context))) return "List";
        if ("map".equals(kind) || ("group".equals(kind) && "Map".equals(context))) return "Map";
        Object wire = node.get("wire");
        // a codec-wrapper group with a single read is inlined to its child later — match like
        // that child. Container groups (List, Optional, ...) keep their own identity: their
        // sole visible read may be a count/flag standing in for the whole value.
        if (wire == null && "group".equals(node.get("kind")) && "Composite".equals(node.get("context"))
                && node.get("fields") instanceof List<?> l && l.size() == 1
                && l.getFirst() instanceof Map<?, ?> only
                && !Boolean.TRUE.equals(only.get("_count"))) {
            return childWire(only);
        }
        return wire == null ? "" : (String) wire;
    }

    /** Type bucket of a Java field type. */
    static String bucketOf(Class<?> t) {
        if (t == boolean.class || t == Boolean.class) return "bool";
        if (t == float.class || t == Float.class) return "float";
        if (t == double.class || t == Double.class) return "double";
        if (t == long.class || t == Long.class) return "long";
        if (t == byte.class || t == short.class || t == int.class || t == char.class
                || t == Byte.class || t == Short.class || t == Integer.class || t == Character.class
                || t.getName().equals("java.util.OptionalInt")) return "int";
        return "object";
    }

    /** Type bucket of a traced wire label. */
    static String bucketOf(String wire) {
        return switch (wire) {
            case "Boolean" -> "bool";
            case "Float" -> "float";
            case "Double" -> "double";
            case "Long", "VarLong", "Long (Instant)" -> "long";
            case "Byte", "Unsigned Byte", "Short", "Unsigned Short", "Int", "VarInt", "Medium",
                 "Char", "Container ID" -> "int";
            default -> "object"; // groups, strings, NBT, UUID, enums, dispatch, ...
        };
    }
}
