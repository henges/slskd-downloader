package dev.polluxus.slskd_downloader.client.spotify;

import com.google.common.collect.Lists;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.AlbumInfo.AlbumTrack;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.*;
import se.michaelthelin.spotify.requests.AbstractRequest;
import se.michaelthelin.spotify.requests.data.albums.GetSeveralAlbumsRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SpotifyClient {

    private final SpotifyApi spotify;
    private ClientCredentials credentials;

    private SpotifyClient(final Config config) {
        this.spotify = SpotifyApi.builder()
                .setClientId(config.spotifyClientId().orElseThrow())
                .setClientSecret(config.spotifyClientSecret().orElseThrow())
                .build();
        this.credentials = null;
    }

    public static SpotifyClient create(final Config config) {

        return new SpotifyClient(config);
    }

    private void init() {
        if (this.credentials == null) {
            credentials = executeUnchecked(spotify.clientCredentials().build());
            spotify.setAccessToken(credentials.getAccessToken());
        }
    }

    private <T> T executeUnchecked(AbstractRequest<T> request) {

        init();

        try {
            return request.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Playlist getPlaylist(final String playlistId) {

        return executeUnchecked(spotify.getPlaylist(playlistId).build());
    }

    public List<PlaylistTrack> getAllPlaylistItems(final String playlistId) {

        List<PlaylistTrack> results = new ArrayList<>();
        boolean hasNext;
        do {
            Paging<PlaylistTrack> r = executeUnchecked(spotify.getPlaylistsItems(playlistId).offset(results.size()).build());
            results.addAll(List.of(r.getItems()));
            hasNext = r.getNext() != null;
        } while(hasNext);

        return results;
    }

    public List<AlbumInfo> getAlbums(List<String> albumIds) {

        final List<GetSeveralAlbumsRequest> reqs = Lists.partition(albumIds, 20).stream()
                .map(l -> spotify.getSeveralAlbums(l.toArray(String[]::new)).build())
                .toList();
        final List<AlbumInfo> results = new ArrayList<>();
        for (var r: reqs) {
            Album[] res = executeUnchecked(r);
            results.addAll(Stream.of(res)
                    .map(a -> new AlbumInfo(a.getName(),
                            a.getId(),
                            Stream.of(a.getTracks().getItems()).map(i -> new AlbumTrack(String.valueOf(i.getTrackNumber()), i.getName())).toList(),
                            Stream.of(a.getArtists()).map(ArtistSimplified::getName).toList()
                            ))
                    .toList());
        }

        return results;
    }
}
