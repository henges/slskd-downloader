package dev.polluxus.slskd_downloader.infosupplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.polluxus.slskd_downloader.client.musicbrainz.MusicbrainzClient;
import dev.polluxus.slskd_downloader.client.musicbrainz.MusicbrainzClient.SearchOptions;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzRecording;
import dev.polluxus.slskd_downloader.client.musicbrainz.dto.MusicbrainzReleaseSearchResult;
import dev.polluxus.slskd_downloader.client.rym.RateYourMusicClient;
import dev.polluxus.slskd_downloader.config.Config;
import dev.polluxus.slskd_downloader.config.JacksonConfig;
import dev.polluxus.slskd_downloader.model.AlbumArtistPair;
import dev.polluxus.slskd_downloader.model.AlbumInfo;
import dev.polluxus.slskd_downloader.model.AlbumInfo.AlbumTrack;
import dev.polluxus.slskd_downloader.model.Playlist;
import dev.polluxus.slskd_downloader.model.Playlist.PlaylistAlbum;
import dev.polluxus.slskd_downloader.model.Playlist.PlaylistSong;
import dev.polluxus.slskd_downloader.service.SpotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class AlbumInfoSupplier {

    private static final Logger log = LoggerFactory.getLogger(AlbumInfoSupplier.class);

    public static Iterator<AlbumInfo> from(Config config) {

        if (config.rateYourMusicUser().isPresent()) {
            RateYourMusicClient rym = RateYourMusicClient.create(config);
            MusicbrainzClient musicbrainzClient = MusicbrainzClient.create(config);
            return rateYourMusicSupplier(config, musicbrainzClient, rym);
        }

        if (config.fileSource().isPresent()) {
            MusicbrainzClient mbc = MusicbrainzClient.create(config);
            File f = new File(config.fileSource().get());
            return fileSupplier(mbc, f);
        }

        if (config.spotifyClientId().isPresent()) {

            if (config.spotifyClientSecret().isEmpty() || config.spotifyPlaylistId().isEmpty()) {
                throw new RuntimeException("Spotify configuration incomplete! missing either playlistId or client secret.");
            }
            SpotifyService s = new SpotifyService(config);
            return spotifyPlaylistSupplier(s, config.spotifyPlaylistId().orElseThrow());
        }

        throw new RuntimeException("No data sources configured");
    }

    public static Iterator<AlbumInfo> spotifyPlaylistSupplier(final SpotifyService spotifyService, final String playlistId) {

        final Playlist p = spotifyService.getPlaylist(playlistId);
        final List<PlaylistAlbum> distinctPlaylistAlbums = p.tracks().stream().map(PlaylistSong::playlistAlbum).distinct().toList();
        List<AlbumInfo> ais = spotifyService.getAlbums(distinctPlaylistAlbums.stream().map(PlaylistAlbum::spotifyId).toList());
        return ais.iterator();
    }

    public static Iterator<AlbumInfo> fileSupplier(final MusicbrainzClient client, final File artistAlbumSrc) {

        final ObjectMapper mapper = JacksonConfig.MAPPER;
        final List<AlbumArtistPair> pairs;
        try {
            pairs = mapper.readValue(artistAlbumSrc, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Iterator<AlbumArtistPair> pairsIt = pairs.iterator();
        return musicbrainzAlbumArtistPairIterator(pairsIt, client);
    }

    public static Iterator<AlbumInfo> rateYourMusicSupplier(final Config config, final MusicbrainzClient mb, final RateYourMusicClient rym) {

        final var allResults = rym.getAllRatings(
                config.rateYourMusicUser().orElseThrow(),
                config.rateYourMusicMinRating(),
                config.rateYourMusicMaxRating());

        return musicbrainzAlbumArtistPairIterator(allResults.iterator(), mb);
    }

    private static Iterator<AlbumInfo> musicbrainzAlbumArtistPairIterator(Iterator<AlbumArtistPair> pairsIt, MusicbrainzClient client) {

        return new Iterator<>() {

            AlbumInfo next;

            @Override
            public boolean hasNext() {
                if (!pairsIt.hasNext()) {
                    return false;
                }
                final var currPair = pairsIt.next();
                try {
                    MusicbrainzReleaseSearchResult r = client.searchReleases(currPair.artist(), currPair.album(), SearchOptions.SORTED_DATE);

                    if (r.releases().isEmpty()) {
                        return this.hasNext();
                    }
                    final var bestReleaseMatch = r.releases().get(0);
                    final String artistName = bestReleaseMatch.artistCredit().get(0).artist().name();
                    final String albumName = bestReleaseMatch.title();
                    MusicbrainzRecording recording = client.getRecording(bestReleaseMatch.id());
                    if (recording.media().isEmpty()) {
                        return this.hasNext();
                    }
                    final List<AlbumTrack> tracks = recording.media().stream()
                            .flatMap(m -> m.tracks().stream())
                            .map(t -> new AlbumTrack(t.number(), t.title()))
                            .toList();

                    next = new AlbumInfo(albumName, null, tracks, List.of(artistName));

                    return true;
                } catch (Exception e) {
                    log.error("Error in album info provider", e);
                    return false;
                }
            }

            @Override
            public AlbumInfo next() {

                final var ret = next;
                next = null;
                return ret;
            }
        };
    }
}
