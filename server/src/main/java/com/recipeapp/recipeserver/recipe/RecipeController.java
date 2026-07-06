package com.recipeapp.recipeserver.recipe;

import com.recipeapp.recipeserver.recipe.dto.RecipeRequest;
import com.recipeapp.recipeserver.recipe.dto.RecipeResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "controller" is the web-facing layer. It maps incoming HTTP requests
 * (URLs + methods like GET/POST) to Java methods, and turns the returned Java
 * objects back into JSON responses.
 */
// @RestController is a shorthand that combines two things:
//   - @Controller: "this class handles web requests"
//   - @ResponseBody: "return values should be written directly into the HTTP
//     response body as JSON" (rather than being treated as the name of an HTML page)
// Spring uses the Jackson library to automatically convert our DTOs to/from JSON.
@RestController
// @RequestMapping sets the base URL path for every method in this class. So every
// endpoint below lives under "/api/recipes".
@RequestMapping("/api/recipes")
public class RecipeController {

    // Same dependency-injection pattern as the service: the controller depends on
    // the service, and Spring supplies it through the constructor.
    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    // @GetMapping (with no path) handles: GET /api/recipes
    // This is the "read all recipes" endpoint. Returning a List<RecipeResponse>
    // makes Spring serialize it into a JSON array. Default HTTP status is 200 OK.
    @GetMapping
    public List<RecipeResponse> list() {
        return recipeService.findAll();
    }

    // @PostMapping (with no path) handles: POST /api/recipes
    // This is the "create a recipe" endpoint.
    @PostMapping
    // By default a successful controller method returns 200 OK. For creating a new
    // resource, the correct REST status is 201 CREATED, which we set explicitly here.
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse create(
            // @RequestBody tells Spring to read the HTTP request's JSON body and
            // deserialize it into a RecipeRequest object.
            // @Valid triggers the validation constraints we declared on
            // RecipeRequest (the @NotBlank checks). If validation fails, Spring
            // short-circuits and returns a 400 Bad Request — this method never runs.
            @Valid @RequestBody RecipeRequest request) {
        return recipeService.create(request);
    }
}
