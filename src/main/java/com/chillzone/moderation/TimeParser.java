package com.chillzone.moderation;

public final class TimeParser {
    private TimeParser() {}

    public static long parseMillis(String input) {
        if (input == null || input.length() < 2) return -1;
        try {
            long n = Long.parseLong(input.substring(0, input.length() - 1));
            char unit = Character.toLowerCase(input.charAt(input.length() - 1));
            return switch (unit) {
                case 'm' -> n * 60_000L;
                case 'h' -> n * 3_600_000L;
                case 'd' -> n * 86_400_000L;
                case 'w' -> n * 604_800_000L;
                default -> -1L;
            };
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public static String formatRemaining(long ms) {
        if (ms <= 0) return "Expired";
        long minutes = ms / 60_000L;
        long days = minutes / 1440L;
        minutes %= 1440L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
