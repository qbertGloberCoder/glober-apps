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

// Thin entry point - all real logic lives in CommandDispatcher and below, so it's testable without
// exercising System.exit(...). Matches globe-wrapping-tool's cli.Main shape.
public final class Main {
	private Main() {
	}

	public static void main(String[] args) {
		try {
			int exitCode = new CommandDispatcher().run(args);
			System.exit(exitCode);
		} catch (CliUsageException e) {
			System.err.println(e.getMessage());
			System.exit(2);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
		}
	}
}
