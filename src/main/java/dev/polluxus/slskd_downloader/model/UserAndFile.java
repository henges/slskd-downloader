package dev.polluxus.slskd_downloader.model;

import dev.polluxus.slskd_downloader.client.slskd.response.SlskdGetDownloadResponse.SlskdDownloadFileResponse;

import java.util.List;

public record UserAndFile(String username, SlskdDownloadFileResponse file) {

    public String asKey() {

        return STR."\{username}~\{file.id()}";
    }
}
