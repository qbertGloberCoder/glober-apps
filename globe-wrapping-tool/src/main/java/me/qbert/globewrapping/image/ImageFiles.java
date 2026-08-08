package me.qbert.globewrapping.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Thin {@code ImageIO} load/save wrappers. Successor to {@code old_src}'s
 * {@code utils.ImageLoader} — deliberately drops its
 * {@code Thread.sleep(500)} polling-retry loop around a possibly-still-loading
 * image and its swallowed-exception-returns-null behavior (see
 * {@code old_project_topology.md} lines 46-52); failures surface directly as
 * {@link IOException} instead.
 */
public final class ImageFiles {

    private ImageFiles() {
    }

    public static BufferedImage load(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Unable to decode image (missing file, or unsupported/corrupt format): " + path);
        }
        return image;
    }

    public static void save(BufferedImage image, Path path, String formatName) throws IOException {
        boolean written = ImageIO.write(image, formatName, path.toFile());
        if (!written) {
            throw new IOException("No ImageIO writer available for format '" + formatName + "': " + path);
        }
    }
}
