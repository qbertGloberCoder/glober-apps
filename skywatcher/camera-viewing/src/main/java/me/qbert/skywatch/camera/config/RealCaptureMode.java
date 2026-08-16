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

// Sub-type of a Real camera - see CLAUDE.md's "Camera setup: Real vs. Virtual".
public enum RealCaptureMode {
	// latest.jpg overwritten on a regular capture interval, mtime used to detect "now". Never
	// eligible for the equatorial mount / geolocation stabilizer - an open-ended live feed has no
	// fixed archive to anchor a transform's reference point against.
	LIVE_AND_RECORDED,
	// A complete, closed archive with no live component - e.g. an imported real-EQ-mount timelapse.
	// Eligible for the equatorial mount / geolocation stabilizer via the epoch-based mechanism
	// (see me.qbert.skywatch.camera.orientation's epoch classes), not the Virtual-camera
	// activation-based one.
	PRE_RECORDED_ONLY
}
