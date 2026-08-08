package me.qbert.globewrapping.pipeline;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.qbert.globewrapping.blend.SourceAccumulator;
import me.qbert.globewrapping.blend.SourceContribution;
import me.qbert.globewrapping.calibration.SourceCalibration;
import me.qbert.globewrapping.image.EquirectCanvas;
import me.qbert.globewrapping.image.ImageFiles;

/**
 * {@code unwrap}: N calibrated source images -&gt; one canonical full-globe
 * equirectangular image, alpha-channel-marked per-pixel coverage
 * (globe-unwrapper-requirements.md section 3). File-in/file-out only — the
 * actual reprojection/blending logic lives in {@code geometry}/{@code image}/
 * {@code blend} so it stays testable without going through this class.
 */
public final class UnwrapStage {

    /** Default canonical equirect resolution (0.1 degrees/pixel) when the caller doesn't specify one. */
    public static final int DEFAULT_CANVAS_WIDTH = 3600;
    public static final int DEFAULT_CANVAS_HEIGHT = 1800;

    private final SourceAccumulator accumulator;

    public UnwrapStage() {
        this(new SourceAccumulator());
    }

    public UnwrapStage(SourceAccumulator accumulator) {
        this.accumulator = accumulator;
    }

    /** One named calibration profile paired with the image path to load for it. */
    public record SourceInput(SourceCalibration calibration, Path imagePath) {
    }

    public void run(List<SourceInput> sources, Path outputPath) throws IOException {
        run(sources, outputPath, DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT);
    }

    public void run(List<SourceInput> sources, Path outputPath, int canvasWidth, int canvasHeight) throws IOException {
        List<SourceContribution> contributions = new ArrayList<>(sources.size());
        for (SourceInput input : sources) {
            BufferedImage image = ImageFiles.load(input.imagePath());
            contributions.add(new SourceContribution(input.calibration().disc(), image));
        }

        EquirectCanvas canvas = new EquirectCanvas(canvasWidth, canvasHeight);
        accumulator.accumulate(canvas, contributions);

        ImageFiles.save(canvas.toBufferedImage(), outputPath, OutputFormats.inferFrom(outputPath));
    }
}
