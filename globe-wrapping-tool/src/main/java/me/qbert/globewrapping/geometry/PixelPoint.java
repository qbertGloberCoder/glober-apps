package me.qbert.globewrapping.geometry;

/**
 * A plain (x, y) coordinate pair. Depending on the producing method, the
 * values are either fractional [0,1] (calibration-relative, resolution
 * independent) or absolute pixel indices in a specific image — each method's
 * javadoc says which.
 */
public record PixelPoint(double x, double y) {
}
