package com.recipeapp.recipeserver.shoppinglist;

import com.recipeapp.recipeserver.shoppinglist.dto.ShoppingListRequest;
import com.recipeapp.recipeserver.shoppinglist.dto.ShoppingListResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoint that turns a set of selected recipes into one combined shopping list.
 */
@RestController
@RequestMapping("/api/shopping-list")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    /**
     * POST /api/shopping-list  with body {"recipeIds": [1, 4, 9]}
     *
     * WHY POST FOR SOMETHING THAT CHANGES NOTHING?
     *
     * This is worth pausing on, because it looks like a rule being broken. POST
     * conventionally means "this modifies something", and this endpoint modifies
     * nothing at all — the service is @Transactional(readOnly = true) and there is no
     * shopping-list table to write to. By the letter of REST it should be a GET.
     *
     * The practical problem is that GET requests carry their input in the URL, so a
     * selection would have to be ?recipeIds=1,4,9. That works, right up until someone
     * selects forty recipes and the URL runs past the length limits that proxies and
     * servers quietly enforce (there is no standard maximum, which is worse than a
     * low one — it fails in some deployments and not others). A request BODY has no
     * such limit.
     *
     * So: POST, with a comment explaining that it is safe and idempotent despite the
     * verb. That is a normal, defensible trade, and this is roughly how every "search
     * with a complex filter" endpoint on the internet ends up working. Worth knowing
     * both the rule and why it gets bent.
     *
     * Status is 200 OK (the default), not 201 — nothing was created.
     */
    @PostMapping
    public ShoppingListResponse combine(@Valid @RequestBody ShoppingListRequest request) {
        return shoppingListService.combine(request);
    }
}
