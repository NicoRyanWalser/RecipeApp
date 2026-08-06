package com.recipeapp.recipeserver.ingredient;

import com.recipeapp.recipeserver.common.NotFoundException;
import com.recipeapp.recipeserver.common.Slugs;
import com.recipeapp.recipeserver.ingredient.dto.IngredientRequest;
import com.recipeapp.recipeserver.ingredient.dto.IngredientResponse;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the ingredient catalog: searching it, and adding to it.
 */
@Service
public class IngredientService {

    // A hard cap on how many rows a search can return. The combobox shows a dropdown
    // list — nobody scrolls past 20 options, and an uncapped query on a growing table
    // is a slow request waiting to happen.
    private static final int MAX_SEARCH_RESULTS = 20;

    private final IngredientRepository ingredientRepository;

    public IngredientService(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    /**
     * Powers the combobox. An empty query returns the first alphabetical page, so the
     * dropdown has something useful in it before the user types a single character.
     */
    @Transactional(readOnly = true)
    public List<IngredientResponse> search(String query) {
        Limit limit = Limit.of(MAX_SEARCH_RESULTS);

        // The search runs against the SLUG, so we must normalize the user's query the
        // exact same way the stored slugs were normalized. Searching a lowercased
        // column with a capitalized term would silently return nothing — one of those
        // bugs that looks like "the database is broken" for an hour.
        String slugQuery = Slugs.of(query);

        List<Ingredient> found = slugQuery.isEmpty()
                ? ingredientRepository.findAllByOrderByNameAsc(limit)
                : ingredientRepository.search(slugQuery, limit);

        return found.stream()
                .map(IngredientResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IngredientResponse findById(Long id) {
        return ingredientRepository.findById(id)
                .map(IngredientResponse::from)
                // orElseThrow is why NotFoundException exists: the "missing" case is
                // handled here in one expression, and GlobalExceptionHandler turns it
                // into a 404 without this method knowing what HTTP is.
                .orElseThrow(() -> NotFoundException.of("Ingredient", id));
    }

    /**
     * GET-OR-CREATE. Returns the existing ingredient if this name already maps to a
     * known slug; otherwise inserts a new one.
     *
     * WHY NOT REJECT DUPLICATES WITH A 409 CONFLICT? That is the REST-purist answer,
     * and it would be defensible. But consider the caller: the combobox has just
     * offered "Create 'shallot'" and the user clicked it. If the server responds 409,
     * the frontend must catch the error, issue a SECOND request to find the row that
     * already existed, and then proceed — a branch that only runs in a rare race, so
     * it is exactly the branch that will be written wrong and never tested.
     *
     * Get-or-create gives the caller ONE code path: post the name, take the body,
     * select it. The endpoint still distinguishes the cases in its status code (201
     * created vs 200 found) for anyone who cares, but nobody has to care to be correct.
     *
     * This is a good instinct to keep: when an API design forces every client to write
     * the same recovery logic, that logic probably belongs in the API.
     */
    @Transactional
    public CreateResult getOrCreate(IngredientRequest request) {
        String slug = Slugs.of(request.name());

        // A NOTE ON THE RACE CONDITION WE ARE ACCEPTING. Two simultaneous requests to
        // create "Shallot" could both find nothing below and both try to insert. The
        // database's UNIQUE constraint on slug means the second INSERT fails rather
        // than creating a duplicate — so the DATA stays correct, and the loser just
        // gets a 500. For a single-user learning app that is a fine trade. The
        // production fix is to catch DataIntegrityViolationException and retry the
        // lookup once, because by then the winner's row is there to be found.

        // The lookup. If "Shallot", "shallot", or "  SHALLOT " was ever added, they
        // all normalize to the same slug and we find the existing row.
        return ingredientRepository.findBySlug(slug)
                .map(existing -> new CreateResult(IngredientResponse.from(existing), false))
                .orElseGet(() -> {
                    // Not found, so create it. Name only — see IngredientRequest for
                    // why users don't supply categories or conversion factors.
                    Ingredient saved = ingredientRepository.save(new Ingredient(request.name()));
                    return new CreateResult(IngredientResponse.from(saved), true);
                });
    }

    /**
     * The service's return type for getOrCreate: the ingredient, plus whether it was
     * newly inserted.
     *
     * Why not just return IngredientResponse? Because the controller needs to choose
     * between HTTP 201 and 200, and it cannot tell from the ingredient alone. This
     * carries the one extra fact across the layer boundary without the service having
     * to know what a status code is.
     *
     * Declaring it INSIDE the service, rather than in the dto package, is intentional:
     * a dto is part of the public API shape, and this is an internal detail between
     * two of our own layers. It never gets serialized to JSON.
     */
    public record CreateResult(IngredientResponse ingredient, boolean wasCreated) {
    }
}
