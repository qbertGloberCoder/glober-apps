package me.qbert.skywatch.camera.source;

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

// spec §10.2: on the one day per year clocks fall back, a local time like "01:30" is genuinely
// ambiguous (it happens twice). Old captures can't be retroactively disambiguated, so each camera
// declares how to resolve it for its archive rather than guessing per-file. New captures can
// sidestep this entirely by encoding a UTC offset/time directly in the filename going forward.
public enum DstAmbiguousPolicy {
	ASSUME_STANDARD,
	ASSUME_DAYLIGHT
}
