package me.qbert.globewrapping.image;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * The canonical equirectangular image's in-memory representation: RGB plus a
 * per-pixel accumulated confidence-weight buffer, so multiple sources'
 * contributions to the same pixel can be combined as a running weighted
 * average (the {@code blend} package's job) rather than last-write-wins like
 * {@code old_src}'s {@code App.mapData} running-average compositing. A pixel
 * with zero accumulated weight is "uncovered" and renders fully transparent
 * (alpha = 0), per globe-unwrapper-requirements.md section 5 — this is what
 * lets a basemap show through in the {@code combine} stage.
 */
public final class EquirectCanvas {

    private final int width;
    private final int height;
    private final double[] red;
    private final double[] green;
    private final double[] blue;
    private final double[] weight;

    public EquirectCanvas(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        int pixelCount = width * height;
        this.red = new double[pixelCount];
        this.green = new double[pixelCount];
        this.blue = new double[pixelCount];
        this.weight = new double[pixelCount];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Accumulates a weighted RGB contribution (each channel 0..1) at pixel (x, y). No-op if {@code contributionWeight <= 0}. */
    public void accumulate(int x, int y, double r, double g, double b, double contributionWeight) {
        if (contributionWeight <= 0.0) {
            return;
        }
        int idx = index(x, y);
        red[idx] += r * contributionWeight;
        green[idx] += g * contributionWeight;
        blue[idx] += b * contributionWeight;
        weight[idx] += contributionWeight;
    }

    public boolean isCovered(int x, int y) {
        return weight[index(x, y)] > 0.0;
    }

    /** The weighted-average RGB (each channel 0..1) at (x, y), or empty if uncovered. */
    public Optional<double[]> averageRgb(int x, int y) {
        int idx = index(x, y);
        double totalWeight = weight[idx];
        if (totalWeight <= 0.0) {
            return Optional.empty();
        }
        return Optional.of(new double[] {red[idx] / totalWeight, green[idx] / totalWeight, blue[idx] / totalWeight});
    }

    /** Renders to an ARGB {@link BufferedImage}: alpha = 0 for uncovered pixels, 255 for covered ones. */
    public BufferedImage toBufferedImage() {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = index(x, y);
                double totalWeight = weight[idx];
                int argb;
                if (totalWeight <= 0.0) {
                    argb = 0;
                } else {
                    int r = clampToByte(red[idx] / totalWeight);
                    int g = clampToByte(green[idx] / totalWeight);
                    int b = clampToByte(blue[idx] / totalWeight);
                    argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private int index(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") outside " + width + "x" + height);
        }
        return y * width + x;
    }

    private static int clampToByte(double value01) {
        int v = (int) Math.round(value01 * 255.0);
        return Math.max(0, Math.min(255, v));
    }
}
