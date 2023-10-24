package dev.polluxus.slskd_downloader.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class FilenameUtilsTest {

    @ParameterizedTest
    @MethodSource("testGetParentNameArgs")
    public void testGetParentName(final String testString, final String expected) {

        assertEquals(expected, FilenameUtils.getParentName(testString));
    }

    static Stream<Arguments> testGetParentNameArgs() {

        return Stream.of(
                arguments(
                        "c:\\users\\swans77\\desktop\\slsk folder\\ultimate 90's - 1997\\juggernaut - ruffneck rules da artcore scene.mp3",
                        "ultimate 90's - 1997"),
                arguments(
                        "@@nxgdx\\[Music]\\[Regional／Traditional ／ Ethnic ／World]\\[Hispanic／Latin]\\[Bossa Nova／Samba]\\Antônio Carlos Jobim\\(1967) Wave\\01 Wave.mp3",
                        "(1967) Wave"
                ),
                arguments(
                        "@@bhfrv\\Fonoteca Grao\\Fonoteca\\S\\Sparks\\Albums\\1988 - Interior Design (Flac)\\Sparks - Interior Design (LBRCD104).jpg",
                        "1988 - Interior Design (Flac)"
                )
        );
    }
}
