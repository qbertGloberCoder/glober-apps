package me.qbert.globewrapping.cli;

/** A user-facing CLI usage error (bad arguments, missing files referenced by flags, etc.). */
public class CliUsageException extends RuntimeException {

    public CliUsageException(String message) {
        super(message);
    }
}
