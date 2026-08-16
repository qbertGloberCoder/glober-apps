package me.qbert.skywatch.camera.cli;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.concurrent.CountDownLatch;

import javax.swing.SwingUtilities;

import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.GlobalSettings;
import me.qbert.skywatch.camera.config.GlobalSettingsStore;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.ui.AppController;
import me.qbert.skywatch.camera.ui.ControlPanel;

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

// "java -jar app.jar" with no subcommand at all - the primary way this app is meant to be launched
// (direct user instruction): full interactive mode, no camera selected, the control panel opens
// showing whatever's already in the camera library so the user can add/switch cameras from there
// instead of relaunching with a fresh set of flags each time.
//
// Same buildController(...)/run(...) split, headless guard, and "run(...) blocks until the window
// closes" shape as PreviewCommand/CalibrateCommand, for the identical reasons - see PreviewCommand's
// own class comment for why the blocking matters (cli.Main calls System.exit(...) the instant
// run(...) returns).
final class AppCommand {
	static final String USAGE = "Usage: (no subcommand) [--library-dir <dir>] [--cache-dir <dir>] "
			+ "[--cache-scan-limit <n>] [--cache-refresh-interval-seconds <n>] [--settings-file <path>] "
			+ "[--stars main|named|all|visible] [--min-radius <px>] "
			+ CameraConfigArgs.RENDER_FLAGS_USAGE + "  (requires a display)";

	int run(String[] args) throws Exception {
		if (GraphicsEnvironment.isHeadless())
			throw new CliUsageException("interactive app mode requires a display - this environment is headless");

		AppController controller = buildController(args);
		CountDownLatch windowClosed = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			ControlPanel panel = new ControlPanel(controller);
			panel.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					windowClosed.countDown();
				}
			});
			panel.setVisible(true);
		});

		windowClosed.await();
		return 0;
	}

	AppController buildController(String[] args) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		CameraLibrary library = new CameraLibrary(CameraConfigArgs.libraryDirectory(scanner));

		File cacheDir = CameraConfigArgs.cacheDirectory(scanner);
		DirectoryCache cache = new DirectoryCache(cacheDir, CameraConfigArgs.cacheScanLimit(scanner));

		FrameCompositor.Options options = CameraConfigArgs.buildOptions(scanner, WallClock.SYSTEM);
		SimulatedClock clock = new SimulatedClock();

		File settingsFile = CameraConfigArgs.globalSettingsPath(scanner);
		GlobalSettings settings = GlobalSettingsStore.loadOrDefault(settingsFile);

		return new AppController(library, cache, options, clock, CameraConfigArgs.cacheRefreshIntervalMillis(scanner),
				settings, settingsFile);
	}
}
