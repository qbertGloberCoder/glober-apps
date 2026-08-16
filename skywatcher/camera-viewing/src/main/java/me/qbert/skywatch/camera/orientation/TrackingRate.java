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

// Equatorial-mount tracking-rate presets, mirroring a real mount's hand controller. Replaces the
// old prototype's single arbitrary raDrift default (15.0, undocumented as to which body it
// actually tracked). See CLAUDE.md's "Three tracking-rate modes" for the derivations.
//
// This is also what "positional accuracy" means for the equatorial mount, per the user's own
// clarification - getting these three constants right, not a broader accuracy audit. Task 9.2's
// golden-value check: values below are derived directly from the user's own supplied "standard
// rates" reference table (arcsec/hour, degrees/hour = arcsec/hour / 3600), superseding an earlier
// round's more astronomically precise but less conventional derivation - see the "Three tracking-
// rate modes" section of CLAUDE.md for the full reasoning, including why the old
// 15.0410686/23.934469h true-sidereal-day figure was replaced rather than kept alongside this.
public enum TrackingRate {
	// 54,000.0 arcsec/hour = exactly 15.0 deg/hour - the conventional "15 arcsec/second" figure
	// real mount hand controllers use for their default sidereal-tracking button. This is a
	// practical rounding of the true astronomical sidereal rate (360.985647deg/23.934469h =
	// 15.0410686 deg/hour, an earlier round's value) - good enough for a night's tracking, but NOT
	// exact: 24 hours at this rate is one full 360-degree rotation (15.0 x 24 = 360 exactly), while
	// a real sidereal day is actually ~3.93 minutes shorter than that. Coincidentally exactly
	// matches the old prototype's own unlabeled raDrift default of 15.0 too.
	SIDEREAL(15.0),
	// 53,997.7 arcsec/hour = 14.999361 deg/hour (99.996% of the sidereal rate above) - the sun's
	// motion relative to the stars.
	SOLAR(53997.7 / 3600.0),
	// 52,145.0 arcsec/hour = 14.484722 deg/hour (96.56% of the sidereal rate above) - the moon's
	// mean orbital motion. The Moon's real angular velocity varies over its elliptical orbit
	// (faster near perigee); real mounts offer this as a single mean rate rather than a
	// continuously-corrected one, and this implementation does the same.
	LUNAR(52145.0 / 3600.0);

	private final double degreesPerHour;

	TrackingRate(double degreesPerHour) {
		this.degreesPerHour = degreesPerHour;
	}

	public double getDegreesPerHour() {
		return degreesPerHour;
	}

	// A raw custom rate underneath the three presets, matching the old code's flexibility - not
	// one of these enum constants, just a plain double wherever a rate is needed.
}
