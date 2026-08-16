package me.qbert.skywatch.camera.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import me.qbert.skywatch.camera.catalog.StarCatalogLoader;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.catalog.StarVisibilityOverrides;

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

// CLI-only controls for the "locally visible" star restructure (no UI star-picker this round, per
// direct user confirmation) - marks/unmarks an existing catalog star visible by designation, or
// (--name/--magnitude/--ra/--dec/--group-level all supplied together) introduces a brand-new star
// not present in stars.db at all. Reads/writes catalog.StarVisibilityOverrides' own file.
final class StarVisibilityCommand {
	static final String USAGE = "Usage: star-visibility --designation <designation> --visible true|false\n"
			+ "                       [--name <name> --magnitude <n> --ra <deg> --dec <deg> --group-level <1-3>]\n"
			+ "                       [--star-visibility-file <path>]\n"
			+ "       (the --name/--magnitude/--ra/--dec/--group-level group is required only when\n"
			+ "       --designation does not already exist in stars.db or the override file - it\n"
			+ "       introduces a brand-new star instead of toggling an existing one)";

	int run(String[] args) throws Exception {
		return run(args, System.out);
	}

	int run(String[] args, PrintStream out) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		String designation = scanner.requireOption("designation");
		boolean visible = scanner.booleanOption("visible", true);
		File overridesFile = CameraConfigArgs.starVisibilityOverridesPath(scanner);

		List<StarCoordinate> base = loadBaseCatalog();
		List<StarCoordinate> overrides = StarVisibilityOverrides.load(overridesFile);

		boolean knownAlready = find(base, designation) != null || find(overrides, designation) != null;

		List<StarCoordinate> updated;
		if (knownAlready) {
			updated = StarVisibilityOverrides.withVisibility(overrides, base, designation, visible);
		} else {
			String name = scanner.option("name")
					.orElseThrow(() -> new CliUsageException(
							"designation \"" + designation + "\" is not in stars.db or the overrides file yet - "
									+ "supply --name/--magnitude/--ra/--dec/--group-level to introduce it as a new star.\n" + USAGE));
			double magnitude = scanner.requireDoubleOption("magnitude");
			double rightAscension = scanner.requireDoubleOption("ra");
			double declination = scanner.requireDoubleOption("dec");
			int groupLevel = scanner.intOption("group-level", 3);
			StarCoordinate newStar = new StarCoordinate(name, designation, magnitude, rightAscension, declination,
					groupLevel, visible);
			updated = StarVisibilityOverrides.addOrReplace(overrides, newStar);
		}

		StarVisibilityOverrides.save(overridesFile, updated);
		out.println((knownAlready ? "Updated" : "Added") + " \"" + designation + "\" (visible=" + visible + ") in "
				+ overridesFile);
		return 0;
	}

	private static List<StarCoordinate> loadBaseCatalog() throws IOException {
		StarCatalogLoader loader = new StarCatalogLoader();
		InputStream stream = StarVisibilityCommand.class.getResourceAsStream("/stars.db");
		if (stream == null)
			throw new IOException("stars.db not found on classpath");
		try {
			return loader.load(stream);
		} finally {
			stream.close();
		}
	}

	private static StarCoordinate find(List<StarCoordinate> stars, String designation) {
		for (StarCoordinate star : stars) {
			if (star.getDesignation().equals(designation))
				return star;
		}
		return null;
	}
}
