package com.recipeapp.recipeserver.ingredient.dto;

import com.recipeapp.recipeserver.ingredient.Ingredient;
import com.recipeapp.recipeserver.unit.Unit;

/**
 * The public shape of a catalog ingredient — what the combobox renders and what gets
 * nested inside each line of a recipe's detail response.
 */
public record IngredientResponse(
        Long id,
        String name,
        String category,

        // Sent as the enum CONSTANT NAME ("TABLESPOON"), because that is what the
        // frontend must send back when saving a recipe line. Jackson serializes a
        // Java enum to its name() by default, which is exactly what we want here.
        Unit defaultUnit,

        // Whether this ingredient knows how to convert between weight and count, and
        // between weight and volume.
        //
        // We expose the BOOLEANS rather than the raw factors, because the browser has
        // no use for the numbers — all conversion happens server-side — but it does
        // have a use for the fact. It lets the UI show a quiet hint like "amounts for
        // this ingredient may not combine" next to an ingredient the user just
        // created, which explains a split shopping-list line BEFORE it surprises them.
        boolean hasPieceWeight,
        boolean hasDensity) {

    public static IngredientResponse from(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getCategory(),
                ingredient.getDefaultUnit(),
                ingredient.getGramsPerPiece() != null,
                ingredient.getGramsPerMl() != null);
    }
}
