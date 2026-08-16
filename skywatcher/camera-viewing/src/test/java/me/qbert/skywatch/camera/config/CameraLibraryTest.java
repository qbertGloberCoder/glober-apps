package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;

class CameraLibraryTest {

	@Test
	void constructingCreatesTheDirectoryIfMissing(@TempDir File tempDir) {
		File libraryDir = new File(tempDir, "does/not/exist/yet");

		new CameraLibrary(libraryDir);

		assertTrue(libraryDir.isDirectory());
	}

	@Test
	void startsEmpty(@TempDir File tempDir) {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));

		assertTrue(library.listCameraNames().isEmpty());
	}

	@Test
	void saveAndLoadRoundTrip(@TempDir File tempDir) throws Exception {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));
		CameraConfig camera = camera("polaris", tempDir);

		library.save("polaris", camera);
		CameraConfig loaded = library.load("polaris");

		assertEquals("polaris", loaded.getName());
		assertEquals(45.0, loaded.getCalibrationHistory().latest().getLatitude(), 0.0001);
	}

	@Test
	void listCameraNamesIsSortedAndReflectsWhatWasSaved(@TempDir File tempDir) throws Exception {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));
		library.save("zeta", camera("zeta", tempDir));
		library.save("polaris", camera("polaris", tempDir));
		library.save("alpha", camera("alpha", tempDir));

		List<String> names = library.listCameraNames();

		assertEquals(Arrays.asList("alpha", "polaris", "zeta"), names);
	}

	@Test
	void containsReflectsWhatsActuallySaved(@TempDir File tempDir) throws Exception {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));

		assertFalse(library.contains("polaris"));

		library.save("polaris", camera("polaris", tempDir));

		assertTrue(library.contains("polaris"));
	}

	@Test
	void loadThrowsForAnUnknownCamera(@TempDir File tempDir) {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));

		assertThrows(IOException.class, () -> library.load("nobody"));
	}

	@Test
	void removeDeletesAndIsANoOpForAnUnknownCamera(@TempDir File tempDir) throws Exception {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));
		library.save("polaris", camera("polaris", tempDir));
		assertTrue(library.contains("polaris"));

		library.remove("polaris");
		assertFalse(library.contains("polaris"));

		library.remove("polaris"); // no-op, must not throw
	}

	@Test
	void fileForRejectsAnEmptyName(@TempDir File tempDir) {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));

		assertThrows(IllegalArgumentException.class, () -> library.fileFor(""));
	}

	private CameraConfig camera(String name, File tempDir) {
		RealImageSource source = RealImageSource.preRecordedOnly(new File(tempDir, name).getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig(name, CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));
		return camera;
	}
}
