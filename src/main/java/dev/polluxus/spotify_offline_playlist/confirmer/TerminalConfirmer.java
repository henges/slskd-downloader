package dev.polluxus.spotify_offline_playlist.confirmer;

import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
import dev.polluxus.spotify_offline_playlist.processor.DownloadProcessor.DownloadConfirmer;
import dev.polluxus.spotify_offline_playlist.processor.DownloadProcessor.UserConfirmationResult;
import dev.polluxus.spotify_offline_playlist.processor.model.ProcessorUserResult;
import dev.polluxus.spotify_offline_playlist.util.OutputMultiplexer;
import org.beryx.textio.TextIoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

public class TerminalConfirmer implements DownloadConfirmer {

    private static final Logger log = LoggerFactory.getLogger(TerminalConfirmer.class);

    private final OutputMultiplexer console;

    public TerminalConfirmer() {
        this.console = OutputMultiplexer.withStdin(TextIoFactory.getTextTerminal());
    }

    public UserConfirmationResult confirm(final AlbumInfo albumInfo, final ProcessorUserResult res) {

        final String searchString = albumInfo.searchString();
        console.printf("For query %s\n", searchString);
        console.println("Desired tracklist is:");
        for (var t : albumInfo.tracks()) {
            console.printf("\t%s - %s\n", t.number(), t.title());
        }
        console.printf("(%d tracks total)\n", albumInfo.tracks().size());

        console.println("Will download these files from " + res.username() + ": ");
        for (var f : res.bestCandidates().stream().sorted(Comparator.comparing(x -> x.originalData().filename())).toList()) {
            console.printf("\t%s\n", f.originalData().filename());
        }
        console.printf("(%d tracks total)\n\n", res.bestCandidates().size());

        console.println("OK? [y/n/skip]");
        String response;
        do {
            response = console.read();
            switch (response) {
                case "y" -> {
                    return UserConfirmationResult.YES;
                }
                case "n" -> {
                    return UserConfirmationResult.NO;
                }
                case "skip" -> {
                    return UserConfirmationResult.SKIP;
                }
                default -> console.println("Invalid response");
            }
        } while (true);
    }

    public void informFailure(AlbumInfo ai, String username) {

        console.printf("There was an issue downloading %s from %s... continuing with next result\n", ai.searchString(),
                username);
    }

    public void shutdown() {
        console.dispose();
    }
}
