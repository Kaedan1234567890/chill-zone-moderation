package com.chillzone.moderation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {
    private TimeParser() {}
    private static final Pattern PART = Pattern.compile("(\\d+)(mo|[mhdwy])", Pattern.CASE_INSENSITIVE);

    public static long parseMillis(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        Matcher matcher = PART.matcher(s);
        long total = 0L;
        int end = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != end) return -1;
                long n = Long.parseLong(matcher.group(1));
                long unit = switch (matcher.group(2)) {
                    case "m" -> 60_000L;
                    case "h" -> 3_600_000L;
                    case "d" -> 86_400_000L;
                    case "w" -> 604_800_000L;
                    case "mo" -> 2_629_746_000L; // average Gregorian month
                    case "y" -> 31_556_952_000L; // average Gregorian year
                    default -> -1L;
                };
                if (unit < 0 || n > Long.MAX_VALUE / unit || total > Long.MAX_VALUE - n * unit) return -1;
                total += n * unit;
                end = matcher.end();
            }
            return end == s.length() && total > 0 ? total : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    public static String formatRemaining(long ms) {
        if (ms <= 0) return "Expired";
        long minutes = Math.max(1, (ms + 59_999L) / 60_000L);
        long days = minutes / 1440L;
        minutes %= 1440L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public static String formatDuration(long ms) {
        long minutes = Math.max(1, ms / 60_000L);
        long days = minutes / 1440L;
        long rem = minutes % 1440L;
        long hours = rem / 60L;
        long mins = rem % 60L;
        if (days > 0) return days + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }
}
