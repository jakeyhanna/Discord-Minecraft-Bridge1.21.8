package com.jackyon;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Finds a unique closest name across each candidate's aliases. */
final class NameMatcher {
    private NameMatcher() {
    }

    static <T> T findClosest(String query, List<T> candidates,
                            Function<T, List<String>> aliases) {
        String normalized = query.toLowerCase(Locale.ROOT);
        int limit = Math.min(3, Math.max(1, normalized.length() / 4));
        int bestScore = Integer.MAX_VALUE;
        T best = null;
        boolean tied = false;

        for (T candidate : candidates) {
            int score = Integer.MAX_VALUE;
            for (String alias : aliases.apply(candidate)) {
                String name = alias.toLowerCase(Locale.ROOT);
                int distance = editDistance(normalized, name);
                int aliasScore = distance <= limit ? distance : Integer.MAX_VALUE;
                // Three characters are required for abbreviated names.
                if (normalized.length() >= 3 && name.startsWith(normalized)) {
                    aliasScore = Math.min(aliasScore, 1);
                }
                score = Math.min(score, aliasScore);
            }
            if (score == Integer.MAX_VALUE) {
                continue;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
                tied = false;
            } else if (score == bestScore) {
                tied = true;
            }
        }
        return tied ? null : best;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1]
                        + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution,
                        Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
