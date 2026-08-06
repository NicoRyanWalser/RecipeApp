package com.recipeapp.recipeserver.unit;

/**
 * A "dimension" is the KIND of physical quantity a unit measures.
 *
 * Grams and pounds both measure MASS. Milliliters and cups both measure VOLUME.
 * "Pieces" and "dozen" both measure COUNT. This distinction is the single most
 * important idea in the whole shopping-list feature, because:
 *
 *   - Two amounts in the SAME dimension can ALWAYS be added together, once you
 *     convert them to a common unit. 1 kg + 200 g = 1200 g. Always. No extra
 *     knowledge required.
 *   - Two amounts in DIFFERENT dimensions can only be added if you know something
 *     extra about the specific ingredient. "4 oz of carrots + 1 carrot" is only
 *     answerable if you know how much one carrot weighs.
 *
 * That second case is why Ingredient carries gramsPerPiece and gramsPerMl, and why
 * ShoppingListService is allowed to give up and print two separate lines.
 *
 * Each dimension names its own BASE UNIT — the one unit we internally convert
 * everything into before doing any arithmetic. Picking a base unit per dimension is
 * a classic trick called "dimensional analysis": instead of writing conversion code
 * for every PAIR of units (grams<->ounces, grams<->pounds, ounces<->pounds, ...),
 * every unit only needs to know how to get to and from ONE base. That turns N*N
 * conversions into N.
 */
public enum Dimension {

    // Weight. Base unit: the gram. Chosen because it's the smallest whole-number
    // metric unit most recipes use, so conversions rarely produce ugly fractions.
    MASS("g"),

    // Liquid (and loose-solid) volume. Base unit: the milliliter.
    VOLUME("ml"),

    // Whole countable things: eggs, carrots, tortillas. Base unit: one "piece".
    // COUNT feels less "physical" than the other two, but treating it as a real
    // dimension is what lets "3 eggs" and "1 dozen eggs" combine into "15 eggs".
    COUNT("pc");

    // The short human label for this dimension's base unit ("g", "ml", "pc"). Used
    // when we format a shopping-list line and haven't picked a nicer unit yet.
    private final String baseDisplayName;

    // Enum constructors are implicitly private — you can never create a new
    // Dimension at runtime. The three constants above are the complete set, forever.
    Dimension(String baseDisplayName) {
        this.baseDisplayName = baseDisplayName;
    }

    public String getBaseDisplayName() {
        return baseDisplayName;
    }
}
