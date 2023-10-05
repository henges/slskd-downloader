package dev.polluxus.spotify_offline_playlist.client;

import com.google.common.collect.Lists;
import dev.polluxus.spotify_offline_playlist.Config;
import dev.polluxus.spotify_offline_playlist.model.AlbumInfo;
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

    private SpotifyClient(final Config config) {
        this.spotify = SpotifyApi.builder()
                .setClientId(config.spotifyClientId())
                .setClientSecret(config.spotifyClientSecret())
                .build();
    }

    public static SpotifyClient create(final Config config) {

        final SpotifyClient client = new SpotifyClient(config);
        client.init();
        return client;
    }

    private void init() {

        final ClientCredentials credentials = executeUnchecked(spotify.clientCredentials().build());
        spotify.setAccessToken(credentials.getAccessToken());
    }

    private <T> T executeUnchecked(AbstractRequest<T> request) {

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
                            Stream.of(a.getTracks().getItems()).map(TrackSimplified::getName).toList(),
                            Stream.of(a.getArtists()).map(ArtistSimplified::getName).toList()
                            ))
                    .toList());
        }

        return results;
    }
}
