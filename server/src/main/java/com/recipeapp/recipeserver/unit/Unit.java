package com.recipeapp.recipeserver.unit;

import java.math.BigDecimal;

/**
 * A "unit" is how a quantity is measured: grams, cups, tablespoons, pieces.
 *
 * WHY IS THIS AN ENUM AND NOT A DATABASE TABLE?
 * This is a real design decision, and the reasoning generalizes. A unit's
 * conversion factor is a LAW OF PHYSICS, not user data:
 *   - It never changes. A kilogram will always be 1000 grams.
 *   - No user will ever add one. The set is closed.
 *   - Our own code needs to name specific units (Unit.GRAM) to format output.
 *     You cannot write "Unit.GRAM" against a database row.
 * Making it a table would mean seeding it, joining to it on every single read, and
 * accepting that someone could one day store a factor of 0 and silently break every
 * division in the app. An enum makes all of those impossible by construction.
 *
 * The rule of thumb: if the values are fixed by the outside world and your code
 * refers to them by name, use an enum. If users create them, use a table.
 * ("Ingredient" is the opposite case — users DO create those — so it IS a table.)
 *
 * Each constant records three things: which Dimension it belongs to, how to convert
 * it to that dimension's base unit, and a short label to show a human.
 */
public enum Unit {

    // ---- MASS (base unit: GRAM) --------------------------------------------
    // Read the numbers as "one of THIS equals N of the base unit".
    // One kilogram = 1000 grams, so KILOGRAM's factor is 1000.
    MILLIGRAM(Dimension.MASS, "0.001", "mg"),
    GRAM(Dimension.MASS, "1", "g"),
    KILOGRAM(Dimension.MASS, "1000", "kg"),
    // The imperial units are exact definitions, not approximations: one pound is
    // DEFINED as exactly 453.59237 grams, and an ounce is exactly 1/16 of that.
    OUNCE(Dimension.MASS, "28.349523125", "oz"),
    POUND(Dimension.MASS, "453.59237", "lb"),

    // ---- VOLUME (base unit: MILLILITER) ------------------------------------
    MILLILITER(Dimension.VOLUME, "1", "ml"),
    CENTILITER(Dimension.VOLUME, "10", "cl"),
    DECILITER(Dimension.VOLUME, "100", "dl"),
    LITER(Dimension.VOLUME, "1000", "l"),
    // Cooking volumes are regional and genuinely inconsistent in the real world:
    // a US cup is 240 ml, a metric cup is 250 ml, an Australian tablespoon is 20 ml
    // rather than 15. We commit to US customary here and say so, because silently
    // mixing conventions is how a recipe ends up 20% too salty.
    TEASPOON(Dimension.VOLUME, "4.92892", "tsp"),
    TABLESPOON(Dimension.VOLUME, "14.78676", "tbsp"),
    FLUID_OUNCE(Dimension.VOLUME, "29.5735", "fl oz"),
    CUP(Dimension.VOLUME, "240", "cup"),
    // A pinch is not a real measurement, but recipes use it constantly. Treating it
    // as 1/16 tsp lets it participate in arithmetic instead of being a special case.
    PINCH(Dimension.VOLUME, "0.308", "pinch"),

    // ---- COUNT (base unit: PIECE) ------------------------------------------
    PIECE(Dimension.COUNT, "1", "pc"),
    DOZEN(Dimension.COUNT, "12", "dozen");

    // Which kind of quantity this unit measures. See Dimension for why this matters.
    private final Dimension dimension;

    // Multiply an amount in THIS unit by this number to get the amount in the
    // dimension's base unit. 2 kg -> 2 * 1000 -> 2000 g.
    //
    // Note this is a BigDecimal built from a String, not a double. Binary floating
    // point cannot represent most decimal fractions exactly: in Java, 0.1 + 0.2 is
    // 0.30000000000000004. A shopping list reading "0.30000000000000004 kg flour"
    // is a bug you would have to explain to someone. BigDecimal stores decimal
    // digits exactly. It MUST be constructed from a String — new BigDecimal(0.1)
    // captures the already-wrong double and defeats the whole point.
    private final BigDecimal factorToBase;

    // The short label a human reads on a shopping list: "tbsp", "kg", "pc".
    private final String displayName;

    Unit(Dimension dimension, String factorToBase, String displayName) {
        this.dimension = dimension;
        this.factorToBase = new BigDecimal(factorToBase);
        this.displayName = displayName;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public BigDecimal getFactorToBase() {
        return factorToBase;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Converts an amount expressed in THIS unit into the base unit of its dimension.
     * 2 KILOGRAM -> 2000 (grams). This is the only place the factor is applied, so
     * there is exactly one line of code in the app that can get it wrong.
     */
    public BigDecimal toBase(BigDecimal amount) {
        return amount.multiply(factorToBase);
    }

    // DELIBERATELY NOT IMPLEMENTED: parsing units from free text.
    // There is no fromString("tbsp") / alias table here, because the frontend picks
    // units from a dropdown that is populated by GET /api/units — so a unit can only
    // ever arrive as one of the exact constant names above. If this app ever accepted
    // typed units, you would add a Set<String> aliases field ("tbsp", "Tbsp",
    // "tablespoon", "tablespoons") plus a static Map<String, Unit> lookup built once
    // in a static block. That is a real feature with real ambiguity ("oz" means mass,
    // "fl oz" means volume) — worth avoiding until you actually need it.
}
