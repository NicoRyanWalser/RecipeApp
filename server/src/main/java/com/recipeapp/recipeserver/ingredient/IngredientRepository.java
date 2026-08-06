package com.recipeapp.recipeserver.ingredient;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The repository for the ingredient catalog. As with RecipeRepository, we only
 * declare method signatures — Spring Data generates the implementation at startup.
 */
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /**
     * Does an ingredient with this slug already exist?
     *
     * Used by the seeder (to skip rows it already inserted) and by the get-or-create
     * flow. "exists" rather than "find" because we only need a yes/no: it compiles to
     * a SELECT 1 ... LIMIT 1 rather than hauling the whole row back.
     */
    boolean existsBySlug(String slug);

    /**
     * Find the catalog row for an exact normalized name. This is the lookup half of
     * "get or create": before inserting "Shallot", we check whether "shallot" is
     * already here.
     *
     * Optional<Ingredient> is Java's way of saying "there might not be one". It forces
     * the caller to decide what happens when it's absent, instead of handing back a
     * null that someone forgets to check.
     */
    Optional<Ingredient> findBySlug(String slug);

    /**
     * The search behind the recipe form's ingredient combobox.
     *
     * This one is a hand-written @Query rather than a derived method name, because the
     * ORDER BY expresses something a method name cannot: PREFIX MATCHES SHOULD COME
     * FIRST. Typing "car" should offer "Carrot" before "Turkey Carcass", even though
     * both technically contain "car". A plain findBySlugContaining would return them
     * in arbitrary order and the combobox would feel broken.
     *
     * The query is written in JPQL, not SQL: it names the ENTITY (Ingredient) and its
     * FIELDS (i.slug), not the table and columns. Hibernate translates it to real SQL
     * for whatever database you're on.
     */
    @Query("""
            select i from Ingredient i
            where i.slug like concat('%', :q, '%')
            order by
                case when i.slug like concat(:q, '%') then 0 else 1 end,
                i.name asc
            """)
    // The two-part ORDER BY reads as: first sort every prefix match into group 0 and
    // everything else into group 1, then sort alphabetically inside each group.
    //
    // ":q" is a NAMED PARAMETER — a placeholder the database fills in separately from
    // the query text. This is not merely tidier than string-concatenating the user's
    // input into the query; it is the thing that prevents SQL INJECTION. Because the
    // value never becomes part of the parsed statement, a search for "'; drop table
    // recipes; --" is just a search for a very strange ingredient name.
    List<Ingredient> search(@Param("q") String slugFragment, Limit limit);

    /**
     * The combobox's empty state: what to show before the user has typed anything.
     * Alphabetical, capped by the caller.
     */
    List<Ingredient> findAllByOrderByNameAsc(Limit limit);
}
