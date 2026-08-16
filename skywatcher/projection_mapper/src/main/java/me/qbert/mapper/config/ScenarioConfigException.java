package me.qbert.mapper.config;

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

// A malformed or incomplete scenario config - a missing required key, an unresolvable pinid
// reference, a bad number/color/date - as opposed to a plain IOException (a real filesystem
// failure). This file format is explicitly meant to be hand-edited, so naming exactly which
// file/key/row is wrong matters more here than for most config-loading code.
public final class ScenarioConfigException extends IOException {
	private static final long serialVersionUID = 1L;

	public ScenarioConfigException(String message) {
		super(message);
	}

	public ScenarioConfigException(String message, Throwable cause) {
		super(message, cause);
	}
}
