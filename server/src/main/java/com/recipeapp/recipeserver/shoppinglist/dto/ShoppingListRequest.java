package com.recipeapp.recipeserver.shoppinglist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * "Combine these recipes into one shopping list."
 *
 * The order of the ids is meaningful: the merged instructions come back in the same
 * order the client sent them, because that is the order the user chose.
 *
 * Duplicate ids are tolerated and DE-DUPLICATED rather than rejected. Sending recipe
 * 4 twice does not double its quantities. That is a real decision, not an oversight:
 * "select these recipes" is a set operation in the user's head, and the checkbox UI
 * cannot even express "twice". If cooking a recipe at double quantity is ever wanted,
 * it should be an explicit multiplier, not an accident of duplicate ids.
 */
public record ShoppingListRequest(

        // @NotEmpty catches both null and []. An empty selection isn't an error the
        // user can reach — the button is disabled — so if it arrives, something is
        // wrong and a clear 400 beats a mysteriously empty list.
        @NotEmpty(message = "Select at least one recipe")
        @Size(max = 50, message = "You can combine at most 50 recipes at once")
        List<Long> recipeIds) {

    // A DELIBERATE V2 HOOK, not built today: scaling.
    //
    // "Combine four recipes into a shopping list" leads almost immediately to
    // "...but I'm cooking for six". The shape that fits is an optional map alongside
    // the ids:  {"recipeIds": [1, 2], "multipliers": {"1": 2}}
    // meaning "recipe 1 at double quantity". The aggregation would need exactly one
    // extra .multiply() where it converts a line to base units, and the Recipe entity
    // already carries the servings column it would need.
    //
    // It is noted here rather than built because the interesting half of the feature
    // is the UI for choosing portions, and that is a different piece of work.
}
