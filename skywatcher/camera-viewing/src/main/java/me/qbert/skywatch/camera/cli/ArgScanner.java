package me.qbert.skywatch.camera.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

// Hand-rolled CLI argument scanner - no external CLI-parsing dependency, matching this repo's
// existing convention (per task 6.5's own instruction: consistent with globe-wrapping-tool's
// cli.ArgScanner). Ported to this module's Java 8 target rather than copied verbatim - the original
// uses records/text blocks/switch expressions (Java 17+, matching that module's own toolchain),
// none of which are available here.
//
// Splits raw args into positionals (anything not starting with "--") and named options (anything
// starting with "--"), supporting both "--flag=value" and "--flag value" (space-separated) forms.
// No short-flag ("-f") support, matching the original. Repeated flags are allowed - options(name)
// returns every value in order; option(name) returns the first.
final class ArgScanner {
	private final List<String> positionals = new ArrayList<String>();
	private final List<String[]> options = new ArrayList<String[]>();

	ArgScanner(String[] args) {
		int i = 0;
		while (i < args.length) {
			String arg = args[i];
			if (arg.startsWith("--")) {
				String name = arg.substring(2);
				int equalsIndex = name.indexOf('=');
				if (equalsIndex >= 0) {
					options.add(new String[] { name.substring(0, equalsIndex), name.substring(equalsIndex + 1) });
					i++;
				} else {
					if (i + 1 >= args.length)
						throw new CliUsageException("missing value for --" + name);
					options.add(new String[] { name, args[i + 1] });
					i += 2;
				}
			} else {
				positionals.add(arg);
				i++;
			}
		}
	}

	List<String> positionals() {
		return positionals;
	}

	Optional<String> option(String name) {
		for (String[] option : options)
			if (option[0].equals(name))
				return Optional.of(option[1]);
		return Optional.empty();
	}

	List<String> options(String name) {
		List<String> values = new ArrayList<String>();
		for (String[] option : options)
			if (option[0].equals(name))
				values.add(option[1]);
		return values;
	}

	String requireOption(String name) {
		Optional<String> value = option(name);
		if (!value.isPresent())
			throw new CliUsageException("missing required --" + name);
		return value.get();
	}

	double requireDoubleOption(String name) {
		return parseDouble(name, requireOption(name));
	}

	double doubleOption(String name, double defaultValue) {
		Optional<String> value = option(name);
		return value.isPresent() ? parseDouble(name, value.get()) : defaultValue;
	}

	// No bare "--flag" (presence-only) support - the constructor above always requires a value for
	// every "--name", so a boolean toggle is spelled "--flag true"/"--flag=false" rather than a
	// naked "--flag", matching every other typed option() on this class rather than being a special
	// case.
	boolean booleanOption(String name, boolean defaultValue) {
		Optional<String> value = option(name);
		if (!value.isPresent())
			return defaultValue;
		if ("true".equalsIgnoreCase(value.get()))
			return true;
		if ("false".equalsIgnoreCase(value.get()))
			return false;
		throw new CliUsageException("--" + name + " must be true or false, got \"" + value.get() + "\"");
	}

	int intOption(String name, int defaultValue) {
		Optional<String> value = option(name);
		if (!value.isPresent())
			return defaultValue;
		try {
			return Integer.parseInt(value.get());
		} catch (NumberFormatException e) {
			throw new CliUsageException("--" + name + " must be an integer, got \"" + value.get() + "\"");
		}
	}

	private double parseDouble(String name, String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new CliUsageException("--" + name + " must be a number, got \"" + value + "\"");
		}
	}
}
