package org.viajeseventos.validation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** URL-safe identifiers derived from a name — events and companies both use them. */
public final class Slugs {

    /** Lowercase letters and digits, single dashes between them. */
    public static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private Slugs() {
    }

    /** "Maná - Vivir sin aire Tour" → "mana-vivir-sin-aire-tour"; {@code fallback} if nothing is left. */
    public static String from(String name, int maxLength, String fallback) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) base = fallback;
        if (base.length() > maxLength) base = base.substring(0, maxLength).replaceAll("-+$", "");
        return base;
    }

    /** {@code base}, or {@code base-2}, {@code base-3}… — the first one {@code taken} rejects. */
    public static String unique(String base, Predicate<String> taken) {
        String slug = base;
        for (int n = 2; taken.test(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }
}
