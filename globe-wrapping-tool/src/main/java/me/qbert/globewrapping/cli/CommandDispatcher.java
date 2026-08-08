package me.qbert.globewrapping.cli;

import java.io.IOException;
import java.util.Arrays;

/** Dispatches to one of the three independent subcommands (globe-unwrapper-requirements.md section 7). */
final class CommandDispatcher {

    private static final String USAGE = """
        Usage:
          unwrap <output.png> <alias1> <path1> [<alias2> <path2> ...] [--config <path>] [--override <alias>.<field>=<value> ...]
          combine <basemap.jpg|none> <input.png> <output.jpg>
          wrap <input.png> center <lat,lon> [height <km>] size <WxH> <output.jpg>""";

    int run(String[] args) throws IOException {
        if (args.length == 0) {
            throw new CliUsageException(USAGE);
        }

        String subcommand = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        return switch (subcommand) {
            case "unwrap" -> new UnwrapCommand().run(rest);
            case "combine" -> new CombineCommand().run(rest);
            case "wrap" -> new WrapCommand().run(rest);
            default -> throw new CliUsageException("Unknown subcommand '" + subcommand + "'.\n" + USAGE);
        };
    }
}
