package dev.polluxus.slskd_downloader;

import au.com.muel.envconfig.EnvConfig;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.service.SlskdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class SlskdRetrier {

    private static final Logger log = LoggerFactory.getLogger(SlskdRetrier.class);

    public static void main(String[] args) {

        log.info("slskd-retrier started at {}", LocalDateTime.now());
        Config config = EnvConfig.fromEnv(Config.class);

        final var s = new SlskdService(config)
                .startDownloadPoll()
                .startRetrier(false);
    }
}
