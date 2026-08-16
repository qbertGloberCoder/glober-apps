package me.qbert.skywatch.camera.batch;

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

// Holds the three independent Layer-1 memoization slots (see LastImageCache) - one per distinct
// thing that gets cached, each with its own key shape, so they must not share a slot. Held as a
// single long-lived, per-camera field on ui.PreviewController (constructed once, naturally fresh
// per camera since a new PreviewController is built per AppController.switchToCamera(...) - no
// explicit invalidation-on-switch logic needed, matching how source.ArchiveFrameCache already
// behaves), and threaded through as one new nullable parameter into CameraImageDispatch.
// composite(...)/RealCameraScrubber.composite(...) via the same overload idiom already used for
// archiveFrameCache/globalSettings.
public final class CameraImageCaches {
	private final LastImageCache realFrameImage = new LastImageCache();
	private final LastImageCache virtualSceneSource = new LastImageCache();
	private final LastImageCache ptzRenderedOutput = new LastImageCache();

	// Real cameras only - keyed on the resolved archived Frame's own File (real path-based equals())
	// by RealCameraScrubber, so scrubbing within the same archived frame's time window skips a
	// redundant ImageIO.read.
	LastImageCache realFrameImage() {
		return realFrameImage;
	}

	// Virtual cameras only (Fixed and PTZ alike) - keyed on the configured scene file's own path by
	// CameraImageDispatch, so a scene path that essentially never changes between calls skips a
	// redundant ImageIO.read every render.
	LastImageCache virtualSceneSource() {
		return virtualSceneSource;
	}

	// PTZ Virtual cameras only - keyed on scene path + orientation + projection (by reference
	// identity) + canvas size, so EquirectangularSceneRenderer's O(width*height) per-pixel render is
	// skipped entirely whenever none of those have changed since the last call - the fix that
	// actually matters for PTZ performance during pure time-scrubbing, not just the file re-read.
	LastImageCache ptzRenderedOutput() {
		return ptzRenderedOutput;
	}
}
