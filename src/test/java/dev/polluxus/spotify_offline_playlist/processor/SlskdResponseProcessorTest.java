package dev.polluxus.spotify_offline_playlist.processor;

import dev.polluxus.spotify_offline_playlist.processor.matcher.MatchStrategyType;
import org.junit.Test;


public class SlskdResponseProcessorTest extends AbstractProcessorTest {

    @Test
    public void test() {

        var processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
        var reses = processor.process(responses, albumInfo);

        System.out.println("");
    }
}
