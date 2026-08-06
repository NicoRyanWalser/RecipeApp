package com.recipeapp.recipeserver.ingredient;

import com.recipeapp.recipeserver.ingredient.dto.IngredientRequest;
import com.recipeapp.recipeserver.ingredient.dto.IngredientResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for the ingredient catalog.
 */
@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    /**
     * GET /api/ingredients?q=car
     *
     * The combobox calls this on every (debounced) keystroke.
     */
    @GetMapping
    public List<IngredientResponse> search(
            // @RequestParam binds a QUERY STRING parameter — the "?q=car" part of the
            // URL — as opposed to @PathVariable (part of the path) or @RequestBody
            // (the request's payload). Those three cover essentially all input.
            //
            // required = false plus a default of "" means GET /api/ingredients with no
            // query at all is legal and returns the alphabetical first page. That is
            // the combobox's initial state, so making it a valid request rather than a
            // 400 saves the frontend a special case.
            @RequestParam(name = "q", required = false, defaultValue = "") String query) {
        return ingredientService.search(query);
    }

    /**
     * GET /api/ingredients/{id}
     */
    @GetMapping("/{id}")
    public IngredientResponse findById(
            // @PathVariable pulls the value out of the URL path itself. The {id}
            // placeholder in the mapping above and this parameter are matched by name.
            @PathVariable Long id) {
        return ingredientService.findById(id);
    }

    /**
     * POST /api/ingredients — creates an ingredient, or returns the existing one if
     * the name already maps to a known slug. See IngredientService.getOrCreate for
     * why this is deliberately forgiving rather than returning 409 Conflict.
     *
     * Because the status code varies (201 when we inserted, 200 when we found), this
     * method returns ResponseEntity rather than a plain DTO. ResponseEntity is the
     * "I want to control the whole HTTP response" type — status, headers, and body —
     * and it's what you reach for the moment a fixed @ResponseStatus won't do.
     */
    @PostMapping
    public ResponseEntity<IngredientResponse> create(@Valid @RequestBody IngredientRequest request) {
        IngredientService.CreateResult result = ingredientService.getOrCreate(request);

        // 201 CREATED means "a new resource now exists". 200 OK means "here is the
        // thing you asked about; nothing changed." Both are successes, so the frontend
        // can treat them identically — but a proxy, a log, or a future you reading the
        // access log can still tell what actually happened.
        HttpStatus status = result.wasCreated() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(result.ingredient());
    }

    // NO DELETE ENDPOINT — AND THAT IS ON PURPOSE.
    //
    // Recipes reference ingredients by foreign key. Deleting "Onion" while 40 recipes
    // point at it would violate that constraint and produce a 500, or — far worse, if
    // someone "fixed" it by adding ON DELETE CASCADE — silently delete those 40
    // recipes' onion lines. A catalog row is shared infrastructure; removing it is
    // never a local decision.
    //
    // When you do want this, the two sound options are:
    //   1. Soft delete: add a "boolean archived" field. Hide archived rows from
    //      search, keep the row so existing recipes still resolve. This is almost
    //      always the right answer for shared reference data.
    //   2. Guarded hard delete: count referencing recipe lines first and return 409
    //      Conflict with "used by 4 recipes" if any exist.
    // What is never right is ON DELETE CASCADE on a catalog table.
}
