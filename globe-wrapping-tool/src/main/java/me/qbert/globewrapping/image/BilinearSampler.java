package me.qbert.globewrapping.image;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * Alpha-weighted bilinear sampling of a {@link BufferedImage} at fractional
 * pixel coordinates. Used both by {@code unwrap} (sampling a source image at
 * a fractional (u, v)) and {@code wrap} (sampling the canonical equirect at a
 * fractional lat/lon-derived pixel) — an explicit upgrade over {@code old_src}'s
 * nearest-neighbor-only sampling (no interpolation anywhere in {@code App.mapData}).
 */
public final class BilinearSampler {

    private BilinearSampler() {
    }

    /**
     * Samples {@code image} at fractional pixel coordinates ({@code x}, {@code y}),
     * clamped to the image bounds (edge-clamp, not wraparound). Each of the four
     * contributing texels is weighted by both its bilinear basis weight and its
     * own alpha, so fully- or partially-transparent neighbors don't bleed dark
     * fringes into the result.
     *
     * @return RGB in 0..1, or empty if every contributing texel is fully transparent
     */
    public static Optional<double[]> sample(BufferedImage image, double x, double y) {
        int width = image.getWidth();
        int height = image.getHeight();

        double cx = clamp(x, 0, width - 1);
        double cy = clamp(y, 0, height - 1);

        int x0 = (int) Math.floor(cx);
        int y0 = (int) Math.floor(cy);
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);

        double fx = cx - x0;
        double fy = cy - y0;

        int[] corners = {
            image.getRGB(x0, y0), image.getRGB(x1, y0),
            image.getRGB(x0, y1), image.getRGB(x1, y1),
        };
        double[] basisWeights = {
            (1 - fx) * (1 - fy), fx * (1 - fy),
            (1 - fx) * fy, fx * fy,
        };

        double sumWeight = 0.0;
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        for (int i = 0; i < corners.length; i++) {
            double contributionWeight = basisWeights[i] * alpha(corners[i]);
            if (contributionWeight <= 0.0) {
                continue;
            }
            sumWeight += contributionWeight;
            r += red(corners[i]) * contributionWeight;
            g += green(corners[i]) * contributionWeight;
            b += blue(corners[i]) * contributionWeight;
        }

        if (sumWeight <= 0.0) {
            return Optional.empty();
        }
        return Optional.of(new double[] {r / sumWeight, g / sumWeight, b / sumWeight});
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double alpha(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0;
    }

    private static double red(int argb) {
        return ((argb >>> 16) & 0xFF) / 255.0;
    }

    private static double green(int argb) {
        return ((argb >>> 8) & 0xFF) / 255.0;
    }

    private static double blue(int argb) {
        return (argb & 0xFF) / 255.0;
    }
}
