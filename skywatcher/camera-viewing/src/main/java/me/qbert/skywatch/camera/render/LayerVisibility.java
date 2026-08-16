package me.qbert.skywatch.camera.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

// The auto-disable rules and layer draw order from CLAUDE.md's "Sky (3A) / ground (3B-or-3C)
// auto-disable rules" section - both 3A and the ground sub-layer sit above Layer 1 in the stack, so
// an opaque fill there would obscure a Layer-1 image entirely. Pure decision logic only - no pixel
// drawing here, this just answers "should this layer render, and in what order."
public final class LayerVisibility {
	private LayerVisibility() {
	}

	// 3A is forced off whenever the camera's own image sits in Layer 1, UNLESS it's a Virtual
	// camera's image with a deliberately transparent sky region - in that case 3A becomes
	// user-selectable again (filling the transparent area with the computed sky color instead of
	// leaving it blank), not forced either way.
	public static boolean isSkyForcedOff(ImagePlacement placement, boolean cameraImageHasTransparentSkyRegion) {
		if (placement != ImagePlacement.LAYER_1)
			return false;
		return !cameraImageHasTransparentSkyRegion;
	}

	// The ground sub-layer has no transparency exception - it's forced off whenever Layer 1 holds
	// an image, full stop.
	public static boolean isGroundForcedOff(ImagePlacement placement) {
		return placement == ImagePlacement.LAYER_1;
	}

	// manualSkyToggle is only consulted when isSkyForcedOff(...) is false - the forced-off rule
	// always wins over a manual toggle, never the other way around.
	public static boolean shouldRenderSky(ImagePlacement placement, boolean cameraImageHasTransparentSkyRegion,
			boolean manualSkyToggle) {
		if (isSkyForcedOff(placement, cameraImageHasTransparentSkyRegion))
			return false;
		return manualSkyToggle;
	}

	// manualHideGroundToggle is the new "as seen from space" control - meaningful specifically when
	// placement is NONE (nothing in Layer 1 to protect from being obscured), but still respected
	// regardless of placement, since a manual choice should never be silently ignored just because
	// it's "less useful" in some other placement.
	public static boolean shouldRenderGround(ImagePlacement placement, boolean manualHideGroundToggle) {
		if (isGroundForcedOff(placement))
			return false;
		return !manualHideGroundToggle;
	}

	// The bottom-to-top draw order for this frame, given which layers are actually active.
	// groundPaintsOverObjects selects the ground/objects sub-layer order: false (the default) means
	// objects paint after (visually above) ground - "objects seen through the ground", so
	// below-horizon objects stay visible; true is the swap option, ground painted after objects, for
	// a more realistic/opaque look.
	//
	// Layer 2 (graticule/paths) sits ABOVE 3A (sky) but BELOW 3B/3C (ground/objects) - corrected
	// this round after building the graticule and watched-object crosshair surfaced the bug in the
	// original ordering (2 below 3A meant an opaque sky flood fill silently hid Layer 2 entirely,
	// which was never the intent). The origin of Layer 2's low position was always "don't let the
	// graticule/crosshair obscure the celestial objects" - that only requires 2 to sit below 3B/3C,
	// not below 3A too. Swapping 2 and 3A fixes the sky-hides-graticule bug while preserving the
	// actually-intended "objects/ground still occlude Layer 2" property untouched.
	public static List<RenderLayer> compositeOrder(ImagePlacement placement, boolean renderSky,
			boolean renderGround, boolean groundPaintsOverObjects) {
		List<RenderLayer> order = new ArrayList<RenderLayer>();

		if (placement == ImagePlacement.LAYER_1)
			order.add(RenderLayer.IMAGE_BACKGROUND);

		if (renderSky)
			order.add(RenderLayer.SKY);

		order.add(RenderLayer.GRATICULE_AND_PATHS);

		if (groundPaintsOverObjects) {
			order.add(RenderLayer.OBJECTS);
			if (renderGround)
				order.add(RenderLayer.GROUND);
		} else {
			if (renderGround)
				order.add(RenderLayer.GROUND);
			order.add(RenderLayer.OBJECTS);
		}

		if (placement == ImagePlacement.LAYER_4)
			order.add(RenderLayer.IMAGE_FOREGROUND);

		order.add(RenderLayer.OSD);

		return Collections.unmodifiableList(order);
	}
}
