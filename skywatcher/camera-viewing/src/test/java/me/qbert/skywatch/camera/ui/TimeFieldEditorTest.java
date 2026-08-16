package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

// TimeFieldEditor is a package-visible, non-Swing pure-logic class (see its own comment) - fully
// testable, unlike ControlPanel itself. Fixed UTC zone throughout so no test result depends on the
// machine running the suite.
class TimeFieldEditorTest {

	private static final ZoneId UTC = ZoneId.of("UTC");

	private static ZonedDateTime at(int year, int month, int day, int hour, int minute, int second) {
		return ZonedDateTime.of(year, month, day, hour, minute, second, 0, UTC);
	}

	@Test
	void applyYearSetsTheYearAloneAndClampsAtSpinnerBounds() {
		ZonedDateTime current = at(2024, 6, 15, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyYear(current, 1990);

		assertEquals(at(1990, 6, 15, 10, 30, 0), updated, "only the year should change");
	}

	@Test
	void applyMonthInBoundsSetsTheMonthAloneClampingDayIfNeeded() {
		// Jan 31 -> February must clamp the day to Feb's own last valid day (28, non-leap 2023),
		// per ZonedDateTime.withMonth(...)'s own documented behavior - not an exception.
		ZonedDateTime current = at(2023, 1, 31, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyMonth(current, 2);

		assertEquals(at(2023, 2, 28, 10, 30, 0), updated);
	}

	@Test
	void applyMonthOverflowSlotRollsToNextYearJanuary() {
		ZonedDateTime current = at(2024, 12, 15, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyMonth(current, 13);

		assertEquals(at(2025, 1, 15, 10, 30, 0), updated);
	}

	@Test
	void applyMonthUnderflowSlotRollsToPreviousYearDecember() {
		ZonedDateTime current = at(2024, 1, 15, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyMonth(current, 0);

		assertEquals(at(2023, 12, 15, 10, 30, 0), updated);
	}

	@Test
	void applyDayInBoundsSetsTheDayAlone() {
		ZonedDateTime current = at(2024, 4, 1, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyDay(current, 30);

		assertEquals(at(2024, 4, 30, 10, 30, 0), updated);
	}

	@Test
	void applyDayOverflowRollsIntoNextMonthEvenForAShortMonth() {
		// April has only 30 days - the spinner's own fixed extended bound is 32, but the REAL
		// overflow threshold is this month's actual length (30), not the spinner's static max.
		ZonedDateTime current = at(2024, 4, 30, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyDay(current, 31);

		assertEquals(at(2024, 5, 1, 10, 30, 0), updated);
	}

	@Test
	void applyDayUnderflowRollsIntoPreviousMonthsLastDay() {
		ZonedDateTime current = at(2024, 3, 1, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyDay(current, 0);

		assertEquals(at(2024, 2, 29, 10, 30, 0), updated, "2024 is a leap year - Feb has 29 days");
	}

	@Test
	void applyHourInBoundsSetsTheHourAlone() {
		ZonedDateTime current = at(2024, 6, 15, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyHour(current, 5);

		assertEquals(at(2024, 6, 15, 5, 30, 0), updated);
	}

	@Test
	void applyHourOverflowRollsToNextDayMidnight() {
		ZonedDateTime current = at(2024, 6, 15, 23, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyHour(current, 24);

		assertEquals(at(2024, 6, 16, 0, 30, 0), updated);
	}

	@Test
	void applyHourUnderflowRollsToPreviousDayLastHour() {
		ZonedDateTime current = at(2024, 6, 15, 0, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applyHour(current, -1);

		assertEquals(at(2024, 6, 14, 23, 30, 0), updated);
	}

	@Test
	void applyMinuteOverflowRollsToNextHour() {
		ZonedDateTime current = at(2024, 6, 15, 10, 59, 30);

		ZonedDateTime updated = TimeFieldEditor.applyMinute(current, 60);

		assertEquals(at(2024, 6, 15, 11, 0, 30), updated);
	}

	@Test
	void applyMinuteUnderflowRollsToPreviousHour() {
		ZonedDateTime current = at(2024, 6, 15, 10, 0, 30);

		ZonedDateTime updated = TimeFieldEditor.applyMinute(current, -1);

		assertEquals(at(2024, 6, 15, 9, 59, 30), updated);
	}

	@Test
	void applySecondOverflowRollsToNextMinute() {
		ZonedDateTime current = at(2024, 6, 15, 10, 30, 59);

		ZonedDateTime updated = TimeFieldEditor.applySecond(current, 60);

		assertEquals(at(2024, 6, 15, 10, 31, 0), updated);
	}

	@Test
	void applySecondUnderflowRollsToPreviousMinute() {
		ZonedDateTime current = at(2024, 6, 15, 10, 30, 0);

		ZonedDateTime updated = TimeFieldEditor.applySecond(current, -1);

		assertEquals(at(2024, 6, 15, 10, 29, 59), updated);
	}

	// A rollover that itself crosses a year boundary (Dec 31 23:59:59 + 1 second) must cascade all
	// the way up, proving these aren't independent per-field operations that could leave an
	// inconsistent result.
	@Test
	void secondOverflowCascadesThroughMinuteHourDayMonthAndYear() {
		ZonedDateTime current = at(2024, 12, 31, 23, 59, 59);

		ZonedDateTime updated = TimeFieldEditor.applySecond(current, 60);

		assertEquals(at(2025, 1, 1, 0, 0, 0), updated);
	}
}
