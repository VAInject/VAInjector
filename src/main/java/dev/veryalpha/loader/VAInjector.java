package dev.veryalpha.loader;

import dev.veryalpha.loader.mod.ModLoader;
import dev.veryalpha.loader.mod.StubGenerator;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.Map;
import dev.veryalpha.loader.transform.ClassOverrideTransformer;
import dev.veryalpha.loader.transform.RubyDungTransformer;
import dev.veryalpha.loader.transform.TextureOverrideTransformer;
import dev.veryalpha.mod.ModLogger;

public class VAInjector {
    public static final String VERSION = "1.0.0";
    private static final ModLogger LOGGER = new ModLogger("VAInjector");
    public static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        init(inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        init(inst);
    }

    private static void init(Instrumentation inst) {
        if (instrumentation != null) return;
        instrumentation = inst;
        logStartupBanner();

        inst.addTransformer(new ClassOverrideTransformer(), true);
        inst.addTransformer(new TextureOverrideTransformer(), true);
        inst.addTransformer(new RubyDungTransformer(), true);

        ModLoader.scanMods((pct, msg) -> LOGGER.info("  [" + pct + "%] " + msg));

        Map<String, byte[]> stubs = ModLoader.getStubs();
        if (stubs != null && !stubs.isEmpty()) {
            LOGGER.info("Installing " + stubs.size() + " stub(s) via system classloader...");
            StubGenerator.installStubs(stubs, inst);
        }
    }

    private static void logStartupBanner() {
        String javaVersion = System.getProperty("java.version", "?");
        String javaVendor = System.getProperty("java.vendor", "?");
        String osArch = System.getProperty("os.arch", "?");
        String osName = System.getProperty("os.name", "?");
        String osVersion = System.getProperty("os.version", "?");
        String bitness = osArch.contains("64") ? "64-bit" : "32-bit";
        int processors = Runtime.getRuntime().availableProcessors();
        long maxMemMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        String jvmArgs = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());

        LOGGER.info("=== VAInjector v" + VERSION + " ===");
        LOGGER.info("Java: version " + javaVersion + ", " + bitness + " (" + osArch + "), from " + javaVendor);
        LOGGER.info("OS: " + osName + " " + osVersion + " (" + osArch + ")");
        LOGGER.info("Processors: " + processors + ", Max memory: " + maxMemMb + " MB");
        LOGGER.info("JVM arguments: " + jvmArgs);

        try {
            String agentLocation = VAInjector.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            LOGGER.info("Agent path: " + agentLocation);
        } catch (Exception e) {
            LOGGER.info("Agent path: unknown");
        }
    }
}
