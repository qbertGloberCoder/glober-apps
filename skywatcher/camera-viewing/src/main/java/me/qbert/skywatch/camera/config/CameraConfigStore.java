package me.qbert.skywatch.camera.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import me.qbert.skywatch.camera.orientation.MountMode;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.projection.ZoomRange;
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

// Task 0.4's config-file loader - no JSON/YAML framework dependency, per explicit user policy (see
// CLAUDE.md's "Config storage": adding one is bundled with a separate, deliberate project-wide
// Java 21 decision, not something to trigger incidentally here). Uses plain java.util.Properties
// syntax (key=value lines, # comments, standard escaping) - array-shaped structures (right now,
// only the calibration history) use index-prefixed keys (calibration.0.altitude=...,
// calibration.1.altitude=...), one of the two candidates task 0.4 itself proposed. Properties.load()
// handles all the parsing/escaping for free; OrderedProperties (below) only exists so
// Properties.store() writes keys in the order this class sets them rather than an unspecified
// hash-table order - the file is meant to be hand-editable (CLAUDE.md's "Image sources"), and a
// scrambled key order would work against that.
//
// Every optional/type-gated field CameraConfig exposes (realImageSource, virtualScenePath,
// projection, zoomRange - all "null until explicitly configured, not an error") round-trips as
// present-or-absent, matching CameraConfig's own stance rather than forcing every camera profile to
// specify everything.
public final class CameraConfigStore {
	private CameraConfigStore() {
	}

	// ---- public API ----

	public static void save(CameraConfig config, File file) throws IOException {
		OutputStream out = new FileOutputStream(file);
		try {
			save(config, new OutputStreamWriter(out, StandardCharsets.UTF_8));
		} finally {
			out.close();
		}
	}

	public static void save(CameraConfig config, Writer writer) throws IOException {
		if (config == null)
			throw new IllegalArgumentException("config must not be null");

		OrderedProperties properties = toProperties(config);
		properties.store(writer, "camera-viewing camera config - see CLAUDE.md's \"Config storage\"");
	}

	public static CameraConfig load(File file) throws IOException {
		InputStream in = new FileInputStream(file);
		try {
			return load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} finally {
			in.close();
		}
	}

	public static CameraConfig load(Reader reader) throws IOException {
		Properties properties = new Properties();
		properties.load(reader);
		return fromProperties(properties);
	}

	// ---- save ----

	private static OrderedProperties toProperties(CameraConfig config) {
		OrderedProperties p = new OrderedProperties();

		p.setProperty("name", config.getName());

		CameraType type = config.getType();
		p.setProperty("type.kind", type.getKind().name());
		if (type.getKind() == CameraType.Kind.REAL)
			p.setProperty("type.realCaptureMode", type.getRealCaptureMode().name());
		else
			p.setProperty("type.virtualImageSource", type.getVirtualImageSource().name());

		p.setProperty("timeBiasMillis", Long.toString(config.getTimeBiasMillis()));

		writeObserverLocationSetting(p, "observerLocation", config.getObserverLocation());

		if (type.getOrientationMode() == OrientationMode.FIXED) {
			List<CalibrationEntry> entries = config.getCalibrationHistory().getEntries();
			p.setProperty("calibration.count", Integer.toString(entries.size()));
			for (int i = 0; i < entries.size(); i++)
				writeCalibrationEntry(p, "calibration." + i, entries.get(i));
		} else {
			writeOrientation(p, "currentOrientation", config.getCurrentOrientation());
			writeObserverLocationSetting(p, "currentLocation", config.getCurrentLocation());
		}

		if (type.isEquatorialMountEligible())
			p.setProperty("mountControl.mode", config.getMountControl().getMode().name());

		if (type.getKind() == CameraType.Kind.REAL) {
			RealImageSource source = config.getRealImageSource();
			if (source != null)
				writeRealImageSource(p, "realImageSource", source);
		} else {
			String scenePath = config.getVirtualScenePath();
			if (scenePath != null)
				p.setProperty("virtualScenePath", scenePath);
			p.setProperty("virtualImagePlacement", config.getVirtualImagePlacement().name());
		}

		CameraProjection projection = config.getProjection();
		if (projection != null)
			writeProjection(p, "projection", projection);

		ZoomRange zoomRange = config.getZoomRange();
		if (zoomRange != null) {
			p.setProperty("zoomRange.minFocalLengthMillimeters", Double.toString(zoomRange.getMinFocalLengthMillimeters()));
			p.setProperty("zoomRange.maxFocalLengthMillimeters", Double.toString(zoomRange.getMaxFocalLengthMillimeters()));
		}

		return p;
	}

	private static void writeObserverLocationSetting(OrderedProperties p, String prefix, ObserverLocationSetting setting) {
		p.setProperty(prefix + ".useSystemLocale", Boolean.toString(setting.isUseSystemLocale()));
		if (!setting.isUseSystemLocale()) {
			p.setProperty(prefix + ".latitude", Double.toString(setting.getLatitude()));
			p.setProperty(prefix + ".longitude", Double.toString(setting.getLongitude()));
		}
	}

	private static void writeOrientation(OrderedProperties p, String prefix, Orientation orientation) {
		p.setProperty(prefix + ".altitude", Double.toString(orientation.getAltitude()));
		p.setProperty(prefix + ".azimuth", Double.toString(orientation.getAzimuth()));
		p.setProperty(prefix + ".barrelRoll", Double.toString(orientation.getBarrelRoll()));
	}

	private static void writeCalibrationEntry(OrderedProperties p, String prefix, CalibrationEntry entry) {
		p.setProperty(prefix + ".effectiveFromEpochMillis", Long.toString(entry.getEffectiveFromEpochMillis()));
		writeOrientation(p, prefix, entry.getOrientation());
		p.setProperty(prefix + ".zoom", Double.toString(entry.getZoom()));
		p.setProperty(prefix + ".latitude", Double.toString(entry.getLatitude()));
		p.setProperty(prefix + ".longitude", Double.toString(entry.getLongitude()));
	}

	private static void writeRealImageSource(OrderedProperties p, String prefix, RealImageSource source) {
		p.setProperty(prefix + ".hasLatestSource", Boolean.toString(source.hasLatestSource()));
		if (source.hasLatestSource())
			p.setProperty(prefix + ".latestPath", source.getLatestPath());
		p.setProperty(prefix + ".archiveTemplate", source.getArchiveTemplate());
		writeTimezoneSetting(p, prefix + ".timezone", source.getTimezone());
		p.setProperty(prefix + ".dstAmbiguousPolicy", source.getDstAmbiguousPolicy().name());
	}

	private static void writeTimezoneSetting(OrderedProperties p, String prefix, TimezoneSetting timezone) {
		p.setProperty(prefix + ".useSystemDefault", Boolean.toString(timezone.isUseSystemDefault()));
		if (!timezone.isUseSystemDefault())
			p.setProperty(prefix + ".zoneId", timezone.getExplicitZone().getId());
	}

	private static void writeProjection(OrderedProperties p, String prefix, CameraProjection projection) {
		if (projection instanceof RectilinearProjection) {
			RectilinearProjection rectilinear = (RectilinearProjection) projection;
			p.setProperty(prefix + ".kind", "RECTILINEAR");
			p.setProperty(prefix + ".focalLengthMillimeters", Double.toString(rectilinear.getFocalLengthMillimeters()));
		} else if (projection instanceof FisheyeProjection) {
			FisheyeProjection fisheye = (FisheyeProjection) projection;
			p.setProperty(prefix + ".kind", "FISHEYE");
			p.setProperty(prefix + ".focalLengthMillimeters", Double.toString(fisheye.getFocalLengthMillimeters()));
			p.setProperty(prefix + ".maxAngleRadians", Double.toString(fisheye.getMaxAngleRadians()));
		} else {
			throw new IllegalArgumentException("unrecognized CameraProjection implementation: " + projection.getClass());
		}

		// Every CameraProjection this codebase can construct extends AbstractCameraProjection (the
		// instanceof chain above already exhaustively enumerates the same two concrete types) - safe
		// to cast unconditionally here. Written only when NOT the identity default (a==0 && b==0 &&
		// c==0), matching zoomRange's own "optional, all-or-nothing" convention - keeps the common
		// no-distortion camera's file exactly as short as it was before this round.
		AbstractCameraProjection abstractProjection = (AbstractCameraProjection) projection;
		if (abstractProjection.getDistortionCoefficientA() != 0.0 || abstractProjection.getDistortionCoefficientB() != 0.0
				|| abstractProjection.getDistortionCoefficientC() != 0.0) {
			p.setProperty(prefix + ".distortionA", Double.toString(abstractProjection.getDistortionCoefficientA()));
			p.setProperty(prefix + ".distortionB", Double.toString(abstractProjection.getDistortionCoefficientB()));
			p.setProperty(prefix + ".distortionC", Double.toString(abstractProjection.getDistortionCoefficientC()));
			p.setProperty(prefix + ".distortionD", Double.toString(abstractProjection.getDistortionCoefficientD()));
		}
	}

	// ---- load ----

	private static CameraConfig fromProperties(Properties p) throws CameraConfigFormatException {
		String name = require(p, "name");
		CameraType type = readCameraType(p);
		ObserverLocationSetting observerLocation = readObserverLocationSetting(p, "observerLocation");

		CameraConfig config = new CameraConfig(name, type, observerLocation);

		// worldModel used to be a required per-camera key here - now lives on GlobalSettings (see
		// its own comment). An OLD file that still has a "worldModel=..." line is simply never
		// looked at (Properties.load(...) already tolerates unknown keys), not an error - no
		// migration step needed for existing saved camera profiles.
		config.setTimeBiasMillis(readLong(p, "timeBiasMillis"));

		if (type.getOrientationMode() == OrientationMode.FIXED) {
			int count = readInt(p, "calibration.count");
			for (int i = 0; i < count; i++)
				config.getCalibrationHistory().append(readCalibrationEntry(p, "calibration." + i));
		} else {
			config.setCurrentOrientation(readOrientation(p, "currentOrientation"));
			config.setCurrentLocation(readObserverLocationSetting(p, "currentLocation"));
		}

		// Optional on read (unlike on write, which always sets it) - mountControl.mode was added to
		// this format after some cameras had already been saved without it (the same "an older file
		// predates a newer key" situation worldModel's own comment above describes), so an old
		// EQ-eligible camera's file that lacks this key must still load, keeping MountControl's own
		// NONE default rather than treating the absence as a format error.
		if (type.isEquatorialMountEligible() && p.getProperty("mountControl.mode") != null)
			config.getMountControl().setMode(readEnum(p, "mountControl.mode", MountMode.class));

		if (type.getKind() == CameraType.Kind.REAL) {
			if (p.getProperty("realImageSource.archiveTemplate") != null)
				config.setRealImageSource(readRealImageSource(p, "realImageSource"));
		} else {
			String scenePath = p.getProperty("virtualScenePath");
			if (scenePath != null)
				config.setVirtualScenePath(scenePath);
			// Optional on read (unlike on write, which always sets it) - a hand-edited file that
			// omits it just keeps CameraConfig's own default (LAYER_1), rather than treating the
			// absence as a format error.
			if (p.getProperty("virtualImagePlacement") != null)
				config.setVirtualImagePlacement(readEnum(p, "virtualImagePlacement", VirtualImagePlacement.class));
		}

		if (p.getProperty("projection.kind") != null)
			config.setProjection(readProjection(p, "projection"));

		if (p.getProperty("zoomRange.minFocalLengthMillimeters") != null) {
			config.setZoomRange(new ZoomRange(readDouble(p, "zoomRange.minFocalLengthMillimeters"),
					readDouble(p, "zoomRange.maxFocalLengthMillimeters")));
		}

		return config;
	}

	private static CameraType readCameraType(Properties p) throws CameraConfigFormatException {
		CameraType.Kind kind = readEnum(p, "type.kind", CameraType.Kind.class);
		if (kind == CameraType.Kind.REAL)
			return CameraType.real(readEnum(p, "type.realCaptureMode", RealCaptureMode.class));
		return CameraType.virtual(readEnum(p, "type.virtualImageSource", VirtualImageSource.class));
	}

	private static ObserverLocationSetting readObserverLocationSetting(Properties p, String prefix)
			throws CameraConfigFormatException {
		boolean useSystemLocale = readBoolean(p, prefix + ".useSystemLocale");
		if (useSystemLocale)
			return ObserverLocationSetting.useSystemLocale();
		return ObserverLocationSetting.explicit(readDouble(p, prefix + ".latitude"), readDouble(p, prefix + ".longitude"));
	}

	private static Orientation readOrientation(Properties p, String prefix) throws CameraConfigFormatException {
		return new Orientation(readDouble(p, prefix + ".altitude"), readDouble(p, prefix + ".azimuth"),
				readDouble(p, prefix + ".barrelRoll"));
	}

	private static CalibrationEntry readCalibrationEntry(Properties p, String prefix) throws CameraConfigFormatException {
		return new CalibrationEntry(readLong(p, prefix + ".effectiveFromEpochMillis"), readOrientation(p, prefix),
				readDouble(p, prefix + ".zoom"), readDouble(p, prefix + ".latitude"), readDouble(p, prefix + ".longitude"));
	}

	private static RealImageSource readRealImageSource(Properties p, String prefix) throws CameraConfigFormatException {
		boolean hasLatestSource = readBoolean(p, prefix + ".hasLatestSource");
		String archiveTemplate = require(p, prefix + ".archiveTemplate");
		TimezoneSetting timezone = readTimezoneSetting(p, prefix + ".timezone");
		DstAmbiguousPolicy dstAmbiguousPolicy = readEnum(p, prefix + ".dstAmbiguousPolicy", DstAmbiguousPolicy.class);

		if (hasLatestSource)
			return RealImageSource.liveAndRecorded(require(p, prefix + ".latestPath"), archiveTemplate, timezone,
					dstAmbiguousPolicy);
		return RealImageSource.preRecordedOnly(archiveTemplate, timezone, dstAmbiguousPolicy);
	}

	private static TimezoneSetting readTimezoneSetting(Properties p, String prefix) throws CameraConfigFormatException {
		boolean useSystemDefault = readBoolean(p, prefix + ".useSystemDefault");
		if (useSystemDefault)
			return TimezoneSetting.useSystemDefault();
		try {
			return TimezoneSetting.explicit(ZoneId.of(require(p, prefix + ".zoneId")));
		} catch (DateTimeException e) {
			throw new CameraConfigFormatException("invalid " + prefix + ".zoneId: " + e.getMessage(), e);
		}
	}

	private static CameraProjection readProjection(Properties p, String prefix) throws CameraConfigFormatException {
		String kind = require(p, prefix + ".kind");
		double focalLength = readDouble(p, prefix + ".focalLengthMillimeters");
		AbstractCameraProjection projection;
		if ("RECTILINEAR".equals(kind))
			projection = new RectilinearProjection(focalLength);
		else if ("FISHEYE".equals(kind))
			projection = new FisheyeProjection(focalLength, readDouble(p, prefix + ".maxAngleRadians"));
		else
			throw new CameraConfigFormatException("unknown " + prefix + ".kind: \"" + kind + "\" (expected RECTILINEAR or FISHEYE)");

		// Absent on a file predating this feature, or a camera with no distortion configured - the
		// identity default (set by AbstractCameraProjection's own constructor) is left as-is.
		if (p.getProperty(prefix + ".distortionA") != null) {
			projection.setDistortionCoefficients(readDouble(p, prefix + ".distortionA"),
					readDouble(p, prefix + ".distortionB"), readDouble(p, prefix + ".distortionC"),
					readDouble(p, prefix + ".distortionD"));
		}
		return projection;
	}

	// ---- small read helpers - all raise CameraConfigFormatException naming the offending key,
	// rather than a raw NumberFormatException/IllegalArgumentException, since these files are meant
	// to be hand-edited and a vague error is much harder to fix by hand.

	private static String require(Properties p, String key) throws CameraConfigFormatException {
		String value = p.getProperty(key);
		if (value == null)
			throw new CameraConfigFormatException("missing required key: " + key);
		return value;
	}

	private static <T extends Enum<T>> T readEnum(Properties p, String key, Class<T> type) throws CameraConfigFormatException {
		String value = require(p, key);
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw new CameraConfigFormatException("invalid value for " + key + ": \"" + value + "\"", e);
		}
	}

	private static double readDouble(Properties p, String key) throws CameraConfigFormatException {
		String value = require(p, key);
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new CameraConfigFormatException("invalid number for " + key + ": \"" + value + "\"", e);
		}
	}

	private static long readLong(Properties p, String key) throws CameraConfigFormatException {
		String value = require(p, key);
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new CameraConfigFormatException("invalid integer for " + key + ": \"" + value + "\"", e);
		}
	}

	private static int readInt(Properties p, String key) throws CameraConfigFormatException {
		return (int) readLong(p, key);
	}

	private static boolean readBoolean(Properties p, String key) throws CameraConfigFormatException {
		String value = require(p, key);
		if ("true".equalsIgnoreCase(value))
			return true;
		if ("false".equalsIgnoreCase(value))
			return false;
		throw new CameraConfigFormatException("invalid boolean for " + key + ": \"" + value + "\" (expected true or false)");
	}

	// ---- ordered Properties: Properties.store(...) otherwise writes keys in an unspecified
	// hash-table order, not insertion order - this file is meant to be hand-edited/hand-read (see
	// CLAUDE.md's "Image sources"), and a scrambled key order would work against that. A well-known,
	// minimal technique: override the views Properties.store()/keys() actually iterate.

	private static final class OrderedProperties extends Properties {
		private static final long serialVersionUID = 1L;
		private final LinkedHashSet<Object> keyOrder = new LinkedHashSet<Object>();

		@Override
		public synchronized Object put(Object key, Object value) {
			keyOrder.add(key);
			return super.put(key, value);
		}

		@Override
		public Set<Object> keySet() {
			return keyOrder;
		}

		@Override
		public synchronized Enumeration<Object> keys() {
			return Collections.enumeration(keyOrder);
		}

		@Override
		public Set<Map.Entry<Object, Object>> entrySet() {
			LinkedHashSet<Map.Entry<Object, Object>> ordered = new LinkedHashSet<Map.Entry<Object, Object>>();
			for (Object key : keyOrder)
				ordered.add(new AbstractMap.SimpleEntry<Object, Object>(key, get(key)));
			return ordered;
		}
	}
}
