package dev.polluxus.slskd_downloader.util;

/**
 * This is a copy of the relevant portions from commons-io's FilenameUtils, albeit
 * with an annoying (unnecessary) check for illegal characters on Windows removed.
 */
public class FilenameUtils {

    private static final int NOT_FOUND = -1;
    /**
     * The Unix separator character.
     */
    private static final char UNIX_SEPARATOR = '/';

    /**
     * The Windows separator character.
     */
    private static final char WINDOWS_SEPARATOR = '\\';
    public static final char EXTENSION_SEPARATOR = '.';

    /**
     * Gets the name minus the path from a full fileName.
     * <p>
     * This method will handle a file in either Unix or Windows format.
     * The text after the last forward or backslash is returned.
     * <pre>
     * a/b/c.txt --&gt; c.txt
     * a.txt     --&gt; a.txt
     * a/b/c     --&gt; c
     * a/b/c/    --&gt; ""
     * </pre>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     *
     * @param fileName  the fileName to query, null returns null
     * @return the name of the file without the path, or an empty string if none exists.
     * Null bytes inside string will be removed
     */
    public static String getName(final String fileName) {
        if (fileName == null) {
            return null;
        }
        requireNonNullChars(fileName);
        final int index = indexOfLastSeparator(fileName);
        return fileName.substring(index + 1);
    }

    /**
     * Gets the name of the parent directory.
     * @param fileName
     * @return
     */
    public static String getParentName(final String fileName) {
        if (fileName == null) {
            return null;
        }
        requireNonNullChars(fileName);
        final int lastIndex = indexOfLastSeparator(fileName);
        final int secondLastIndex = indexOfLastSeparatorBefore(fileName, lastIndex);
        return fileName.substring(secondLastIndex + 1, lastIndex);
    }

    private static void requireNonNullChars(final String path) {
        if (path.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Null byte present in file/path name. There are no "
                    + "known legitimate use cases for such data, but several injection attacks may use it");
        }
    }

    public static int indexOfLastSeparator(final String fileName) {
        return indexOfLastSeparatorBefore(fileName, -1);
    }

    public static int indexOfLastSeparatorBefore(final String fileName, final int beforeIndex) {
        if (fileName == null) {
            return NOT_FOUND;
        }
        final int lastUnixPos, lastWindowsPos;
        if (beforeIndex == -1) {

           lastUnixPos = fileName.lastIndexOf(UNIX_SEPARATOR);
           lastWindowsPos = fileName.lastIndexOf(WINDOWS_SEPARATOR);
        } else {

            lastUnixPos = fileName.lastIndexOf(UNIX_SEPARATOR, beforeIndex - 1);
            lastWindowsPos = fileName.lastIndexOf(WINDOWS_SEPARATOR, beforeIndex - 1);
        }
        return Math.max(lastUnixPos, lastWindowsPos);
    }

    /**
     * Gets the base name, minus the full path and extension, from a full fileName.
     * <p>
     * This method will handle a file in either Unix or Windows format.
     * The text after the last forward or backslash and before the last dot is returned.
     * <pre>
     * a/b/c.txt --&gt; c
     * a.txt     --&gt; a
     * a/b/c     --&gt; c
     * a/b/c/    --&gt; ""
     * </pre>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     *
     * @param fileName  the fileName to query, null returns null
     * @return the name of the file without the path, or an empty string if none exists. Null bytes inside string
     * will be removed
     */
    public static String getBaseName(final String fileName) {
        return removeExtension(getName(fileName));
    }

    public static String removeExtension(final String fileName) {
        if (fileName == null) {
            return null;
        }
        requireNonNullChars(fileName);

        final int index = indexOfExtension(fileName);
        if (index == NOT_FOUND) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    public static int indexOfExtension(final String fileName) throws IllegalArgumentException {
        if (fileName == null) {
            return NOT_FOUND;
        }
        // NB: This is the part we've removed for our purposes - we don't care about this aspect.
//        if (isSystemWindows()) {
//            // Special handling for NTFS ADS: Don't accept colon in the fileName.
//            final int offset = fileName.indexOf(':', getAdsCriticalOffset(fileName));
//            if (offset != -1) {
//                throw new IllegalArgumentException("NTFS ADS separator (':') in file name is forbidden.");
//            }
//        }
        final int extensionPos = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        final int lastSeparator = indexOfLastSeparator(fileName);
        return lastSeparator > extensionPos ? NOT_FOUND : extensionPos;
    }
}
