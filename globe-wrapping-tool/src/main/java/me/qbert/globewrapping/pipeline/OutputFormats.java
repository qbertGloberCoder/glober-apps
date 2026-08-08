package me.qbert.globewrapping.pipeline;

import java.nio.file.Path;

/** Infers an {@code ImageIO} format name from an output path's file extension. */
final class OutputFormats {

    private OutputFormats() {
    }

    static String inferFrom(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException(
                "Output path has no file extension to infer an image format from: " + path);
        }
        return name.substring(dot + 1);
    }
}
