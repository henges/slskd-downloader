package dev.polluxus.spotify_offline_playlist.client;

import dev.polluxus.spotify_offline_playlist.Config;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.requests.AbstractRequest;

import java.util.ArrayList;
import java.util.List;

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
}
