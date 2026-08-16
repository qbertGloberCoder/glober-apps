package me.qbert.skywatch.camera.plate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraConfigStore;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.render.ImagePlacement;

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

// Task 7.1's manual plate solve (technique 1 - visual comparison [CLAUDE.md's "Plate solving is
// Real archived-camera-only"]): "core support (apply override, recompute, redraw) can land before
// any real UI exists." This is task 0.7's live-edit/Save/Revert model, applied specifically to the
// Fixed-camera case that model was written for (real or virtual - PTZ has no such model at all, it
// auto-persists on exit/switch instead, see CLAUDE.md's "Orientation editing"):
//
//  - "Update the camera's orientation": adjustOrientation(...)/adjustLocation(...)/adjustZoom(...)
//    freely mutate an in-memory PENDING value - never touch CalibrationHistory or the persisted
//    file. render(...) is "apply override, recompute, redraw" in one call: it composes a preview
//    frame using the PENDING orientation/location, not whatever CalibrationHistory.latest() says,
//    so a manual adjustment's effect is visible before it's ever saved.
//  - "Save": commits the pending values as a new, appended §7.2 calibration entry (orientation AND
//    location together, per CalibrationEntry/task 1.2), then persists the whole camera profile via
//    CameraConfigStore. Calibration history is append-only (CalibrationHistory's own contract) -
//    Save is always a new entry, never an edit to an existing one.
//  - "Revert to previously saved values": reloads the whole camera fresh from the backing config
//    file, discarding the in-memory pending edit and resetting the pending baseline to whatever was
//    actually last persisted - the user's own words, quoted directly in CLAUDE.md's "Orientation
//    editing" section.
public final class PlateSolveSession {
	private CameraConfig cameraConfig;
	private final File configFile;

	private Orientation pendingOrientation;
	private double pendingLatitude;
	private double pendingLongitude;
	private double pendingZoom;
	private boolean hasPendingEdit;

	public PlateSolveSession(CameraConfig cameraConfig, File configFile) {
		if (cameraConfig == null)
			throw new IllegalArgumentException("cameraConfig must not be null");
		if (configFile == null)
			throw new IllegalArgumentException("configFile must not be null");
		requireFixed(cameraConfig);

		this.cameraConfig = cameraConfig;
		this.configFile = configFile;
		resetPendingToCurrent();
	}

	public CameraConfig getCameraConfig() {
		return cameraConfig;
	}

	public Orientation getPendingOrientation() {
		return pendingOrientation;
	}

	public double getPendingLatitude() {
		return pendingLatitude;
	}

	public double getPendingLongitude() {
		return pendingLongitude;
	}

	public double getPendingZoom() {
		return pendingZoom;
	}

	// True once any adjustXxx(...) call has been made since construction, the last save(), or the
	// last revert() - whichever happened most recently. Purely informational (e.g. for a future
	// UI's "unsaved changes" indicator) - render(...)/save()/revert() all work the same whether or
	// not this is true.
	public boolean hasPendingEdit() {
		return hasPendingEdit;
	}

	// "Update the camera's orientation" [CLAUDE.md's "Orientation editing"] - altitude/azimuth/
	// barrel-roll, all three bundled in Orientation as everywhere else in this module.
	public void adjustOrientation(Orientation orientation) {
		if (orientation == null)
			throw new IllegalArgumentException("orientation must not be null");
		this.pendingOrientation = orientation;
		this.hasPendingEdit = true;
	}

	public void adjustLocation(double latitude, double longitude) {
		this.pendingLatitude = latitude;
		this.pendingLongitude = longitude;
		this.hasPendingEdit = true;
	}

	public void adjustZoom(double zoom) {
		this.pendingZoom = zoom;
		this.hasPendingEdit = true;
	}

	// "Apply override, recompute, redraw" in one call - composes a preview frame against a
	// caller-supplied still image (the archived frame being plate-solved against - technique 1 is
	// always a visual comparison against a specific real photo) using the PENDING orientation/
	// location, not whatever is actually persisted. options' cameraImage/placement are overwritten
	// (matching BatchReprocessor.compositeOverlay's own established contract for this - both are
	// inherent to what a still-frame preview IS, not a caller choice) - every other field (stars,
	// colorScheme, labels, graticule, ...) is the caller's own configuration and is read as-is.
	public BufferedImage render(BufferedImage stillFrame, ObservationTime time, FrameCompositor.Options options)
			throws Exception {
		if (stillFrame == null)
			throw new IllegalArgumentException("stillFrame must not be null");
		if (time == null)
			throw new IllegalArgumentException("time must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");

		// withFocalLength(pendingZoom) is what makes "zoom" actually do anything - requireProjection()
		// alone is the camera's fixed, persisted lens; a real user report found the zoom spinner had
		// no effect at all before this, since nothing ever fed pendingZoom back into the projection
		// used to render.
		CameraProjection projection = requireProjection().withFocalLength(pendingZoom);
		// Plate solving (technique 1 - visual comparison against stillFrame) only makes sense for a
		// Real camera's real photo - PlateSolveSession itself is also reused for a Virtual Fixed
		// camera's ordinary orientation editing (CLAUDE.md: "have the same save/revert rules as a
		// real camera"), but a Virtual camera's still image is never a photograph of anything real,
		// so there's no lens distortion to correct for there - direct user instruction.
		((AbstractCameraProjection) projection).setDistortionEnabled(cameraConfig.getType().getKind() == CameraType.Kind.REAL);
		// Dusk/night fade is Virtual-camera-only (direct user instruction - see FrameCompositor.
		// Options' applyLayer1DuskFade field comment) - the same Real-vs-Virtual split as distortion
		// above, just inverted.
		options.setApplyLayer1DuskFade(cameraConfig.getType().getKind() == CameraType.Kind.VIRTUAL);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(pendingLatitude, pendingLongitude);

		options.setCameraImage(stillFrame).setPlacement(ImagePlacement.LAYER_1);

		return FrameCompositor.compose(projection, pendingOrientation, time, location, options);
	}

	// Commits the pending orientation/location/zoom as a new calibration entry effective from
	// effectiveFromEpochMillis, and persists the whole camera profile to configFile. Always a new
	// append (CalibrationHistory.append(...)'s own contract) - never edits an existing entry, even
	// one from earlier in this same session.
	public void save(long effectiveFromEpochMillis) throws IOException {
		CalibrationEntry newEntry = new CalibrationEntry(effectiveFromEpochMillis, pendingOrientation, pendingZoom,
				pendingLatitude, pendingLongitude);
		cameraConfig.getCalibrationHistory().append(newEntry);
		CameraConfigStore.save(cameraConfig, configFile);
		hasPendingEdit = false;
	}

	// "Revert to previously saved values" - reloads the camera fresh from configFile (replacing
	// this session's CameraConfig reference entirely, not mutating the old one in place - see the
	// class comment), discarding any in-memory pending edit.
	public void revert() throws IOException {
		this.cameraConfig = CameraConfigStore.load(configFile);
		requireFixed(cameraConfig);
		resetPendingToCurrent();
	}

	private void resetPendingToCurrent() {
		CalibrationEntry latest = requireLatestCalibration();
		this.pendingOrientation = latest.getOrientation();
		this.pendingLatitude = latest.getLatitude();
		this.pendingLongitude = latest.getLongitude();
		this.pendingZoom = latest.getZoom();
		this.hasPendingEdit = false;
	}

	private CameraProjection requireProjection() {
		CameraProjection projection = cameraConfig.getProjection();
		if (projection == null)
			throw new IllegalStateException("camera " + cameraConfig.getName() + " has no projection configured");
		return projection;
	}

	private CalibrationEntry requireLatestCalibration() {
		CalibrationEntry latest = cameraConfig.getCalibrationHistory().latest();
		if (latest == null)
			throw new IllegalStateException("camera " + cameraConfig.getName()
					+ " has no calibration entries yet - append an initial one before starting a plate-solve session");
		return latest;
	}

	private void requireFixed(CameraConfig cameraConfig) {
		if (cameraConfig.getType().getOrientationMode() != OrientationMode.FIXED)
			throw new IllegalArgumentException("plate-solve Save/Revert is Fixed-camera-only (real or virtual) - "
					+ "PTZ cameras auto-persist instead, see CLAUDE.md's \"Orientation editing\"");
	}
}
