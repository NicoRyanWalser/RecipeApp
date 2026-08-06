package com.recipeapp.recipeserver.recipe.dto;

import java.time.Instant;

/**
 * A LIGHTWEIGHT recipe, for the list view: enough to render a card and let the user
 * pick one, and nothing more. Returned by GET /api/recipes.
 *
 * WHY A SECOND RESPONSE SHAPE INSTEAD OF REUSING RecipeDetailResponse?
 *
 * Because the list endpoint would then have to load every recipe's ingredient lines
 * and every line's catalog entry, for every recipe on the page — to display a title
 * and a count. That is the N+1 SELECT PROBLEM in its natural habitat: 1 query for
 * the recipes, then 2 more per recipe as each one's lazy collections are touched.
 * Fifty recipes, a hundred and one queries, all to show fifty names.
 *
 * This DTO is built by a database PROJECTION instead — see
 * RecipeRepository.findAllSummaries. The counts are computed by SQL's COUNT, so the
 * ingredient rows are never loaded into memory at all. One query, no N+1 to fix,
 * because there was never an opportunity for one to form.
 *
 * The general lesson is worth more than the specific fix: the cheapest way to solve
 * N+1 is to not fetch what you are not going to display. Reach for JOIN FETCH and
 * @BatchSize when you genuinely need the children; reach for a narrower DTO when you
 * don't.
 */
public record RecipeSummaryResponse(
        Long id,
        String name,
        String description,
        int servings,

        // Counts, not contents. "8 ingredients, 5 steps" is what a card shows.
        //
        // These are declared as long rather than int because SQL's count() returns a
        // 64-bit value, and JPQL will refuse to match this constructor if the types
        // disagree — a startup error rather than a runtime one, but a confusing one.
        long ingredientCount,
        long stepCount,

        Instant createdAt) {
}
