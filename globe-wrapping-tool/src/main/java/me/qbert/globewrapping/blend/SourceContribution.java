package me.qbert.globewrapping.blend;

import java.awt.image.BufferedImage;
import me.qbert.globewrapping.geometry.DiscCalibration;

/** One already-loaded source image paired with its calibration, ready to be sampled by {@link SourceAccumulator}. */
public record SourceContribution(DiscCalibration calibration, BufferedImage image) {

    public SourceContribution {
        if (calibration == null) {
            throw new IllegalArgumentException("calibration must not be null");
        }
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
    }
}
