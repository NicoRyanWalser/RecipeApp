package com.recipeapp.recipeserver.recipe;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A repository for ingredient LINES, used by the shopping list.
 *
 * You might reasonably ask why this exists at all, given that lines are owned by
 * recipes and could be reached through Recipe.getIngredients(). The answer is the
 * shopping list's access pattern: it wants EVERY line across a set of recipes, all
 * at once, flattened. Going through the recipes would mean loading N recipe
 * aggregates and walking their collections — more objects, more queries, and a shape
 * that has to be flattened anyway.
 *
 * This is a normal thing to do. "One repository per entity" is a habit, not a rule;
 * what actually matters is that writes still go through the aggregate root (Recipe),
 * so the invariants about positions and back-references stay intact. Reading through
 * a side door is fine. Writing through one is not.
 */
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    /**
     * Fetches every ingredient line belonging to any of the given recipes, in ONE
     * query, with the catalog ingredient and parent recipe already loaded.
     *
     * "JOIN FETCH" is the important part. A plain join would let us filter by the
     * related table but would still leave ri.ingredient as an unloaded lazy proxy —
     * so the moment the aggregation loop calls getIngredient().getName(), Hibernate
     * fires another SELECT. Once per line. That is the N+1 problem again, and here it
     * would be N+1 on the single most performance-sensitive endpoint in the app.
     *
     * JOIN FETCH says "and while you're joining, actually populate that field".
     * The result is one round trip for the entire shopping list.
     *
     * Note that two JOIN FETCHes are fine here because both are single-valued
     * (@ManyToOne). It is fetching two COLLECTIONS in one query that throws
     * MultipleBagFetchException — see the note on Recipe.ingredients.
     *
     * The ORDER BY makes the output deterministic: lines come back grouped by recipe
     * and in their display order, so the aggregation produces the same shopping list
     * for the same input every time. Without it the database may return rows in any
     * order it likes, and the list would subtly reshuffle between identical requests.
     */
    @Query("""
            select ri from RecipeIngredient ri
            join fetch ri.ingredient
            join fetch ri.recipe
            where ri.recipe.id in :recipeIds
            order by ri.recipe.id asc, ri.position asc
            """)
    List<RecipeIngredient> findAllForRecipes(@Param("recipeIds") Collection<Long> recipeIds);
}
