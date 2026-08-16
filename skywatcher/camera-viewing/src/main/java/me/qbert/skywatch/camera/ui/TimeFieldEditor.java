package me.qbert.skywatch.camera.ui;

import java.time.ZonedDateTime;

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

// Item 4 (sprint backlog review): pure rollover-aware editing logic for the Time tab's six
// year/month/day/hour/minute/second spinners, extracted out of ControlPanel (a JFrame, untestable
// in this module's headless sandbox - see ControlPanel's own class comment) so at least the logic
// itself is directly unit-testable, matching this module's established pattern for Swing-adjacent
// pure logic (PtzOrientationPanel.integrate(...), ShuttleControl.rateForPosition(...)).
//
// Design, per the sprint plan: each spinner's SpinnerNumberModel is given one extra "overflow" slot
// past its normal bound in each direction it can roll (year is the one exception - see applyYear -
// nothing above a year to cascade into, so it stays a plain clamped [1990,2050] range with no
// overflow slot). A value strictly inside the normal range mutates exactly that one field
// (withYear/withMonth/withDayOfMonth/withHour/withMinute/withSecond - "this field only", leaving
// every other field of `current` untouched, unlike the previous single-shared-listener design that
// rebuilt the whole timestamp from all six spinners' values on every change). A value that landed in
// the overflow slot instead ADDS one unit of that field to `current` (plusMonths(1)/plusDays(1)/etc,
// or the minus* equivalent for the underflow slot) - the standard "roll into the next/previous
// bigger unit" behavior a spinner user expects (e.g. incrementing hour past 23 lands on the next
// day's 00:00, not stuck at 23).
//
// None of the with*(...) calls below can throw DateTimeException in practice: day's own normal
// range is computed from `current`'s OWN current month (lengthOfMonth()), so withDayOfMonth(...) is
// always called with an already-valid value for that month; withMonth(...)/withYear(...) never throw
// at all - per their own javadoc, an invalid resulting day-of-month (e.g. Jan 31 -> February) is
// silently clamped to the new month's last valid day, not rejected.
final class TimeFieldEditor {
	private TimeFieldEditor() {
	}

	// No overflow slot - the spinner's own model clamps to [1990,2050] directly (nothing larger than
	// a year to roll into), per the sprint plan's explicit instruction.
	static ZonedDateTime applyYear(ZonedDateTime current, int spinnerValue) {
		return current.withYear(spinnerValue);
	}

	// Normal range: 1-12. Overflow slot: 13 (rolls to next year's January). Underflow slot: 0 (rolls
	// to previous year's December).
	static ZonedDateTime applyMonth(ZonedDateTime current, int spinnerValue) {
		if (spinnerValue > 12)
			return current.plusMonths(1);
		if (spinnerValue < 1)
			return current.minusMonths(1);
		return current.withMonth(spinnerValue);
	}

	// Normal range: 1..lengthOfMonth() of CURRENT's own month (varies 28-31) - computed fresh each
	// call, not a fixed constant, since it depends on which month is currently selected. Overflow
	// slot: fixed at 32 (no month has more than 31 days, so 32 always signals "past the end of this
	// month" regardless of which month it actually is). Underflow slot: 0.
	static ZonedDateTime applyDay(ZonedDateTime current, int spinnerValue) {
		int lastDayOfMonth = current.toLocalDate().lengthOfMonth();
		if (spinnerValue > lastDayOfMonth)
			return current.plusDays(1);
		if (spinnerValue < 1)
			return current.minusDays(1);
		return current.withDayOfMonth(spinnerValue);
	}

	// Normal range: 0-23. Overflow slot: 24. Underflow slot: -1.
	static ZonedDateTime applyHour(ZonedDateTime current, int spinnerValue) {
		if (spinnerValue > 23)
			return current.plusHours(1);
		if (spinnerValue < 0)
			return current.minusHours(1);
		return current.withHour(spinnerValue);
	}

	// Normal range: 0-59. Overflow slot: 60. Underflow slot: -1.
	static ZonedDateTime applyMinute(ZonedDateTime current, int spinnerValue) {
		if (spinnerValue > 59)
			return current.plusMinutes(1);
		if (spinnerValue < 0)
			return current.minusMinutes(1);
		return current.withMinute(spinnerValue);
	}

	// Normal range: 0-59. Overflow slot: 60. Underflow slot: -1.
	static ZonedDateTime applySecond(ZonedDateTime current, int spinnerValue) {
		if (spinnerValue > 59)
			return current.plusSeconds(1);
		if (spinnerValue < 0)
			return current.minusSeconds(1);
		return current.withSecond(spinnerValue);
	}
}
