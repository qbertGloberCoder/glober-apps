package me.qbert.globewrapping.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal hand-rolled CLI token scanner: splits {@code --flag value} /
 * {@code --flag=value} options out from positional arguments, in any order.
 * No external CLI-parsing dependency, in keeping with
 * globe-unwrapper-requirements.md section 8's "single runnable jar, no
 * required external tools" spirit and the small surface this tool actually
 * needs.
 */
final class ArgScanner {

    record Option(String name, String value) {
    }

    private final List<String> positionals = new ArrayList<>();
    private final List<Option> options = new ArrayList<>();

    ArgScanner(String[] args) {
        int i = 0;
        while (i < args.length) {
            String token = args[i];
            if (token.startsWith("--")) {
                String name = token.substring(2);
                String value;
                int eq = name.indexOf('=');
                if (eq >= 0) {
                    value = name.substring(eq + 1);
                    name = name.substring(0, eq);
                    i++;
                } else {
                    if (i + 1 >= args.length) {
                        throw new CliUsageException("Missing value for option --" + name);
                    }
                    value = args[i + 1];
                    i += 2;
                }
                options.add(new Option(name, value));
            } else {
                positionals.add(token);
                i++;
            }
        }
    }

    List<String> positionals() {
        return positionals;
    }

    List<Option> options(String name) {
        return options.stream().filter(o -> o.name().equals(name)).toList();
    }

    Optional<String> option(String name) {
        return options.stream().filter(o -> o.name().equals(name)).map(Option::value).findFirst();
    }
}
