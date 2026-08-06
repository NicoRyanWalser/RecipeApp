package com.recipeapp.recipeserver.shoppinglist.dto;

import com.recipeapp.recipeserver.unit.Unit;
import java.math.BigDecimal;

/**
 * ONE printable amount of one ingredient: "1.4 kg", "500 ml", "3 pc".
 *
 * WHY AN INGREDIENT CAN HAVE MORE THAN ONE OF THESE — this is the heart of the whole
 * design, so it's worth stating plainly.
 *
 * Two recipes call for milk. One wants 500 ml, the other wants 200 g. To print a
 * single combined number, the server would need to know milk's density — and if the
 * catalog doesn't have it, there are only three options:
 *
 *   1. Guess a density. Silently wrong, and wrong in a way nobody can detect from
 *      the output.
 *   2. Throw an error. Refuses to produce a shopping list at all, over one line.
 *   3. Print both amounts and let the human resolve it.
 *
 * The third is the only honest one, and it is why "amounts" is a LIST. In the common
 * case it holds exactly one entry. When a conversion isn't possible it holds two, and
 * the user reads "500 ml + 200 g" and knows perfectly well what to buy.
 *
 * The general principle is worth carrying elsewhere: when a computation cannot be
 * done correctly, degrade to showing the inputs rather than inventing an output.
 */
public record AmountLine(

        // The numeric amount in the chosen display unit, e.g. 1.4.
        BigDecimal quantity,

        // The unit constant, e.g. KILOGRAM. Present in case the frontend wants to do
        // anything unit-aware; it mostly won't.
        Unit unit,

        // The short label, e.g. "kg".
        String unitDisplay,

        // The pre-formatted string, e.g. "1.4 kg". Formatting is done server-side so
        // that the rounding rules live in one place next to the arithmetic that
        // produced the number, rather than being re-implemented in TypeScript.
        String display) {

    public static AmountLine of(BigDecimal quantity, Unit unit) {
        // toPlainString avoids scientific notation. Without it, a very small amount
        // can render as "3E-2 g", which is not something anyone wants on a shopping
        // list. Rounding and zero-stripping have already happened by this point.
        String display = quantity.toPlainString() + " " + unit.getDisplayName();
        return new AmountLine(quantity, unit, unit.getDisplayName(), display);
    }
}
