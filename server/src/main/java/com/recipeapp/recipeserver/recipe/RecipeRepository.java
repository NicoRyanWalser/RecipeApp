package com.recipeapp.recipeserver.recipe;

import com.recipeapp.recipeserver.recipe.dto.RecipeSummaryResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * A "repository" is the layer that talks to the database. This is one of Spring's
 * most powerful features: we only DECLARE an interface — we never write the
 * implementation. At startup, Spring Data JPA automatically generates a class
 * that implements this interface and wires it into the app.
 *
 * By extending JpaRepository<Recipe, Long> we say:
 *   - this repository manages "Recipe" entities
 *   - whose primary key (@Id) type is "Long"
 *
 * That single line gives us a full set of ready-made methods for free, including:
 *   - save(recipe)      -> INSERT or UPDATE
 *   - findById(id)      -> SELECT one row by primary key
 *   - findAll()         -> SELECT every row
 *   - deleteById(id)    -> DELETE a row
 *   - count()           -> COUNT rows
 * ...and many more, without writing any SQL.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * Loads the LIST view: one lightweight summary per recipe, newest first.
     *
     * This is a PROJECTION — a query that builds a DTO directly in the database
     * rather than loading entities and converting them afterwards. Three things are
     * worth noticing:
     *
     * 1. "select new com.…RecipeSummaryResponse(...)" constructs the DTO inside the
     *    query. Hibernate calls the matching constructor for each row. The argument
     *    list must match a real constructor by TYPE and ORDER, and a mismatch is a
     *    startup failure — annoying to hit, but far better than a runtime one.
     *
     * 2. size(r.ingredients) becomes a SQL COUNT over the child table. The ingredient
     *    rows are never loaded into Java at all. That is what makes this immune to the
     *    N+1 problem rather than merely a workaround for it: there is no lazy
     *    collection to accidentally touch, because no entity was ever created.
     *
     * 3. The fully-qualified class name is required. JPQL has no imports.
     *
     * The alternative — findAll() then mapping in Java — would work and would be
     * simpler to read. It would also load every ingredient line of every recipe to
     * display a name and a count.
     */
    @Query("""
            select new com.recipeapp.recipeserver.recipe.dto.RecipeSummaryResponse(
                r.id,
                r.name,
                r.description,
                r.servings,
                size(r.ingredients),
                size(r.steps),
                r.createdAt)
            from Recipe r
            order by r.createdAt desc
            """)
    List<RecipeSummaryResponse> findAllSummaries();

    // The old derived query, findAllByOrderByCreatedAtDesc(), is gone. It loaded full
    // Recipe entities for the list view, which is exactly the fetch the projection
    // above exists to avoid. Nothing calls it any more, and an unused query method is
    // a trap for the next person who assumes it's the right one to use.
}
