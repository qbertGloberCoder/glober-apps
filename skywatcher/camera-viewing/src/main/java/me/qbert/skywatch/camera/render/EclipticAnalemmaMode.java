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

// Which of spec §6's "ecliptic mode" / "analemma mode" (if either) is active for one body - the two
// modes are the same sampling with AbstractPrecession's showAsAnalemma flag flipped, never shown
// simultaneously for the same body, so a 3-way mode (rather than two independent booleans) is the
// natural fit.
public enum EclipticAnalemmaMode {
	NONE, ECLIPTIC, ANALEMMA
}
