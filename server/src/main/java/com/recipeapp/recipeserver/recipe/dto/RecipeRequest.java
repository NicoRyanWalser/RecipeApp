package com.recipeapp.recipeserver.recipe.dto;

// This annotation comes from Jakarta Bean Validation. When we mark a controller
// parameter with @Valid (see RecipeController), Spring checks every constraint
// like this one and rejects the request automatically if any fail.
import jakarta.validation.constraints.NotBlank;

/**
 * A DTO (Data Transfer Object) for INCOMING data — the shape of the JSON the
 * browser sends when creating a recipe.
 *
 * Why not just accept a Recipe entity directly? Two reasons:
 *   1. Security/correctness: the client should NOT be able to set fields like
 *      "id" or "createdAt" — those belong to the server/database. A request DTO
 *      only exposes the fields the client is actually allowed to provide.
 *   2. Separation: the API's shape can evolve independently of the database table.
 *
 * "record" is a modern Java feature (Java 16+) for immutable data carriers. This
 * one line automatically generates a constructor, getter-style accessors
 * (name(), ingredients(), instructions()), equals(), hashCode(), and toString().
 * It's perfect for DTOs, which are just bundles of data.
 */
public record RecipeRequest(
        // @NotBlank means the value must be present AND not empty AND not just
        // whitespace. If the client omits the name or sends "   ", validation
        // fails and Spring returns a 400 Bad Request with this message.
        @NotBlank(message = "Recipe name is required") String name,
        @NotBlank(message = "Ingredients are required") String ingredients,
        @NotBlank(message = "Instructions are required") String instructions) {
}
