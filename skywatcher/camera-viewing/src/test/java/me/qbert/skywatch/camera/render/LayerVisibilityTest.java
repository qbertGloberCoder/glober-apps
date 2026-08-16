package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

// Pins CLAUDE.md's "Sky (3A) / ground (3B-or-3C) auto-disable rules" section exactly, rule by rule.
class LayerVisibilityTest {

	@Test
	void skyAndGroundAreBothForcedOffWhenLayer1HoldsAnOpaqueImage() {
		assertTrue(LayerVisibility.isSkyForcedOff(ImagePlacement.LAYER_1, false));
		assertTrue(LayerVisibility.isGroundForcedOff(ImagePlacement.LAYER_1));

		// Forced off wins over the manual toggle either way.
		assertFalse(LayerVisibility.shouldRenderSky(ImagePlacement.LAYER_1, false, true));
		assertFalse(LayerVisibility.shouldRenderGround(ImagePlacement.LAYER_1, false));
	}

	@Test
	void transparentSkyRegionLiftsOnlyTheSkyExceptionNotGround() {
		// The user's own "unicorns on a see-through background" case - 3A becomes selectable again,
		// but the ground sub-layer has no equivalent exception per CLAUDE.md.
		assertFalse(LayerVisibility.isSkyForcedOff(ImagePlacement.LAYER_1, true));
		assertTrue(LayerVisibility.isGroundForcedOff(ImagePlacement.LAYER_1));

		assertTrue(LayerVisibility.shouldRenderSky(ImagePlacement.LAYER_1, true, true));
		assertFalse(LayerVisibility.shouldRenderSky(ImagePlacement.LAYER_1, true, false),
				"selectable means the manual toggle now actually governs it, not forced on either");
	}

	@Test
	void layer4PlacementHasNoAutoDisableAtAll() {
		assertFalse(LayerVisibility.isSkyForcedOff(ImagePlacement.LAYER_4, false));
		assertFalse(LayerVisibility.isGroundForcedOff(ImagePlacement.LAYER_4));
		assertTrue(LayerVisibility.shouldRenderSky(ImagePlacement.LAYER_4, false, true));
		assertTrue(LayerVisibility.shouldRenderGround(ImagePlacement.LAYER_4, false));
	}

	@Test
	void noImageAtAllHasNoAutoDisableEither() {
		assertFalse(LayerVisibility.isSkyForcedOff(ImagePlacement.NONE, false));
		assertFalse(LayerVisibility.isGroundForcedOff(ImagePlacement.NONE));
	}

	@Test
	void hideGroundToggleIsRespectedRegardlessOfPlacement() {
		// Meaningful specifically for "as seen from space" (NONE), but never silently ignored.
		assertFalse(LayerVisibility.shouldRenderGround(ImagePlacement.NONE, true));
		assertTrue(LayerVisibility.shouldRenderGround(ImagePlacement.NONE, false));
		assertFalse(LayerVisibility.shouldRenderGround(ImagePlacement.LAYER_4, true));
	}

	@Test
	void compositeOrderPutsTheImageInTheRightSlotForEachPlacement() {
		List<RenderLayer> layer1Order = LayerVisibility.compositeOrder(ImagePlacement.LAYER_1, false, false, false);
		assertEquals(RenderLayer.IMAGE_BACKGROUND, layer1Order.get(0));
		assertFalse(layer1Order.contains(RenderLayer.IMAGE_FOREGROUND));

		List<RenderLayer> layer4Order = LayerVisibility.compositeOrder(ImagePlacement.LAYER_4, true, true, false);
		assertFalse(layer4Order.contains(RenderLayer.IMAGE_BACKGROUND));
		assertEquals(RenderLayer.OSD, layer4Order.get(layer4Order.size() - 1), "OSD is always frontmost");
		assertTrue(layer4Order.indexOf(RenderLayer.IMAGE_FOREGROUND) < layer4Order.indexOf(RenderLayer.OSD),
				"a Layer-4 image occludes 2/3A-3C but never the OSD");

		List<RenderLayer> noneOrder = LayerVisibility.compositeOrder(ImagePlacement.NONE, true, true, false);
		assertFalse(noneOrder.contains(RenderLayer.IMAGE_BACKGROUND));
		assertFalse(noneOrder.contains(RenderLayer.IMAGE_FOREGROUND));
	}

	@Test
	void defaultOrderPaintsObjectsAfterGroundSoBelowHorizonObjectsStayVisible() {
		List<RenderLayer> order = LayerVisibility.compositeOrder(ImagePlacement.NONE, true, true, false);

		assertTrue(order.indexOf(RenderLayer.GROUND) < order.indexOf(RenderLayer.OBJECTS),
				"default: objects paint after (on top of) ground - 'objects seen through the ground'");
	}

	@Test
	void swapOptionPaintsGroundAfterObjectsForAnOpaqueLook() {
		List<RenderLayer> order = LayerVisibility.compositeOrder(ImagePlacement.NONE, true, true, true);

		assertTrue(order.indexOf(RenderLayer.OBJECTS) < order.indexOf(RenderLayer.GROUND),
				"swapped: ground paints after (on top of) objects");
	}

	@Test
	void skyAndGroundAreOmittedFromTheOrderWhenNotRendering() {
		List<RenderLayer> order = LayerVisibility.compositeOrder(ImagePlacement.NONE, false, false, false);

		assertFalse(order.contains(RenderLayer.SKY));
		assertFalse(order.contains(RenderLayer.GROUND));
		assertTrue(order.contains(RenderLayer.OBJECTS), "objects always render regardless of sky/ground toggles");
	}

	@Test
	void graticuleSitsAboveSkyButBelowGroundAndObjects() {
		// Corrected ordering: Layer 2 must be visible even when sky renders (the original bug), but
		// must still be occludable by ground/objects (the original, actually-intended purpose of
		// placing Layer 2 low in the stack in the first place).
		List<RenderLayer> defaultOrder = LayerVisibility.compositeOrder(ImagePlacement.NONE, true, true, false);
		assertTrue(defaultOrder.indexOf(RenderLayer.SKY) < defaultOrder.indexOf(RenderLayer.GRATICULE_AND_PATHS),
				"sky must no longer sit above the graticule/paths layer");
		assertTrue(defaultOrder.indexOf(RenderLayer.GRATICULE_AND_PATHS) < defaultOrder.indexOf(RenderLayer.GROUND),
				"ground must still be able to occlude the graticule/paths layer");
		assertTrue(defaultOrder.indexOf(RenderLayer.GRATICULE_AND_PATHS) < defaultOrder.indexOf(RenderLayer.OBJECTS),
				"objects must still be able to occlude the graticule/paths layer");

		List<RenderLayer> swappedOrder = LayerVisibility.compositeOrder(ImagePlacement.NONE, true, true, true);
		assertTrue(swappedOrder.indexOf(RenderLayer.GRATICULE_AND_PATHS) < swappedOrder.indexOf(RenderLayer.OBJECTS),
				"objects must occlude the graticule/paths layer in the swapped ground-over-objects order too");
	}

	@Test
	void graticuleAndOsdAreAlwaysPresentRegardlessOfEverythingElse() {
		List<RenderLayer> order = LayerVisibility.compositeOrder(ImagePlacement.LAYER_1, false, false, false);

		assertTrue(order.contains(RenderLayer.GRATICULE_AND_PATHS));
		assertTrue(order.contains(RenderLayer.OSD));
	}
}
