package me.qbert.globewrapping.blend;

import java.util.List;
import java.util.Optional;
import me.qbert.globewrapping.geometry.EquirectangularMapping;
import me.qbert.globewrapping.geometry.GeoPoint;
import me.qbert.globewrapping.geometry.GeoLimb;
import me.qbert.globewrapping.geometry.GreatCircle;
import me.qbert.globewrapping.geometry.PixelPoint;
import me.qbert.globewrapping.geometry.SatelliteDiscProjection;
import me.qbert.globewrapping.image.BilinearSampler;
import me.qbert.globewrapping.image.EquirectCanvas;

/**
 * Fills an {@link EquirectCanvas} from one or more {@link SourceContribution}s:
 * for every canonical-equirect pixel, asks each source's
 * {@link SatelliteDiscProjection} whether it's visible there, weights visible
 * sources by a {@link ConfidenceWeightFunction}, and accumulates the
 * bilinearly-sampled result. Pixels no source covers are simply never
 * accumulated into, leaving them uncovered/transparent in the canvas — see
 * globe-unwrapper-requirements.md section 5.
 */
public final class SourceAccumulator {

    private final ConfidenceWeightFunction weightFunction;

    public SourceAccumulator() {
        this(new LinearFalloffConfidenceWeight());
    }

    public SourceAccumulator(ConfidenceWeightFunction weightFunction) {
        this.weightFunction = weightFunction;
    }

    public void accumulate(EquirectCanvas canvas, List<SourceContribution> sources) {
        int width = canvas.width();
        int height = canvas.height();

        double[] maxAngles = new double[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            maxAngles[i] = GeoLimb.visibleHalfAngleRadians(sources.get(i).calibration().distanceKm());
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                GeoPoint target = EquirectangularMapping.pixelToGeo(x + 0.5, y + 0.5, width, height);
                accumulatePixel(canvas, sources, maxAngles, x, y, target);
            }
        }
    }

    private void accumulatePixel(
        EquirectCanvas canvas, List<SourceContribution> sources, double[] maxAngles,
        int x, int y, GeoPoint target) {

        for (int i = 0; i < sources.size(); i++) {
            SourceContribution source = sources.get(i);

            Optional<PixelPoint> projected = SatelliteDiscProjection.project(source.calibration(), target);
            if (projected.isEmpty()) {
                continue;
            }

            double theta = GreatCircle.angularDistanceRadians(source.calibration().subPoint(), target);
            double weight = weightFunction.weight(theta, maxAngles[i]);
            if (weight <= 0.0) {
                continue;
            }

            PixelPoint uv = projected.get();
            double sourcePixelX = uv.x() * source.image().getWidth();
            double sourcePixelY = uv.y() * source.image().getHeight();
            Optional<double[]> sampled = BilinearSampler.sample(source.image(), sourcePixelX, sourcePixelY);
            if (sampled.isEmpty()) {
                continue;
            }

            double[] rgb = sampled.get();
            canvas.accumulate(x, y, rgb[0], rgb[1], rgb[2], weight);
        }
    }
}
