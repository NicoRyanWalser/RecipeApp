package com.recipeapp.recipeserver.recipe;

import com.recipeapp.recipeserver.common.NotFoundException;
import com.recipeapp.recipeserver.ingredient.Ingredient;
import com.recipeapp.recipeserver.ingredient.IngredientRepository;
import com.recipeapp.recipeserver.recipe.dto.RecipeDetailResponse;
import com.recipeapp.recipeserver.recipe.dto.RecipeIngredientRequest;
import com.recipeapp.recipeserver.recipe.dto.RecipeRequest;
import com.recipeapp.recipeserver.recipe.dto.RecipeSummaryResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "service" layer holds the business logic and sits BETWEEN the controller
 * (which handles HTTP) and the repository (which handles the database).
 *
 * Why have a separate layer at all? It's a convention that keeps responsibilities
 * clean:
 *   - Controller  = "what does the web look like?" (URLs, status codes, JSON)
 *   - Service     = "what are the business rules?" (validation orchestration,
 *                    transactions, converting entities <-> DTOs)
 *   - Repository  = "how do we talk to the database?"
 *
 * That separation earns its keep here in a way it didn't when a recipe was three
 * strings: creating one now means resolving ingredient ids against the catalog,
 * building child entities, assigning positions, and doing it all inside a single
 * transaction so a half-written recipe can never exist.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;

    // The recipe service needs the ingredient catalog, because a request arrives
    // carrying ingredient IDs and those must be turned into real Ingredient entities
    // before a line can be built.
    private final IngredientRepository ingredientRepository;

    public RecipeService(RecipeRepository recipeRepository, IngredientRepository ingredientRepository) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
    }

    /**
     * The list view. Returns lightweight summaries — see RecipeSummaryResponse for
     * why this deliberately does not return full recipes.
     */
    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> findAll() {
        // No .stream().map(...) here: the projection query builds the DTOs itself,
        // inside the database. There is nothing left to convert.
        return recipeRepository.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public RecipeDetailResponse findById(Long id) {
        Recipe recipe = loadOrThrow(id);
        // The mapping happens HERE, inside the transaction, because the recipe's
        // ingredients and steps are lazy collections. See RecipeDetailResponse.from
        // for the full explanation of why that placement is not optional.
        return RecipeDetailResponse.from(recipe);
    }

    @Transactional
    public RecipeDetailResponse create(RecipeRequest request) {
        // 1. Build the parent row from the fields the client is allowed to set. Note
        //    the client's data is used, while id, createdAt and updatedAt are handled
        //    by the entity and database — the client can't spoof them.
        Recipe recipe = new Recipe(request.name(), request.description(), request.servings());

        // 2. Turn the submitted ingredient lines into real child entities, which
        //    includes checking that every referenced ingredient actually exists.
        // 3. Attach children to the parent. This is what assigns positions and sets
        //    each line's back-reference to the recipe.
        recipe.setInitialContent(buildLines(request), List.copyOf(request.steps()));

        // 4. One save() writes the recipe AND all of its children, because the
        //    @OneToMany is declared with cascade = ALL. Without that cascade we would
        //    have to save every line individually and in the right order.
        return RecipeDetailResponse.from(recipeRepository.save(recipe));
    }

    @Transactional
    public RecipeDetailResponse update(Long id, RecipeRequest request) {
        Recipe recipe = loadOrThrow(id);

        // applyChanges is the recipe's single named mutator — see Recipe.applyChanges
        // for why this is one method rather than a handful of setters. It replaces the
        // child collections in place, which is what makes orphanRemoval delete the
        // lines the user removed.
        recipe.applyChanges(
                request.name(),
                request.description(),
                request.servings(),
                buildLines(request),
                List.copyOf(request.steps()));

        // No explicit save() call is needed and this is worth understanding rather
        // than memorizing. Inside a transaction, an entity loaded from the database is
        // MANAGED: Hibernate holds a snapshot of it and, at commit time, compares the
        // current state against that snapshot and issues UPDATE/INSERT/DELETE for
        // whatever differs. This is called "dirty checking". Calling save() here would
        // be harmless but redundant.
        //
        // The flip side, and the reason people find this confusing: it means you can
        // accidentally persist a change you only meant to make in memory. Mutating a
        // managed entity inside a transaction always hits the database.
        return RecipeDetailResponse.from(recipe);
    }

    @Transactional
    public void delete(Long id) {
        // existsById first, so deleting a recipe that isn't there produces a clean 404
        // rather than silently succeeding. deleteById is a no-op for a missing row,
        // which would tell the user "deleted!" about something that never existed.
        if (!recipeRepository.existsById(id)) {
            throw NotFoundException.of("Recipe", id);
        }
        // The child rows go too, via cascade = ALL plus orphanRemoval. Without those,
        // this would fail on a foreign key violation: you cannot delete a parent row
        // while children still point at it.
        recipeRepository.deleteById(id);
    }

    private Recipe loadOrThrow(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Recipe", id));
    }

    /**
     * Converts the submitted ingredient lines into RecipeIngredient entities.
     *
     * The interesting part is how the ingredient IDs get resolved. The naive version
     * calls ingredientRepository.findById(...) inside the loop — one query per line,
     * which is the N+1 problem yet again, this time on a write path. Instead we
     * collect every id first and fetch them in a single findAllById.
     *
     * Recognizing this shape is most of the skill: any time a loop body performs a
     * lookup, ask whether the whole set can be fetched once before the loop.
     */
    private List<RecipeIngredient> buildLines(RecipeRequest request) {
        // 1. Gather the distinct ingredient ids the request refers to. A LinkedHashSet
        //    de-duplicates (a recipe legitimately might list "Olive Oil" twice, for
        //    the pan and for drizzling) while preserving order for a stable error
        //    message if one is missing.
        Set<Long> requestedIds = request.ingredients().stream()
                .map(RecipeIngredientRequest::ingredientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 2. One query for all of them, indexed by id so the loop below is a cheap
        //    map lookup rather than a database round trip.
        Map<Long, Ingredient> byId = ingredientRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));

        // 3. Fail loudly if the client referenced an ingredient that doesn't exist.
        //    Doing this BEFORE building anything means we never half-construct a
        //    recipe and then abandon it. The message names the offending id, because
        //    "not found" without saying what is a frustrating error to receive.
        for (Long requestedId : requestedIds) {
            if (!byId.containsKey(requestedId)) {
                throw NotFoundException.of("Ingredient", requestedId);
            }
        }

        // 4. Build one line per submitted entry, in the order they were sent. The
        //    position is NOT set here — Recipe.replaceIngredients assigns it from the
        //    list index, so ordering lives in exactly one place.
        return request.ingredients().stream()
                .map(line -> new RecipeIngredient(
                        byId.get(line.ingredientId()),
                        line.quantity(),
                        line.unit(),
                        line.note()))
                .toList();
    }
}
