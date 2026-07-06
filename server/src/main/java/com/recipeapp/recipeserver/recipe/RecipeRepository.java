package com.recipeapp.recipeserver.recipe;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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

    // This is a "derived query method". Spring Data JPA parses the METHOD NAME
    // and writes the SQL for us. Reading it left to right:
    //   findAll ... ByOrderByCreatedAtDesc
    //   = "find all recipes, ordered by the createdAt field, descending".
    // The result: newest recipes first. We didn't write any query — the name IS
    // the query. (If you rename it incorrectly, Spring fails fast at startup.)
    List<Recipe> findAllByOrderByCreatedAtDesc();
}
