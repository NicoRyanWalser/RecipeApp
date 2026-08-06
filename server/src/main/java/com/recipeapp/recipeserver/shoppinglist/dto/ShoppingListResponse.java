package com.recipeapp.recipeserver.shoppinglist.dto;

import java.util.List;

/**
 * The finished result: a combined shopping list plus the instructions for every
 * selected recipe.
 *
 * THIS OBJECT IS NEVER STORED. It is computed on demand from the recipes and thrown
 * away — there is no shopping_lists table and no ShoppingList entity.
 *
 * That is a deliberate scope decision worth understanding, because "should this be
 * saved?" comes up constantly and the default answer is too often yes. Persisting it
 * would mean a table, an id, CRUD endpoints, and a policy for what happens when a
 * recipe changes after a list was saved (does the list update? go stale? both are
 * defensible, neither is free). Computing it is one endpoint and no schema.
 *
 * And nothing is lost by waiting: this response shape IS the contract. Adding
 * persistence later means a POST that returns this same shape with an id attached.
 * The frontend's rendering code would not change at all. Deferring a feature is
 * cheap when you leave the door open on purpose.
 */
public record ShoppingListResponse(

        // How many distinct recipes went into this. Useful for a heading, and it
        // reflects the DE-DUPLICATED count, so selecting recipe 4 twice reports 1.
        int recipeCount,

        // The combined ingredients, grouped in the order they were first encountered.
        List<ShoppingListItem> items,

        // One section per recipe, in the order the client sent the ids.
        List<InstructionSection> instructionSections) {
}
