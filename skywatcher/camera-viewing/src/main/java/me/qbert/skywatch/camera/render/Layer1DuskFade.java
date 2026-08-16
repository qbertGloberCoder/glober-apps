package me.qbert.skywatch.camera.render;

import java.awt.image.BufferedImage;

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

// Fades Layer 1 (this camera's own image, in either the LAYER_1 or LAYER_4 placement) toward black
// as the sun sets, so that by night the image is "effectively" not drawn any more - a direct user
// request, describing a technique from the old prototype: "I painted that same image and for
// dusk -> night transition, I did something like a box fill with alpha channel of black."
//
// Investigated the old prototype (org.bluerock.ui.components.VirtualCamera.getView(...), driven from
// StarFieldController's main draw loop, ~line 6369) via a grep-only pass per this module's own
// CLAUDE.md (never a full read of the two forbidden god classes). The old formula darkens the
// image's own RGB by a "pct" factor while leaving alpha untouched - mathematically identical to
// alpha-compositing solid black over the image, exactly the technique described above - but it uses
// a single LINEAR ramp across the whole 0 to -18 degree span (the same ramp already ported into
// SkyColor for the sky fill), not a curve that treats civil/nautical/astronomical twilight as
// separate bands. Presented that finding directly; the user chose a NEW, non-linear "banded" curve
// instead of a literal port of the old formula - mostly bright through civil twilight, most of the
// darkening happens in nautical twilight, virtually black entering astronomical twilight. This class
// implements that approved curve, not the old prototype's literal one.
public final class Layer1DuskFade {
	private Layer1DuskFade() {
	}

	private static final double CIVIL_END_DEGREES = -6.0;
	private static final double NAUTICAL_END_DEGREES = -12.0;
	private static final double ASTRONOMICAL_END_DEGREES = -18.0;

	private static final double CIVIL_END_BRIGHTNESS = 0.85;
	private static final double NAUTICAL_END_BRIGHTNESS = 0.05;

	// 1.0 = full brightness (daytime, no darkening), 0.0 = fully black. Piecewise-linear across three
	// segments, approved by the user via a concrete numeric preview:
	//   [0, -6]   -> [1.00, 0.85]  (civil twilight - mostly stays bright)
	//   [-6, -12] -> [0.85, 0.05]  (nautical twilight - most of the darkening happens here)
	//   [-12, -18]-> [0.05, 0.00]  (astronomical twilight - already virtually black, small residual fade)
	public static double brightnessFraction(double sunAltitudeDegrees) {
		if (sunAltitudeDegrees >= 0.0)
			return 1.0;
		if (sunAltitudeDegrees <= ASTRONOMICAL_END_DEGREES)
			return 0.0;

		if (sunAltitudeDegrees >= CIVIL_END_DEGREES) {
			double t = sunAltitudeDegrees / CIVIL_END_DEGREES; // 0 at alt=0, 1 at alt=-6
			return lerp(1.0, CIVIL_END_BRIGHTNESS, t);
		}
		if (sunAltitudeDegrees >= NAUTICAL_END_DEGREES) {
			double t = (sunAltitudeDegrees - CIVIL_END_DEGREES) / (NAUTICAL_END_DEGREES - CIVIL_END_DEGREES);
			return lerp(CIVIL_END_BRIGHTNESS, NAUTICAL_END_BRIGHTNESS, t);
		}
		double t = (sunAltitudeDegrees - NAUTICAL_END_DEGREES) / (ASTRONOMICAL_END_DEGREES - NAUTICAL_END_DEGREES);
		return lerp(NAUTICAL_END_BRIGHTNESS, 0.0, t);
	}

	private static double lerp(double from, double to, double t) {
		return from + (to - from) * t;
	}

	// Darkens the given canvas in place toward black, per-pixel - the exact old-prototype technique
	// (VirtualCamera.java's pr/pg/pb = original * pct, alpha copied through unchanged): each channel
	// scaled by brightnessFraction(...), alpha left untouched so a transparent pixel stays transparent
	// rather than being turned into opaque black. No-op (skips the loop entirely) at full brightness -
	// the common daytime case.
	public static void darken(BufferedImage canvas, double sunAltitudeDegrees) {
		double brightness = brightnessFraction(sunAltitudeDegrees);
		if (brightness >= 1.0)
			return;

		int width = canvas.getWidth();
		int height = canvas.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = canvas.getRGB(x, y);
				int a = (argb >>> 24) & 0xFF;
				if (a == 0)
					continue;
				int r = (int) Math.round(((argb >> 16) & 0xFF) * brightness);
				int g = (int) Math.round(((argb >> 8) & 0xFF) * brightness);
				int b = (int) Math.round((argb & 0xFF) * brightness);
				canvas.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
	}
}
