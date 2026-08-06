package com.recipeapp.recipeserver.recipe.dto;

import com.recipeapp.recipeserver.unit.Unit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One ingredient line as submitted by the client: "ingredient 12, 800 GRAM, chopped".
 *
 * The whole shape of this record is the answer to the misspelling problem. There is
 * no name field. The client identifies the ingredient by ID — a number it can only
 * have obtained by picking a real row out of the catalog. It is not that typos are
 * rejected; it is that there is nowhere to type.
 */
public record RecipeIngredientRequest(

        // Which catalog ingredient this line refers to. @NotNull rather than @NotBlank
        // because this is a number, not text — @NotBlank only applies to Strings.
        @NotNull(message = "Each ingredient line must reference an ingredient")
        Long ingredientId,

        /**
         * How much. NULL IS ALLOWED and means "to taste" — see the Recipe entity for
         * why that is a real case rather than an oversight.
         *
         * @Positive (not @PositiveOrZero) is deliberate: it permits null, but rejects
         * 0 and negatives when a number IS given. Zero is never a meaningful quantity
         * — "0 g of salt" is not a thing a person means — and it is exactly the value
         * a frontend bug produces, because Number('') is 0 in JavaScript. Rejecting it
         * here means that bug surfaces as a clear 400 instead of a shopping list that
         * quietly tells you to buy nothing.
         */
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity,

        // Which unit the quantity is in. Nullable alongside a null quantity.
        //
        // Jackson converts the incoming JSON string "GRAM" into the enum constant
        // automatically. A value that isn't a valid constant fails at deserialization
        // with a 400, so an unknown unit can never reach the database.
        Unit unit,

        @Size(max = 120, message = "Note must be 120 characters or fewer")
        String note) {

    /**
     * A CROSS-FIELD RULE: a quantity without a unit is meaningless.
     *
     * The annotations above each guard ONE field in isolation, and no combination of
     * them can express "these two must agree". @AssertTrue is the escape hatch: it
     * runs a method you write and fails validation if it returns false. Validation
     * calls it because it follows the JavaBean convention for a boolean property —
     * a no-argument method whose name starts with "is".
     *
     * The rule matters because of what the shopping list would otherwise have to do
     * with {quantity: 2, unit: null}. Two of what? It cannot add it to anything,
     * because it has no dimension. It cannot print it, because "2" is not an amount.
     * Its only options would be to silently discard the number or to guess a unit.
     *
     * Rejecting the combination at the door means the aggregation code downstream can
     * rely on a genuinely simple invariant: no unit implies no quantity, so a
     * unit-less line is always a "to taste" line and never a data-loss bug. Pushing a
     * check to the boundary to keep the core simple is usually a good trade.
     */
    @jakarta.validation.constraints.AssertTrue(
            message = "A unit is required when a quantity is given")
    public boolean isUnitPresentWhenQuantityIsGiven() {
        return quantity == null || unit != null;
    }

    // NOTE WHAT IS ABSENT: there is no "position" field.
    //
    // A line's position is its INDEX in the submitted array, assigned server-side.
    // Letting the client send positions would mean accepting [0, 1, 1, 5] and having
    // to validate against duplicates, gaps, and negatives — an entire category of bug
    // that simply cannot occur when the order is implied by the array itself.
    //
    // This generalizes well: when a piece of data is already implied by the structure
    // of the request, deriving it beats accepting it.
}
