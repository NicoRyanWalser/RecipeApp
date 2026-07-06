package com.recipeapp.recipeserver.recipe.dto;

import com.recipeapp.recipeserver.recipe.Recipe;
import java.time.Instant;

/**
 * A DTO for OUTGOING data — the shape of the JSON we send back to the browser.
 *
 * Using a separate response DTO (instead of returning the Recipe entity directly)
 * keeps our database structure decoupled from our public API. If we later add an
 * internal field to the Recipe entity that clients shouldn't see, it won't leak
 * out through the API, because only the fields listed here are ever serialized.
 *
 * Unlike the request DTO, this one INCLUDES id and createdAt, because those are
 * values the server generated and the client legitimately wants to see.
 */
public record RecipeResponse(
        Long id,
        String name,
        String ingredients,
        String instructions,
        Instant createdAt) {

    // A "factory method": a convenient way to build a RecipeResponse from a Recipe
    // entity. Marking it "static" means you call it on the type itself
    // (RecipeResponse.from(recipe)) rather than needing an existing instance.
    // Centralizing the entity -> DTO conversion here means we only write the
    // mapping once and reuse it everywhere.
    public static RecipeResponse from(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getIngredients(),
                recipe.getInstructions(),
                recipe.getCreatedAt());
    }
}
