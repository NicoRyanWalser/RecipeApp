package com.recipeapp.recipeserver.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns a human-typed ingredient name into a "slug" — a normalized key used to
 * decide whether two names mean the SAME thing.
 *
 * A "slug" is a simplified version of a string, stripped of everything that varies
 * without changing meaning: capitalization, stray whitespace, accents, punctuation.
 *
 *   "Olive Oil"    ->  "olive oil"
 *   "  olive  OIL" ->  "olive oil"
 *   "Jalapeño"     ->  "jalapeno"
 *
 * The ingredients table has a UNIQUE constraint on this column, which is what stops
 * "Tomato", "tomato", and "  Tomato " becoming three separate catalog rows that then
 * refuse to combine on a shopping list.
 *
 * BE HONEST ABOUT WHAT THIS DOES NOT DO. It will not merge "Tomato" with "Tomatoes",
 * because that requires knowing English plural rules, and naive de-pluralization is
 * wrong more often than it's right ("Molasses" -> "Molasse", "Couscous" -> "Couscou").
 * The plural problem is handled in the UI instead: the ingredient search matches on
 * "contains", so typing "tomato" surfaces the existing "Tomatoes" before the user
 * reaches for "create new". Prevention by visibility beats a clever wrong algorithm.
 *
 * The class is final with a private constructor because it is a pure utility — it
 * holds no state and there is never a reason to create one. This is the standard
 * Java idiom for "a bag of static helper methods".
 */
public final class Slugs {

    private Slugs() {
        // Never instantiated. Call Slugs.of(...) directly on the type.
    }

    /**
     * Produces the normalized key for a name. Runs on every ingredient create AND on
     * every lookup, so the two can never disagree about what counts as a duplicate.
     */
    public static String of(String rawName) {
        if (rawName == null) {
            return "";
        }

        // STEP 1 — Unicode normalization, form NFD ("Canonical Decomposition").
        // An accented character like "ñ" can be stored two different ways: as one
        // code point, or as a plain "n" followed by a combining tilde. NFD always
        // splits it into the second form, which lets the next step strip the accent.
        // Without this, "Jalapeño" typed on a Mac and on Windows can produce two
        // different byte sequences that look identical and compare as unequal.
        String decomposed = Normalizer.normalize(rawName, Normalizer.Form.NFD);

        return decomposed
                // STEP 2 — delete the now-separated accent marks. \p{M} is a regex
                // class meaning "any Unicode mark character" (the combining tilde,
                // acute, umlaut...). "ñ" has already become "n" + tilde, so this
                // leaves a clean "n".
                .replaceAll("\\p{M}", "")

                // STEP 3 — lowercase, so "Olive" and "olive" collapse together.
                // Locale.ROOT matters more than it looks: in Turkish, lowercasing
                // "I" produces a dotless "ı", not "i". Locale.ROOT pins us to
                // locale-independent rules so the slug never depends on the server's
                // regional settings. This is a genuine, famous bug class.
                .toLowerCase(Locale.ROOT)

                // STEP 4 — trim leading/trailing whitespace ("  olive oil " -> "olive oil").
                .trim()

                // STEP 5 — collapse any run of internal whitespace to a single space,
                // so "olive   oil" and "olive oil" agree. \s+ means "one or more
                // whitespace characters", which also catches tabs from a paste.
                .replaceAll("\\s+", " ");
    }
}
