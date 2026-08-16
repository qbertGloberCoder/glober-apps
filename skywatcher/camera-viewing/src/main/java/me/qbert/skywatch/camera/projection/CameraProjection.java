package me.qbert.skywatch.camera.projection;

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

// A camera lens's angle-to-image mapping [the user's own design]. Projecting a target direction is
// split into two independent steps: me.qbert.skywatch.camera.orientation.BoresightAngles computes
// theta (angular distance from the camera's boresight) and phi (bearing around that ring) from a
// camera orientation and a target alt/az - a step that's the same regardless of lens type. This
// interface is the lens-specific second step: theta alone maps to a radius on a virtual
// 35mm-equivalent sensor. The actual x/y sensor-plane position is then
// radius*cos(phi)/radius*sin(phi) - computing that composition is left to whatever consumes this
// interface (e.g. converting to screen pixels also needs a canvas-scale factor this interface
// deliberately doesn't know about).
//
// Real cameras must be modeled to match their actual lens; this interface exists specifically so a
// rectilinear zoom lens, a fisheye, and (via a suitably large max angle) a 180- or 360-degree
// fisheye can all be represented behind the same abstraction, with a Real camera's PathTemplate/
// RealImageSource-level config picking one concrete implementation. Virtual PTZ cameras may use any
// implementation, freely, including changing it - there's no real hardware constraining them. Fixed
// cameras (real or virtual) using a declared projection whose theta/phi geometry doesn't actually
// match how their static image was captured is a discrepancy left to the user's own discretion (see
// CLAUDE.md's "Camera setup") - this interface only computes where the *overlay* should draw, it
// never warps or generates the underlying image.
public interface CameraProjection {
	// The reference "35mm-equivalent" full-frame sensor's long-edge width, in millimeters - the
	// common frame of reference the user's own zoom-range convention (e.g. "18-70mm", "35-200mm")
	// implies.
	double SENSOR_WIDTH_MILLIMETERS = 36.0;

	// The radius, in millimeters on the virtual reference sensor, at which a point theta radians
	// off the boresight lands.
	double sensorRadiusMillimeters(double thetaRadians);

	// The inverse - the off-boresight angle for a given sensor radius, needed to map a screen
	// position (e.g. a plate-solve click) back to a sky direction.
	double angleForSensorRadiusMillimeters(double radiusMillimeters);

	// The largest theta this projection can represent at all - e.g. a rectilinear lens must cap
	// short of 90 degrees (its true asymptote, where the mapped radius diverges to infinity); a
	// 180-degree fisheye caps at exactly 90 degrees; a 360-degree one at 180 degrees.
	double getMaxAngleRadians();

	// A copy of this projection at a different 35mm-equivalent focal length, otherwise identical
	// (a FisheyeProjection's own maxAngleRadians, for instance, is preserved) - what actually lets
	// "zoom" mean anything: ZoomRange's own class comment already described a camera's current zoom
	// setting as "whatever supplies focalLengthMillimeters to a RectilinearProjection/
	// FisheyeProjection instance for a given frame," but nothing ever called this until it was added
	// - CameraConfig.getProjection() alone is a fixed lens definition, not a per-frame current zoom.
	CameraProjection withFocalLength(double newFocalLengthMillimeters);
	
}
