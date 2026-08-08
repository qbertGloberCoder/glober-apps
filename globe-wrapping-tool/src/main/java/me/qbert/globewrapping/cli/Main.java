package me.qbert.globewrapping.cli;

/** Entry point. See {@link CommandDispatcher} for the three subcommands. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            int exitCode = new CommandDispatcher().run(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (CliUsageException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
