package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Pins the geometry of the user's own two-angle projection design: theta (angular distance from
// the camera's boresight) and phi (bearing around that ring, sweeping toward "up" as it increases).
class BoresightAnglesTest {

	private static final double EPSILON = 1e-6;

	@Test
	void targetAtTheBoresightHasZeroTheta() {
		Orientation camera = new Orientation(20.0, 130.0, 0.0);

		BoresightAngles.Angles angles = BoresightAngles.decompose(camera, 20.0, 130.0);

		assertEquals(0.0, angles.getThetaRadians(), EPSILON);
	}

	@Test
	void targetDirectlyOppositeHasThetaOfPi() {
		// A camera pointing at the zenith; straight down is the unambiguous antipodal direction.
		Orientation camera = new Orientation(90.0, 0.0, 0.0);

		BoresightAngles.Angles angles = BoresightAngles.decompose(camera, -90.0, 0.0);

		assertEquals(Math.PI, angles.getThetaRadians(), EPSILON);
	}

	@Test
	void targetOffsetInAltitudeAppearsStraightUpInAnUnrolledFrame() {
		// A level camera facing due north (az=0); a target 10 degrees higher at the same azimuth is
		// "straight up" in-frame when there's no roll applied.
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		BoresightAngles.Angles angles = BoresightAngles.decompose(camera, 10.0, 0.0);

		assertEquals(Math.toRadians(10.0), angles.getThetaRadians(), 1e-3);
		assertEquals(Math.PI / 2.0, angles.getPhiRadians(), 1e-3, "an object directly above center should sweep to phi=90 degrees (up)");
	}

	@Test
	void barrelRollShiftsPhiByExactlyTheRollAmount() {
		Orientation unrolled = new Orientation(0.0, 0.0, 0.0);
		Orientation rolled = new Orientation(0.0, 0.0, 90.0);

		BoresightAngles.Angles withoutRoll = BoresightAngles.decompose(unrolled, 10.0, 0.0);
		BoresightAngles.Angles withRoll = BoresightAngles.decompose(rolled, 10.0, 0.0);

		double expectedPhi = withoutRoll.getPhiRadians() + Math.toRadians(90.0);
		assertEquals(normalize(expectedPhi), normalize(withRoll.getPhiRadians()), 1e-3);

		// theta must be unaffected by roll - rolling the camera doesn't change how far off-axis a
		// point is, only where around the ring it appears.
		assertEquals(withoutRoll.getThetaRadians(), withRoll.getThetaRadians(), EPSILON);
	}

	@Test
	void cameraPointingStraightUpDoesNotProduceNaN() {
		// The zenith-based "up" reference is degenerate when the camera itself points at the
		// zenith - confirms the north-based fallback kicks in and still returns finite values.
		Orientation camera = new Orientation(90.0, 0.0, 0.0);

		BoresightAngles.Angles angles = BoresightAngles.decompose(camera, 80.0, 45.0);

		assertFalse(Double.isNaN(angles.getThetaRadians()));
		assertFalse(Double.isNaN(angles.getPhiRadians()));
		assertTrue(angles.getThetaRadians() > 0.0 && angles.getThetaRadians() < Math.PI);
	}

	@Test
	void reconstructAltAzIsTheExactInverseOfDecompose() {
		Orientation camera = new Orientation(15.0, 200.0, 25.0);

		BoresightAngles.Angles angles = BoresightAngles.decompose(camera, 40.0, 260.0);
		ObjectDirectionAltAz reconstructed = BoresightAngles.reconstructAltAz(camera, angles.getThetaRadians(),
				angles.getPhiRadians());

		assertEquals(40.0, reconstructed.getAltitude(), 1e-3);
		assertEquals(260.0, reconstructed.getAzimuth(), 1e-3);
	}

	@Test
	void reconstructAltAzMatchesTheStraightUpWorkedExample() {
		// Mirrors targetOffsetInAltitudeAppearsStraightUpInAnUnrolledFrame's setup, inverted:
		// theta=10 degrees, phi=90 degrees (straight up) should reconstruct back to alt=10, az=0.
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		ObjectDirectionAltAz altAz = BoresightAngles.reconstructAltAz(camera, Math.toRadians(10.0), Math.PI / 2.0);

		assertEquals(10.0, altAz.getAltitude(), 1e-3);
		assertEquals(0.0, altAz.getAzimuth(), 1e-3);
	}

	private static double normalize(double angleRadians) {
		double twoPi = 2.0 * Math.PI;
		double result = angleRadians % twoPi;
		if (result < 0.0)
			result += twoPi;
		return result;
	}
}
