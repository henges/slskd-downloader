package dev.polluxus.slskd_downloader.util;

public class Validations {

    public static int requirePositive(int value) {

        if (value <= 0) {
            throw new RuntimeException(STR."Value was expected to be positive, but was \{value}");
        }

        return value;
    }
}
