package me.qbert.skywatch.camera.catalog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

// Externalizes per-user "locally visible" star flagging out of stars.db entirely (a real user
// complaint: that column "does not belong in the project repository and even if it did, it would
// be compiled into the jar file, making updates very hard to implement") into a separate, plain
// per-install file under ~/.camera-viewing/. CSV-shaped, matching stars.db's OWN established
// format (name,designation,magnitude,rightAscension,declination,groupLevel,visible - the SAME
// 7-column shape stars.db itself used before this round, now specific to this file) - a
// deliberate, narrow exception to this module's "prefer java.util.Properties" policy, for the same
// reason stars.db itself already is one: this is genuinely tabular, row-oriented data, not
// key/value configuration.
//
// An override entry either corrects an EXISTING designation's visible flag (merge(...) replaces
// stars.db's row for that designation wholesale, not just its visible bit) or introduces a BRAND
// NEW star not present in stars.db at all - both are just "this designation's row, as I want it,"
// no special-casing needed between the two cases.
public final class StarVisibilityOverrides {
	private StarVisibilityOverrides() {
	}

	// Empty list if the file doesn't exist yet - a fresh install has no overrides, not an error.
	public static List<StarCoordinate> load(File file) throws IOException {
		if (!file.exists())
			return Collections.emptyList();

		List<StarCoordinate> overrides = new ArrayList<StarCoordinate>();
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty() || line.startsWith("#"))
					continue;
				StarCoordinate star = parseLine(line);
				if (star != null)
					overrides.add(star);
			}
		} finally {
			reader.close();
		}
		return Collections.unmodifiableList(overrides);
	}

	public static void save(File file, List<StarCoordinate> overrides) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
			throw new IOException("could not create directory: " + parent);

		Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
		try {
			writer.write("# star visibility overrides - hand-editable, one star per line:\n");
			writer.write("# name,designation,magnitude,rightAscension,declination,groupLevel,visible\n");
			for (StarCoordinate star : overrides) {
				writer.write(star.getName());
				writer.write(',');
				writer.write(star.getDesignation());
				writer.write(',');
				writer.write(Double.toString(star.getApparentMagnitude()));
				writer.write(',');
				writer.write(Double.toString(star.getRightAscension()));
				writer.write(',');
				writer.write(Double.toString(star.getDeclination()));
				writer.write(',');
				writer.write(Integer.toString(star.getGroupLevel()));
				writer.write(',');
				writer.write(Boolean.toString(star.isVisible()));
				writer.write('\n');
			}
		} finally {
			writer.close();
		}
	}

	// base's own visible flags are always false (stars.db no longer carries the column at all) -
	// overrides win wholesale for any matching designation; any override designation not already in
	// base is appended as a brand-new star.
	public static List<StarCoordinate> merge(List<StarCoordinate> base, List<StarCoordinate> overrides) {
		Map<String, StarCoordinate> merged = new LinkedHashMap<String, StarCoordinate>();
		for (StarCoordinate star : base)
			merged.put(star.getDesignation(), star);
		for (StarCoordinate override : overrides)
			merged.put(override.getDesignation(), override);
		return Collections.unmodifiableList(new ArrayList<StarCoordinate>(merged.values()));
	}

	// Sets a single star's visibility by designation - looks up the star's other fields from base
	// if it's not already present in overrides (an ordinary existing-star toggle), or requires the
	// caller to supply a full StarCoordinate directly via addOrReplace(...) for a brand-new star not
	// in base at all. Returns a NEW override list (this class holds no state) - the caller is
	// responsible for persisting it via save(...).
	public static List<StarCoordinate> withVisibility(List<StarCoordinate> overrides, List<StarCoordinate> base,
			String designation, boolean visible) {
		StarCoordinate existing = find(overrides, designation);
		if (existing == null)
			existing = find(base, designation);
		if (existing == null)
			throw new IllegalArgumentException("no star with designation \"" + designation
					+ "\" found in the base catalog or the existing overrides - use addOrReplace(...) to introduce a new star");

		StarCoordinate updated = new StarCoordinate(existing.getName(), existing.getDesignation(),
				existing.getApparentMagnitude(), existing.getRightAscension(), existing.getDeclination(),
				existing.getGroupLevel(), visible);
		return addOrReplace(overrides, updated);
	}

	// Appends replacement as a new override, or replaces an existing override with the same
	// designation - the mechanism for introducing a brand-new star not present in stars.db at all.
	public static List<StarCoordinate> addOrReplace(List<StarCoordinate> overrides, StarCoordinate replacement) {
		List<StarCoordinate> result = new ArrayList<StarCoordinate>();
		boolean replaced = false;
		for (StarCoordinate star : overrides) {
			if (star.getDesignation().equals(replacement.getDesignation())) {
				result.add(replacement);
				replaced = true;
			} else {
				result.add(star);
			}
		}
		if (!replaced)
			result.add(replacement);
		return Collections.unmodifiableList(result);
	}

	private static StarCoordinate find(List<StarCoordinate> stars, String designation) {
		for (StarCoordinate star : stars) {
			if (star.getDesignation().equals(designation))
				return star;
		}
		return null;
	}

	private static StarCoordinate parseLine(String line) {
		String[] fields = line.split(",", -1);
		if (fields.length != 7)
			return null;

		try {
			String name = fields[0];
			String designation = fields[1];
			double magnitude = Double.parseDouble(fields[2]);
			double rightAscension = Double.parseDouble(fields[3]);
			double declination = Double.parseDouble(fields[4]);
			int groupLevel = Integer.parseInt(fields[5]);
			boolean visible = Boolean.parseBoolean(fields[6]);

			return new StarCoordinate(name, designation, magnitude, rightAscension, declination, groupLevel,
					visible);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
