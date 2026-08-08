package me.qbert.globewrapping.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.qbert.globewrapping.calibration.CalibrationOverrides;
import me.qbert.globewrapping.calibration.CalibrationRegistry;
import me.qbert.globewrapping.calibration.SourceCalibration;
import me.qbert.globewrapping.pipeline.UnwrapStage;

/**
 * {@code unwrap <output.png> <alias1> <path1> [<alias2> <path2> ...]}
 * (globe-unwrapper-requirements.md section 7), plus {@code --config <path>}
 * and repeatable {@code --override <alias>.<field>=<value>} (see
 * {@link CliConfig}).
 */
final class UnwrapCommand {

    private static final String USAGE = """
        Usage: unwrap <output.png> <alias1> <path1> [<alias2> <path2> ...]
                       [--config <path>] [--override <alias>.<field>=<value> ...]""";

    int run(String[] args) throws IOException {
        ArgScanner scanner = new ArgScanner(args);
        List<String> positionals = scanner.positionals();

        if (positionals.size() < 3 || positionals.size() % 2 != 1) {
            throw new CliUsageException(USAGE);
        }

        Path outputPath = Path.of(positionals.get(0));

        CalibrationRegistry registry = CliConfig.loadRegistry(scanner.option("config").orElse(null));
        Map<String, CalibrationOverrides> overridesByAlias = CliConfig.parseOverrides(scanner.options("override"));

        List<UnwrapStage.SourceInput> sources = new ArrayList<>();
        for (int i = 1; i < positionals.size(); i += 2) {
            String alias = positionals.get(i);
            Path imagePath = Path.of(positionals.get(i + 1));
            CalibrationOverrides overrides = overridesByAlias.getOrDefault(alias, CalibrationOverrides.none());
            SourceCalibration calibration;
            try {
                calibration = registry.resolve(alias, overrides);
            } catch (IllegalArgumentException e) {
                throw new CliUsageException(e.getMessage()
                    + " (known aliases: " + registry.all().keySet() + ")");
            }
            sources.add(new UnwrapStage.SourceInput(calibration, imagePath));
        }

        new UnwrapStage().run(sources, outputPath);
        System.out.println("Wrote " + outputPath);
        return 0;
    }
}
