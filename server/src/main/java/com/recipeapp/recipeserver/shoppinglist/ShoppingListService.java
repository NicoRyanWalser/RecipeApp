package com.recipeapp.recipeserver.shoppinglist;

import com.recipeapp.recipeserver.common.NotFoundException;
import com.recipeapp.recipeserver.ingredient.Ingredient;
import com.recipeapp.recipeserver.recipe.Recipe;
import com.recipeapp.recipeserver.recipe.RecipeIngredient;
import com.recipeapp.recipeserver.recipe.RecipeIngredientRepository;
import com.recipeapp.recipeserver.recipe.RecipeRepository;
import com.recipeapp.recipeserver.shoppinglist.dto.AmountLine;
import com.recipeapp.recipeserver.shoppinglist.dto.InstructionSection;
import com.recipeapp.recipeserver.shoppinglist.dto.ShoppingListItem;
import com.recipeapp.recipeserver.shoppinglist.dto.ShoppingListRequest;
import com.recipeapp.recipeserver.shoppinglist.dto.ShoppingListResponse;
import com.recipeapp.recipeserver.unit.Dimension;
import com.recipeapp.recipeserver.unit.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Combines several recipes into one shopping list.
 *
 * This is the most interesting code in the application, so it is worth reading
 * slowly. The problem it solves:
 *
 *   Recipe A wants "1 carrot".
 *   Recipe B wants "4 oz carrots".
 *   How many carrots do you buy?
 *
 * A naive implementation compares ingredient names and gives up, or adds 1 + 4 and
 * produces "5 of something". The reason this one can do better is entirely due to
 * decisions made earlier in the data model: both lines point at the SAME catalog row
 * (so they are known to be the same food), each unit knows its dimension and its
 * conversion factor (so "4 oz" is really 113 g), and the carrot itself carries
 * gramsPerPiece = 61 (so "1 carrot" is really 61 g). 174 g, or about three carrots.
 *
 * THE ALGORITHM, in four stages:
 *
 *   1. BUCKET   — group every line by (ingredient, dimension). Mass amounts of
 *                 tomatoes go in one bucket, volume amounts in another.
 *   2. SUM      — add up each bucket in its dimension's BASE unit, so 1 kg and 200 g
 *                 become 1000 and 200 rather than two incomparable numbers.
 *   3. MERGE    — try to fold an ingredient's buckets together using that
 *                 ingredient's own conversion factors. When there is no factor,
 *                 STOP AND KEEP THEM SEPARATE. This is the interesting stage.
 *   4. FORMAT   — pick a readable unit per amount (1500 g reads better as "1.5 kg").
 *
 * The design rule running through all of it: NEVER GUESS, NEVER THROW. Every path
 * through this code produces a shopping list. When the data is insufficient to
 * combine two amounts, the output shows both amounts rather than inventing a number
 * or refusing to answer.
 */
@Service
public class ShoppingListService {

    /**
     * When an ingredient appears in more than one dimension, which one do we try to
     * express the total in?
     *
     * Grams first, because weight is what a shopping list is usually best in and what
     * a kitchen scale can verify. Volume second. Count last — "173 g of carrot" is
     * more useful than "2.8 carrots", and a fractional count reads like an error.
     *
     * This is a list rather than a single choice because the preferred dimension might
     * not be present at all: an ingredient measured only in ml and pieces should
     * settle on ml, not fall back to nothing.
     */
    private static final List<Dimension> MERGE_PREFERENCE =
            List.of(Dimension.MASS, Dimension.VOLUME, Dimension.COUNT);

    // How many decimal places to keep in intermediate conversions. Three is well past
    // anything meaningful for shopping and keeps rounding from accumulating visibly.
    private static final int CONVERSION_SCALE = 3;

    // How many decimal places to SHOW. "1.43 kg" is useful; "1.4288 kg" is noise.
    private static final int DISPLAY_SCALE = 2;

    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeRepository recipeRepository;

    public ShoppingListService(RecipeIngredientRepository recipeIngredientRepository,
                               RecipeRepository recipeRepository) {
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * readOnly = true, and it means it: this method computes and returns, it never
     * writes. Marking it so lets Hibernate skip dirty-checking and tells the database
     * it can optimize. It also documents the intent — a POST endpoint that changes
     * nothing is unusual enough to be worth stating.
     */
    @Transactional(readOnly = true)
    public ShoppingListResponse combine(ShoppingListRequest request) {

        // ---- STAGE 0: validate and de-duplicate the selection ------------------
        // A LinkedHashSet does both jobs at once: it removes duplicates and preserves
        // the order the ids arrived in, which is the order the instruction sections
        // will use. A plain HashSet would de-duplicate but scramble the order.
        Set<Long> recipeIds = new LinkedHashSet<>(request.recipeIds());

        // Load the recipes up front, both to build the instruction sections and to
        // verify every id is real. Checking here means a bad id produces a clean 404
        // rather than a silently short shopping list — the kind of failure a user
        // would never notice until they got to the shop.
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);
        if (recipes.size() != recipeIds.size()) {
            Set<Long> found = recipes.stream().map(Recipe::getId).collect(Collectors.toSet());
            // Name the first missing id. "Recipe 7 was not found" is actionable;
            // "some recipes were not found" sends the reader back to the request to
            // work out which. Error messages are part of the interface.
            Long missing = recipeIds.stream()
                    .filter(id -> !found.contains(id))
                    .findFirst()
                    .orElseThrow();
            throw NotFoundException.of("Recipe", missing);
        }

        // findAllById does not promise to preserve the order we asked in, so index the
        // results and re-read them in the client's order below.
        Map<Long, Recipe> recipesById = new LinkedHashMap<>();
        recipes.forEach(recipe -> recipesById.put(recipe.getId(), recipe));

        // ---- STAGE 1: bucket every line by (ingredient, dimension) ---------------
        // One query fetches every line across every selected recipe, with the catalog
        // ingredient already joined in. See RecipeIngredientRepository for why the
        // JOIN FETCH there matters so much on this particular path.
        List<RecipeIngredient> lines = recipeIngredientRepository.findAllForRecipes(recipeIds);

        // The shape is a map of maps: ingredient id -> dimension -> running total.
        //
        // WHY KEY BY ID RATHER THAN BY THE Ingredient OBJECT? Entities do not override
        // equals/hashCode here, so they compare by object identity. Within a single
        // transaction Hibernate does guarantee one instance per row, so using the
        // entity as a key would actually work — but it would work for a subtle reason
        // that stops being true the moment an entity crosses a transaction boundary.
        // Keying by the primary key is correct for a reason you can state in one line,
        // which is a better foundation than correct-by-coincidence.
        Map<Long, Map<Dimension, Bucket>> buckets = new LinkedHashMap<>();
        // Keep the Ingredient objects to hand for their names and conversion factors.
        Map<Long, Ingredient> ingredientsById = new LinkedHashMap<>();

        for (RecipeIngredient line : lines) {
            Ingredient ingredient = line.getIngredient();
            ingredientsById.putIfAbsent(ingredient.getId(), ingredient);

            // A null unit means a "to taste" line, which has no dimension. Null is a
            // legal key in a LinkedHashMap, so such lines get their own bucket and are
            // never summed with anything — exactly the isolation we want.
            Dimension dimension = (line.getUnit() == null) ? null : line.getUnit().getDimension();

            Bucket bucket = buckets
                    .computeIfAbsent(ingredient.getId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(dimension, key -> new Bucket());

            if (line.getQuantity() != null && line.getUnit() != null) {
                // ---- STAGE 2: sum in BASE units -----------------------------------
                // Converting before adding is the whole trick. 1 kg and 200 g are not
                // addable as written; 1000 and 200 grams are. Because every unit knows
                // its factor to a single base per dimension, this one line handles
                // every unit combination within a dimension.
                bucket.baseAmount = bucket.baseAmount.add(line.getUnit().toBase(line.getQuantity()));
            } else {
                // A quantity-less line. Remember that it happened so the ingredient
                // still appears on the list, and let it contribute nothing to the
                // arithmetic. (RecipeIngredientRequest guarantees that a null unit
                // implies a null quantity, so no number is being discarded here.)
                bucket.unquantified = true;
            }

            if (line.getNote() != null && !line.getNote().isBlank()) {
                // A Set, so "chopped" from three recipes appears once.
                bucket.notes.add(line.getNote().trim());
            }
            bucket.recipeNames.add(line.getRecipe().getName());
        }

        // ---- STAGES 3 & 4: merge and format, one ingredient at a time ------------
        List<ShoppingListItem> items = new ArrayList<>();
        buckets.forEach((ingredientId, byDimension) ->
                items.add(buildItem(ingredientsById.get(ingredientId), byDimension)));

        // ---- The instructions: concatenated, not merged -------------------------
        List<InstructionSection> sections = recipeIds.stream()
                .map(recipesById::get)
                .map(recipe -> new InstructionSection(
                        recipe.getId(),
                        recipe.getName(),
                        recipe.getServings(),
                        List.copyOf(recipe.getSteps())))
                .toList();

        return new ShoppingListResponse(recipeIds.size(), items, sections);
    }

    /**
     * Folds one ingredient's per-dimension buckets into a single shopping-list item.
     *
     * This is where the "never guess" rule actually lives.
     */
    private ShoppingListItem buildItem(Ingredient ingredient, Map<Dimension, Bucket> byDimension) {

        // Pick the dimension to aim for: the most-preferred one actually present.
        // Null when this ingredient was only ever mentioned without a unit.
        Dimension target = MERGE_PREFERENCE.stream()
                .filter(byDimension::containsKey)
                .findFirst()
                .orElse(null);

        // Running total in the target dimension's base unit.
        BigDecimal targetTotal = (target == null) ? null : byDimension.get(target).baseAmount;

        // Amounts we could not fold into the target, kept as their own lines.
        List<AmountLine> extraAmounts = new ArrayList<>();

        boolean unquantified = false;
        Set<String> notes = new LinkedHashSet<>();
        Set<String> recipeNames = new LinkedHashSet<>();

        for (Map.Entry<Dimension, Bucket> entry : byDimension.entrySet()) {
            Dimension dimension = entry.getKey();
            Bucket bucket = entry.getValue();

            // Notes and recipe names are collected from every bucket regardless of
            // whether its amount could be merged.
            notes.addAll(bucket.notes);
            recipeNames.addAll(bucket.recipeNames);
            unquantified |= bucket.unquantified;

            // The unit-less bucket contributes no amount, and the target bucket is
            // already counted in targetTotal.
            if (dimension == null || dimension == target) {
                continue;
            }

            // ---- THE GRACEFUL-DEGRADATION BRANCH --------------------------------
            // convert() returns empty when this ingredient lacks the factor needed to
            // bridge these two dimensions. When that happens we do NOT invent a
            // density, and we do NOT abort the whole shopping list. We simply keep
            // this amount as its own line and carry on.
            //
            // This is the branch that makes the difference between a tool you can
            // trust and one you can't. A wrong number on a shopping list is invisible;
            // two honest numbers are self-explanatory.
            Optional<BigDecimal> converted = convert(bucket.baseAmount, dimension, target, ingredient);
            if (converted.isPresent()) {
                targetTotal = targetTotal.add(converted.get());
            } else {
                extraAmounts.add(humanize(bucket.baseAmount, dimension));
            }
        }

        // Assemble the amounts, target first.
        List<AmountLine> amounts = new ArrayList<>();
        if (target != null) {
            amounts.add(humanize(targetTotal, target));
        }
        amounts.addAll(extraAmounts);

        return new ShoppingListItem(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getCategory(),
                List.copyOf(amounts),
                unquantified,
                List.copyOf(notes),
                List.copyOf(recipeNames));
    }

    /**
     * Converts an amount expressed in the base unit of {@code from} into the base unit
     * of {@code to}, using THIS INGREDIENT'S OWN physical factors.
     *
     * Returns an empty Optional when the necessary factor is unknown. That is not an
     * error condition — it is the normal, expected outcome for any ingredient a user
     * created themselves, and the caller's cue to print a separate line.
     *
     * Optional is used rather than returning null precisely because it makes the
     * "we don't know" case impossible to forget: the caller cannot use the value
     * without first acknowledging it might not be there.
     */
    private Optional<BigDecimal> convert(BigDecimal amount, Dimension from, Dimension to,
                                         Ingredient ingredient) {
        if (from == to) {
            return Optional.of(amount);
        }

        Double gramsPerMl = ingredient.getGramsPerMl();
        Double gramsPerPiece = ingredient.getGramsPerPiece();

        // A "switch expression" — the arrow form, which returns a value rather than
        // falling through like the old colon form. Because Dimension is an enum and
        // every constant is covered, the compiler verifies the switch is exhaustive:
        // add a fourth dimension one day and this stops compiling until it's handled.
        // That is a much better outcome than a silent default branch.
        return switch (from) {
            case MASS -> switch (to) {
                // grams -> ml requires dividing by density.
                case VOLUME -> divide(amount, gramsPerMl);
                // grams -> pieces requires dividing by the weight of one piece.
                case COUNT -> divide(amount, gramsPerPiece);
                case MASS -> Optional.of(amount);
            };
            case VOLUME -> switch (to) {
                // ml -> grams: multiply by density.
                case MASS -> multiply(amount, gramsPerMl);
                // ml -> pieces has no direct factor, so it goes VIA GRAMS: ml to grams
                // to pieces. Either step can fail, and flatMap propagates that — if
                // the first returns empty the second never runs, and the whole chain
                // is empty. Chaining two possibly-missing conversions without a pile
                // of null checks is exactly what Optional is for.
                case COUNT -> multiply(amount, gramsPerMl).flatMap(g -> divide(g, gramsPerPiece));
                case VOLUME -> Optional.of(amount);
            };
            case COUNT -> switch (to) {
                // pieces -> grams: multiply by the weight of one piece. THIS IS THE
                // CARROT CASE: 1 piece * 61 g = 61 g, which can then be added to the
                // 113 g that "4 oz" became.
                case MASS -> multiply(amount, gramsPerPiece);
                case VOLUME -> multiply(amount, gramsPerPiece).flatMap(g -> divide(g, gramsPerMl));
                case COUNT -> Optional.of(amount);
            };
        };
    }

    // multiply and divide are the two guards that make convert() incapable of
    // throwing. A null factor means "unknown" and yields empty. A zero divisor —
    // which would be an ArithmeticException, not an Infinity, because BigDecimal has
    // no concept of infinity — also yields empty. Every unsafe case is funnelled
    // through these two methods, so there is exactly one place to get it right.

    private Optional<BigDecimal> multiply(BigDecimal amount, Double factor) {
        if (amount == null || factor == null) {
            return Optional.empty();
        }
        return Optional.of(amount.multiply(BigDecimal.valueOf(factor)));
    }

    private Optional<BigDecimal> divide(BigDecimal amount, Double factor) {
        if (amount == null || factor == null || factor == 0.0) {
            return Optional.empty();
        }
        // BigDecimal division REQUIRES an explicit scale and rounding mode for any
        // result that doesn't terminate — 1/3 has no exact decimal representation, and
        // without these arguments the call throws ArithmeticException rather than
        // rounding. It is a genuinely surprising API, and forgetting it is a common
        // production bug that only fires on certain inputs.
        return Optional.of(amount.divide(BigDecimal.valueOf(factor), CONVERSION_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Turns a raw base-unit amount into something a person wants to read.
     *
     * 1500 grams is correct but nobody writes it that way; "1.5 kg" is the same fact,
     * legibly. This is pure presentation — it cannot change a total, only how it looks.
     */
    private AmountLine humanize(BigDecimal baseAmount, Dimension dimension) {
        Unit displayUnit = switch (dimension) {
            case MASS -> {
                if (baseAmount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
                    yield Unit.KILOGRAM;
                }
                // Below a gram, switch to milligrams so a pinch of saffron doesn't
                // render as "0.3 g" or, worse, round away to "0 g".
                yield baseAmount.compareTo(BigDecimal.ONE) < 0 ? Unit.MILLIGRAM : Unit.GRAM;
            }
            case VOLUME -> baseAmount.compareTo(BigDecimal.valueOf(1000)) >= 0
                    ? Unit.LITER
                    : Unit.MILLILITER;
            // Counts stay as counts. There is no larger unit worth promoting to —
            // "1 dozen eggs" is arguably nicer than "12 pc", but it stops being nicer
            // the moment the total is 13.
            case COUNT -> Unit.PIECE;
        };

        // Convert out of the base unit into the display unit, then tidy the number.
        BigDecimal displayQuantity = baseAmount
                .divide(displayUnit.getFactorToBase(), DISPLAY_SCALE, RoundingMode.HALF_UP)
                // Rounding to 2 places gives 200.00, and "200.00 g" reads badly.
                // stripTrailingZeros removes them.
                .stripTrailingZeros();

        // THE CATCH, which this code originally got wrong and which is worth keeping
        // as a warning: stripTrailingZeros removes zeros by adjusting the number's
        // SCALE, and it will happily go NEGATIVE. 200.00 becomes 2 with a scale of -2
        // — mathematically identical, but it now formats as "2E+2".
        //
        // toPlainString() suppresses that when building the display string, so the
        // human-readable "200 g" looked correct while the numeric JSON field beside it
        // serialized as 2E+2. Technically valid JSON that JavaScript parses back to
        // 200, and still the kind of thing that makes a reader distrust the output.
        //
        // setScale(0) pulls a negative scale back to plain integer form. Only when it
        // is negative: forcing scale 0 unconditionally would round 174.4 to 174.
        if (displayQuantity.scale() < 0) {
            displayQuantity = displayQuantity.setScale(0);
        }

        return AmountLine.of(displayQuantity, displayUnit);
    }

    /**
     * A running total for one (ingredient, dimension) pair while the algorithm works.
     *
     * This is a mutable holder, not a DTO — it exists only inside this class and never
     * leaves it, which is why plain fields are fine and no getters are needed. Keeping
     * the scratch type private is what lets the public DTOs stay immutable records.
     */
    private static final class Bucket {
        // BigDecimal.ZERO, not null, so the first .add() has something to add to.
        private BigDecimal baseAmount = BigDecimal.ZERO;
        private boolean unquantified = false;
        // LinkedHashSet: de-duplicates AND keeps first-seen order, so the output is
        // stable across identical requests. A HashSet would reorder notes randomly
        // between calls, which looks like a bug even though the data is right.
        private final Set<String> notes = new LinkedHashSet<>();
        private final Set<String> recipeNames = new LinkedHashSet<>();
    }
}
