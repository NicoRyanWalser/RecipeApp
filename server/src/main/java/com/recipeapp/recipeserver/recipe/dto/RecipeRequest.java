package com.recipeapp.recipeserver.recipe.dto;

// These annotations come from Jakarta Bean Validation. When we mark a controller
// parameter with @Valid (see RecipeController), Spring checks every constraint
// like this one and rejects the request automatically if any fail.
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A DTO (Data Transfer Object) for INCOMING data — the shape of the JSON the
 * browser sends when creating or updating a recipe.
 *
 * Why not just accept a Recipe entity directly? Two reasons:
 *   1. Security/correctness: the client should NOT be able to set fields like
 *      "id" or "createdAt" — those belong to the server/database. A request DTO
 *      only exposes the fields the client is actually allowed to provide.
 *   2. Separation: the API's shape can evolve independently of the database table.
 *
 * "record" is a modern Java feature (Java 16+) for immutable data carriers. This
 * one line automatically generates a constructor, getter-style accessors
 * (name(), ingredients(), steps()), equals(), hashCode(), and toString().
 * It's perfect for DTOs, which are just bundles of data.
 *
 * WHAT CHANGED FROM THE FIRST VERSION: this used to be three strings — name,
 * ingredients, instructions — where the last two were free text. Ingredients and
 * steps are now structured LISTS. That change is what makes every other feature
 * possible, because you cannot combine, scale, or unit-convert prose.
 */
public record RecipeRequest(

        // @NotBlank means the value must be present AND not empty AND not just
        // whitespace. If the client omits the name or sends "   ", validation
        // fails and Spring returns a 400 Bad Request with this message.
        @NotBlank(message = "Recipe name is required")
        @Size(max = 255, message = "Recipe name must be 255 characters or fewer")
        String name,

        // Optional — no @NotBlank, so null and "" are both fine.
        @Size(max = 200, message = "Description must be 200 characters or fewer")
        String description,

        // @Min rejects 0 and negatives. A recipe that serves nobody is not a recipe,
        // and a zero here would become a division by zero the day scaling is added.
        @Min(value = 1, message = "Servings must be at least 1")
        int servings,

        /**
         * The ingredient lines.
         *
         * @Valid is the load-bearing annotation here, and the easiest one in the whole
         * file to forget. Without it, the constraints declared inside
         * RecipeIngredientRequest are NEVER CHECKED — validation stops at the surface
         * of this object and does not descend into nested ones. The failure is quiet:
         * everything appears to work, and invalid nested data goes straight through to
         * the database. If nested validation ever seems not to fire, look for a
         * missing @Valid first.
         *
         * @NotEmpty rejects both null and an empty list. A recipe with no ingredients
         * would produce an empty shopping list that looks like a bug in the
         * aggregation rather than what it is — a bug in the data.
         *
         * @Size caps it. Any endpoint accepting a collection should bound it, or one
         * request can ask the server to do unbounded work.
         */
        @Valid
        @NotEmpty(message = "A recipe needs at least one ingredient")
        @Size(max = 100, message = "A recipe cannot have more than 100 ingredients")
        List<RecipeIngredientRequest> ingredients,

        /**
         * The instruction steps, in order, as plain strings.
         *
         * The constraint syntax here rewards a close read: the annotations sit INSIDE
         * the generic brackets, applied to the element type rather than to the list.
         * List<@NotBlank String> means "every string in this list must be non-blank".
         * Writing @NotBlank List<String> instead would be a type error, because
         * @NotBlank applies to CharSequence and a List is not one. That placement is
         * how you validate the CONTENTS of a collection rather than the collection.
         */
        @NotEmpty(message = "A recipe needs at least one step")
        @Size(max = 100, message = "A recipe cannot have more than 100 steps")
        List<@NotBlank(message = "Steps cannot be blank")
             @Size(max = 2000, message = "A step must be 2000 characters or fewer") String> steps) {
}
