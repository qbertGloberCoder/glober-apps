package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.catalog.StarVisibilityOverrides;

// Task 6/Item 6's "CLI-only controls for the locally visible restructure" - the user's own
// confirmation was CLI-only, no UI star-picker, for this round.
class StarVisibilityCommandTest {

	private static int run(File overridesFile, ByteArrayOutputStream captured, String... args) throws Exception {
		String[] full = new String[args.length + 2];
		System.arraycopy(args, 0, full, 0, args.length);
		full[args.length] = "--star-visibility-file";
		full[args.length + 1] = overridesFile.getPath();
		return new StarVisibilityCommand().run(full, new java.io.PrintStream(captured, true, "UTF-8"));
	}

	@Test
	void marksAnExistingCatalogStarVisible(@TempDir File tempDir) throws Exception {
		File overridesFile = new File(tempDir, "overrides.csv");
		ByteArrayOutputStream captured = new ByteArrayOutputStream();

		// Acamar is a real, confirmed entry in the bundled stars.db (HR 897, groupLevel 1).
		int exitCode = run(overridesFile, captured, "--designation", "HR 897", "--visible", "true");

		assertEquals(0, exitCode);
		List<StarCoordinate> overrides = StarVisibilityOverrides.load(overridesFile);
		assertEquals(1, overrides.size());
		assertEquals("Acamar", overrides.get(0).getName(), "the star's other fields must come from stars.db");
		assertTrue(overrides.get(0).isVisible());

		// "Updated" not "Added" - HR 897 already exists in the base catalog (stars.db), so toggling
		// its visibility for the first time is still an update to a KNOWN star, not the introduction
		// of a brand-new one. "Added" is reserved for a genuinely new designation (see the next test).
		String output = captured.toString(StandardCharsets.UTF_8.name());
		assertTrue(output.contains("Updated"));
	}

	@Test
	void togglingTheSameDesignationTwiceUpdatesRatherThanDuplicates(@TempDir File tempDir) throws Exception {
		File overridesFile = new File(tempDir, "overrides.csv");
		run(overridesFile, new ByteArrayOutputStream(), "--designation", "HR 897", "--visible", "true");

		ByteArrayOutputStream secondRun = new ByteArrayOutputStream();
		int exitCode = run(overridesFile, secondRun, "--designation", "HR 897", "--visible", "false");

		assertEquals(0, exitCode);
		List<StarCoordinate> overrides = StarVisibilityOverrides.load(overridesFile);
		assertEquals(1, overrides.size(), "toggling the same designation again must update, not duplicate");
		assertEquals(false, overrides.get(0).isVisible());
		assertTrue(secondRun.toString(StandardCharsets.UTF_8.name()).contains("Updated"));
	}

	@Test
	void introducesABrandNewStarWhenFullFieldsAreSupplied(@TempDir File tempDir) throws Exception {
		File overridesFile = new File(tempDir, "overrides.csv");
		ByteArrayOutputStream captured = new ByteArrayOutputStream();

		int exitCode = run(overridesFile, captured, "--designation", "USER 1", "--visible", "true", "--name", "MyStar",
				"--magnitude", "4.5", "--ra", "123.4", "--dec", "-12.3", "--group-level", "3");

		assertEquals(0, exitCode);
		List<StarCoordinate> overrides = StarVisibilityOverrides.load(overridesFile);
		assertEquals(1, overrides.size());
		assertEquals("MyStar", overrides.get(0).getName());
		assertEquals(3, overrides.get(0).getGroupLevel());
	}

	@Test
	void rejectsAnUnknownDesignationWithoutTheNewStarFields(@TempDir File tempDir) throws Exception {
		File overridesFile = new File(tempDir, "overrides.csv");
		assertThrows(CliUsageException.class,
				() -> run(overridesFile, new ByteArrayOutputStream(), "--designation", "NO SUCH STAR", "--visible", "true"));
	}

	@Test
	void requiresDesignation(@TempDir File tempDir) throws Exception {
		File overridesFile = new File(tempDir, "overrides.csv");
		assertThrows(CliUsageException.class,
				() -> run(overridesFile, new ByteArrayOutputStream(), "--visible", "true"));
	}
}
