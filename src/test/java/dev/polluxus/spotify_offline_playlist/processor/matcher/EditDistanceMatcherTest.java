package dev.polluxus.spotify_offline_playlist.processor.matcher;

import dev.polluxus.spotify_offline_playlist.processor.AbstractProcessorTest;
import org.junit.Test;

public class EditDistanceMatcherTest extends AbstractProcessorTest {

    @Test
    public void test() {

        final var reses = responses.stream()
                .map(r -> MatchStrategyType.EDIT_DISTANCE.match(r, albumInfo))
                .filter(m -> !m.isEmpty())
                .toList();

        System.out.println("Stop");
    }
}
