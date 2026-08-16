package me.qbert.skywatch.camera.source;

import java.time.ZoneId;

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

// Compiles the user-facing archive path template syntax [spec §10.2, Real cameras only - Virtual
// cameras have a single static/cached-crop image, never a directory to describe] down to a
// TimestampPattern regex, rather than making the user hand-write named-group regex directly. The
// user's own worked example:
//   /somepath/cameras/1/2026/08/02/20260802_121400_1.jpg
//   -> /somepath/cameras/1/**/YYYYmmdd_HHMMSS*.jpg
//
// Token grammar:
//  - "**" - arbitrary directory depth, including zero (kept as one script's worth of subdirectory
//    housekeeping, per the user's own daily-rollover example - "the subdirectory path is not
//    strictly important, hence the '**'"). Ignored for parsing, never captures anything.
//  - "*"  - a single-segment wildcard (matches any run of non-'/' characters), e.g. the trailing
//    "*" in "HHMMSS*.jpg" for a per-camera filename suffix like "_1".
//  - Date/time tokens, matching the user's own stated convention exactly - note "mm" (lowercase,
//    month) vs. "MM" (uppercase, minute) is the OPPOSITE of Java's SimpleDateFormat convention,
//    intentionally kept as the user specified it rather than "corrected":
//    YYYY=year, mm=month, dd=day, HH=hour, MM=minute, SS=second.
//  - Anything else is a literal, regex-escaped as needed.
public final class PathTemplate {
	private PathTemplate() {
	}

	public static TimestampPattern compile(String template, ZoneId timezone, DstAmbiguousPolicy dstAmbiguousPolicy) {
		return new TimestampPattern(toRegex(template), timezone, dstAmbiguousPolicy);
	}

	// The directory a tree walk (e.g. source.ArchiveFrameScanner, backed by DirectoryCache) should
	// start from: the template's literal prefix up to the first token/wildcard, trimmed back to the
	// last complete path separator so scanning starts from a real directory, never a partial
	// filename fragment. For the user's own worked example
	// ("/somepath/cameras/1/**/YYYYmmdd_HHMMSS*.jpg") this is "/somepath/cameras/1/".
	public static String rootDirectory(String template) {
		if (template == null)
			throw new IllegalArgumentException("template must not be null");

		int i = 0;
		int length = template.length();
		while (i < length && !isTokenStart(template, i))
			i++;

		String prefix = template.substring(0, i);
		int lastSlash = prefix.lastIndexOf('/');
		return lastSlash >= 0 ? prefix.substring(0, lastSlash + 1) : prefix;
	}

	private static boolean isTokenStart(String template, int i) {
		return template.startsWith("**", i) || template.charAt(i) == '*'
				|| template.startsWith("YYYY", i) || template.startsWith("mm", i)
				|| template.startsWith("dd", i) || template.startsWith("HH", i)
				|| template.startsWith("MM", i) || template.startsWith("SS", i);
	}

	// Package-visible for direct unit testing of the token grammar, independent of TimestampPattern.
	static String toRegex(String template) {
		if (template == null)
			throw new IllegalArgumentException("template must not be null");

		StringBuilder regex = new StringBuilder();
		int i = 0;
		int length = template.length();

		while (i < length) {
			if (template.startsWith("/**/", i)) {
				regex.append("/(?:.*/)?");
				i += 4;
			} else if (template.startsWith("YYYY", i)) {
				regex.append("(?<yyyy>\\d{4})");
				i += 4;
			} else if (template.startsWith("mm", i)) {
				regex.append("(?<mm>\\d{2})");
				i += 2;
			} else if (template.startsWith("dd", i)) {
				regex.append("(?<dd>\\d{2})");
				i += 2;
			} else if (template.startsWith("HH", i)) {
				regex.append("(?<hh>\\d{2})");
				i += 2;
			} else if (template.startsWith("MM", i)) {
				regex.append("(?<min>\\d{2})");
				i += 2;
			} else if (template.startsWith("SS", i)) {
				regex.append("(?<ss>\\d{2})");
				i += 2;
			} else if (template.startsWith("**", i)) {
				// Arbitrary depth without a template-supplied bounding "/" on both sides (e.g. at
				// the very start/end of the template) - ".*" alone still covers any depth.
				regex.append(".*");
				i += 2;
			} else if (template.charAt(i) == '*') {
				regex.append("[^/]*");
				i += 1;
			} else {
				char c = template.charAt(i);
				if ("\\.^$|?*+()[]{}".indexOf(c) >= 0)
					regex.append('\\');
				regex.append(c);
				i += 1;
			}
		}

		return regex.toString();
	}
}
