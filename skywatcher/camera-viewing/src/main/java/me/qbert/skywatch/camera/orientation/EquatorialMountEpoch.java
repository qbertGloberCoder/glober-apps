package me.qbert.skywatch.camera.orientation;

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

// The equatorial-mount mechanism for Pre-recorded-only Real cameras (see CameraType,
// RealCaptureMode, and CLAUDE.md's "Real camera on a real EQ mount") - deliberately a different,
// simpler mechanism from EquatorialMountController's PTZ activation/deactivation state machine,
// not a variant of it: there is no live "engaged/disengaged" state here, no
// enable()/disable()/persist-on-disable step, nothing to reconcile with CameraConfig's PTZ-only
// auto-persist. The user sets an EQ mount epoch - a specific archived frame's own calibrated
// orientation and filename-derived timestamp - once, and every other frame's orientation while
// scrubbing back and forth is a pure re-evaluation of that same fixed reference, never mutated.
//
// This reuses EquatorialMountTransform's lock()/computeLockedOrientation() directly - setting the
// epoch IS locking, and it is simply never unlocked for the lifetime of this camera's mount
// configuration.
public final class EquatorialMountEpoch {
	private final EquatorialMountTransform transform = new EquatorialMountTransform();

	public void setEpoch(Orientation epochFrameOrientation, double observerLatitude, long epochFrameEpochMillis) {
		transform.lock(epochFrameOrientation, observerLatitude, epochFrameEpochMillis);
	}

	public boolean hasEpoch() {
		return transform.isLocked();
	}

	public void setTrackingRate(TrackingRate trackingRate) {
		transform.setTrackingRate(trackingRate);
	}

	public void setTrackingRateDegreesPerHour(double trackingRateDegreesPerHour) {
		transform.setTrackingRateDegreesPerHour(trackingRateDegreesPerHour);
	}

	// The orientation to render the computed overlay at for the frame captured at
	// frameEpochMillis - a pure function of the epoch and this timestamp, recomputed fresh on
	// every call (e.g. every scrub position), never cached/mutated.
	public Orientation orientationForFrame(long frameEpochMillis) {
		if (!transform.isLocked())
			throw new IllegalStateException("no epoch set - call setEpoch(...) first");
		return transform.computeLockedOrientation(frameEpochMillis);
	}
}
