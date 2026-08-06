package com.recipeapp.recipeserver.ingredient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of POST /api/ingredients — how the combobox's "Create 'shallot'" action
 * mints a new catalog entry.
 *
 * Notice how little the client is allowed to send: just a name. No category, no
 * conversion factors, and certainly no id.
 *
 * That is a deliberate answer to the question "who may add an ingredient, and how?"
 * The catalog is SEEDED with curated data (categories and conversion factors that
 * were checked once, carefully) and EXTENDED by users with names only. A user should
 * not be inventing a density for shallots from memory, and the app should not pretend
 * they did. An ingredient created this way simply has no factors — which the shopping
 * list handles by keeping amounts on separate lines instead of guessing.
 *
 * The upside of allowing creation at all: the alternative was a strictly closed
 * dropdown, and there are far too many ingredients in the world for one person to
 * enumerate. This gets the data-quality benefit of a catalog without the wall.
 */
public record IngredientRequest(

        // @NotBlank rejects null, "", and "   " alike — stricter than @NotNull, which
        // would happily accept a string of spaces. Blank names are how a catalog fills
        // up with invisible rows nobody can search for.
        @NotBlank(message = "Ingredient name is required")
        @Size(max = 100, message = "Ingredient name must be 100 characters or fewer")
        String name) {
}
