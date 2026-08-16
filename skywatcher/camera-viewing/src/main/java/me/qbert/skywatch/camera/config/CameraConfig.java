package me.qbert.skywatch.camera.config;

import me.qbert.skywatch.camera.orientation.MountControl;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.ZoomRange;

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

// Base per-camera config [spec §7.1]: observer location, time bias, and this camera's orientation
// state - a CalibrationHistory for Fixed cameras (real or virtual), or a single mutable "current
// orientation" for PTZ cameras (no history - see CameraType). Only the field matching
// getType().getOrientationMode() is meaningful; the accessors guard against reading the wrong one,
// matching CameraType's own style.
//
// worldModel (spec §7.1's globe/flat toggle) deliberately does NOT live here - a real user report:
// it was originally modeled per-camera despite having zero downstream consumers anywhere in the
// rendering pipeline, and isn't actually a property of any one camera. It now lives on
// config.GlobalSettings instead - see that class's own comment.
//
// observerLocation (below) is the config-time base/default location, present for every camera.
// Task 1.3b's location-editing tab operates on two different things depending on orientation mode,
// mirroring how orientation editing already splits: a Fixed camera's location travels with its
// CalibrationEntry (getCalibrationHistory(), now location-bearing - see CLAUDE.md's "Fixed cameras'
// location joins the time-versioned calibration entry"); a PTZ camera gets a second single mutable
// value, currentLocation, auto-persisted exactly like currentOrientation (no history - "roaming" is
// just this control plus the geolocation stabilizer if engaged).
public final class CameraConfig {
	private final String name;
	private final CameraType type;
	private long timeBiasMillis = 0L;
	private ObserverLocationSetting observerLocation;

	// Fixed cameras only.
	private final CalibrationHistory calibrationHistory;
	// PTZ cameras only.
	private Orientation currentOrientation;
	// PTZ cameras only - a second single mutable value alongside currentOrientation (task 1.3b).
	private ObserverLocationSetting currentLocation;

	// Present only when type.isEquatorialMountEligible() - see CameraType.
	private final MountControl mountControl;

	// Real cameras only - null until configured (see RealImageSource; no config-file loader exists
	// yet per task 0.4's progress note, so nothing requires this to be set at construction).
	private RealImageSource realImageSource;
	// Virtual cameras only - null until configured. A single static scene image, replaceable later
	// (CLAUDE.md's "Camera setup") - never a directory/template/timezone concern, unlike Real.
	private String virtualScenePath;
	// Virtual cameras only - unlike virtualScenePath, this has a sensible default ("the ordinary/
	// default placement" per CLAUDE.md's Layer model table), so it's never null for a Virtual camera.
	private VirtualImagePlacement virtualImagePlacement;

	// Present for every camera, Real or Virtual, Fixed or PTZ - unlike realImageSource/
	// virtualScenePath, this is NOT type-gated: CLAUDE.md's "Camera projection model" is explicit
	// that a Virtual PTZ camera "may use any projection freely, including changing it", so there is
	// no kind-based restriction to enforce here. A Real camera's projection should be set to match
	// its actual physical lens - that correctness is the caller's responsibility (nothing here can
	// verify a declared lens model matches real hardware). Null until explicitly configured.
	private CameraProjection projection;
	// The declared zoom capability this camera's projection is expected to stay within (optional,
	// independent of whether projection itself is set yet). Enforcing it against a specific
	// CameraProjection instance's own focal length is left to the caller - CameraProjection doesn't
	// universally expose one (e.g. an equirectangular "lens" has no single focal-length concept).
	private ZoomRange zoomRange;

	public CameraConfig(String name, CameraType type, ObserverLocationSetting observerLocation) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("name must not be empty");
		if (type == null)
			throw new IllegalArgumentException("type must not be null");
		if (observerLocation == null)
			throw new IllegalArgumentException("observerLocation must not be null");

		this.name = name;
		this.type = type;
		this.observerLocation = observerLocation;

		boolean isFixed = type.getOrientationMode() == OrientationMode.FIXED;
		this.calibrationHistory = isFixed ? new CalibrationHistory() : null;
		this.currentOrientation = isFixed ? null : new Orientation(0.0, 0.0, 0.0);
		this.currentLocation = isFixed ? null : observerLocation;

		this.mountControl = type.isEquatorialMountEligible() ? new MountControl() : null;

		this.virtualImagePlacement = type.getKind() == CameraType.Kind.VIRTUAL ? VirtualImagePlacement.LAYER_1 : null;
	}

	public String getName() {
		return name;
	}

	public CameraType getType() {
		return type;
	}

	public long getTimeBiasMillis() {
		return timeBiasMillis;
	}

	public void setTimeBiasMillis(long timeBiasMillis) {
		this.timeBiasMillis = timeBiasMillis;
	}

	public ObserverLocationSetting getObserverLocation() {
		return observerLocation;
	}

	public void setObserverLocation(ObserverLocationSetting observerLocation) {
		if (observerLocation == null)
			throw new IllegalArgumentException("observerLocation must not be null");
		this.observerLocation = observerLocation;
	}

	public CalibrationHistory getCalibrationHistory() {
		if (calibrationHistory == null)
			throw new IllegalStateException("PTZ cameras have no calibration history - see CameraType");
		return calibrationHistory;
	}

	public Orientation getCurrentOrientation() {
		if (currentOrientation == null)
			throw new IllegalStateException("Fixed cameras have no single current orientation - use getCalibrationHistory()");
		return currentOrientation;
	}

	public void setCurrentOrientation(Orientation currentOrientation) {
		if (this.currentOrientation == null)
			throw new IllegalStateException("Fixed cameras have no single current orientation - append to getCalibrationHistory() instead");
		if (currentOrientation == null)
			throw new IllegalArgumentException("currentOrientation must not be null");
		this.currentOrientation = currentOrientation;
	}

	// PTZ cameras only - see the class comment. Fixed cameras' location lives in
	// getCalibrationHistory()'s entries instead, versioned together with orientation.
	public ObserverLocationSetting getCurrentLocation() {
		if (currentLocation == null)
			throw new IllegalStateException("Fixed cameras have no single current location - use getCalibrationHistory() instead");
		return currentLocation;
	}

	public void setCurrentLocation(ObserverLocationSetting currentLocation) {
		if (this.currentLocation == null)
			throw new IllegalStateException("Fixed cameras have no single current location - append to getCalibrationHistory() instead");
		if (currentLocation == null)
			throw new IllegalArgumentException("currentLocation must not be null");
		this.currentLocation = currentLocation;
	}

	// Never null when type.isEquatorialMountEligible() is true; throws otherwise, matching the
	// other type-conditional accessors on this class.
	public MountControl getMountControl() {
		if (mountControl == null)
			throw new IllegalStateException("this camera type is never equatorial-mount/geolocation-stabilizer eligible - see CameraType.isEquatorialMountEligible()");
		return mountControl;
	}

	// Real cameras only. Null (not an error) until explicitly configured - see the field comment.
	public RealImageSource getRealImageSource() {
		if (type.getKind() != CameraType.Kind.REAL)
			throw new IllegalStateException("not a Real camera - see getVirtualScenePath() instead");
		return realImageSource;
	}

	public void setRealImageSource(RealImageSource realImageSource) {
		if (type.getKind() != CameraType.Kind.REAL)
			throw new IllegalStateException("not a Real camera - see setVirtualScenePath() instead");
		if (realImageSource == null)
			throw new IllegalArgumentException("realImageSource must not be null");
		this.realImageSource = realImageSource;
	}

	// Virtual cameras only. Null (not an error) until explicitly configured - see the field comment.
	public String getVirtualScenePath() {
		if (type.getKind() != CameraType.Kind.VIRTUAL)
			throw new IllegalStateException("not a Virtual camera - see getRealImageSource() instead");
		return virtualScenePath;
	}

	public void setVirtualScenePath(String virtualScenePath) {
		if (type.getKind() != CameraType.Kind.VIRTUAL)
			throw new IllegalStateException("not a Virtual camera - see setRealImageSource() instead");
		if (virtualScenePath == null || virtualScenePath.isEmpty())
			throw new IllegalArgumentException("virtualScenePath must not be empty");
		this.virtualScenePath = virtualScenePath;
	}

	// Virtual cameras only. Defaults to LAYER_1 - never null for a Virtual camera, unlike
	// virtualScenePath (which has no sensible default and stays null until configured).
	public VirtualImagePlacement getVirtualImagePlacement() {
		if (type.getKind() != CameraType.Kind.VIRTUAL)
			throw new IllegalStateException("not a Virtual camera - Real cameras are always Layer 1, no stored setting");
		return virtualImagePlacement;
	}

	public void setVirtualImagePlacement(VirtualImagePlacement virtualImagePlacement) {
		if (type.getKind() != CameraType.Kind.VIRTUAL)
			throw new IllegalStateException("not a Virtual camera - Real cameras are always Layer 1, no stored setting");
		if (virtualImagePlacement == null)
			throw new IllegalArgumentException("virtualImagePlacement must not be null");
		this.virtualImagePlacement = virtualImagePlacement;
	}

	// Null (not an error) until explicitly configured - see the field comment. Not type-gated.
	public CameraProjection getProjection() {
		return projection;
	}

	public void setProjection(CameraProjection projection) {
		if (projection == null)
			throw new IllegalArgumentException("projection must not be null");
		this.projection = projection;
	}

	public ZoomRange getZoomRange() {
		return zoomRange;
	}

	public void setZoomRange(ZoomRange zoomRange) {
		if (zoomRange == null)
			throw new IllegalArgumentException("zoomRange must not be null");
		this.zoomRange = zoomRange;
	}
}
