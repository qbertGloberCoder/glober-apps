package me.qbert.skywatch.camera.config;

import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.camera.source.PathTemplate;
import me.qbert.skywatch.camera.source.TimestampPattern;

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

// Real cameras only [spec §10.1/§10.2, CLAUDE.md's "Image sources"] - Virtual cameras have a single
// static/cached-crop scene image (CameraConfig.getVirtualScenePath()), no directory, no pattern, no
// timezone concern at all.
//
// "Latest" (live) source: a plain, non-templated, always-overwritten-in-place static file path -
// present only for Live+recorded cameras (RealCaptureMode). Pre-recorded-only cameras have no live
// source at all, archive only - see the two factory methods.
//
// Archive source: the user-facing PathTemplate syntax (see PathTemplate), compiled to a
// TimestampPattern on demand rather than at construction, so that later edits to the timezone/DST
// policy (mutable - see TimezoneSetting) are picked up without rebuilding this object.
public final class RealImageSource {
	private final String latestPath;
	private final String archiveTemplate;
	private TimezoneSetting timezone;
	private DstAmbiguousPolicy dstAmbiguousPolicy;

	private RealImageSource(String latestPath, String archiveTemplate, TimezoneSetting timezone,
			DstAmbiguousPolicy dstAmbiguousPolicy) {
		if (archiveTemplate == null || archiveTemplate.isEmpty())
			throw new IllegalArgumentException("archiveTemplate must not be empty");
		if (timezone == null)
			throw new IllegalArgumentException("timezone must not be null");
		if (dstAmbiguousPolicy == null)
			throw new IllegalArgumentException("dstAmbiguousPolicy must not be null");

		this.latestPath = latestPath;
		this.archiveTemplate = archiveTemplate;
		this.timezone = timezone;
		this.dstAmbiguousPolicy = dstAmbiguousPolicy;
	}

	public static RealImageSource liveAndRecorded(String latestPath, String archiveTemplate,
			TimezoneSetting timezone, DstAmbiguousPolicy dstAmbiguousPolicy) {
		if (latestPath == null || latestPath.isEmpty())
			throw new IllegalArgumentException("latestPath must not be empty for a live+recorded source");
		return new RealImageSource(latestPath, archiveTemplate, timezone, dstAmbiguousPolicy);
	}

	public static RealImageSource preRecordedOnly(String archiveTemplate, TimezoneSetting timezone,
			DstAmbiguousPolicy dstAmbiguousPolicy) {
		return new RealImageSource(null, archiveTemplate, timezone, dstAmbiguousPolicy);
	}

	public boolean hasLatestSource() {
		return latestPath != null;
	}

	public String getLatestPath() {
		if (latestPath == null)
			throw new IllegalStateException("pre-recorded-only sources have no latest path");
		return latestPath;
	}

	public String getArchiveTemplate() {
		return archiveTemplate;
	}

	public TimezoneSetting getTimezone() {
		return timezone;
	}

	public void setTimezone(TimezoneSetting timezone) {
		if (timezone == null)
			throw new IllegalArgumentException("timezone must not be null");
		this.timezone = timezone;
	}

	public DstAmbiguousPolicy getDstAmbiguousPolicy() {
		return dstAmbiguousPolicy;
	}

	public void setDstAmbiguousPolicy(DstAmbiguousPolicy dstAmbiguousPolicy) {
		if (dstAmbiguousPolicy == null)
			throw new IllegalArgumentException("dstAmbiguousPolicy must not be null");
		this.dstAmbiguousPolicy = dstAmbiguousPolicy;
	}

	public TimestampPattern compileArchivePattern() {
		return PathTemplate.compile(archiveTemplate, timezone.resolve(), dstAmbiguousPolicy);
	}
}
