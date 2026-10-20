import net.minecraft.network.codec.StreamCodec;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Attributes;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves which StreamCodec a hand-written reader hands its buffer to at a given source
 * line, by reading the method's bytecode: {@code SoundEvent.STREAM_CODEC.decode(input)} loads
 * a static field, {@code ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(input)} calls a
 * codec factory with constant arguments, {@code input.readNullable(X.STREAM_CODEC)} passes one
 * along. Values are evaluated by reflection, so traced packets can splice in the walker's exact
 * node for the codec instead of the raw reads it performs.
 */
public class CallSiteCodecs {

    static final Map<String, ClassModel> CLASSES = new HashMap<>();
    /** class#method -> line -> codecs consumed by invokes on that line, in bytecode order. */
    static final Map<String, Map<Integer, List<Object>>> METHODS = new HashMap<>();
    static final Object UNKNOWN = new Object();

    /** Codecs consumed by calls at {@code className.methodName:line}; empty when nothing resolves. */
    static List<Object> codecsAt(String className, String methodName, int line) {
        Map<Integer, List<Object>> lines = METHODS.computeIfAbsent(className + "#" + methodName, k -> {
            Map<Integer, List<Object>> out = new HashMap<>();
            try {
                ClassModel cm = classModel(className);
                if (cm != null) {
                    for (MethodModel mm : cm.methods()) {
                        if (mm.methodName().equalsString(methodName)) mm.code().ifPresent(code -> simulate(code, out));
                    }
                }
            } catch (Throwable t) {
                // unreadable class: no splice, tracing proceeds as before
            }
            return out;
        });
        return lines.getOrDefault(line, List.of());
    }

    /** A local variable's name and descriptor (from the shipped LocalVariableTable). */
    record Local(String name, String descriptor) {}

    static final Map<String, Map<Integer, Local>> LOCALS = new HashMap<>();

    /**
     * The local a read at {@code line} is stored into ({@code float clickX = input.readFloat()}),
     * when the line holds exactly one read call whose result goes straight to a local.
     */
    static Local localStoredAt(String className, String methodName, int line) {
        Map<Integer, Local> lines = LOCALS.computeIfAbsent(className + "#" + methodName, k -> {
            Map<Integer, Local> out = new HashMap<>();
            Map<Integer, Integer> readsPerLine = new HashMap<>();
            try {
                ClassModel cm = classModel(className);
                if (cm == null) return out;
                for (MethodModel mm : cm.methods()) {
                    if (!mm.methodName().equalsString(methodName) || mm.code().isEmpty()) continue;
                    CodeModel code = mm.code().get();
                    List<LocalVariableInfo> table = code.findAttribute(Attributes.localVariableTable())
                            .map(a -> (List<LocalVariableInfo>) a.localVariables()).orElse(List.of());
                    int pc = 0, current = -1;
                    boolean pendingRead = false;
                    int readLine = -1;
                    for (CodeElement el : code) {
                        if (el instanceof LineNumber ln) {
                            current = ln.line();
                            continue;
                        }
                        if (!(el instanceof Instruction ins)) continue;
                        if (ins instanceof InvokeInstruction inv && isRead(inv)) {
                            readsPerLine.merge(current, 1, Integer::sum);
                            pendingRead = true;
                            readLine = current;
                        } else if (pendingRead && ins instanceof StoreInstruction store) {
                            int startPc = pc + ins.sizeInBytes();
                            for (LocalVariableInfo lv : table) {
                                if (lv.slot() == store.slot() && lv.startPc() == startPc) {
                                    out.put(readLine, new Local(lv.name().stringValue(), lv.type().stringValue()));
                                }
                            }
                            pendingRead = false;
                        } else if (!(ins instanceof TypeCheckInstruction) && !isUnboxing(ins)) {
                            pendingRead = false; // the value went elsewhere (a field, an argument, ...)
                        }
                        pc += ins.sizeInBytes();
                    }
                }
            } catch (Throwable ignored) {
            }
            out.keySet().removeIf(l -> readsPerLine.getOrDefault(l, 0) != 1);
            return out;
        });
        return lines.get(line);
    }

    /** Casts and unboxing between a read and its store keep the value the same. */
    static boolean isUnboxing(Instruction ins) {
        return ins instanceof InvokeInstruction inv && inv.name().stringValue().endsWith("Value")
                && inv.owner().asInternalName().startsWith("java/lang/");
    }

    /** Buffer reads and codec decodes — the calls that consume wire data. */
    static boolean isRead(InvokeInstruction inv) {
        String name = inv.name().stringValue();
        return name.equals("decode") || (name.startsWith("read") && !name.equals("readableBytes"))
                || (inv.owner().asInternalName().endsWith("/VarInt") && name.equals("read"));
    }

    static ClassModel classModel(String className) throws Exception {
        if (CLASSES.containsKey(className)) return CLASSES.get(className);
        ClassModel cm = null;
        String resource = className.replace('.', '/') + ".class";
        try (InputStream in = CallSiteCodecs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) cm = ClassFile.of().parse(in.readAllBytes());
        }
        CLASSES.put(className, cm);
        return cm;
    }

    /**
     * Lines of a codec class's decode method that call another codec's decode, in bytecode
     * order — for composite(...) codecs, one line per sub-codec.
     */
    static List<Integer> decodeCallLines(String className) {
        List<Integer> lines = new ArrayList<>();
        try {
            ClassModel cm = classModel(className);
            if (cm == null) return lines;
            for (MethodModel mm : cm.methods()) {
                if (!mm.methodName().equalsString("decode")) continue;
                mm.code().ifPresent(code -> {
                    int current = -1;
                    for (CodeElement el : code) {
                        if (el instanceof LineNumber ln) current = ln.line();
                        else if (el instanceof InvokeInstruction inv && inv.name().equalsString("decode")
                                && inv.owner().asInternalName().equals("net/minecraft/network/codec/StreamCodec")
                                && !lines.contains(current)) {
                            lines.add(current);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        return lines;
    }

    /**
     * Abstract interpretation of a method's straight-line stack traffic: constants, static
     * field loads and pure codec-factory calls produce known values, everything else unknown
     * ones. Each invoke records the codecs among the values it consumes under its own line —
     * the line its stack frame reports, even when the codec was loaded lines earlier
     * (multi-line constructor calls). Control flow resets the stack.
     */
    static void simulate(CodeModel code, Map<Integer, List<Object>> out) {
        int line = -1;
        List<Object> stack = new ArrayList<>();
        for (CodeElement el : code) {
            if (el instanceof LineNumber ln) {
                line = ln.line();
                continue;
            }
            if (!(el instanceof Instruction ins)) continue;
            switch (ins) {
                case ConstantInstruction c -> stack.add(c.constantValue());
                case LoadInstruction l -> stack.add(UNKNOWN);
                case StoreInstruction s -> pop(stack, 1);
                case NewObjectInstruction n -> stack.add(UNKNOWN);
                case TypeCheckInstruction t -> {
                    if (t.opcode() == Opcode.INSTANCEOF) {
                        pop(stack, 1);
                        stack.add(UNKNOWN);
                    } // checkcast leaves the value as-is
                }
                case StackInstruction s -> {
                    switch (s.opcode()) {
                        case DUP -> stack.add(stack.isEmpty() ? UNKNOWN : stack.getLast());
                        case POP -> pop(stack, 1);
                        default -> stack.clear();
                    }
                }
                case FieldInstruction f -> {
                    switch (f.opcode()) {
                        case GETSTATIC -> stack.add(staticField(f.owner().asSymbol(), f.name().stringValue()));
                        case GETFIELD -> {
                            pop(stack, 1);
                            stack.add(UNKNOWN);
                        }
                        case PUTFIELD -> pop(stack, 2);
                        default -> pop(stack, 1);
                    }
                }
                case InvokeDynamicInstruction indy -> {
                    pop(stack, indy.typeSymbol().parameterCount());
                    stack.add(UNKNOWN);
                }
                case InvokeInstruction inv -> {
                    MethodTypeDesc type = inv.typeSymbol();
                    int need = type.parameterCount() + (inv.opcode() == Opcode.INVOKESTATIC ? 0 : 1);
                    List<Object> consumed = new ArrayList<>(stack.subList(Math.max(0, stack.size() - need), stack.size()));
                    while (consumed.size() < need) consumed.addFirst(UNKNOWN);
                    pop(stack, need);
                    for (Object v : consumed) {
                        if (v instanceof StreamCodec<?, ?>) out.computeIfAbsent(line, k -> new ArrayList<>()).add(v);
                    }
                    if (!type.returnType().descriptorString().equals("V")) stack.add(evaluate(inv, consumed));
                }
                default -> stack.clear();
            }
        }
    }

    static void pop(List<Object> stack, int n) {
        for (int i = 0; i < n && !stack.isEmpty(); i++) stack.removeLast();
    }

    static Object staticField(ClassDesc owner, String name) {
        try {
            Class<?> cls = Class.forName(binaryName(owner), true, CallSiteCodecs.class.getClassLoader());
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /**
     * Evaluate a call when it is a pure codec construction: methods of the codec package
     * (ByteBufCodecs.registry, list, StreamCodec#apply, ...) with every argument known.
     */
    static Object evaluate(InvokeInstruction inv, List<Object> args) {
        String owner = binaryName(inv.owner().asSymbol());
        if (!owner.startsWith("net.minecraft.network.codec.")) return UNKNOWN;
        for (Object a : args) {
            if (a == UNKNOWN) return UNKNOWN;
        }
        boolean isStatic = inv.opcode() == Opcode.INVOKESTATIC;
        MethodTypeDesc type = inv.typeSymbol();
        try {
            Class<?> cls = Class.forName(owner, true, CallSiteCodecs.class.getClassLoader());
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(inv.name().stringValue()) || m.getParameterCount() != type.parameterCount()) continue;
                if (!matches(m, type)) continue;
                m.setAccessible(true);
                Object receiver = isStatic ? null : args.getFirst();
                Object[] callArgs = (isStatic ? args : args.subList(1, args.size())).toArray();
                return m.invoke(receiver, callArgs);
            }
        } catch (Throwable ignored) {
        }
        return UNKNOWN;
    }

    static boolean matches(Method m, MethodTypeDesc type) {
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (!params[i].descriptorString().equals(type.parameterType(i).descriptorString())) return false;
        }
        return true;
    }

    static String binaryName(ClassDesc desc) {
        String d = desc.descriptorString(); // Lpkg/Outer$Inner;
        return d.substring(1, d.length() - 1).replace('/', '.');
    }
}
