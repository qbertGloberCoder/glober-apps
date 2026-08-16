package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Locks down the full Real/Virtual x sub-type matrix from CLAUDE.md's "Camera setup" section -
// this has been corrected twice across the design conversation, so it's worth pinning precisely.
class CameraTypeTest {

	@Test
	void liveAndRecordedRealCameraIsFixedAndNeverEligible() {
		CameraType type = CameraType.real(RealCaptureMode.LIVE_AND_RECORDED);

		assertEquals(OrientationMode.FIXED, type.getOrientationMode());
		assertFalse(type.isEquatorialMountEligible());
		assertFalse(type.usesEpochBasedMount());
	}

	@Test
	void preRecordedOnlyRealCameraIsFixedAndEligibleViaEpoch() {
		CameraType type = CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY);

		assertEquals(OrientationMode.FIXED, type.getOrientationMode());
		assertTrue(type.isEquatorialMountEligible());
		assertTrue(type.usesEpochBasedMount(), "pre-recorded-only real cameras use the epoch mechanism, not activation-based");
	}

	@Test
	void virtual360IsPtzAndEligibleViaActivation() {
		CameraType type = CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360);

		assertEquals(OrientationMode.PTZ, type.getOrientationMode());
		assertTrue(type.isEquatorialMountEligible());
		assertFalse(type.usesEpochBasedMount());
	}

	@Test
	void virtualStaticDirectionalIsFixedButStillEligible() {
		CameraType type = CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL);

		assertEquals(OrientationMode.FIXED, type.getOrientationMode());
		assertTrue(type.isEquatorialMountEligible(), "Fixed virtual cameras ARE eligible - corrected from an earlier PTZ-only rule");
		assertFalse(type.usesEpochBasedMount(), "epoch mechanism is Real-camera-specific, even though this is also Fixed");
	}

	@Test
	void accessorsRejectTheWrongKind() {
		CameraType real = CameraType.real(RealCaptureMode.LIVE_AND_RECORDED);
		assertThrows(IllegalStateException.class, real::getVirtualImageSource);

		CameraType virtual = CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL);
		assertThrows(IllegalStateException.class, virtual::getRealCaptureMode);
	}
}
