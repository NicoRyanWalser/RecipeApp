package com.recipeapp.recipeserver.unit.dto;

import com.recipeapp.recipeserver.unit.Unit;

/**
 * A DTO describing one unit to the frontend, so the recipe form can build its
 * unit dropdown from real server data instead of a hardcoded list in TypeScript.
 *
 * Why bother, when the frontend could just hardcode the same 16 options? Because
 * two hardcoded lists ALWAYS drift apart eventually. Add a unit to the enum, forget
 * the TypeScript, and now the UI can't produce a value the backend happily accepts.
 * Serving the list from the enum makes drift structurally impossible — there is
 * only one list, and it lives next to the conversion factors it belongs to.
 *
 * Note what is NOT here: factorToBase. All conversion arithmetic happens on the
 * server, so the browser never needs the factor and we don't ship it.
 */
public record UnitResponse(
        // The exact enum constant name, e.g. "TABLESPOON". This is the value the
        // frontend sends back in a RecipeIngredientRequest, so it must match the
        // enum spelling exactly — which it does, because we generate it from it.
        String code,

        // The short human label, e.g. "tbsp". This is what the dropdown shows.
        String display,

        // "MASS" / "VOLUME" / "COUNT". The frontend can use this to group the
        // dropdown into sections, so a user picking a unit for flour sees the
        // weight units together rather than interleaved with teaspoons.
        String dimension) {

    // The same static factory pattern used by RecipeResponse.from — one place that
    // knows how to turn the internal type into its public shape.
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                // name() is built into every Java enum and returns the constant's
                // exact declared name as a String.
                unit.name(),
                unit.getDisplayName(),
                unit.getDimension().name());
    }
}
