package me.qbert.skywatch.camera.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

// The user's own worked example, verbatim from the design conversation: a security-camera script
// rolls the previous day's files into YYYY/MM/DD subdirectories, and the filename itself carries
// the full timestamp plus a per-camera numeric suffix.
class PathTemplateTest {

	private static final String USER_TEMPLATE = "/somepath/cameras/1/**/YYYYmmdd_HHMMSS*.jpg";

	@Test
	void compilesTheUsersOwnWorkedExample() {
		TimestampPattern pattern = PathTemplate.compile(USER_TEMPLATE, ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		Long epochMillis = pattern.parseEpochMillis("/somepath/cameras/1/2026/08/02/20260802_121400_1.jpg");

		OffsetDateTime expected = OffsetDateTime.of(2026, 8, 2, 12, 14, 0, 0, ZoneOffset.UTC);
		assertEquals(expected.toInstant().toEpochMilli(), epochMillis);
	}

	@Test
	void doubleStarAlsoMatchesZeroDirectoryDepth() {
		// "**" means arbitrary depth *including zero* - the "latest day, not yet rolled into a
		// subdirectory" case in the user's own scenario.
		TimestampPattern pattern = PathTemplate.compile(USER_TEMPLATE, ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		Long epochMillis = pattern.parseEpochMillis("/somepath/cameras/1/20260808_000000_1.jpg");

		OffsetDateTime expected = OffsetDateTime.of(2026, 8, 8, 0, 0, 0, 0, ZoneOffset.UTC);
		assertEquals(expected.toInstant().toEpochMilli(), epochMillis);
	}

	@Test
	void doubleStarMatchesSeveralDirectoryLevels() {
		// The user's confirmed real-world layout: .../1/2026/08/01 through .../1/2026/08/07.
		TimestampPattern pattern = PathTemplate.compile(USER_TEMPLATE, ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		Long epochMillis = pattern.parseEpochMillis("/somepath/cameras/1/2026/08/07/20260807_235900_1.jpg");

		OffsetDateTime expected = OffsetDateTime.of(2026, 8, 7, 23, 59, 0, 0, ZoneOffset.UTC);
		assertEquals(expected.toInstant().toEpochMilli(), epochMillis);
	}

	@Test
	void singleStarMatchesThePerCameraFilenameSuffix() {
		TimestampPattern pattern = PathTemplate.compile(USER_TEMPLATE, ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		// A different suffix than "_1" - the whole point of the trailing "*" before ".jpg".
		Long epochMillis = pattern.parseEpochMillis("/somepath/cameras/1/2026/08/02/20260802_121400_camera7.jpg");

		OffsetDateTime expected = OffsetDateTime.of(2026, 8, 2, 12, 14, 0, 0, ZoneOffset.UTC);
		assertEquals(expected.toInstant().toEpochMilli(), epochMillis);
	}

	@Test
	void pathsFromADifferentCameraRootDoNotMatch() {
		TimestampPattern pattern = PathTemplate.compile(USER_TEMPLATE, ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);

		assertNull(pattern.parseEpochMillis("/somepath/cameras/2/2026/08/02/20260802_121400_1.jpg"));
	}

	@Test
	void literalDotsInTheTemplateAreNotTreatedAsRegexWildcards() {
		// "." in ".jpg" must be escaped - otherwise it would spuriously match "Xjpg" too.
		String regex = PathTemplate.toRegex(USER_TEMPLATE);

		TimestampPattern pattern = new TimestampPattern(regex, ZoneOffset.UTC, DstAmbiguousPolicy.ASSUME_STANDARD);
		assertNull(pattern.parseEpochMillis("/somepath/cameras/1/2026/08/02/20260802_121400_1Xjpg"));
	}

	@Test
	void rootDirectoryStopsAtTheDoubleStar() {
		assertEquals("/somepath/cameras/1/", PathTemplate.rootDirectory(USER_TEMPLATE));
	}

	@Test
	void rootDirectoryStopsAtASingleStarWhenThereIsNoDoubleStar() {
		assertEquals("/somepath/cameras/1/", PathTemplate.rootDirectory("/somepath/cameras/1/latest_*.jpg"));
	}

	@Test
	void rootDirectoryStopsAtADateTokenWhenThereIsNoWildcardAtAll() {
		assertEquals("/somepath/cameras/1/", PathTemplate.rootDirectory("/somepath/cameras/1/YYYYmmdd_HHMMSS.jpg"));
	}
}
