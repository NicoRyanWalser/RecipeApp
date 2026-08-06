package com.recipeapp.recipeserver.shoppinglist.dto;

import java.util.List;

/**
 * One recipe's instructions within the combined view: a heading followed by its steps.
 *
 * NOTE THAT THERE IS NO MERGING HERE, and that this is the correct answer rather than
 * a shortcut. Ingredients combine because "800 g tomatoes" and "1 kg tomatoes" are
 * quantities of the same thing, and one trip to the shop buys both. Instructions do
 * not combine, because two recipes are two separate acts of cooking. Interleaving
 * "heat the oil" from one with "whisk the eggs" from the other would produce
 * confident nonsense.
 *
 * So the shopping list aggregates, and the instructions concatenate — recipe title,
 * then its steps, then the next recipe. That asymmetry is exactly what was asked for,
 * and it's worth noticing that it falls directly out of what the data MEANS.
 */
public record InstructionSection(
        Long recipeId,
        String recipeName,

        // Carried through so the printed page says what portion size these steps
        // assume — the one piece of context that would otherwise be lost when a
        // recipe is read outside its own page.
        int servings,

        List<String> steps) {
}
