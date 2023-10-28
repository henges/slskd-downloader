package dev.polluxus.slskd_downloader.util;

import org.apache.commons.text.similarity.LevenshteinDistance;

import java.util.regex.Pattern;

public class Matchers {

    // Matches e.g. '1-01' - for multi disk albums
    public static final Pattern MULTI_DISK_TRACK_NUMBER_PATTERN = Pattern.compile("^\\d+-\\d+\\s*");
    public static final Pattern VINYL_SIDE_TRACK_NUMBER_PATTERN = Pattern.compile("^\\w+\\d*\\s*-\\s*");
    // Generic matcher that tries to match as many 'track number' looking characters at the start of the string
    // Have to be careful to not match a track number that itself is numeric (e.g.: Global Communication's 76:14)
    public static final Pattern GENERIC_TRACK_NUMBER_PATTERN = Pattern.compile("^\\d+(\s|\\.|-)*");
    public static final Pattern LEADING_GARBAGE = Pattern.compile("^(\s|\\.|-)*");
    public static final Pattern FEATURED_ARTIST_MATCHER = Pattern.compile("\\s*\\((feat|ft|featuring)[^(]+\\)");

    public static final LevenshteinDistance LEVENSHTEIN_DISTANCE_1 = new LevenshteinDistance(1);
    public static final LevenshteinDistance LEVENSHTEIN_DISTANCE_2 = new LevenshteinDistance(2);
    public static final LevenshteinDistance LEVENSHTEIN_DISTANCE_4 = new LevenshteinDistance(4);
    public static final LevenshteinDistance LEVENSHTEIN_DISTANCE_8 = new LevenshteinDistance(8);

    public static LevenshteinDistance getEditDistanceFunc(final String name) {

        final int length = name.length();
        if (length <= 6) {
            return LEVENSHTEIN_DISTANCE_1;
        } else if (length <= 22) {
            return LEVENSHTEIN_DISTANCE_4;
        } else {
            return LEVENSHTEIN_DISTANCE_8;
        }
    }
}
