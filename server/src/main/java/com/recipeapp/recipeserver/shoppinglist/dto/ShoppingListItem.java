package com.recipeapp.recipeserver.shoppinglist.dto;

import java.util.List;

/**
 * One line of the finished shopping list: everything you need to know about one
 * ingredient across every selected recipe.
 */
public record ShoppingListItem(

        Long ingredientId,
        String ingredientName,

        // The supermarket-aisle grouping ("Produce", "Dairy & Eggs"), so the frontend
        // can render the list grouped and you walk the shop once. Null for
        // user-created ingredients, which the UI collects under "Other".
        String category,

        /**
         * How much to buy. Usually one entry; more than one when amounts in different
         * dimensions could not be converted — see AmountLine for the full reasoning.
         *
         * EMPTY when every mention of this ingredient was quantity-less ("salt, to
         * taste"). An empty list plus unquantified = true is a meaningful, correct
         * state, not a missing value.
         */
        List<AmountLine> amounts,

        // True if at least one recipe called for this without a quantity. The UI shows
        // "to taste" alongside (or instead of) the amounts. Kept as a separate flag
        // rather than a fake AmountLine so the frontend never has to parse a string to
        // find out whether there's a real number.
        boolean unquantified,

        /**
         * The distinct preparation notes gathered from every line: ["chopped", "peeled"].
         *
         * These are shown but never merged or interpreted. "Chopped" and "diced" stay
         * two separate notes, because a shopping list's job is to remind you what you
         * agreed to, not to reconcile it. Notes are also why the ingredient reference
         * being an id rather than text works so well: the descriptive part of "800 g
         * chopped tomatoes" is preserved, it just lives in a field that nothing
         * computes on.
         */
        List<String> notes,

        // Which of the selected recipes wanted this. Lets the UI answer "why is this
        // on my list?", which matters most for the items you didn't expect.
        List<String> fromRecipes) {
}
