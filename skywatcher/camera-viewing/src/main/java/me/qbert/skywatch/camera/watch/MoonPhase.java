package me.qbert.skywatch.camera.watch;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SunObject;

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

// Spec §8's three moon-cycle OSD detail fields - "Moon orbit position (degrees and percentage)",
// "Moon age (days of synodic month)", "Moon phase (percentage, waxing/waning)". Deferred in the
// previous OSD-detail-tier round because none of them existed anywhere in this module or sw-base
// yet; see CLAUDE.md's "OSD expandable per-watched-object detail tier" section for that round's
// research (a well-known reference implementation exists in old_draw_project's
// ch.unige.obs.skops.astro.MoonTool.java - a ~1000-line port of the public-domain "moontool.c" - but
// it re-derives its own low-precision Sun/Moon ephemeris from scratch rather than reusing sw-base's
// already-more-precise SunObject/MoonObject, so it was deliberately NOT ported here).
//
// This class instead computes all three fields from a single well-established quantity: the
// elongation between the Sun and Moon (their ecliptic-longitude separation, 0-360 degrees,
// increasing from 0 at New Moon through 180 at Full Moon back to 360=0 at the next New Moon). Both
// bodies' apparent geocentric ecliptic longitude of date are already computed by sw-base's
// SunObject/MoonObject.recompute() - see SunObject.getApparentEclipticLongitudeDegrees() and
// MoonObject.getEclipticLongitudeDegrees(), both added alongside this class specifically so this
// computation could reuse sw-base's existing, already-verified-elsewhere ephemeris rather than a
// second independent one. This is the same simplified approach real planetarium software commonly
// uses for a phase readout (not full-precision phase-angle-from-distances, which would need
// Sun-Earth-Moon distances this module doesn't track) - accurate to a fraction of a percent, well
// within what a text readout needs.
//
// Independent of any watch.WatchedObject selection - like Osd's "nominal local solar noon" field,
// this is generically useful contextual sky data, not conditioned on whether the Moon happens to be
// the object currently being watched.
public final class MoonPhase {

	// The mean synodic month (New Moon to New Moon), in days - the standard astronomical constant.
	public static final double SYNODIC_MONTH_DAYS = 29.530588853;

	private final double orbitPositionDegrees;
	private final double illuminatedFractionPercent;
	private final double ageDays;
	private final boolean waxing;

	private MoonPhase(double orbitPositionDegrees, double illuminatedFractionPercent, double ageDays, boolean waxing) {
		this.orbitPositionDegrees = orbitPositionDegrees;
		this.illuminatedFractionPercent = illuminatedFractionPercent;
		this.ageDays = ageDays;
		this.waxing = waxing;
	}

	public static MoonPhase resolve(ObservationTime time, ObserverLocation location) throws Exception {
		if (time == null)
			throw new IllegalArgumentException("time must not be null");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");

		SunObject sun = (SunObject) SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute(); // build() does not recompute - see CelestialObjectsLayer's class comment gotcha
		MoonObject moon = (MoonObject) MoonObject.create().setObserverLocation(location).setObserverTime(time).build();
		moon.recompute();

		double elongation = normalizeDegrees(moon.getEclipticLongitudeDegrees() - sun.getApparentEclipticLongitudeDegrees());
		double illuminatedFractionPercent = (1.0 - Math.cos(Math.toRadians(elongation))) / 2.0 * 100.0;
		double ageDays = elongation / 360.0 * SYNODIC_MONTH_DAYS;
		boolean waxing = elongation < 180.0;

		return new MoonPhase(elongation, illuminatedFractionPercent, ageDays, waxing);
	}

	// The moon's position in its current orbit around the elongation cycle, 0-360 degrees (0 = New
	// Moon, 180 = Full Moon).
	public double getOrbitPositionDegrees() {
		return orbitPositionDegrees;
	}

	public double getOrbitPositionPercent() {
		return orbitPositionDegrees / 360.0 * 100.0;
	}

	// 0% at New Moon, 100% at Full Moon.
	public double getIlluminatedFractionPercent() {
		return illuminatedFractionPercent;
	}

	// Days since the most recent New Moon (0 at New Moon, ~SYNODIC_MONTH_DAYS/2 at Full Moon).
	public double getAgeDays() {
		return ageDays;
	}

	// True from New Moon to Full Moon (elongation 0-180), false from Full Moon back to New Moon
	// (elongation 180-360).
	public boolean isWaxing() {
		return waxing;
	}

	private static double normalizeDegrees(double degrees) {
		double result = degrees % 360.0;
		if (result < 0.0)
			result += 360.0;
		return result;
	}
}
