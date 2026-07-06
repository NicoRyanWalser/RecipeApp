package com.recipeapp.recipeserver.recipe;

import com.recipeapp.recipeserver.recipe.dto.RecipeRequest;
import com.recipeapp.recipeserver.recipe.dto.RecipeResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "service" layer holds the business logic and sits BETWEEN the controller
 * (which handles HTTP) and the repository (which handles the database).
 *
 * Why have a separate layer at all for such simple logic? It's a convention that
 * keeps responsibilities clean:
 *   - Controller  = "what does the web look like?" (URLs, status codes, JSON)
 *   - Service     = "what are the business rules?" (validation orchestration,
 *                    transactions, converting entities <-> DTOs)
 *   - Repository  = "how do we talk to the database?"
 * As the app grows, this separation is what keeps it maintainable.
 */
// @Service marks this class as a Spring-managed "bean". At startup Spring creates
// ONE shared instance and makes it available to be injected wherever it's needed
// (here, into the controller). This is "Dependency Injection" — you never write
// "new RecipeService()" yourself; the framework builds and wires objects for you.
@Service
public class RecipeService {

    // The service needs the repository to reach the database. We store it as a
    // "final" field so it can never be reassigned after construction.
    private final RecipeRepository recipeRepository;

    // "Constructor injection": Spring sees this constructor needs a
    // RecipeRepository, finds the one it auto-generated (see RecipeRepository),
    // and passes it in automatically. This is the recommended DI style because it
    // makes dependencies explicit and the object impossible to build half-wired.
    public RecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    // @Transactional wraps this method in a database transaction. readOnly = true
    // is a hint that we're only reading (no writes), which lets the database and
    // Hibernate optimize. If anything threw mid-method, the transaction would roll
    // back cleanly.
    @Transactional(readOnly = true)
    public List<RecipeResponse> findAll() {
        // 1. Ask the repository for all recipes, newest first (our derived query).
        // 2. Turn the list into a "stream" so we can transform each element.
        // 3. map(...) converts every Recipe ENTITY into a Recipe RESPONSE DTO,
        //    so we never expose raw entities to the web layer.
        // 4. toList() collects the transformed items back into a List.
        return recipeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(RecipeResponse::from)
                .toList();
    }

    // No readOnly here because this method WRITES to the database. The whole method
    // runs inside one transaction: if save() failed, nothing would be committed.
    @Transactional
    public RecipeResponse create(RecipeRequest request) {
        // Convert the incoming request DTO into a real Recipe entity. Note the
        // client's data (name/ingredients/instructions) is used, while id and
        // createdAt are handled by the entity/database — the client can't spoof them.
        Recipe recipe = new Recipe(request.name(), request.ingredients(), request.instructions());

        // save() INSERTs the row and returns the SAME entity now populated with the
        // database-generated id (and the createdAt we set). We immediately convert
        // that saved entity into a response DTO to hand back to the controller.
        return RecipeResponse.from(recipeRepository.save(recipe));
    }
}
