package dev.veryalpha.loader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

public class ResourceOverride {
    public static BufferedImage loadImage(String resourceName) {
        String localPath = "vai-resources" + (resourceName.startsWith("/") ? resourceName : "/" + resourceName);
        File localFile = new File(localPath);
        if (localFile.exists()) {
            try {
                return ImageIO.read(localFile);
            } catch (Exception e) {}
        }
        try {
            InputStream in = ResourceOverride.class.getResourceAsStream(resourceName);
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {}
        return null;
    }
}
