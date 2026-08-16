package me.qbert.skywatch.camera.config;

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

// Fixed-camera (real or virtual) time-versioned calibration list [spec §7.2]. Append-only - never
// edit or remove an existing entry; re-plate-solving after a detected shift is always a new entry.
// PTZ cameras never have one of these - see CLAUDE.md's "Camera setup".
//
// Selection semantics ported from org.bluerock.model.CameraConfiguration's private getSetting(Long):
// the entry with the largest effectiveFrom that is still <= the query time (or the largest overall
// if the query time is null/omitted) - see CLAUDE.md's porting-notes table row for this class pair.
public final class CalibrationHistory {
	private final List<CalibrationEntry> entries = new ArrayList<CalibrationEntry>();

	public void append(CalibrationEntry entry) {
		if (entry == null)
			throw new IllegalArgumentException("entry must not be null");
		entries.add(entry);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public List<CalibrationEntry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	// The calibration that was in effect for a frame captured at queryEpochMillis.
	public CalibrationEntry latestAsOf(long queryEpochMillis) {
		return bestFit(Long.valueOf(queryEpochMillis));
	}

	// The most recently appended calibration, regardless of its effectiveFrom.
	public CalibrationEntry latest() {
		return bestFit(null);
	}

	private CalibrationEntry bestFit(Long queryEpochMillis) {
		CalibrationEntry bestFit = null;

		for (CalibrationEntry candidate : entries) {
			boolean isNewerThanCurrentBest = (bestFit == null)
					|| (candidate.getEffectiveFromEpochMillis() > bestFit.getEffectiveFromEpochMillis());
			boolean isNotAfterQuery = (queryEpochMillis == null)
					|| (candidate.getEffectiveFromEpochMillis() <= queryEpochMillis.longValue());

			if (isNewerThanCurrentBest && isNotAfterQuery)
				bestFit = candidate;
		}

		return bestFit;
	}
}
