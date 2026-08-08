package me.qbert.globewrapping.pipeline;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.image.ImageFiles;

/**
 * {@code combine}: canonical equirect + an optional reference basemap -&gt; an
 * alpha-composited, flattened (opaque) equirect for orientation/viewing — no
 * recentering (globe-unwrapper-requirements.md section 3). The canonical
 * image itself is left untouched on disk; this stage only ever produces a new
 * flattened output, so the canonical stays independently saveable/reusable
 * both with and without a basemap baked in.
 */
public final class CombineStage {

    /** Background used for uncovered canonical pixels when no basemap is supplied. */
    public static final Color DEFAULT_BACKGROUND = Color.BLACK;

    /**
     * @param basemapPath the basemap image to show through uncovered canonical pixels, or {@code null} for none
     */
    public void run(Path canonicalPath, Path basemapPath, Path outputPath) throws IOException {
        BufferedImage canonical = ImageFiles.load(canonicalPath);
        int width = canonical.getWidth();
        int height = canonical.getHeight();

        BufferedImage flattened = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flattened.createGraphics();
        try {
            if (basemapPath != null) {
                BufferedImage basemap = ImageFiles.load(basemapPath);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(basemap, 0, 0, width, height, null);
            } else {
                g.setColor(DEFAULT_BACKGROUND);
                g.fillRect(0, 0, width, height);
            }
            // Default composite (SrcOver) alpha-blends the canonical's transparent/uncovered
            // pixels with whatever was just drawn as the background.
            g.drawImage(canonical, 0, 0, null);
        } finally {
            g.dispose();
        }

        ImageFiles.save(flattened, outputPath, OutputFormats.inferFrom(outputPath));
    }
}
