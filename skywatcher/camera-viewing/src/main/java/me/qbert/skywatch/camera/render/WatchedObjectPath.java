package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.TimeZone;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

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

// Layer 2's "watched-object path" mode [spec §6] - "plots the chosen object's position over a
// trailing window (default: past 24h)". Distinct from the crosshair (render.WatchedObjectCrosshair,
// a single marker at ONE other reference time) and from the ecliptic/analemma path
// (render.EclipticAnalemmaPath, daily samples over a year/month using sw-base's own precession
// engine, now painted in the objects sub-layer instead of here - see CLAUDE.md's "Ecliptic/analemma
// path moved into the objects sub-layer" section for why this mode stayed in Layer 2 while that one
// moved) - this is a short, fine-grained trailing trajectory of whichever object is currently being
// watched, ending at the frame's own live render time.
//
// Unlike EclipticAnalemmaPath (which delegates the actual sampling loop to sw-base's
// AbstractPrecession), there is no existing sw-base primitive for "sample an arbitrary object across
// an arbitrary short trailing window" - watch.WatchedObject.resolveAltAz(time, location) already
// does the per-sample work (see its own class comment: built specifically to be resolved at many
// different times for exactly this kind of use), so the sampling loop here is genuinely this
// class's own, thin as it is. Reuses Graticule.drawSegmentIfPlausible(...) for the same
// "connect two projected points, skip an implausible jump" rule as every other broken-line path in
// this module, rather than a third copy of it.
public final class WatchedObjectPath {
	private WatchedObjectPath() {
	}

	public static final long DEFAULT_TRAILING_WINDOW_MILLIS = 24L * 60 * 60 * 1000; // spec §6's own stated default
	public static final long DEFAULT_SAMPLE_INTERVAL_MILLIS = 10L * 60 * 1000; // 10 minutes - 144 samples/24h

	public static void paint(Graphics2D g2d, WatchedObject watchedObject, ObservationTime currentTime,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		paint(g2d, watchedObject, currentTime, location, projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels, DEFAULT_TRAILING_WINDOW_MILLIS, DEFAULT_SAMPLE_INTERVAL_MILLIS);
	}

	public static void paint(Graphics2D g2d, WatchedObject watchedObject, ObservationTime currentTime,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels, long trailingWindowMillis, long sampleIntervalMillis)
			throws Exception {
		if (watchedObject == null)
			throw new IllegalArgumentException("watchedObject must not be null");
		if (currentTime == null)
			throw new IllegalArgumentException("currentTime must not be null");
		if (trailingWindowMillis <= 0)
			throw new IllegalArgumentException("trailingWindowMillis must be positive");
		if (sampleIntervalMillis <= 0)
			throw new IllegalArgumentException("sampleIntervalMillis must be positive");

		long endMillis = currentTime.getTime().getTimeInMillis();
		long startMillis = endMillis - trailingWindowMillis;

		// A private sampler, deliberately not the caller's own currentTime - resolving the same
		// object at many different past moments means repeatedly changing "what time is it", which
		// must not mutate (or fire change notifications from) the live ObservationTime the rest of
		// the frame is rendering against.
		ObservationTime sampler = new ObservationTime();
		sampler.initTime(TimeZone.getTimeZone("UTC"));

		Point2D.Double previous = null;
		for (long sampleMillis = startMillis; sampleMillis <= endMillis; sampleMillis += sampleIntervalMillis) {
			sampler.setUnixTime(sampleMillis);
			ObjectDirectionAltAz altAz = watchedObject.resolveAltAz(sampler, location);
			Point2D.Double current = CameraProjector.projectToPixels(projection, cameraOrientation, altAz.getAltitude(),
					altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
			Graticule.drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}
}
