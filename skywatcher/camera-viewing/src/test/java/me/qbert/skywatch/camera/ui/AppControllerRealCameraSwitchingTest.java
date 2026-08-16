package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;

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

// A real user report: the control panel's Camera Location/Orientation controls stayed disabled no
// matter which camera was opened or how many times cameras were switched. AppControllerTest's own
// fixtures build a CameraConfig by hand; this test instead goes through CameraEditDialog.
// buildCameraConfig(...) - the ACTUAL construction path the control panel's "Add..." button uses -
// to rule out a data-flow bug specific to real, dialog-built camera profiles saved/loaded/switched
// through the full CameraLibrary round trip, independent of Swing (which this module's own test
// environment cannot exercise directly - see AppControllerTest's own standing caveat).
class AppControllerRealCameraSwitchingTest {

	@Test
	void twoCamerasBuiltViaTheRealAddDialogPathBothGetWorkingEditSessionsAcrossRepeatedSwitches(@TempDir File tempDir)
			throws Exception {
		File cameraRoot1 = new File(tempDir, "cam1");
		cameraRoot1.mkdirs();
		File cameraRoot2 = new File(tempDir, "cam2");
		cameraRoot2.mkdirs();

		CameraConfig cam1 = CameraEditDialog.buildRealCameraConfig("cam1", RealCaptureMode.PRE_RECORDED_ONLY, 45.0, -75.0,
				10.0, 90.0, 0.0, 50.0, "rectilinear", 180.0, cameraRoot1.getPath() + "/YYYYmmdd_HHMMSS*.jpg", "",
				"system");
		CameraConfig cam2 = CameraEditDialog.buildRealCameraConfig("cam2", RealCaptureMode.PRE_RECORDED_ONLY, 40.0, -80.0,
				5.0, 180.0, 0.0, 24.0, "rectilinear", 180.0, cameraRoot2.getPath() + "/YYYYmmdd_HHMMSS*.jpg", "",
				"system");

		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));
		library.save("cam1", cam1);
		library.save("cam2", cam2);

		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		FrameCompositor.Options options = new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
		AppController app = new AppController(library, cache, options, new SimulatedClock());

		assertNotNull(app.switchToCamera("cam1").getActiveEditSession(), "cam1 should get an active edit session");
		assertNotNull(app.switchToCamera("cam2").getActiveEditSession(), "cam2 should get an active edit session");
		assertNotNull(app.switchToCamera("cam1").getActiveEditSession(),
				"switching back to cam1 should still get an active edit session");
	}
}
