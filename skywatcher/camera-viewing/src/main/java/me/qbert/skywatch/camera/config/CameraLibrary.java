package me.qbert.skywatch.camera.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

// A named multi-camera "library" - the primary way a user is meant to work with this app (per
// direct user instruction): a directory of saved camera profiles, browsable/switchable from the
// control panel, instead of encoding a whole camera as CLI flags on every launch. Deliberately NOT
// a new file format - each camera is just an ordinary CameraConfigStore-managed .properties file,
// named `<camera name>.properties`; this class only adds directory-level list/lookup/remove on top
// of that already-built, already-tested round trip. `--camera <name>` on the CLI resolves through
// this; the control panel's Add/Edit/Remove/Open actions are a direct UI over the same three
// operations.
public final class CameraLibrary {
	private static final String EXTENSION = ".properties";

	private final File directory;

	public CameraLibrary(File directory) {
		if (directory == null)
			throw new IllegalArgumentException("directory must not be null");
		directory.mkdirs();
		this.directory = directory;
	}

	public File getDirectory() {
		return directory;
	}

	// Sorted for a stable, predictable order in any UI listing these (a camera dropdown that
	// reorders itself between opens would be confusing) - filesystem directory listing order is
	// otherwise unspecified.
	public List<String> listCameraNames() {
		String[] files = directory.list((dir, name) -> name.endsWith(EXTENSION));
		List<String> names = new ArrayList<String>();
		if (files != null)
			for (String file : files)
				names.add(file.substring(0, file.length() - EXTENSION.length()));
		Collections.sort(names);
		return names;
	}

	public boolean contains(String name) {
		return fileFor(name).isFile();
	}

	public CameraConfig load(String name) throws IOException {
		File file = fileFor(name);
		if (!file.isFile())
			throw new IOException("no camera named \"" + name + "\" in library " + directory);
		return CameraConfigStore.load(file);
	}

	public void save(String name, CameraConfig config) throws IOException {
		if (config == null)
			throw new IllegalArgumentException("config must not be null");
		CameraConfigStore.save(config, fileFor(name));
	}

	// A no-op if the camera doesn't exist - matches java.io.File.delete()'s own quiet-on-nothing-
	// to-delete behavior rather than treating "already gone" as an error.
	public void remove(String name) {
		fileFor(name).delete();
	}

	// Exposed directly (not just an implementation detail) - plate.PlateSolveSession's constructor
	// needs the exact backing file to Save/Revert against, so callers that open a camera for
	// calibration need this, not just the loaded CameraConfig.
	public File fileFor(String name) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("name must not be empty");
		return new File(directory, name + EXTENSION);
	}
}
