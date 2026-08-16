package me.qbert.skywatch.camera.plate;

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

// Task 7.2a's growing collection of PlateSolveMarks for one plate-solve attempt: "repeat across
// several frames spanning a few hours." Deliberately independent of plate.PlateSolveSession (7.1) -
// technique 1 (manual Save/Revert against a single still frame) and technique 2 (marks collected
// across several frames, later fit by 7.2b) are different workflows over the same camera, not one
// combined session; nothing here reads or writes a CalibrationHistory.
//
// Append-only by design, matching CalibrationHistory's own established shape for a similarly
// growing, order-preserving log in this module - no removal was asked for, and a mis-click is
// simply not a case this round needs to handle.
public final class PlateSolveMarkSet {
	private final List<PlateSolveMark> marks = new ArrayList<PlateSolveMark>();

	public void add(PlateSolveMark mark) {
		if (mark == null)
			throw new IllegalArgumentException("mark must not be null");
		marks.add(mark);
	}

	public List<PlateSolveMark> getMarks() {
		return Collections.unmodifiableList(marks);
	}

	public boolean isEmpty() {
		return marks.isEmpty();
	}

	public int size() {
		return marks.size();
	}
}
