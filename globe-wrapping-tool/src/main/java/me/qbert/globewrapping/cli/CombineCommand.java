package me.qbert.globewrapping.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.qbert.globewrapping.pipeline.CombineStage;

/** {@code combine <basemap.jpg|none> <input.png> <output.jpg>} (globe-unwrapper-requirements.md section 7). */
final class CombineCommand {

    private static final String USAGE = "Usage: combine <basemap.jpg|none> <input.png> <output.jpg>";

    int run(String[] args) throws IOException {
        ArgScanner scanner = new ArgScanner(args);
        List<String> positionals = scanner.positionals();
        if (positionals.size() != 3) {
            throw new CliUsageException(USAGE);
        }

        String basemapArg = positionals.get(0);
        Path basemapPath = "none".equalsIgnoreCase(basemapArg) ? null : Path.of(basemapArg);
        Path inputPath = Path.of(positionals.get(1));
        Path outputPath = Path.of(positionals.get(2));

        new CombineStage().run(inputPath, basemapPath, outputPath);
        System.out.println("Wrote " + outputPath);
        return 0;
    }
}
