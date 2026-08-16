package me.qbert.skywatch.camera.plate;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;

import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveFitter.ResolvedMark;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.render.CameraProjector;

/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

// Phase B of technique 2's semi-automated plate solve [CLAUDE.md's "Plate solving is Real
// archived-camera-only" / sprint Item 2]: once altitude/azimuth/barrel-roll/focal-length are
// accepted from PlateSolveFitter's own fit (Phase A), a SEPARATE search fits the four barrel-
// distortion coefficients (A/B/C/D - projection.AbstractCameraProjection's own quartic, "Barrel
// distortion built into the lens hierarchy") against the SAME mark set, holding orientation/zoom
// fixed at the just-accepted values. Same local-random-search algorithm and residual definition as
// PlateSolveFitter (perturb one parameter within a shrinking range at a time, keep the candidate
// only if its residual improved) - reuses PlateSolveFitter.ResolvedMark/resolveMarks(...) directly
// rather than a second copy of that resolve-once shape.
//
// Deliberately a SEPARATE class from PlateSolveFitter, not a generalization of it - the two fits
// have different fixed/free parameter splits (Phase A: orientation+zoom free, no distortion;
// Phase B: distortion free, orientation+zoom fixed) and different parameter scales (degrees/
// millimeters vs. the quartic's own tiny/near-unity coefficients - see fit(...)'s own comment on
// why the search ranges can't just reuse Phase A's constants).
public final class DistortionSolveFitter {
	private DistortionSolveFitter() {
	}

	private static final double INITIAL_ABC_RANGE = 0.05;
	// A/B/C's identity default is 0.0 and real calibrated values run roughly 1e-4 to 1e-2 in
	// magnitude (see CLAUDE.md's own worked example: A=-0.0152, B=-0.0262, C=9.25e-4) - a fixed
	// absolute range is appropriate since there's no "current value" to scale off of the way focal
	// length has one. D's identity default is 1.0 (not 0.0), so it gets a range proportional to the
	// initial guess instead, mirroring PlateSolveFitter's own focal-length-range convention -
	// clamped to a small floor so an initial guess of exactly 0.0 doesn't collapse the range to
	// nothing.
	private static final double INITIAL_D_RANGE_FRACTION = 0.2;
	private static final double MIN_D_RANGE = 0.05;
	private static final double RANGE_DECAY_PER_ATTEMPT = 0.98;

	// A pixel distance assigned to a mark whose object falls outside the candidate's representable
	// angle - same role, same value, as PlateSolveFitter.OFF_LENS_PENALTY_PIXELS (not reused
	// directly since that field is package-visible to plate.PlateSolveFitterTest specifically, not
	// meant as a general public constant).
	static final double OFF_LENS_PENALTY_PIXELS = 1_000_000.0;

	public static final class Coefficients {
		private final double a;
		private final double b;
		private final double c;
		private final double d;

		public Coefficients(double a, double b, double c, double d) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
		}

		public double getA() {
			return a;
		}

		public double getB() {
			return b;
		}

		public double getC() {
			return c;
		}

		public double getD() {
			return d;
		}
	}

	public static final class Result {
		private final Coefficients coefficients;
		private final double residualPixels;

		Result(Coefficients coefficients, double residualPixels) {
			this.coefficients = coefficients;
			this.residualPixels = residualPixels;
		}

		public Coefficients getCoefficients() {
			return coefficients;
		}

		public double getResidualPixels() {
			return residualPixels;
		}
	}

	// projection is the SAME lens instance (rectilinear/fisheye) Phase A's accepted result was
	// fitted against, already at the accepted focal length (CameraProjection.withFocalLength(...) -
	// callers build this the same way PlateSolveSession.render(...) does) - its distortion
	// coefficients/enabled flag are mutated during the search (every candidate evaluation) and
	// restored to their original values before returning, regardless of outcome, so the caller's own
	// projection instance is left exactly as it was passed in.
	public static Result fit(PlateSolveMarkSet marks, ObserverLocation location, Orientation orientation,
			AbstractCameraProjection projection, Coefficients initialGuess, int canvasWidthPixels,
			int canvasHeightPixels, long randomSeed, int iterations) throws Exception {
		if (marks == null || marks.isEmpty())
			throw new IllegalArgumentException("marks must not be null or empty");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");
		if (orientation == null)
			throw new IllegalArgumentException("orientation must not be null");
		if (projection == null)
			throw new IllegalArgumentException("projection must not be null");
		if (initialGuess == null)
			throw new IllegalArgumentException("initialGuess must not be null");
		if (canvasWidthPixels <= 0 || canvasHeightPixels <= 0)
			throw new IllegalArgumentException("canvas dimensions must be positive");
		if (iterations < 0)
			throw new IllegalArgumentException("iterations must not be negative");

		boolean originalEnabled = projection.isDistortionEnabled();
		double originalA = projection.getDistortionCoefficientA();
		double originalB = projection.getDistortionCoefficientB();
		double originalC = projection.getDistortionCoefficientC();
		double originalD = projection.getDistortionCoefficientD();
		try {
			// Distortion must actually be evaluated during this search, unlike Phase A/ordinary live
			// editing (see CLAUDE.md's "Distortion scope-gating" section) - the whole point here is
			// fitting it.
			projection.setDistortionEnabled(true);

			List<ResolvedMark> resolvedMarks = PlateSolveFitter.resolveMarks(marks, location, canvasWidthPixels,
					canvasHeightPixels);
			Random random = new Random(randomSeed);

			Coefficients best = initialGuess;
			double bestResidual = residual(resolvedMarks, projection, orientation, best, canvasWidthPixels,
					canvasHeightPixels);

			double rangeA = INITIAL_ABC_RANGE;
			double rangeB = INITIAL_ABC_RANGE;
			double rangeC = INITIAL_ABC_RANGE;
			double rangeD = Math.max(MIN_D_RANGE, Math.abs(initialGuess.getD()) * INITIAL_D_RANGE_FRACTION);

			for (int i = 0; i < iterations; i++) {
				int paramIndex = i % 4;
				Coefficients perturbed = perturb(best, paramIndex, random, rangeA, rangeB, rangeC, rangeD);
				double perturbedResidual = residual(resolvedMarks, projection, orientation, perturbed, canvasWidthPixels,
						canvasHeightPixels);

				if (perturbedResidual < bestResidual) {
					best = perturbed;
					bestResidual = perturbedResidual;
				}

				switch (paramIndex) {
					case 0:
						rangeA *= RANGE_DECAY_PER_ATTEMPT;
						break;
					case 1:
						rangeB *= RANGE_DECAY_PER_ATTEMPT;
						break;
					case 2:
						rangeC *= RANGE_DECAY_PER_ATTEMPT;
						break;
					case 3:
						rangeD *= RANGE_DECAY_PER_ATTEMPT;
						break;
					default:
						throw new IllegalStateException("unreachable: paramIndex is always in [0, 3]");
				}
			}

			return new Result(best, bestResidual);
		} finally {
			projection.setDistortionCoefficients(originalA, originalB, originalC, originalD);
			projection.setDistortionEnabled(originalEnabled);
		}
	}

	private static double residual(List<ResolvedMark> resolvedMarks, AbstractCameraProjection projection,
			Orientation orientation, Coefficients candidate, int canvasWidthPixels, int canvasHeightPixels) {
		projection.setDistortionCoefficients(candidate.getA(), candidate.getB(), candidate.getC(), candidate.getD());

		double sum = 0.0;
		for (ResolvedMark mark : resolvedMarks) {
			Point2D.Double predicted = CameraProjector.projectToPixels(projection, orientation, mark.altAz.getAltitude(),
					mark.altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);

			if (predicted == null) {
				sum += OFF_LENS_PENALTY_PIXELS;
				continue;
			}

			double dx = predicted.x - mark.pixelX;
			double dy = predicted.y - mark.pixelY;
			sum += Math.sqrt(dx * dx + dy * dy);
		}

		return sum;
	}

	private static Coefficients perturb(Coefficients base, int paramIndex, Random random, double rangeA,
			double rangeB, double rangeC, double rangeD) {
		switch (paramIndex) {
			case 0:
				return new Coefficients(base.getA() + randomOffset(random, rangeA), base.getB(), base.getC(), base.getD());
			case 1:
				return new Coefficients(base.getA(), base.getB() + randomOffset(random, rangeB), base.getC(), base.getD());
			case 2:
				return new Coefficients(base.getA(), base.getB(), base.getC() + randomOffset(random, rangeC), base.getD());
			case 3:
				return new Coefficients(base.getA(), base.getB(), base.getC(), base.getD() + randomOffset(random, rangeD));
			default:
				throw new IllegalStateException("unreachable: paramIndex is always in [0, 3]");
		}
	}

	private static double randomOffset(Random random, double range) {
		return (random.nextDouble() * 2.0 - 1.0) * range;
	}
}
