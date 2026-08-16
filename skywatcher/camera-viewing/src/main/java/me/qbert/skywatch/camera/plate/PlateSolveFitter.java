package me.qbert.skywatch.camera.plate;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.function.DoubleFunction;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.render.CameraProjector;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

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

// Task 7.2b's automated plate solve (technique 2's fit): "for a candidate camera parameter set,
// compute each mark's object position at its timestamp, project it through the candidate
// orientation to a predicted screen position, sum the pixel distance between predicted and marked
// position across all marks into one residual, and search for the parameter set minimizing it" -
// confirmed against the actual old org.bluerock.controllers.RandomPlateSolveFitter/
// StarFieldController.computePlateSolveFit(): a LOCAL RANDOM SEARCH (perturb one parameter within a
// shrinking range at a time, keep the candidate only if its residual improved), not gradient
// descent or a closed-form solve. The algorithm and the residual definition (summed screen-space
// distance) carry over directly - what's new here is the fitness function itself, built around
// render.CameraProjector.projectToPixels(...) (already the exact forward "sky direction -> screen
// pixel" prediction this needs) instead of the old StarFieldControllerI coupling.
//
// Fitted parameters: altitude, azimuth, barrel roll (bundled as Orientation, as everywhere else in
// this module), and focal length (CameraProjection's own "zoom" concept - see
// projection.CameraProjection's 35mm-equivalent convention). Observer location and each mark's
// timestamp are known inputs, not fitted.
//
// Deliberately does NOT fit the old algorithm's "aspect" or "barrel-distortion coefficients" -
// this is an architecture-driven scoping decision, not an oversight. This module's CameraProjector
// has no aspect-ratio calibration knob at all (canvas aspect ratio is a fixed, known property of
// the real photo being solved, not something to fit - see CameraProjector's own class comment).
// Barrel distortion (projection.AbstractCameraProjection's own quartic coefficients, applied
// directly inside sensorRadiusMillimeters(...) - CLAUDE.md's "Barrel distortion built into the lens
// hierarchy") now HAS a real rendering consumer - render.CameraProjector transparently sees it
// through the CameraProjection interface - so the "nothing else applies to it" premise this
// exclusion originally rested on is gone. Fitting distortion coefficients is still left out here:
// adding four more search dimensions to the random-search fit is its own separate scoping decision
// (a real accuracy/convergence question, not a natural extension of this pass), not something to
// bundle into an unrelated round.
public final class PlateSolveFitter {
	private PlateSolveFitter() {
	}

	// A pixel distance assigned to a mark whose object falls entirely outside the candidate lens's
	// representable angle (CameraProjector.projectToPixels(...) returning null) - large enough that
	// the search always prefers any candidate that can at least represent every marked object, but
	// finite so residual() stays a normal, comparable number rather than infinity/NaN.
	static final double OFF_LENS_PENALTY_PIXELS = 1_000_000.0;

	private static final double INITIAL_ANGLE_RANGE_DEGREES = 10.0;
	private static final double INITIAL_FOCAL_LENGTH_FRACTION = 0.5;
	private static final double RANGE_DECAY_PER_ATTEMPT = 0.98;
	private static final double MIN_FOCAL_LENGTH_MILLIMETERS = 1.0;

	// The fitted camera parameters - immutable, one candidate point in the search space.
	public static final class Candidate {
		private final Orientation orientation;
		private final double focalLengthMillimeters;

		public Candidate(Orientation orientation, double focalLengthMillimeters) {
			if (orientation == null)
				throw new IllegalArgumentException("orientation must not be null");
			if (focalLengthMillimeters <= 0.0)
				throw new IllegalArgumentException("focalLengthMillimeters must be positive");
			this.orientation = orientation;
			this.focalLengthMillimeters = focalLengthMillimeters;
		}

		public Orientation getOrientation() {
			return orientation;
		}

		public double getFocalLengthMillimeters() {
			return focalLengthMillimeters;
		}
	}

	public static final class Result {
		private final Candidate candidate;
		private final double residualPixels;

		Result(Candidate candidate, double residualPixels) {
			this.candidate = candidate;
			this.residualPixels = residualPixels;
		}

		public Candidate getCandidate() {
			return candidate;
		}

		// The sum, across every mark, of the pixel distance between where this candidate's
		// parameters predict the object should appear and where the user actually clicked it -
		// lower is a better fit, 0 is a perfect one.
		public double getResidualPixels() {
			return residualPixels;
		}
	}

	// projectionFactory maps a candidate focal length to a concrete CameraProjection - the caller
	// picks the LENS TYPE (rectilinear/fisheye, matching the camera's actual real lens per
	// CLAUDE.md's "Camera projection model"), e.g. RectilinearProjection::new; only the focal
	// length itself is searched, not the lens type.
	public static Result fit(PlateSolveMarkSet marks, ObserverLocation location,
			DoubleFunction<CameraProjection> projectionFactory, Candidate initialGuess, int canvasWidthPixels,
			int canvasHeightPixels, long randomSeed, int iterations) throws Exception {
		if (marks == null || marks.isEmpty())
			throw new IllegalArgumentException("marks must not be null or empty");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");
		if (projectionFactory == null)
			throw new IllegalArgumentException("projectionFactory must not be null");
		if (initialGuess == null)
			throw new IllegalArgumentException("initialGuess must not be null");
		if (canvasWidthPixels <= 0 || canvasHeightPixels <= 0)
			throw new IllegalArgumentException("canvas dimensions must be positive");
		if (iterations < 0)
			throw new IllegalArgumentException("iterations must not be negative");

		Random random = new Random(randomSeed);

		// Independent, high-ROI performance fix (no dependency on the UI work this class was ported
		// for): each mark's real position never changes between iterations - only the CANDIDATE does.
		// Resolving every mark's alt/az ONCE here, before the search loop, instead of once per mark
		// PER ITERATION (the old shape - iterations x marks.size() resolutions total), eliminates
		// nearly all of that redundant work for a typical multi-hundred-iteration fit.
		List<ResolvedMark> resolvedMarks = resolveMarks(marks, location, canvasWidthPixels, canvasHeightPixels);

		Candidate best = initialGuess;
		double bestResidual = residual(resolvedMarks, projectionFactory, best, canvasWidthPixels, canvasHeightPixels);

		double altitudeRangeDegrees = INITIAL_ANGLE_RANGE_DEGREES;
		double azimuthRangeDegrees = INITIAL_ANGLE_RANGE_DEGREES;
		double rollRangeDegrees = INITIAL_ANGLE_RANGE_DEGREES;
		double focalRangeMillimeters = initialGuess.getFocalLengthMillimeters() * INITIAL_FOCAL_LENGTH_FRACTION;

		for (int i = 0; i < iterations; i++) {
			int paramIndex = i % 4;
			Candidate perturbed = perturb(best, paramIndex, random, altitudeRangeDegrees, azimuthRangeDegrees,
					rollRangeDegrees, focalRangeMillimeters);
			double perturbedResidual = residual(resolvedMarks, projectionFactory, perturbed, canvasWidthPixels,
					canvasHeightPixels);

			if (perturbedResidual < bestResidual) {
				best = perturbed;
				bestResidual = perturbedResidual;
			}

			switch (paramIndex) {
				case 0:
					altitudeRangeDegrees *= RANGE_DECAY_PER_ATTEMPT;
					break;
				case 1:
					azimuthRangeDegrees *= RANGE_DECAY_PER_ATTEMPT;
					break;
				case 2:
					rollRangeDegrees *= RANGE_DECAY_PER_ATTEMPT;
					break;
				case 3:
					focalRangeMillimeters *= RANGE_DECAY_PER_ATTEMPT;
					break;
				default:
					throw new IllegalStateException("unreachable: paramIndex is always i % 4");
			}
		}

		return new Result(best, bestResidual);
	}

	// A mark's real-world position and pixel target, pre-resolved once - see fit(...)'s own comment
	// on why this is hoisted out of the per-iteration residual computation. Package-visible (not
	// private) so plate.DistortionSolveFitter (Phase B - fits distortion coefficients against the
	// same mark set, alt/az/roll/zoom held fixed) can reuse the identical resolve-once shape rather
	// than duplicating it.
	static final class ResolvedMark {
		final ObjectDirectionAltAz altAz;
		final double pixelX;
		final double pixelY;

		ResolvedMark(ObjectDirectionAltAz altAz, double pixelX, double pixelY) {
			this.altAz = altAz;
			this.pixelX = pixelX;
			this.pixelY = pixelY;
		}
	}

	static List<ResolvedMark> resolveMarks(PlateSolveMarkSet marks, ObserverLocation location,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		List<ResolvedMark> resolved = new ArrayList<ResolvedMark>(marks.getMarks().size());
		for (PlateSolveMark mark : marks.getMarks()) {
			ObservationTime time = observationTimeAt(mark.getEpochMillis());
			ObjectDirectionAltAz altAz = mark.getObject().resolveAltAz(time, location);
			resolved.add(new ResolvedMark(altAz, mark.pixelX(canvasWidthPixels), mark.pixelY(canvasHeightPixels)));
		}
		return resolved;
	}

	// The actual per-candidate fitness computation, operating on already-resolved marks - what
	// fit(...)'s hot loop calls directly, once per iteration, without re-resolving anything.
	private static double residual(List<ResolvedMark> resolvedMarks, DoubleFunction<CameraProjection> projectionFactory,
			Candidate candidate, int canvasWidthPixels, int canvasHeightPixels) {
		CameraProjection projection = projectionFactory.apply(candidate.getFocalLengthMillimeters());

		double sum = 0.0;
		for (ResolvedMark mark : resolvedMarks) {
			Point2D.Double predicted = CameraProjector.projectToPixels(projection, candidate.getOrientation(),
					mark.altAz.getAltitude(), mark.altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);

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

	// Package-visible for direct unit testing of the residual metric alone, independent of the
	// search loop around it - kept as the original PlateSolveMarkSet-based signature (resolves
	// fresh every call); fit(...) itself never calls this overload, using the pre-resolved-list one
	// above instead (see this class's own "Independent, high-ROI" comment in fit(...)).
	static double residual(PlateSolveMarkSet marks, ObserverLocation location,
			DoubleFunction<CameraProjection> projectionFactory, Candidate candidate, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		return residual(resolveMarks(marks, location, canvasWidthPixels, canvasHeightPixels), projectionFactory,
				candidate, canvasWidthPixels, canvasHeightPixels);
	}

	private static Candidate perturb(Candidate base, int paramIndex, Random random, double altitudeRangeDegrees,
			double azimuthRangeDegrees, double rollRangeDegrees, double focalRangeMillimeters) {
		Orientation o = base.getOrientation();

		switch (paramIndex) {
			case 0:
				return new Candidate(
						new Orientation(o.getAltitude() + randomOffset(random, altitudeRangeDegrees), o.getAzimuth(),
								o.getBarrelRoll()),
						base.getFocalLengthMillimeters());
			case 1:
				return new Candidate(
						new Orientation(o.getAltitude(), o.getAzimuth() + randomOffset(random, azimuthRangeDegrees),
								o.getBarrelRoll()),
						base.getFocalLengthMillimeters());
			case 2:
				return new Candidate(
						new Orientation(o.getAltitude(), o.getAzimuth(),
								o.getBarrelRoll() + randomOffset(random, rollRangeDegrees)),
						base.getFocalLengthMillimeters());
			case 3:
				double newFocalLength = Math.max(MIN_FOCAL_LENGTH_MILLIMETERS,
						base.getFocalLengthMillimeters() + randomOffset(random, focalRangeMillimeters));
				return new Candidate(o, newFocalLength);
			default:
				throw new IllegalStateException("unreachable: paramIndex is always in [0, 3]");
		}
	}

	private static double randomOffset(Random random, double range) {
		return (random.nextDouble() * 2.0 - 1.0) * range;
	}

	private static ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}
}
