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

// Virtual cameras only [CLAUDE.md's "Layer model" table] - a Virtual camera's own image (see
// getVirtualScenePath()) is composited either as Layer 1 (background, occludable by layers
// 2/3A-3C - "the ordinary/default placement") or Layer 4 (foreground mask, occludes them instead -
// the user's own motivating case: "overlay a static image over everything else, obscuring parts
// that were rendered"). Real cameras never get this choice - always Layer 1, no stored setting
// needed. Deliberately NOT the same type as render.ImagePlacement (which also has a NONE value,
// representing the runtime/session show-hide toggle from CLAUDE.md's "Camera image display" - a
// different concern from this persisted camera-setup choice, kept as a separate concept rather
// than conflating a 2-valued setup choice with a 3-valued render-time state).
public enum VirtualImagePlacement {
	LAYER_1,
	LAYER_4
}
