package cpw.mods.jarhandling.impl;

final class PathNormalization {
    private static final char SEPARATOR = '/';

    private PathNormalization() {}

    public static boolean isNormalized(CharSequence path) {
        if (path.isEmpty()) {
            return true; // This will fail for other reasons
        }

        // Normalized paths use forward slashes
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '\\') {
                return false;
            }
        }

        char prevCh = '\0';
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if ((i == 0 || i == path.length() - 1) && ch == SEPARATOR) {
                return false; // No leading or trailing separators
            }
            if (ch == SEPARATOR && prevCh == SEPARATOR) {
                return false; // No repeated separators
            }
            // TODO We do not support ./ or ../
            prevCh = ch;
        }

        return true;
    }

    public static String normalize(CharSequence path) {
        var result = new StringBuilder(path.length());

        int startOfSegment = 0;
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch == '\\') {
                ch = SEPARATOR;
            }
            if (ch == SEPARATOR) {
                if (i > startOfSegment) {
                    if (!result.isEmpty()) {
                        result.append(SEPARATOR);
                    }

                    var segment = path.subSequence(startOfSegment, i);
                    validateSegment(segment);
                    result.append(segment);
                }
                startOfSegment = i + 1;
            }
        }
        if (startOfSegment < path.length()) {
            if (!result.isEmpty()) {
                result.append(SEPARATOR);
            }
            var segment = path.subSequence(startOfSegment, path.length());
            validateSegment(segment);
            result.append(segment);
        }

        return result.toString();
    }

    private static void validateSegment(CharSequence segment) {
        if (segment.equals(".") || segment.equals("..")) {
            throw new IllegalArgumentException("./ or ../ segments in paths are not supported");
        }
    }
}
