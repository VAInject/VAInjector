package dev.veryalpha.loader.mod;

import dev.veryalpha.mod.ModLogger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModLoader {
    private static final ModLogger LOGGER = new ModLogger("VAInjector");
    private static final Map<String, byte[]> overrides = new HashMap<>();
    private static Map<String, byte[]> lastStubs;
    private static int modCount = 0;

    public static void scanMods(BiConsumer<Integer, String> progress) {
        File resDir = new File("vai-resources");
        if (resDir.exists()) {
            deleteDir(resDir);
        }
        resDir.mkdirs();

        File modsDir = new File("mods");
        if (!modsDir.exists()) {
            modsDir.mkdirs();
            progress.accept(100, "No mods found");
            return;
        }
        File[] modFiles = modsDir.listFiles((d, n) -> n.endsWith(".jar") || n.endsWith(".zip"));
        if (modFiles == null || modFiles.length == 0) {
            progress.accept(100, "No mods found");
            return;
        }
        LOGGER.info("Found " + modFiles.length + " mod file(s) in mods/");
        for (int i = 0; i < modFiles.length; i++) {
            int pct = i * 100 / modFiles.length;
            String fileName = modFiles[i].getName();
            progress.accept(pct, "Reading " + fileName + "...");
            LOGGER.info("Reading " + fileName + " (" + formatSize(modFiles[i].length()) + ")");

            try (JarFile jf = new JarFile(modFiles[i])) {
                int classCount = 0;
                int resCount = 0;
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        String className = name.substring(0, name.length() - 6).replace('/', '.');
                        byte[] data = readEntry(jf, entry);
                        overrides.put(className, data);
                        classCount++;
                    } else {
                        byte[] data = readEntry(jf, entry);
                        File outFile = new File("vai-resources", name);
                        outFile.getParentFile().mkdirs();
                        try {
                            java.nio.file.Files.write(outFile.toPath(), data);
                            resCount++;
                        } catch (Exception e) {
                            LOGGER.error("Failed to extract " + name);
                        }
                    }
                }
                modCount++;
                LOGGER.info(fileName + ": " + classCount + " class(es), " + resCount + " resource(s)");
            } catch (Exception e) {
                LOGGER.error("Failed to read " + fileName);
            }
        }
        int before = overrides.size();
        lastStubs = StubGenerator.generateStubs(overrides);
        if (!lastStubs.isEmpty()) {
            overrides.putAll(lastStubs);
            LOGGER.info("Generated " + lastStubs.size() + " stub(s) for missing class references");
        }
        progress.accept(100, "Scanned " + modCount + " mod(s), " + before + " override(s) + " + (lastStubs != null ? lastStubs.size() : 0) + " stub(s)");
        LOGGER.info("Mod scan complete: " + modCount + " mod(s), " + before + " class override(s) + " + (lastStubs != null ? lastStubs.size() : 0) + " stub(s)");
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                f.delete();
            }
        }
    }

    public static byte[] getOverride(String className) {
        return overrides.get(className);
    }

    public static boolean hasOverrides() {
        return !overrides.isEmpty();
    }

    public static int getModCount() {
        return modCount;
    }

    public static int getOverrideCount() {
        return overrides.size();
    }

    public static Map<String, byte[]> getStubs() {
        return lastStubs;
    }

    private static byte[] readEntry(JarFile jf, JarEntry entry) throws Exception {
        long size = entry.getSize();
        if (size > 0) {
            byte[] data = new byte[(int) size];
            try (InputStream in = jf.getInputStream(entry)) {
                int offset = 0;
                while (offset < data.length) {
                    int read = in.read(data, offset, data.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
            }
            return data;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = jf.getInputStream(entry)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) >= 0) {
                baos.write(buf, 0, read);
            }
        }
        return baos.toByteArray();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

}
