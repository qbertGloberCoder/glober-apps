package me.qbert.skywatch.camera.render;

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

// The 7 layers from CLAUDE.md's "Layer model" table, bottom to top. 3B/3C are represented as the
// two named layers they actually hold (GROUND, OBJECTS) rather than their swappable slot numbers -
// LayerVisibility.compositeOrder(...) is what decides which one paints first.
public enum RenderLayer {
	IMAGE_BACKGROUND,    // Layer 1
	GRATICULE_AND_PATHS, // Layer 2
	SKY,                 // Layer 3A
	GROUND,              // Layer 3B or 3C
	OBJECTS,             // Layer 3C or 3B
	IMAGE_FOREGROUND,    // Layer 4
	OSD                  // Layer 5
}
