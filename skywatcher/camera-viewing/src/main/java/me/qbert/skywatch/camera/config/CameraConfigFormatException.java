package me.qbert.skywatch.camera.config;

import java.io.IOException;

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

// A malformed or incomplete config file - a missing required key, an unrecognized enum value, a
// gap in an index-prefixed list, etc. Distinguished from a plain IOException (a real filesystem
// failure) since this file format is explicitly meant to be hand-editable (see CLAUDE.md's "Image
// sources"), so a clear, specific complaint about *what's wrong with the file's content* matters
// more here than for most config-loading code.
public final class CameraConfigFormatException extends IOException {
	private static final long serialVersionUID = 1L;

	public CameraConfigFormatException(String message) {
		super(message);
	}

	public CameraConfigFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
