package me.qbert.skywatch.camera.catalog;

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

// Restructured this round from an earlier exact-match design (COMMON=groupLevel==1,
// LOCALLY_VISIBLE=groupLevel==2 exclusive, ALL=everything) to match the user's original 4-mode
// prototype design, confirmed against real data (stars.db's own groupLevel distribution: 336 rows
// at 1, 21 at 2, the bulk remainder at 3): VISIBLE_ONLY ignores groupLevel entirely - a fully
// user-curated set via StarCoordinate.isVisible(), not bounded by design though practically
// expected to stay small; MAIN/NAMED/ALL are CUMULATIVE groupLevel<=1/2/3 thresholds, NOT
// exact-match - the old design had no way to select "groupLevel 1 and 2 together" at all. Renamed
// away from "LOCALLY_VISIBLE" specifically to avoid colliding with the new, unrelated per-user
// StarCoordinate.isVisible() concept this same round introduced - see CLAUDE.md's Item 6 entry for
// the full reasoning, including why groupLevel 1+2 (roughly the stars with a real catalog NAME,
// as opposed to groupLevel 3's mostly designation-only bulk remainder) is called NAMED here.
public enum StarCatalogTier {
	VISIBLE_ONLY, MAIN, NAMED, ALL;

	boolean matches(StarCoordinate star) {
		switch (this) {
			case VISIBLE_ONLY:
				return star.isVisible();
			case MAIN:
				return star.getGroupLevel() <= 1;
			case NAMED:
				return star.getGroupLevel() <= 2;
			case ALL:
			default:
				return true;
		}
	}
}
