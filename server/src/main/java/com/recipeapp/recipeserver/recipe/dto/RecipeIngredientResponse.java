package com.recipeapp.recipeserver.recipe.dto;

import com.recipeapp.recipeserver.ingredient.dto.IngredientResponse;
import com.recipeapp.recipeserver.recipe.RecipeIngredient;
import com.recipeapp.recipeserver.unit.Unit;
import java.math.BigDecimal;

/**
 * One ingredient line as sent back to the browser.
 *
 * Note that this NESTS the full IngredientResponse rather than sending a bare
 * ingredientId. The request DTO sends only the id (the server can look it up), but
 * the response sends the whole thing — because otherwise the frontend, holding a
 * recipe with eight lines, would have to fire eight more requests just to learn that
 * ingredient 12 is called "Tomato".
 *
 * That asymmetry between request and response shapes is normal and worth expecting:
 * requests should carry the MINIMUM the server needs to act, responses should carry
 * enough that the client doesn't have to ask again.
 */
public record RecipeIngredientResponse(
        Long id,
        int position,

        // The full catalog entry: id, name, category, default unit.
        IngredientResponse ingredient,

        // Null for a "to taste" line — see the Recipe entity for why that's a real case.
        BigDecimal quantity,

        // The enum constant, e.g. "TABLESPOON". The frontend sends this exact value
        // back when saving an edit, so it must round-trip unchanged.
        Unit unit,

        // The short label, e.g. "tbsp". Derived from the unit, but sent explicitly so
        // the frontend can render "2 tbsp" without shipping its own copy of the
        // enum-to-label mapping. Two copies of a mapping is two chances to disagree.
        String unitDisplay,

        String note) {

    public static RecipeIngredientResponse from(RecipeIngredient line) {
        return new RecipeIngredientResponse(
                line.getId(),
                line.getPosition(),
                IngredientResponse.from(line.getIngredient()),
                line.getQuantity(),
                line.getUnit(),
                // The null guard matters: a "to taste" line has no unit, and calling
                // getDisplayName() on null would throw a NullPointerException in the
                // middle of serializing a response — which surfaces as a 500 with a
                // half-written JSON body, one of the more confusing failures to debug.
                line.getUnit() == null ? null : line.getUnit().getDisplayName(),
                line.getNote());
    }
}
