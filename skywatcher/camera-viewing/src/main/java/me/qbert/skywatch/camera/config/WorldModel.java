package me.qbert.skywatch.camera.config;

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

// spec §7.1's globe/flat world-model toggle. Stored as a plain setting only for now - no behavior
// is wired to FLAT yet. sw-base's GeoCalculator is globe-only; see CLAUDE.md's corrected porting
// note for astro.EquatorialMount's table neighbor on why real flat-earth alt-az math is deferred
// rather than ported speculatively in this pass.
public enum WorldModel {
	GLOBE,
	FLAT
}
