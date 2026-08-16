package me.qbert.skywatch.camera.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TimestampPatternTest {

	// Matches the user's own prototype convention: latest.jpg is separately copied to
	// YYYYmmdd_HHMMSS.jpg, e.g. 20260808_000000.jpg for midnight local time.
	private static final String USER_PROTOTYPE_REGEX =
			"(?<yyyy>\\d{4})(?<mm>\\d{2})(?<dd>\\d{2})_(?<hh>\\d{2})(?<min>\\d{2})(?<ss>\\d{2})\\.jpg";

	@Test
	void parsesTheUsersOwnFilenameConvention() {
		TimestampPattern pattern = new TimestampPattern(USER_PROTOTYPE_REGEX,
				ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		long epochMillis = pattern.parseEpochMillis("20260808_000000.jpg");

		OffsetDateTime expected = OffsetDateTime.of(2026, 8, 8, 0, 0, 0, 0, ZoneOffset.UTC);
		assertEquals(expected.toInstant().toEpochMilli(), epochMillis);
	}

	@Test
	void returnsNullForANonMatchingPath() {
		TimestampPattern pattern = new TimestampPattern(USER_PROTOTYPE_REGEX,
				ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		assertNull(pattern.parseEpochMillis("latest.jpg"));
	}

	// spec §10.2's named case: the one local time per year that genuinely happens twice.
	// America/Toronto's DST ended 2023-11-05 at 02:00 local (clocks set back to 01:00), so
	// 2023-11-05 01:30:00 local occurred once at UTC-4 (EDT) and again at UTC-5 (EST).
	@Test
	void dstFallBackAmbiguousTimeResolvesPerThePolicy() {
		ZoneId toronto = ZoneId.of("America/Toronto");
		String ambiguousFilename = "20231105_013000.jpg";

		TimestampPattern assumeDaylight = new TimestampPattern(USER_PROTOTYPE_REGEX, toronto, DstAmbiguousPolicy.ASSUME_DAYLIGHT);
		TimestampPattern assumeStandard = new TimestampPattern(USER_PROTOTYPE_REGEX, toronto, DstAmbiguousPolicy.ASSUME_STANDARD);

		long daylightEpoch = assumeDaylight.parseEpochMillis(ambiguousFilename);
		long standardEpoch = assumeStandard.parseEpochMillis(ambiguousFilename);

		OffsetDateTime expectedDaylight = OffsetDateTime.of(2023, 11, 5, 1, 30, 0, 0, ZoneOffset.ofHours(-4));
		OffsetDateTime expectedStandard = OffsetDateTime.of(2023, 11, 5, 1, 30, 0, 0, ZoneOffset.ofHours(-5));

		assertEquals(expectedDaylight.toInstant().toEpochMilli(), daylightEpoch);
		assertEquals(expectedStandard.toInstant().toEpochMilli(), standardEpoch);

		// The two policies must disagree by exactly the one-hour DST offset - this is the whole
		// point of the ambiguity, not an incidental detail.
		assertEquals(60L * 60L * 1000L, standardEpoch - daylightEpoch);
	}

	// The spring-forward gap (a local time that never happened) is a different case spec §10.2
	// doesn't cover - confirm it fails loudly rather than silently picking a wrong instant.
	@Test
	void springForwardGapThrowsRatherThanGuessing() {
		ZoneId toronto = ZoneId.of("America/Toronto");
		// 2023-03-12: Toronto's clocks jumped from 02:00 to 03:00 - 02:30 never existed.
		TimestampPattern pattern = new TimestampPattern(USER_PROTOTYPE_REGEX, toronto, DstAmbiguousPolicy.ASSUME_STANDARD);

		assertThrows(DateTimeException.class, () -> pattern.parseEpochMillis("20230312_023000.jpg"));
	}
}
