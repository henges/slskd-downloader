package dev.polluxus.slskd_downloader.decisionmaker;

import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor.DecisionMaker;
import dev.polluxus.slskd_downloader.processor.DownloadProcessor.UserConfirmationResult;
import dev.polluxus.slskd_downloader.processor.model.output.ProcessorUserResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class UnattendedDecisionMaker implements DecisionMaker {

    private static final Logger log = LoggerFactory.getLogger(UnattendedDecisionMaker.class);

    private final Set<AlbumInfo> accepted;

    public UnattendedDecisionMaker() {
        this.accepted = new HashSet<>();
    }

    @Override
    public UserConfirmationResult confirm(AlbumInfo albumInfo, ProcessorUserResult res) {

        if (accepted.contains(albumInfo)) {
            log.warn("Skipping all further results for {} because we already an accepted a result for this album", albumInfo);
            return UserConfirmationResult.SKIP;
        }

        if (res.scoreOfBestCandidates() == 1.0) {
            log.info("Accepting result {} because score is 1.0", res);
            accepted.add(albumInfo);
            return UserConfirmationResult.YES;
        }

        log.info("Best result for {} has score less than 1.0, skipping it. (The result was {})", albumInfo, res);

        return UserConfirmationResult.SKIP;
    }

    @Override
    public void informSuccess(AlbumInfo ai, String username) {
        // Do nothing
    }

    @Override
    public void informFailure(AlbumInfo ai, String username) {
        // Do nothing
    }

    @Override
    public void shutdown() {
        // Do nothing
    }
}
