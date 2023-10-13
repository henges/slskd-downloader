package dev.polluxus.slskd_downloader.util;

import org.beryx.textio.TextTerminal;

import java.io.PrintStream;

public class OutputMultiplexer {

    private final TextTerminal<?> console;

    private final PrintStream out;

    private OutputMultiplexer(TextTerminal<?> console, PrintStream out) {
        this.console = console;
        this.out = out;
    }

    public static OutputMultiplexer withStdin(TextTerminal<?> console) {
        return new OutputMultiplexer(console, System.out);
    }

    public void printf(String format, Object... args) {

        console.printf(format, args);
        out.printf(format, args);
    }

    public void println(String message) {

        console.println(message);
        out.println(message);
    }

    public String read() {

        return console.read(false);
    }

    public void dispose() {
        console.dispose();
    }
}