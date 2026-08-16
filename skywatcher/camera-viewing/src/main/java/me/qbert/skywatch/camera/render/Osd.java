package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.watch.MoonPhase;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;
import me.qbert.ui.renderers.AbstractFractionRenderer;
import me.qbert.ui.renderers.TextRenderer;

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

// Layer 5's two spec §8 tiers.
//
// The "always-on summary" tier - date/time with timezone offset, timezone name, camera lat/lon,
// the flat-earth day/night illustration, barrel roll, altitude, azimuth.
//
// The "expandable per-watched-object detail" tier - built on top of watch.WatchedObject's
// resolveRaDec/resolveCelestialSphereLocation/resolveAltAz/resolveSubObjectLocation accessors, plus
// astro.impl.SunObject's newly-exposed getSolarNoonFraction()/getApparentEclipticLongitudeDegrees()
// and astro.impl.MoonObject's getEclipticLongitudeDegrees(), and watch.MoonPhase (the Sun-Moon
// elongation computation those two ecliptic-longitude getters exist to feed). Covers: Local RA/
// current Dec, Object RA/Dec, object altitude/azimuth, sub-object (overhead) lat/lon, nominal local
// solar noon, timezone noon meridian, moon orbit position/age/phase. **Not built, still genuinely
// underspecified**: "object slope" (no established definition found anywhere in this codebase or
// old_draw_project), "average local RA" (average over what window is never specified),
// "Sun/Moon/Star RA drift rates" (unit/sampling window unspecified). See watch.MoonPhase's own class
// comment for why the moon-cycle fields reuse sw-base's existing Sun/Moon ephemeris (via those two
// ecliptic-longitude getters) rather than porting old_draw_project's standalone "moontool.c" port,
// and for the pre-existing MoonObject.getCelestialSphereLocation() labeling inconsistency (its
// "rightAscension" field is actually ecliptic longitude, not true equatorial RA) discovered along
// the way - left as-is, not fixed, per that class's own reasoning.
//
// The timezone used for display is caller-supplied (a ZoneId), not derived from lat/lon - spec
// §7.1 ties "which timezone is used for displaying/choosing dates and times" to the observer
// location setting's own "explicit lat/lon, or 'use my locale'" choice, which already resolves to
// a concrete ZoneId well before this class is involved (see config.ObserverLocationSetting/
// TimezoneSetting) - deriving a timezone from bare coordinates would need a geo-timezone lookup
// database, well out of scope here.
public final class Osd {
	private Osd() {
	}

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx", Locale.ROOT);
	private static final double LEFT_MARGIN_PIXELS = 8.0;
	private static final double TOP_MARGIN_PIXELS = 16.0;
	private static final double LINE_HEIGHT_PIXELS = 14.0;

	// So a caller showing both tiers at once (FrameCompositor) can stack the detail tier directly
	// below the summary tier without hardcoding the summary's line count itself.
	public static final int SUMMARY_LINE_COUNT = 7;
	public static final double DEFAULT_DETAIL_TIER_TOP_Y_PIXELS = TOP_MARGIN_PIXELS + SUMMARY_LINE_COUNT * LINE_HEIGHT_PIXELS;

	public static void draw(Graphics2D g2d, long epochMillis, ZoneId timezone, double latitude, double longitude,
			Orientation orientation, Color textColor, int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		List<String> lines = summaryLines(epochMillis, timezone, latitude, longitude, orientation);

		double y = TOP_MARGIN_PIXELS;
		for (String line : lines) {
			TextRenderer renderer = new TextRenderer(AbstractFractionRenderer.ABSOLUTE_COORDINATES);
			renderer.setText(line);
			renderer.setX(LEFT_MARGIN_PIXELS);
			renderer.setY(y);
			renderer.setRenderDimensions(0, 0, canvasWidthPixels, canvasHeightPixels);

			g2d.setColor(textColor);
			renderer.renderComponent(g2d);

			y += LINE_HEIGHT_PIXELS;
		}
	}

	// Package-visible for direct content testing without needing to render/scan pixels.
	static List<String> summaryLines(long epochMillis, ZoneId timezone, double latitude, double longitude,
			Orientation orientation) {
		if (timezone == null)
			throw new IllegalArgumentException("timezone must not be null");
		if (orientation == null)
			throw new IllegalArgumentException("orientation must not be null");

		ZonedDateTime zoned = Instant.ofEpochMilli(epochMillis).atZone(timezone);

		List<String> lines = new ArrayList<String>();
		lines.add(TIMESTAMP_FORMAT.format(zoned));
		lines.add(timezone.getId());
		lines.add(String.format(Locale.ROOT, "Lat: %.4f, Lon: %.4f", latitude, longitude));
		// Always "Day", never computed - the whole educational point [spec §8] is that the
		// flat-earth model has no mechanism to explain night at all, so it always claims day.
		lines.add("Flat-earth sun visibility: Day");
		lines.add(String.format(Locale.ROOT, "Roll: %.1f°", orientation.getBarrelRoll()));
		lines.add(String.format(Locale.ROOT, "Altitude: %.1f°", orientation.getAltitude()));
		lines.add(String.format(Locale.ROOT, "Azimuth: %.1f°", orientation.getAzimuth()));

		return lines;
	}

	// The expandable per-watched-object detail tier [spec §8] - see the class comment for exactly
	// which fields this covers and which are still deferred. topYPixels is caller-supplied rather
	// than a fixed constant so this can be positioned independently of (or stacked below, via
	// DEFAULT_DETAIL_TIER_TOP_Y_PIXELS) the always-on summary tier.
	public static void drawWatchedObjectDetail(Graphics2D g2d, WatchedObject watchedObject, ObservationTime time,
			ObserverLocation location, ZoneId timezone, Color textColor, int canvasWidthPixels, int canvasHeightPixels,
			double topYPixels) throws Exception {
		List<String> lines = watchedObjectDetailLines(watchedObject, time, location, timezone);

		double y = topYPixels;
		for (String line : lines) {
			TextRenderer renderer = new TextRenderer(AbstractFractionRenderer.ABSOLUTE_COORDINATES);
			renderer.setText(line);
			renderer.setX(LEFT_MARGIN_PIXELS);
			renderer.setY(y);
			renderer.setRenderDimensions(0, 0, canvasWidthPixels, canvasHeightPixels);

			g2d.setColor(textColor);
			renderer.renderComponent(g2d);

			y += LINE_HEIGHT_PIXELS;
		}
	}

	// Package-visible for direct content testing without needing to render/scan pixels.
	static List<String> watchedObjectDetailLines(WatchedObject watchedObject, ObservationTime time,
			ObserverLocation location, ZoneId timezone) throws Exception {
		if (watchedObject == null)
			throw new IllegalArgumentException("watchedObject must not be null");
		if (time == null)
			throw new IllegalArgumentException("time must not be null");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");
		if (timezone == null)
			throw new IllegalArgumentException("timezone must not be null");

		ObjectDirectionRaDec localRaDec = watchedObject.resolveRaDec(time, location);
		ObjectDirectionRaDec celestialRaDec = watchedObject.resolveCelestialSphereLocation(time, location);
		ObjectDirectionAltAz altAz = watchedObject.resolveAltAz(time, location);
		GeoLocation subObject = watchedObject.resolveSubObjectLocation(time, location);

		long epochMillis = time.getTime().getTimeInMillis();
		double offsetHours = timezone.getRules().getOffset(Instant.ofEpochMilli(epochMillis)).getTotalSeconds() / 3600.0;
		double timezoneNoonMeridianDegrees = 15.0 * offsetHours;

		List<String> lines = new ArrayList<String>();
		lines.add("Watching: " + watchedObject.getDisplayName());
		lines.add(String.format(Locale.ROOT, "Local RA: %.2f°, Dec: %.2f°", localRaDec.getRightAscension(),
				localRaDec.getDeclination()));
		lines.add(String.format(Locale.ROOT, "Object RA: %.2f°, Dec: %.2f°", celestialRaDec.getRightAscension(),
				celestialRaDec.getDeclination()));
		lines.add(String.format(Locale.ROOT, "Altitude: %.2f°, Azimuth: %.2f°", altAz.getAltitude(), altAz.getAzimuth()));
		lines.add(String.format(Locale.ROOT, "Sub-object: Lat %.4f°, Lon %.4f°", subObject.getLatitude(),
				subObject.getLongitude()));
		lines.add("Nominal local solar noon: " + formatSolarNoon(time, location, timezone));
		lines.add(String.format(Locale.ROOT, "Timezone noon meridian: %.1f°", timezoneNoonMeridianDegrees));

		// Independent of watchedObject - see MoonPhase's own class comment for why these are always
		// about the Moon specifically, regardless of what's currently being watched (the same stance
		// already taken for "nominal local solar noon" above being always about the Sun).
		MoonPhase moonPhase = MoonPhase.resolve(time, location);
		lines.add(String.format(Locale.ROOT, "Moon orbit position: %.1f° (%.1f%%)", moonPhase.getOrbitPositionDegrees(),
				moonPhase.getOrbitPositionPercent()));
		lines.add(String.format(Locale.ROOT, "Moon age: %.1f days", moonPhase.getAgeDays()));
		lines.add(String.format(Locale.ROOT, "Moon phase: %.1f%% (%s)", moonPhase.getIlluminatedFractionPercent(),
				moonPhase.isWaxing() ? "waxing" : "waning"));

		return lines;
	}

	// SunObject.getSolarNoonFraction() (astro.impl.SunObject - a fraction of the day, 0.0-1.0)
	// depends on which timezone its own ObservationTime was initialized with (the NOAA formula
	// explicitly adds that timezone's offset), so a FRESH SunObject/ObservationTime pair is built
	// here against the OSD's own display timezone - reusing the frame's own (possibly differently
	// zoned) ObservationTime would silently compute solar noon in the wrong zone's terms.
	private static String formatSolarNoon(ObservationTime frameTime, ObserverLocation location, ZoneId timezone)
			throws Exception {
		ObservationTime localTime = new ObservationTime();
		localTime.initTime(TimeZone.getTimeZone(timezone));
		localTime.setUnixTime(frameTime.getTime().getTimeInMillis());

		SunObject sun = (SunObject) SunObject.create().setObserverLocation(location).setObserverTime(localTime).build();
		sun.recompute(); // build() does not recompute - see CelestialObjectsLayer's class comment gotcha

		double fraction = ((sun.getSolarNoonFraction() % 1.0) + 1.0) % 1.0;
		int totalSeconds = ((int) Math.round(fraction * 86400.0)) % 86400;
		int hours = totalSeconds / 3600;
		int minutes = (totalSeconds % 3600) / 60;
		int seconds = totalSeconds % 60;
		return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
	}
}
