package me.qbert.skywatch.camera.clock;

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

// Seam for injecting a fake wall clock in tests - SimulatedClock's real-time-tracking behavior
// (Play mode) would otherwise need actual sleeps to verify.
public interface WallClock {
	long currentTimeMillis();

	WallClock SYSTEM = new WallClock() {
		@Override
		public long currentTimeMillis() {
			return System.currentTimeMillis();
		}
	};
}
