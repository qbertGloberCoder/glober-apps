package me.qbert.skywatch.camera.ui;

import java.awt.GridLayout;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImagePlacement;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;

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

// The Add/Edit form the control panel's camera-management section uses. Mirrors exactly what
// cli.CameraConfigArgs.buildRealCamera(...)/buildVirtualCamera(...)'s flag-based paths already
// build - this is the same construction, as a form instead of flags.
//
// A genuinely MODAL dialog (unlike ControlPanel, which the user asked for as a live-updating,
// non-blocking control surface) - Add/Edit is a one-shot "fill in the fields, commit or cancel"
// interaction, not something that needs to coexist with other live editing.
//
// buildRealCameraConfig(...)/buildVirtualCameraConfig(...) are deliberately separate, package-visible
// static methods (not inlined into the OK-button handler) so the field-to-CameraConfig construction
// logic is directly unit-testable - the JDialog shell itself throws HeadlessException in this
// module's own display-less test environment (same standing caveat as every other Window subclass
// here), but the actual construction/validation logic doesn't need AWT at all and shouldn't be
// untestable just because it happens to live next to a dialog. Split into two methods (rather than
// one method with an ever-growing parameter list plus a Kind branch inside it) because Real and
// Virtual cameras genuinely need different required fields - Real cameras don't have a Kind
// selector's other case cluttering their own parameter list, and vice versa.
public final class CameraEditDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private final JComboBox<CameraType.Kind> kindField = new JComboBox<>(CameraType.Kind.values());
	private final JTextField nameField = new JTextField();

	// Shared by both kinds - both a Real camera's own lens and a Virtual camera's synthetic one use
	// the same CameraProjection model (see CameraConfig's own "not type-gated" comment on
	// getProjection()/setProjection()).
	private final JTextField latitudeField = new JTextField("0.0");
	private final JTextField longitudeField = new JTextField("0.0");
	private final JTextField altitudeField = new JTextField("0.0");
	private final JTextField azimuthField = new JTextField("0.0");
	private final JTextField rollField = new JTextField("0.0");
	private final JTextField focalLengthField = new JTextField("50.0");
	private final JComboBox<String> lensField = new JComboBox<>(new String[] { "rectilinear", "fisheye" });
	private final JTextField fisheyeMaxAngleField = new JTextField("180.0");

	// Real-only.
	private final JComboBox<RealCaptureMode> captureModeField = new JComboBox<>(RealCaptureMode.values());
	private final JTextField archiveTemplateField = new JTextField();
	private final JTextField latestPathField = new JTextField();
	private final JTextField timezoneField = new JTextField("system");

	// Virtual-only.
	private final JComboBox<VirtualImageSource> virtualImageSourceField = new JComboBox<>(VirtualImageSource.values());
	private final JTextField virtualScenePathField = new JTextField();
	private final JComboBox<VirtualImagePlacement> virtualImagePlacementField = new JComboBox<>(VirtualImagePlacement.values());

	// existingConfig null means "Add" (blank form); non-null means "Edit" (prefilled from it, name
	// and kind both locked - switching kind happens by removing and re-adding in this pass, not an
	// in-place conversion).
	public CameraEditDialog(JFrame owner, CameraConfig existingConfig, Consumer<CameraConfig> onSave) {
		super(owner, existingConfig == null ? "Add camera" : "Edit camera " + existingConfig.getName(), true);
		if (onSave == null)
			throw new IllegalArgumentException("onSave must not be null");

		if (existingConfig != null)
			prefillFrom(existingConfig);

		setLayout(new GridLayout(0, 2, 4, 4));
		addRow("Kind", kindField);
		addRow("Name", nameField);
		addRow("Latitude (deg)", latitudeField);
		addRow("Longitude (deg)", longitudeField);
		addRow("Initial altitude (deg)", altitudeField);
		addRow("Initial azimuth (deg)", azimuthField);
		addRow("Initial barrel roll (deg)", rollField);
		addRow("Focal length (mm)", focalLengthField);
		addRow("Lens", lensField);
		addRow("Fisheye max angle (deg)", fisheyeMaxAngleField);
		addRow("Capture mode (Real only)", captureModeField);
		addRow("Archive template (Real only)", archiveTemplateField);
		addRow("Latest path (Real, live+recorded only)", latestPathField);
		addRow("Timezone (Real only, or \"system\")", timezoneField);
		addRow("Virtual image source (Virtual only)", virtualImageSourceField);
		addRow("Scene image path (Virtual only)", virtualScenePathField);
		addRow("Image placement (Virtual only)", virtualImagePlacementField);

		if (existingConfig != null) {
			kindField.setEnabled(false);
		} else {
			kindField.addActionListener(e -> refreshFieldsEnabledForKind());
		}
		refreshFieldsEnabledForKind();

		JButton ok = new JButton("Save");
		ok.addActionListener(e -> {
			try {
				CameraType.Kind kind = (CameraType.Kind) kindField.getSelectedItem();
				CameraConfig config = kind == CameraType.Kind.VIRTUAL ? buildVirtualCameraFromFields() : buildRealCameraFromFields();
				onSave.accept(config);
				dispose();
			} catch (IllegalArgumentException | DateTimeException ex) {
				JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid camera settings", JOptionPane.ERROR_MESSAGE);
			}
		});
		add(new JLabel());
		add(ok);

		pack();
	}

	// Real-only fields are meaningless for a Virtual camera and vice versa - grey out whichever set
	// doesn't apply to the currently selected Kind, rather than hiding/removing rows (simpler than
	// juggling GridLayout add/remove, and this module's headless test environment can't visually
	// confirm either approach anyway - see the class comment's standing caveat).
	private void refreshFieldsEnabledForKind() {
		boolean isVirtual = kindField.getSelectedItem() == CameraType.Kind.VIRTUAL;
		captureModeField.setEnabled(!isVirtual);
		archiveTemplateField.setEnabled(!isVirtual);
		latestPathField.setEnabled(!isVirtual);
		timezoneField.setEnabled(!isVirtual);
		virtualImageSourceField.setEnabled(isVirtual);
		virtualScenePathField.setEnabled(isVirtual);
		virtualImagePlacementField.setEnabled(isVirtual);
	}

	private void addRow(String label, java.awt.Component field) {
		add(new JLabel(label));
		add(field);
	}

	// The reverse of buildRealCameraConfig(...)/buildVirtualCameraConfig(...) - pulls the form's raw
	// field values back out of an already-built CameraConfig, for "Edit" to start from the camera's
	// current saved state rather than a blank form.
	private void prefillFrom(CameraConfig config) {
		nameField.setText(config.getName());
		nameField.setEditable(false);
		kindField.setSelectedItem(config.getType().getKind());

		CameraProjection projection = config.getProjection();
		if (projection instanceof FisheyeProjection) {
			FisheyeProjection fisheye = (FisheyeProjection) projection;
			focalLengthField.setText(String.valueOf(fisheye.getFocalLengthMillimeters()));
			lensField.setSelectedItem("fisheye");
			fisheyeMaxAngleField.setText(String.valueOf(Math.toDegrees(fisheye.getMaxAngleRadians())));
		} else if (projection instanceof RectilinearProjection) {
			focalLengthField.setText(String.valueOf(((RectilinearProjection) projection).getFocalLengthMillimeters()));
			lensField.setSelectedItem("rectilinear");
		}

		if (config.getType().getKind() == CameraType.Kind.VIRTUAL)
			prefillVirtualFrom(config);
		else
			prefillRealFrom(config);
	}

	private void prefillRealFrom(CameraConfig config) {
		captureModeField.setSelectedItem(config.getType().getRealCaptureMode());

		CalibrationEntry latest = config.getCalibrationHistory().latest();
		latitudeField.setText(String.valueOf(latest.getLatitude()));
		longitudeField.setText(String.valueOf(latest.getLongitude()));
		altitudeField.setText(String.valueOf(latest.getOrientation().getAltitude()));
		azimuthField.setText(String.valueOf(latest.getOrientation().getAzimuth()));
		rollField.setText(String.valueOf(latest.getOrientation().getBarrelRoll()));

		RealImageSource source = config.getRealImageSource();
		if (source != null) {
			archiveTemplateField.setText(source.getArchiveTemplate());
			if (source.hasLatestSource())
				latestPathField.setText(source.getLatestPath());
			timezoneField.setText(
					source.getTimezone().isUseSystemDefault() ? "system" : source.getTimezone().getExplicitZone().getId());
		}
	}

	// Fixed Virtual cameras (STATIC_DIRECTIONAL) version orientation/location through the same
	// CalibrationHistory Real cameras use; PTZ Virtual cameras (EQUIRECTANGULAR_360) instead keep a
	// single mutable currentOrientation/currentLocation - see CameraConfig's own class comment on
	// why these are two different storage shapes.
	private void prefillVirtualFrom(CameraConfig config) {
		virtualImageSourceField.setSelectedItem(config.getType().getVirtualImageSource());
		virtualImagePlacementField.setSelectedItem(config.getVirtualImagePlacement());
		if (config.getVirtualScenePath() != null)
			virtualScenePathField.setText(config.getVirtualScenePath());

		if (config.getType().getOrientationMode() == OrientationMode.FIXED) {
			CalibrationEntry latest = config.getCalibrationHistory().latest();
			latitudeField.setText(String.valueOf(latest.getLatitude()));
			longitudeField.setText(String.valueOf(latest.getLongitude()));
			altitudeField.setText(String.valueOf(latest.getOrientation().getAltitude()));
			azimuthField.setText(String.valueOf(latest.getOrientation().getAzimuth()));
			rollField.setText(String.valueOf(latest.getOrientation().getBarrelRoll()));
		} else {
			Orientation orientation = config.getCurrentOrientation();
			altitudeField.setText(String.valueOf(orientation.getAltitude()));
			azimuthField.setText(String.valueOf(orientation.getAzimuth()));
			rollField.setText(String.valueOf(orientation.getBarrelRoll()));

			ObserverLocationSetting location = config.getCurrentLocation();
			if (!location.isUseSystemLocale()) {
				latitudeField.setText(String.valueOf(location.getLatitude()));
				longitudeField.setText(String.valueOf(location.getLongitude()));
			}
		}
	}

	private double parseDouble(JTextField field, String fieldName) {
		try {
			return Double.parseDouble(field.getText().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("\"" + fieldName + "\" must be a number, was \"" + field.getText() + "\"");
		}
	}

	private CameraConfig buildRealCameraFromFields() {
		return buildRealCameraConfig(nameField.getText(), (RealCaptureMode) captureModeField.getSelectedItem(),
				parseDouble(latitudeField, "latitude"), parseDouble(longitudeField, "longitude"),
				parseDouble(altitudeField, "altitude"), parseDouble(azimuthField, "azimuth"),
				parseDouble(rollField, "barrel roll"), parseDouble(focalLengthField, "focal length"),
				(String) lensField.getSelectedItem(), parseDouble(fisheyeMaxAngleField, "fisheye max angle"),
				archiveTemplateField.getText(), latestPathField.getText(), timezoneField.getText());
	}

	private CameraConfig buildVirtualCameraFromFields() {
		return buildVirtualCameraConfig(nameField.getText(), (VirtualImageSource) virtualImageSourceField.getSelectedItem(),
				(VirtualImagePlacement) virtualImagePlacementField.getSelectedItem(),
				parseDouble(latitudeField, "latitude"), parseDouble(longitudeField, "longitude"),
				parseDouble(altitudeField, "altitude"), parseDouble(azimuthField, "azimuth"),
				parseDouble(rollField, "barrel roll"), parseDouble(focalLengthField, "focal length"),
				(String) lensField.getSelectedItem(), parseDouble(fisheyeMaxAngleField, "fisheye max angle"),
				virtualScenePathField.getText());
	}

	// Package-visible and static specifically for direct testing - see the class comment. Mirrors
	// cli.CameraConfigArgs.buildRealCamera(...)'s flag-based construction (name/capture mode/lat/
	// lon/alt/az/roll/focal-length/lens/fisheye-max-angle/archive-template/latest/timezone) exactly,
	// just taking already-parsed values instead of an ArgScanner.
	static CameraConfig buildRealCameraConfig(String name, RealCaptureMode captureMode, double latitude, double longitude,
			double altitude, double azimuth, double roll, double focalLength, String lens, double fisheyeMaxAngleDegrees,
			String archiveTemplate, String latestPath, String timezone) {
		if (name == null || name.trim().isEmpty())
			throw new IllegalArgumentException("name must not be empty");
		if (captureMode == null)
			throw new IllegalArgumentException("capture mode must be selected");
		if (archiveTemplate == null || archiveTemplate.trim().isEmpty())
			throw new IllegalArgumentException("archive template must not be empty");
		if (captureMode == RealCaptureMode.LIVE_AND_RECORDED && (latestPath == null || latestPath.trim().isEmpty()))
			throw new IllegalArgumentException("latest path is required for a live+recorded camera");

		TimezoneSetting timezoneSetting = "system".equalsIgnoreCase(timezone == null ? "" : timezone.trim())
				? TimezoneSetting.useSystemDefault()
				: TimezoneSetting.explicit(ZoneId.of(timezone.trim()));

		CameraProjection projection = "fisheye".equalsIgnoreCase(lens)
				? new FisheyeProjection(focalLength, Math.toRadians(fisheyeMaxAngleDegrees))
				: new RectilinearProjection(focalLength);

		RealImageSource source = captureMode == RealCaptureMode.LIVE_AND_RECORDED
				? RealImageSource.liveAndRecorded(latestPath.trim(), archiveTemplate.trim(), timezoneSetting,
						DstAmbiguousPolicy.ASSUME_STANDARD)
				: RealImageSource.preRecordedOnly(archiveTemplate.trim(), timezoneSetting, DstAmbiguousPolicy.ASSUME_STANDARD);

		CameraConfig config = new CameraConfig(name.trim(), CameraType.real(captureMode),
				ObserverLocationSetting.explicit(latitude, longitude));
		config.setProjection(projection);
		config.getCalibrationHistory()
				.append(new CalibrationEntry(0L, new Orientation(altitude, azimuth, roll), focalLength, latitude, longitude));
		config.setRealImageSource(source);

		return config;
	}

	// Virtual counterpart to buildRealCameraConfig(...) above - see this class's own comment on why
	// these are two separate methods rather than one with a Kind branch inside it. Handles both
	// Virtual sub-types (Fixed STATIC_DIRECTIONAL and PTZ EQUIRECTANGULAR_360) by branching on
	// CameraType.getOrientationMode() exactly the way CameraConfig itself already expects callers to.
	static CameraConfig buildVirtualCameraConfig(String name, VirtualImageSource imageSource, VirtualImagePlacement placement,
			double latitude, double longitude, double altitude, double azimuth, double roll, double focalLength, String lens,
			double fisheyeMaxAngleDegrees, String scenePath) {
		if (name == null || name.trim().isEmpty())
			throw new IllegalArgumentException("name must not be empty");
		if (imageSource == null)
			throw new IllegalArgumentException("virtual image source must be selected");
		if (placement == null)
			throw new IllegalArgumentException("image placement must be selected");
		if (scenePath == null || scenePath.trim().isEmpty())
			throw new IllegalArgumentException("scene image path must not be empty");

		CameraProjection projection = "fisheye".equalsIgnoreCase(lens)
				? new FisheyeProjection(focalLength, Math.toRadians(fisheyeMaxAngleDegrees))
				: new RectilinearProjection(focalLength);

		CameraType type = CameraType.virtual(imageSource);
		CameraConfig config = new CameraConfig(name.trim(), type, ObserverLocationSetting.explicit(latitude, longitude));
		config.setProjection(projection);
		config.setVirtualScenePath(scenePath.trim());
		config.setVirtualImagePlacement(placement);

		if (type.getOrientationMode() == OrientationMode.FIXED) {
			config.getCalibrationHistory()
					.append(new CalibrationEntry(0L, new Orientation(altitude, azimuth, roll), focalLength, latitude, longitude));
		} else {
			config.setCurrentOrientation(new Orientation(altitude, azimuth, roll));
			config.setCurrentLocation(ObserverLocationSetting.explicit(latitude, longitude));
		}

		return config;
	}
}
