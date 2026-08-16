package me.qbert.skywatch.camera.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

// Injected, non-static replacement for org.bluerock.dao.StarCoordinateDao (see
// CLAUDE.md's porting notes) — no global state, callers decide when/whether to load.
// stars.db's columns are: name,designation,magnitude,rightAscension,declination,groupLevel
// (6 columns as of this round - the trailing "visible" column was removed from the shipped/
// compiled resource entirely; per-user visibility now lives in a separate, external override file
// - see catalog.StarVisibilityOverrides. Every star loaded from here defaults visible=false; the
// caller merges in whatever the override file says on top, via StarVisibilityOverrides.merge(...).
public class StarCatalogLoader {
	// The one known garbage row in stars.db, filtered on load rather than in the data file itself.
	private static final String SENTINEL_NAME = "Null";
	private static final String SENTINEL_DESIGNATION = "NULLNULL";

	public List<StarCoordinate> load(InputStream starsDbStream) throws IOException {
		List<StarCoordinate> stars = new ArrayList<StarCoordinate>();

		BufferedReader reader = new BufferedReader(
				new InputStreamReader(starsDbStream, StandardCharsets.UTF_8));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty())
					continue;

				StarCoordinate star = parseLine(line);
				if (star == null)
					continue;

				if (SENTINEL_NAME.equals(star.getName()) && SENTINEL_DESIGNATION.equals(star.getDesignation()))
					continue;

				stars.add(star);
			}
		} finally {
			reader.close();
		}

		return Collections.unmodifiableList(stars);
	}

	public List<StarCoordinate> filterByTier(List<StarCoordinate> stars, StarCatalogTier tier) {
		List<StarCoordinate> filtered = new ArrayList<StarCoordinate>();
		for (StarCoordinate star : stars) {
			if (tier.matches(star))
				filtered.add(star);
		}
		return Collections.unmodifiableList(filtered);
	}

	private StarCoordinate parseLine(String line) {
		String[] fields = line.split(",", -1);
		if (fields.length != 6)
			return null;

		try {
			String name = fields[0];
			String designation = fields[1];
			double magnitude = Double.parseDouble(fields[2]);
			double rightAscension = Double.parseDouble(fields[3]);
			double declination = Double.parseDouble(fields[4]);
			int groupLevel = Integer.parseInt(fields[5]);

			return new StarCoordinate(name, designation, magnitude, rightAscension, declination,
					groupLevel, false);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
