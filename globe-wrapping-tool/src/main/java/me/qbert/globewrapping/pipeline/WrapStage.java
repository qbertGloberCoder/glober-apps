package me.qbert.globewrapping.pipeline;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import me.qbert.globewrapping.geometry.EquirectangularMapping;
import me.qbert.globewrapping.geometry.GeoPoint;
import me.qbert.globewrapping.geometry.ObserverParameters;
import me.qbert.globewrapping.geometry.ObserverProjection;
import me.qbert.globewrapping.geometry.PerspectiveObserverProjection;
import me.qbert.globewrapping.geometry.PixelPoint;
import me.qbert.globewrapping.image.BilinearSampler;
import me.qbert.globewrapping.image.ImageFiles;

/**
 * {@code wrap}: canonical equirect + a synthetic observer (center, height,
 * output size) -&gt; a rendered view as seen by that observer
 * (globe-unwrapper-requirements.md section 6). Iterates only the requested
 * output image's own pixels; each one independently asks "does this pixel see
 * a point on Earth, and if so which lat/lon" via {@link ObserverProjection} —
 * the same code path naturally handles both the bounded-disc-with-padding
 * (high altitude) and no-visible-edge tight-crop (low altitude) regimes with
 * no special-casing.
 *
 * <p>Output is always rendered opaque (uncovered/off-globe pixels filled with
 * {@link #DEFAULT_BACKGROUND}) rather than left transparent, since the CLI
 * examples in the requirements doc write {@code .jpg} output, which cannot
 * hold an alpha channel.
 */
public final class WrapStage {

    public static final Color DEFAULT_BACKGROUND = Color.BLACK;

    private final ObserverProjection projection;

    public WrapStage() {
        this(new PerspectiveObserverProjection());
    }

    public WrapStage(ObserverProjection projection) {
        this.projection = projection;
    }

    public void run(Path canonicalPath, ObserverParameters observer, int outputWidth, int outputHeight, Path outputPath)
        throws IOException {

        BufferedImage canonical = ImageFiles.load(canonicalPath);
        int canonicalWidth = canonical.getWidth();
        int canonicalHeight = canonical.getHeight();

        BufferedImage output = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
        int backgroundRgb = DEFAULT_BACKGROUND.getRGB();

        for (int y = 0; y < outputHeight; y++) {
            for (int x = 0; x < outputWidth; x++) {
                int argb = backgroundRgb;

                Optional<GeoPoint> target =
                    projection.unproject(observer, outputWidth, outputHeight, x + 0.5, y + 0.5);
                if (target.isPresent()) {
                    PixelPoint canonicalPixel =
                        EquirectangularMapping.geoToPixel(target.get(), canonicalWidth, canonicalHeight);
                    Optional<double[]> sampled =
                        BilinearSampler.sample(canonical, canonicalPixel.x(), canonicalPixel.y());
                    if (sampled.isPresent()) {
                        argb = toOpaqueRgb(sampled.get());
                    }
                }

                output.setRGB(x, y, argb);
            }
        }

        ImageFiles.save(output, outputPath, OutputFormats.inferFrom(outputPath));
    }

    private static int toOpaqueRgb(double[] rgb01) {
        int r = clampToByte(rgb01[0]);
        int g = clampToByte(rgb01[1]);
        int b = clampToByte(rgb01[2]);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clampToByte(double value01) {
        return Math.max(0, Math.min(255, (int) Math.round(value01 * 255.0)));
    }
}
