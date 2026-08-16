package me.qbert.skywatch.camera.source;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

// spec §10.2: a regex-with-named-groups pattern describing where a camera's source files live and
// how to extract a capture timestamp, plus the timezone and DST-ambiguity policy needed to resolve
// it to an actual instant. Required named groups: yyyy, mm, dd, hh, min, ss - e.g. for the user's
// own prototype's "20260808_000000.jpg" filename convention:
// "(?<yyyy>\\d{4})(?<mm>\\d{2})(?<dd>\\d{2})_(?<hh>\\d{2})(?<min>\\d{2})(?<ss>\\d{2})\\.jpg"
public final class TimestampPattern {
	private final Pattern pattern;
	private final ZoneId timezone;
	private final DstAmbiguousPolicy dstAmbiguousPolicy;

	public TimestampPattern(String regex, ZoneId timezone, DstAmbiguousPolicy dstAmbiguousPolicy) {
		if (timezone == null)
			throw new IllegalArgumentException("timezone must not be null");
		if (dstAmbiguousPolicy == null)
			throw new IllegalArgumentException("dstAmbiguousPolicy must not be null");

		this.pattern = Pattern.compile(regex);
		this.timezone = timezone;
		this.dstAmbiguousPolicy = dstAmbiguousPolicy;
	}

	// Null if path doesn't match this pattern at all - matching spec §10.2's per-camera-root
	// scanning model, where a source root can hold files that don't belong to this camera.
	public Long parseEpochMillis(String path) {
		Matcher matcher = pattern.matcher(path);
		if (!matcher.find())
			return null;

		int year = Integer.parseInt(matcher.group("yyyy"));
		int month = Integer.parseInt(matcher.group("mm"));
		int day = Integer.parseInt(matcher.group("dd"));
		int hour = Integer.parseInt(matcher.group("hh"));
		int minute = Integer.parseInt(matcher.group("min"));
		int second = Integer.parseInt(matcher.group("ss"));

		LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute, second);
		ZoneOffset offset = resolveOffset(localDateTime);

		return localDateTime.atOffset(offset).toInstant().toEpochMilli();
	}

	private ZoneOffset resolveOffset(LocalDateTime localDateTime) {
		ZoneRules rules = timezone.getRules();
		List<ZoneOffset> validOffsets = rules.getValidOffsets(localDateTime);

		if (validOffsets.size() == 1)
			return validOffsets.get(0);

		if (validOffsets.isEmpty())
			throw new DateTimeException("local time " + localDateTime + " does not exist in zone "
					+ timezone + " (spring-forward gap) - not handled by spec §10.2, which only "
					+ "covers the fall-back ambiguous case");

		// Fall-back overlap: two valid offsets. Determine which is standard vs. daylight by
		// checking each candidate instant directly, rather than assuming list ordering.
		for (ZoneOffset offset : validOffsets) {
			Instant instant = localDateTime.atOffset(offset).toInstant();
			boolean isDaylight = rules.isDaylightSavings(instant);

			if (dstAmbiguousPolicy == DstAmbiguousPolicy.ASSUME_DAYLIGHT && isDaylight)
				return offset;
			if (dstAmbiguousPolicy == DstAmbiguousPolicy.ASSUME_STANDARD && !isDaylight)
				return offset;
		}

		// Every real overlap has exactly one daylight and one standard offset, so the loop above
		// always returns - this is just a defensive fallback, not expected to be hit.
		return validOffsets.get(0);
	}
}
