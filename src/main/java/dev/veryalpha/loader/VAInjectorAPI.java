package dev.veryalpha.loader;

import dev.veryalpha.loader.mod.ModLoader;
import dev.veryalpha.mod.ModLogger;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public class VAInjectorAPI {
    private static boolean initialized = false;
    private static final ModLogger LOGGER = new ModLogger("VAInjector");

    public static void onInit() {
        if (!initialized) {
            logEnvironment();
            LOGGER.info("Mods are applied at class load time via agent transformer");
            if (ModLoader.hasOverrides()) {
                LOGGER.info(ModLoader.getModCount() + " mod(s), " + ModLoader.getOverrideCount() + " override(s) active");
            }
            setWindowTitle();
            setWindowIcon();
            initialized = true;
        }
    }

    private static void setWindowTitle() {
        try {
            Class<?> display = Class.forName("org.lwjgl.opengl.Display");
            Method setTitle = display.getMethod("setTitle", String.class);
            setTitle.invoke(null, "Minecraft rd-132211 (VAInjector v" + VAInjector.VERSION + ")");
        } catch (Exception e) {
            LOGGER.error("Failed to set window title");
        }
    }

    private static void setWindowIcon() {
        try {
            Class<?> display = Class.forName("org.lwjgl.opengl.Display");
            Method setIconMethod = display.getMethod("setIcon", java.nio.ByteBuffer[].class);

            InputStream in = VAInjectorAPI.class.getResourceAsStream("/assets/VAInjector/icon.png");
            if (in == null) {
                LOGGER.error("Icon not found in agent jar");
                return;
            }
            BufferedImage img = ImageIO.read(in);
            in.close();
            if (img == null) {
                LOGGER.error("Failed to decode icon.png");
                return;
            }

            ByteBuffer buf16 = iconToBuffer(img, 16);
            ByteBuffer buf32 = iconToBuffer(img, 32);
            int used = (int) setIconMethod.invoke(null, new Object[]{new ByteBuffer[]{buf16, buf32}});
            LOGGER.info("Window icon set (" + img.getWidth() + "x" + img.getHeight() + " -> " + used + " icon(s) used)");
        } catch (Exception e) {
            LOGGER.error("Failed to set window icon");
        }
    }

    private static ByteBuffer iconToBuffer(BufferedImage src, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        int[] pixels = new int[size * size];
        scaled.getRGB(0, 0, size, size, pixels, 0, size);
        ByteBuffer buf = ByteBuffer.allocateDirect(size * size * 4);
        for (int pixel : pixels) {
            buf.put((byte) ((pixel >> 16) & 0xFF));
            buf.put((byte) ((pixel >> 8) & 0xFF));
            buf.put((byte) (pixel & 0xFF));
            buf.put((byte) ((pixel >> 24) & 0xFF));
        }
        buf.flip();
        return buf;
    }

    public static void onTick() {}

    public static void onRender() {}

    public static void onDestroy() {}

    private static void logEnvironment() {
        String workingDir = System.getProperty("user.dir", "?");
        String mainClass = System.getProperty("sun.java.command", "?");
        String classpath = System.getProperty("java.class.path", "");
        String libraryPath = System.getProperty("java.library.path", "?");
        String pid = ManagementFactory.getRuntimeMXBean().getName();

        LOGGER.info("Working directory: " + new File(workingDir).getAbsolutePath());
        LOGGER.info("Main class: " + mainClass);
        LOGGER.info("Native path: " + libraryPath);
        LOGGER.info("Mods directory: " + new File("mods").getAbsolutePath());

        String[] cpEntries = classpath.split(File.pathSeparator);
        LOGGER.info("Classpath (" + cpEntries.length + " entries):");
        for (String entry : cpEntries) {
            File f = new File(entry);
            if (f.exists()) {
                LOGGER.info("  " + entry + " (" + (f.isDirectory() ? "dir" : formatSize(f.length())) + ")");
            } else {
                LOGGER.info("  " + entry + " (not found)");
            }
        }

        LOGGER.info("Process ID: " + pid.split("@")[0]);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
