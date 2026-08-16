package me.qbert.skywatch.camera.config;

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

// Two axes: what a camera IS (Real or Virtual, chosen at setup) and its sub-type - Fixed-vs-PTZ
// (OrientationMode) and equatorial-mount/geolocation-stabilizer eligibility both FOLLOW from this,
// they are never chosen independently. See CLAUDE.md's "Camera setup: Real vs. Virtual".
public final class CameraType {
	public enum Kind {
		REAL,
		VIRTUAL
	}

	private final Kind kind;
	private final RealCaptureMode realCaptureMode;
	private final VirtualImageSource virtualImageSource;

	private CameraType(Kind kind, RealCaptureMode realCaptureMode, VirtualImageSource virtualImageSource) {
		this.kind = kind;
		this.realCaptureMode = realCaptureMode;
		this.virtualImageSource = virtualImageSource;
	}

	public static CameraType real(RealCaptureMode captureMode) {
		if (captureMode == null)
			throw new IllegalArgumentException("captureMode must not be null");
		return new CameraType(Kind.REAL, captureMode, null);
	}

	public static CameraType virtual(VirtualImageSource imageSource) {
		if (imageSource == null)
			throw new IllegalArgumentException("imageSource must not be null");
		return new CameraType(Kind.VIRTUAL, null, imageSource);
	}

	public Kind getKind() {
		return kind;
	}

	public RealCaptureMode getRealCaptureMode() {
		if (kind != Kind.REAL)
			throw new IllegalStateException("not a Real camera");
		return realCaptureMode;
	}

	public VirtualImageSource getVirtualImageSource() {
		if (kind != Kind.VIRTUAL)
			throw new IllegalStateException("not a Virtual camera");
		return virtualImageSource;
	}

	// Real cameras are always Fixed, never PTZ - no per-frame orientation metadata, out of scope.
	public OrientationMode getOrientationMode() {
		if (kind == Kind.REAL)
			return OrientationMode.FIXED;

		return (virtualImageSource == VirtualImageSource.STATIC_DIRECTIONAL)
				? OrientationMode.FIXED
				: OrientationMode.PTZ;
	}

	// Any Virtual camera (Fixed or PTZ) or a Pre-recorded-only Real camera; never a
	// Live-and-recorded Real camera. See CLAUDE.md's "Camera setup" and "Real camera on a real EQ
	// mount" - this rule has been corrected twice (first "PTZ-only", then "any Virtual"), this is
	// the current final answer.
	public boolean isEquatorialMountEligible() {
		if (kind == Kind.VIRTUAL)
			return true;

		return realCaptureMode == RealCaptureMode.PRE_RECORDED_ONLY;
	}

	// A Pre-recorded-only Real camera uses the epoch-based mechanism (no live "engaged/disengaged"
	// state - see CLAUDE.md's "Mount mode + enable control"); a Virtual camera uses the
	// activation-based one (MountControl's enable toggle actually matters).
	public boolean usesEpochBasedMount() {
		return isEquatorialMountEligible() && kind == Kind.REAL;
	}
}
