package dev.polluxus.slskd_downloader.processor;

import dev.polluxus.slskd_downloader.processor.matcher.MatchStrategyType;
import org.junit.Test;


public class SlskdResponseProcessorTest extends AbstractProcessorTest {

    @Test
    public void test() {

        var processor = new SlskdResponseProcessor(MatchStrategyType.EDIT_DISTANCE);
        var reses = processor.process(responses, albumInfo);

        reses.userResults().stream().limit(10).forEach(pur -> {
            System.out.printf("User %s, score %f:\n\n", pur.username(), pur.scoreOfBestCandidates());
            pur.bestCandidates().stream().forEach(pfr -> {
                System.out.printf("%s - score %f\n", pfr.originalData().filename(), pfr.score());
            });
        });

        System.out.println("");
    }
}
