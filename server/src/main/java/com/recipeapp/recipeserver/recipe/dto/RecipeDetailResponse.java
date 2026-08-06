package com.recipeapp.recipeserver.recipe.dto;

import com.recipeapp.recipeserver.recipe.Recipe;
import java.time.Instant;
import java.util.List;

/**
 * The FULL shape of one recipe: everything needed to display it or to populate the
 * edit form. Returned by GET /api/recipes/{id}, POST, and PUT.
 *
 * This is the "detail" half of a deliberate pair — see RecipeSummaryResponse for the
 * "list" half, and for why sending this shape for every recipe in a list would be a
 * performance problem rather than a convenience.
 */
public record RecipeDetailResponse(
        Long id,
        String name,
        String description,
        int servings,
        List<RecipeIngredientResponse> ingredients,
        List<String> steps,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * IMPORTANT: this method must be called from INSIDE a @Transactional service
     * method, because getIngredients() and getSteps() are LAZY collections.
     *
     * "Lazy" means the data isn't loaded from the database until something asks for
     * it — and it can only be loaded while the database session is still open, which
     * is to say inside the transaction. Call this after the transaction has closed and
     * you get a LazyInitializationException.
     *
     * This is THE most common Hibernate error, and the reason for a rule the original
     * code already followed without needing to: convert entities to DTOs in the
     * SERVICE, never in the controller. It mattered less when a recipe was three
     * strings with nothing lazy about it. Now it is load-bearing.
     *
     * The related trap: returning the ENTITY from a controller instead of a DTO. It
     * looks like it works, then Jackson touches a lazy collection while serializing —
     * outside the transaction, halfway through writing the response — and you get a
     * 500 with a truncated JSON body. Mapping here, eagerly, avoids the whole class
     * of problem.
     */
    public static RecipeDetailResponse from(Recipe recipe) {
        return new RecipeDetailResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getServings(),
                recipe.getIngredients().stream()
                        .map(RecipeIngredientResponse::from)
                        .toList(),
                // A defensive copy. The entity hands back an unmodifiable VIEW of its
                // live list; copying into a new List here means the DTO stays valid
                // even after the entity is gone.
                List.copyOf(recipe.getSteps()),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt());
    }
}
