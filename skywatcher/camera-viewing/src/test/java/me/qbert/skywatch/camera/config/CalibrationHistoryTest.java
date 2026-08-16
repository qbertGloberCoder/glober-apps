package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.Orientation;

class CalibrationHistoryTest {

	private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

	@Test
	void emptyHistoryHasNoLatest() {
		CalibrationHistory history = new CalibrationHistory();

		assertNull(history.latest());
		assertNull(history.latestAsOf(System.currentTimeMillis()));
	}

	@Test
	void appendingANewerEntryDoesNotAffectOlderFramesResolution() {
		CalibrationHistory history = new CalibrationHistory();

		CalibrationEntry march1 = new CalibrationEntry(0L, new Orientation(12.1, 182.4, 0.3), 1.8, 45.0, -75.0);
		history.append(march1);

		// A frame captured well after march1 but before any later entry exists.
		long midYearFrame = 200L * DAY_MILLIS;
		assertSame(march1, history.latestAsOf(midYearFrame));

		// Detected shift: append a newer calibration, effective partway through that same year.
		CalibrationEntry november = new CalibrationEntry(260L * DAY_MILLIS, new Orientation(12.3, 181.9, 0.3), 1.8, 45.0, -75.0);
		history.append(november);

		// The append-only guarantee spec §7.2 requires: frames timestamped *before* the new entry
		// still resolve to the older one.
		assertSame(march1, history.latestAsOf(midYearFrame),
				"a frame from before the new calibration must keep resolving to the old one");

		// A frame captured after the new entry now resolves to it.
		assertSame(november, history.latestAsOf(300L * DAY_MILLIS));

		// Both entries are preserved - appending never edits or removes history.
		assertEquals(2, history.getEntries().size());
	}

	@Test
	void latestWithNoArgumentIgnoresEffectiveFromOrdering() {
		CalibrationHistory history = new CalibrationHistory();

		CalibrationEntry earlyButAddedLast = new CalibrationEntry(0L, new Orientation(1, 1, 0), 1.0, 45.0, -75.0);
		CalibrationEntry lateButAddedFirst = new CalibrationEntry(500L * DAY_MILLIS, new Orientation(2, 2, 0), 1.0, 45.0, -75.0);

		history.append(lateButAddedFirst);
		history.append(earlyButAddedLast);

		assertSame(lateButAddedFirst, history.latest(), "latest() means largest effectiveFrom, not insertion order");
	}

	@Test
	void locationTravelsWithOrientationAsOneVersionedEntry() {
		// The user's own scenario: someone traveling records sunrises/sunsets from different
		// locations across sessions - a location correction is versioned together with each
		// orientation correction, not through a second desynchronized history.
		CalibrationHistory history = new CalibrationHistory();

		CalibrationEntry home = new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 1.0, 45.0, -75.0);
		history.append(home);

		long midTripFrame = 100L * DAY_MILLIS;
		assertEquals(45.0, history.latestAsOf(midTripFrame).getLatitude());
		assertEquals(-75.0, history.latestAsOf(midTripFrame).getLongitude());

		CalibrationEntry awayFromHome = new CalibrationEntry(200L * DAY_MILLIS, new Orientation(10.0, 90.0, 0.0), 1.0, 35.0, 139.0);
		history.append(awayFromHome);

		// A frame from before the trip still resolves to the original location, not the new one.
		assertEquals(45.0, history.latestAsOf(midTripFrame).getLatitude(),
				"a frame from before the new calibration must keep resolving to the old location");
		assertEquals(35.0, history.latestAsOf(300L * DAY_MILLIS).getLatitude());
		assertEquals(139.0, history.latestAsOf(300L * DAY_MILLIS).getLongitude());
	}
}
