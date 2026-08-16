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

// Where this camera's own image (if shown at all) sits in the 7-layer stack [CLAUDE.md's "Layer
// model"]. Real cameras are always LAYER_1 when shown (or NONE when the show/hide toggle hides
// them). Virtual cameras choose LAYER_1 (background, occludable by layers 2/3A-3C) or LAYER_4
// (foreground mask, occludes them instead) as a rendering choice - never both, always the same
// underlying image either way.
public enum ImagePlacement {
	NONE,
	LAYER_1,
	LAYER_4
}
