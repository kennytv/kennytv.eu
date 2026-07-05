import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Extracts packet wire-format data from a Minecraft server jar at runtime.
 * Usage: java PacketExtractor <path-to-game-jar> <version-name> <output.json> [--pretty]
 */
public class PacketExtractor {

    static final Map<Object, Class<?>> PACKET_TYPE_TO_CLASS = new IdentityHashMap<>();
    static final Map<Object, String> CODEC_CONSTANT_NAMES = new IdentityHashMap<>();
    static final Map<String, Object> CODEC_BY_LABEL = new java.util.HashMap<>();

    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        String versionName = args[1];
        String outputPath = args[2];
        boolean pretty = args.length > 3 && args[3].equals("--pretty");

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        ShapeRegistry.init();
        DecodeTracer.registryAccess = loadRegistries();
        scanJarConstants(jarPath);
        System.err.println("[extractor] packet types mapped: " + PACKET_TYPE_TO_CLASS.size()
                + ", codec constants: " + CODEC_CONSTANT_NAMES.size());

        if (outputPath.startsWith("--debug=")) {
            debugPacket(outputPath.substring("--debug=".length()));
            return;
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("version", versionName);
        try {
            output.put("protocolVersion", SharedConstants.getCurrentVersion().protocolVersion());
        } catch (Throwable t) {
            System.err.println("[extractor] could not read protocol version: " + t);
        }

        Map<String, Map<String, List<Object>>> protocols = new LinkedHashMap<>();
        for (String p : List.of("handshake", "status", "login", "configuration", "play")) {
            protocols.put(p, new LinkedHashMap<>());
        }

        int total = 0, opaque = 0;
        for (Object template : findProtocolTemplates()) {
            ProtocolInfo.Details details = ((ProtocolInfo.DetailsProvider) template).details();
            String protocolId = details.id().id();
            String flow = details.flow().id();
            List<Object> packets = new ArrayList<>();

            int index = 0;
            for (CodecEntryView entry : codecEntries(template)) {
                Class<?> packetClass = PACKET_TYPE_TO_CLASS.get(entry.type());
                Map<String, Object> packet = new LinkedHashMap<>();
                packet.put("index", index);
                packet.put("id", entry.type().id().toString());
                if (packetClass != null) packet.put("class", packetClass.getSimpleName());

                Map<String, Object> body;
                try {
                    body = CodecWalker.walkRoot(entry.codec(), packetClass);
                } catch (Throwable t) {
                    body = CodecWalker.node("opaque", "note", "extraction failed: " + CodecWalker.summarizeFailure(t));
                    System.err.println("[extractor] FAILED " + protocolId + "/" + flow + "/" + entry.type().id() + ": " + t);
                }
                packet.put("body", body);
                if ("opaque".equals(body.get("kind"))) opaque++;
                packets.add(packet);
                index++;
                total++;
            }
            protocols.computeIfAbsent(protocolId, k -> new LinkedHashMap<>()).put(flow, packets);
        }

        output.put("protocols", protocols);
        output.put("types", CodecWalker.TYPES);

        Gson gson = pretty ? new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                : new GsonBuilder().disableHtmlEscaping().create();
        Files.writeString(Path.of(outputPath), gson.toJson(output), StandardCharsets.UTF_8);
        System.err.println("[extractor] wrote " + outputPath + ": " + total + " packets, "
                + CodecWalker.TYPES.size() + " shared types, " + opaque + " opaque");
    }

    /* ── registries ─────────────────────────────────────── */

    /** Load the full registry set (including data-driven registries) from the vanilla data pack. */
    static RegistryAccess loadRegistries() {
        try {
            PackRepository repo = ServerPacksSource.createVanillaTrustedRepository();
            repo.reload();
            repo.setSelected(repo.getAvailableIds());
            CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, repo.openAllSelected());
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            List<Registry.PendingTags<?>> tags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            RegistryAccess.Frozen loadContext = layers.getAccessForLoading(RegistryLayer.WORLDGEN);
            List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(loadContext, tags);
            RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run).join();
            List<HolderLookup.RegistryLookup<?>> dimensionLookups = Stream.concat(lookups.stream(), worldgen.listRegistries()).toList();
            RegistryAccess.Frozen dimensions = RegistryDataLoader.load(resources, dimensionLookups, RegistryDataLoader.DIMENSION_REGISTRIES, Runnable::run).join();
            RegistryAccess full = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen, dimensions).compositeAccess();
            System.err.println("[extractor] loaded full registry access (with data-driven registries)");
            return full;
        } catch (Throwable t) {
            System.err.println("[extractor] falling back to static registries: " + t);
            return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }
    }

    /* ── jar scanning ───────────────────────────────────── */

    /** Scan all net.minecraft classes for PacketType constants (-> packet class) and StreamCodec constants (-> label). */
    static void scanJarConstants(String jarPath) throws Exception {
        List<String> classNames = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("net/minecraft/") && name.endsWith(".class")) {
                    classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
                }
            }
        }
        ClassLoader loader = PacketExtractor.class.getClassLoader();
        for (String className : classNames) {
            Class<?> cl;
            Field[] fields;
            try {
                cl = Class.forName(className, false, loader);
                fields = cl.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field f : fields) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                boolean isPacketType = PacketType.class.isAssignableFrom(ft);
                boolean isCodec = StreamCodec.class.isAssignableFrom(ft);
                if (!isPacketType && !isCodec) continue;
                Object value;
                try {
                    f.setAccessible(true);
                    value = f.get(null);
                } catch (Throwable t) {
                    continue; // class init failure etc.
                }
                if (value == null) continue;
                if (isPacketType) {
                    Type gt = f.getGenericType();
                    if (gt instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
                        Class<?> packetClass = rawClass(pt.getActualTypeArguments()[0]);
                        if (packetClass != null) PACKET_TYPE_TO_CLASS.put(value, packetClass);
                    }
                } else {
                    String label = nestedName(cl) + "." + f.getName();
                    CODEC_CONSTANT_NAMES.putIfAbsent(value, label);
                    CODEC_BY_LABEL.putIfAbsent(label, value);
                }
            }
        }
    }

    /** Unambiguous short class name: package stripped, nesting kept ("ClientboundPlayerInfoUpdatePacket.Entry"). */
    static String nestedName(Class<?> cl) {
        String n = cl.getName();
        int dot = n.lastIndexOf('.');
        return n.substring(dot + 1).replace('$', '.');
    }

    static Class<?> rawClass(Type t) {
        if (t instanceof Class<?> c) return c;
        if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    /* ── protocol templates ─────────────────────────────── */

    static List<Object> findProtocolTemplates() throws Exception {
        String[] protocolClasses = {
                "net.minecraft.network.protocol.handshake.HandshakeProtocols",
                "net.minecraft.network.protocol.status.StatusProtocols",
                "net.minecraft.network.protocol.login.LoginProtocols",
                "net.minecraft.network.protocol.configuration.ConfigurationProtocols",
                "net.minecraft.network.protocol.game.GameProtocols",
        };
        List<Object> templates = new ArrayList<>();
        for (String cn : protocolClasses) {
            Class<?> cl = Class.forName(cn);
            for (Field f : cl.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof ProtocolInfo.DetailsProvider) templates.add(v);
            }
        }
        return templates;
    }

    /** Reflect the captured CodecEntry list out of an unbound template. */
    static List<CodecEntryView> codecEntries(Object template) throws Exception {
        for (Field f : template.getClass().getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            List<?> list = (List<?>) f.get(template);
            if (list == null || list.isEmpty()) continue;
            Object first = list.getFirst();
            Field typeF = null, serF = null;
            for (Field ef : first.getClass().getDeclaredFields()) {
                if (PacketType.class.isAssignableFrom(ef.getType())) typeF = ef;
                if (StreamCodec.class.isAssignableFrom(ef.getType())) serF = ef;
            }
            if (typeF == null || serF == null) continue;
            typeF.setAccessible(true);
            serF.setAccessible(true);
            List<CodecEntryView> out = new ArrayList<>();
            for (Object entry : list) {
                out.add(new CodecEntryView((PacketType<?>) typeF.get(entry), (StreamCodec<?, ?>) serF.get(entry)));
            }
            return out;
        }
        throw new IllegalStateException("no codec entry list found on template " + template.getClass());
    }

    record CodecEntryView(PacketType<?> type, StreamCodec<?, ?> codec) {}

    /** Debug: trace one packet with each policy and dump events/failures. */
    static void debugPacket(String classSimpleName) throws Exception {
        for (Object template : findProtocolTemplates()) {
            for (CodecEntryView entry : codecEntries(template)) {
                Class<?> cls = PACKET_TYPE_TO_CLASS.get(entry.type());
                if (cls == null || !cls.getSimpleName().equals(classSimpleName)) continue;
                System.out.println("=== " + entry.type().id() + " codec " + entry.codec().getClass().getName());
                int[][] policies = {{1, 1}, {0, 0}, {0, 1}};
                for (int[] p : policies) {
                    DecodeTracer.TraceOutcome outcome = DecodeTracer.runTrace(entry.codec(), p[0], p[1] == 1);
                    System.out.println("--- policy varint=" + p[0] + " bool=" + (p[1] == 1)
                            + " -> " + (outcome.ok() ? "OK " + (outcome.result() == null ? "null" : outcome.result().getClass().getSimpleName())
                            : "FAIL " + outcome.failure()));
                    if (!outcome.ok()) outcome.failure().printStackTrace(System.out);
                    for (DecodeTracer.Event ev : outcome.events()) {
                        StringBuilder sb = new StringBuilder("    " + ev.op() + " x" + ev.byteLength() + " v=" + ev.value() + "  [");
                        for (DecodeTracer.Frame f : ev.context()) {
                            sb.append(f.simpleClassName()).append('.').append(f.methodName()).append(':').append(f.line()).append(" > ");
                        }
                        System.out.println(sb.append(']'));
                    }
                    if (outcome.ok()) break;
                }
                return;
            }
        }
        System.out.println("packet not found: " + classSimpleName);
    }
}
