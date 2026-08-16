package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.camera.source.TimestampPattern;

class RealImageSourceTest {

	private static final String ARCHIVE_TEMPLATE = "/somepath/cameras/1/**/YYYYmmdd_HHMMSS*.jpg";

	@Test
	void liveAndRecordedRequiresALatestPath() {
		assertThrows(IllegalArgumentException.class,
				() -> RealImageSource.liveAndRecorded(null, ARCHIVE_TEMPLATE, TimezoneSetting.useSystemDefault(), DstAmbiguousPolicy.ASSUME_STANDARD));
	}

	@Test
	void liveAndRecordedExposesBothSources() {
		RealImageSource source = RealImageSource.liveAndRecorded("/somepath/cameras/1/latest.jpg",
				ARCHIVE_TEMPLATE, TimezoneSetting.useSystemDefault(), DstAmbiguousPolicy.ASSUME_STANDARD);

		assertTrue(source.hasLatestSource());
		assertEquals("/somepath/cameras/1/latest.jpg", source.getLatestPath());
	}

	@Test
	void preRecordedOnlyHasNoLatestSource() {
		RealImageSource source = RealImageSource.preRecordedOnly(ARCHIVE_TEMPLATE,
				TimezoneSetting.useSystemDefault(), DstAmbiguousPolicy.ASSUME_STANDARD);

		assertFalse(source.hasLatestSource());
		assertThrows(IllegalStateException.class, source::getLatestPath);
	}

	@Test
	void compileArchivePatternUsesTheCurrentTimezoneSetting() {
		RealImageSource source = RealImageSource.preRecordedOnly(ARCHIVE_TEMPLATE,
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);

		TimestampPattern pattern = source.compileArchivePattern();
		Long epochMillis = pattern.parseEpochMillis("/somepath/cameras/1/2026/08/02/20260802_121400_1.jpg");

		OffsetDateTime expected = OffsetDateTime.of(2026, 8, 2, 12, 14, 0, 0, ZoneOffset.UTC);
		assertEquals(expected.toInstant().toEpochMilli(), epochMillis);

		// Changing the timezone setting after construction changes the next compiled pattern - the
		// EXIF/shared-timelapse override case (CLAUDE.md's "Image sources").
		source.setTimezone(TimezoneSetting.explicit(ZoneOffset.ofHours(9)));
		TimestampPattern reCompiled = source.compileArchivePattern();
		Long shiftedEpochMillis = reCompiled.parseEpochMillis("/somepath/cameras/1/2026/08/02/20260802_121400_1.jpg");

		assertEquals(9L * 60L * 60L * 1000L, epochMillis - shiftedEpochMillis,
				"the same local wall-clock string in a zone 9 hours east must resolve to an earlier instant");
	}
}
