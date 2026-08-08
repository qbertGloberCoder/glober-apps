package me.qbert.globewrapping.cli;

import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.geometry.ObserverParameters;
import me.qbert.globewrapping.geometry.PerspectiveObserverProjection;
import me.qbert.globewrapping.pipeline.WrapStage;

/**
 * {@code wrap <input.png> center <lat,lon> [height <km>] size <WxH> <output.jpg>}
 * (globe-unwrapper-requirements.md section 7). {@code center}/{@code height}/
 * {@code size} are literal keyword tokens (not {@code --flag}-style options,
 * so not parsed through {@link ArgScanner}), scanned for by name rather than
 * fixed position so {@code height} can be omitted — real satellite altitudes
 * aren't something a user should have to remember/retype every time. Omitting
 * it defaults to {@link PerspectiveObserverProjection#REFERENCE_ALTITUDE_KM}
 * (real GEO, 35786 km — the same altitude the default lens is calibrated
 * against, see that class's javadoc and requirements section 6.1).
 */
final class WrapCommand {

    private static final String USAGE =
        "Usage: wrap <input.png> center <lat,lon> [height <km>] size <WxH> <output.jpg>";

    int run(String[] args) throws IOException {
        if (args.length < 6) {
            throw new CliUsageException(USAGE);
        }

        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[args.length - 1]);

        String centerValue = null;
        String heightValue = null;
        String sizeValue = null;

        int middleCount = args.length - 2;
        if (middleCount % 2 != 0) {
            throw new CliUsageException(USAGE);
        }
        for (int i = 1; i < args.length - 1; i += 2) {
            String keyword = args[i];
            String value = args[i + 1];
            switch (keyword) {
                case "center" -> centerValue = value;
                case "height" -> heightValue = value;
                case "size" -> sizeValue = value;
                default -> throw new CliUsageException(USAGE);
            }
        }

        if (centerValue == null || sizeValue == null) {
            throw new CliUsageException(USAGE);
        }

        double heightKm = heightValue != null
            ? parseHeight(heightValue)
            : PerspectiveObserverProjection.REFERENCE_ALTITUDE_KM;

        ObserverParameters observer = parseObserver(centerValue, heightKm);
        int[] size = parseSize(sizeValue);

        new WrapStage().run(inputPath, observer, size[0], size[1], outputPath);
        System.out.println("Wrote " + outputPath);
        return 0;
    }

    private static ObserverParameters parseObserver(String latLon, double heightKm) {
        String[] parts = latLon.split(",", 2);
        if (parts.length != 2) {
            throw new CliUsageException("Invalid center '<lat,lon>': " + latLon);
        }
        try {
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            return new ObserverParameters(lat, lon, heightKm);
        } catch (NumberFormatException e) {
            throw new CliUsageException("Invalid numeric value in 'center': " + e.getMessage());
        }
    }

    private static double parseHeight(String heightText) {
        try {
            return Double.parseDouble(heightText);
        } catch (NumberFormatException e) {
            throw new CliUsageException("Invalid height value: " + heightText);
        }
    }

    private static int[] parseSize(String sizeText) {
        String[] parts = sizeText.split("x", 2);
        if (parts.length != 2) {
            throw new CliUsageException("Invalid size '<WxH>': " + sizeText);
        }
        try {
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            throw new CliUsageException("Invalid size '<WxH>': " + sizeText);
        }
    }
}
