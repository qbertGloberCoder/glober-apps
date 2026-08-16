package me.qbert.skywatch.camera.cli;

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

// A user-facing input error - bad/missing arguments, unknown subcommand, etc. Ported from
// globe-wrapping-tool's cli.CliUsageException (see that module's cli/ package) for consistency
// across this repo's CLI tools, per task 6.5's own instruction.
final class CliUsageException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	CliUsageException(String message) {
		super(message);
	}
}
