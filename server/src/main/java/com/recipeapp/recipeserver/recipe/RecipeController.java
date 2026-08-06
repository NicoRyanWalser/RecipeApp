package com.recipeapp.recipeserver.recipe;

import com.recipeapp.recipeserver.recipe.dto.RecipeDetailResponse;
import com.recipeapp.recipeserver.recipe.dto.RecipeRequest;
import com.recipeapp.recipeserver.recipe.dto.RecipeSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "controller" is the web-facing layer. It maps incoming HTTP requests
 * (URLs + methods like GET/POST) to Java methods, and turns the returned Java
 * objects back into JSON responses.
 *
 * With all five methods present this is now a full CRUD resource — Create, Read,
 * Update, Delete, the four operations essentially every data-backed app performs.
 * The REST convention maps them onto HTTP verbs and one URL shape:
 *
 *   GET    /api/recipes        list      (read many)
 *   GET    /api/recipes/{id}   detail    (read one)
 *   POST   /api/recipes        create
 *   PUT    /api/recipes/{id}   update
 *   DELETE /api/recipes/{id}   delete
 *
 * The pattern is worth internalizing because it's near-universal: the noun lives in
 * the URL, the verb lives in the HTTP method. A URL like /api/createRecipe is the
 * common beginner shape and works fine, but it fights every tool that assumes REST.
 */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    // GET /api/recipes — every recipe, newest first, as lightweight summaries.
    @GetMapping
    public List<RecipeSummaryResponse> list() {
        return recipeService.findAll();
    }

    // GET /api/recipes/{id} — one recipe in full, including its lines and steps.
    // Returns 404 if the id doesn't exist, via NotFoundException and the global
    // handler. Note the controller contains no error handling of its own.
    @GetMapping("/{id}")
    public RecipeDetailResponse detail(@PathVariable Long id) {
        return recipeService.findById(id);
    }

    // POST /api/recipes — create.
    @PostMapping
    // By default a successful controller method returns 200 OK. For creating a new
    // resource, the correct REST status is 201 CREATED, which we set explicitly here.
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeDetailResponse create(
            // @RequestBody tells Spring to read the HTTP request's JSON body and
            // deserialize it into a RecipeRequest object.
            // @Valid triggers the validation constraints declared on RecipeRequest.
            // If validation fails, Spring short-circuits and GlobalExceptionHandler
            // returns a 400 with per-field messages — this method never runs.
            @Valid @RequestBody RecipeRequest request) {
        return recipeService.create(request);
    }

    /**
     * PUT /api/recipes/{id} — update.
     *
     * PUT means REPLACE: the body describes what the recipe should look like
     * afterwards, in full. Send four ingredients and the recipe has exactly those
     * four, whatever it had before. That is why the same RecipeRequest works for both
     * create and update, and why removing a line is expressed simply by not sending it.
     *
     * The alternative verb is PATCH, which means "apply this partial change" — send
     * only the fields you want altered. PATCH is friendlier over slow connections and
     * meaningfully harder to implement correctly: you need a way to distinguish "this
     * field was omitted" from "this field was explicitly set to null", which JSON does
     * not naturally give you. PUT is the right call for a form that submits the whole
     * recipe anyway.
     *
     * Returns 200 OK (the default), not 201 — nothing new was created.
     */
    @PutMapping("/{id}")
    public RecipeDetailResponse update(
            @PathVariable Long id,
            @Valid @RequestBody RecipeRequest request) {
        return recipeService.update(id, request);
    }

    /**
     * DELETE /api/recipes/{id}
     *
     * 204 NO CONTENT is the conventional success status for a delete: it means "that
     * worked, and there is deliberately no response body". Returning 200 with an empty
     * body would also work, but 204 says the emptiness is intentional rather than an
     * accident, and clients can skip trying to parse a body that isn't there.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        recipeService.delete(id);
    }
}
