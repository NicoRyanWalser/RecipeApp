package com.recipeapp.recipeserver.ingredient;

import com.recipeapp.recipeserver.common.Slugs;
import com.recipeapp.recipeserver.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * An Ingredient is a CATALOG entry — one row per real-world foodstuff. "Carrot"
 * exists exactly once in this table no matter how many recipes call for carrots.
 *
 * THIS TABLE IS THE WHOLE REASON THE SHOPPING LIST CAN WORK.
 *
 * Before this existed, a recipe stored its ingredients as one blob of text. To
 * combine two recipes you would have to compare strings — and "carrot", "Carrot",
 * "carots", and "1 large carrot, diced" are four different strings meaning one
 * thing. String matching is a losing game.
 *
 * Instead, a recipe line stores a FOREIGN KEY to a row in this table (see
 * RecipeIngredient). Combining two recipes then becomes a database JOIN on
 * ingredient_id — an exact integer comparison that is always right. The misspelling
 * problem doesn't get solved so much as DELETED: there is no free-text field on a
 * recipe line to misspell.
 *
 * This pattern is called NORMALIZATION: store each fact once, in one place, and
 * refer to it by id everywhere else.
 */
@Entity
// Two things happen here. The table is named "ingredients" (plural, by convention),
// and we declare a UNIQUE constraint on the slug column.
//
// The unique constraint is the actual guarantee. Checking "does this already exist?"
// in Java before inserting is NOT enough on its own: two requests can both check,
// both see nothing, and both insert. That's a "race condition". Only the database
// can truly enforce uniqueness, because only it sees all writers at once.
@Table(
        name = "ingredients",
        uniqueConstraints = @UniqueConstraint(name = "uk_ingredient_slug", columnNames = "slug"))
public class Ingredient {

    // The PRIMARY KEY. This number is what recipe lines point at, and it is why the
    // name can be corrected later ("Carot" -> "Carrot") without breaking any recipe:
    // recipes reference the id, not the text.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The display form, exactly as it should appear in the UI: "Olive Oil".
    @Column(nullable = false, length = 100)
    private String name;

    // The normalized key used for duplicate detection and search. See Slugs for the
    // full explanation of what gets stripped and why. "Olive Oil" -> "olive oil".
    //
    // Storing BOTH name and slug is deliberate redundancy: the name is what humans
    // read, the slug is what the computer compares. Deriving the slug on every query
    // instead would make the unique constraint impossible and every search a full
    // table scan.
    @Column(nullable = false, length = 100)
    private String slug;

    // A supermarket-aisle grouping: "Produce", "Dairy & Eggs", "Pantry". The shopping
    // list groups by this so you walk the shop once instead of zig-zagging.
    // Nullable, because a user-created ingredient won't have one.
    @Column(length = 40)
    private String category;

    // Which unit the recipe form should pre-select when this ingredient is chosen.
    // Flour defaults to GRAM, olive oil to TABLESPOON, eggs to PIECE. Purely a
    // convenience — the user can always change it — but it removes a click on
    // essentially every row, which adds up fast.
    //
    // @Enumerated(EnumType.STRING) IS NOT OPTIONAL. The JPA default is ORDINAL, which
    // stores the enum's POSITION as an integer — GRAM would be saved as "1" purely
    // because it is declared second. Insert a new unit at the top of the enum later
    // and every existing row silently changes meaning, with no error and no way to
    // detect it after the fact. STRING stores "GRAM", which survives any reordering.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Unit defaultUnit;

    // ---- THE CONVERSION FACTORS -------------------------------------------------
    // These two fields are what let the shopping list answer your carrot question:
    // one recipe wants "1 carrot", another wants "4 oz carrots" — how many carrots
    // do you buy? With gramsPerPiece = 60, both convert to grams and add up.
    //
    // NULL IS THE MOST IMPORTANT VALUE THESE CAN HOLD. It means "we do not know",
    // and it is not a gap to be filled in later — it is a real, permanent state for
    // any ingredient a user creates themselves. ShoppingListService checks for null
    // and, when it finds one, prints the amounts as two separate lines rather than
    // inventing a density. Refusing to guess is a feature: a shopping list that
    // silently halves your flour is worse than one that asks you to think.

    // How much one typical unit of this weighs. 1 carrot ~ 60 g, 1 egg ~ 50 g.
    // Necessarily approximate — carrots vary — which is fine for shopping and would
    // NOT be fine for baking. Note that distinction in your head.
    private Double gramsPerPiece;

    // Density: how much 1 ml of this weighs. Water is 1.0 by definition; olive oil
    // is 0.91 (it floats); honey is 1.42 (it sinks). This is what converts a volume
    // measurement like "1 cup" into a weight.
    private Double gramsPerMl;

    // Note these two are the boxed "Double", not the primitive "double". A primitive
    // double CANNOT be null — it would default to 0.0, and "0 grams per carrot" would
    // sail through the arithmetic and produce a division by zero. The box exists
    // precisely so that "unknown" is representable.

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // JPA's required no-argument constructor. Protected so application code can't
    // accidentally create a blank, invalid Ingredient — only Hibernate uses it.
    protected Ingredient() {
    }

    /**
     * The constructor our code uses. The slug is DERIVED here rather than accepted as
     * a parameter, so there is no way to construct an Ingredient whose slug disagrees
     * with its name. Making invalid states unconstructable beats validating for them.
     */
    public Ingredient(String name, String category, Unit defaultUnit,
                      Double gramsPerPiece, Double gramsPerMl) {
        this.name = name.trim();
        this.slug = Slugs.of(name);
        this.category = category;
        this.defaultUnit = defaultUnit;
        this.gramsPerPiece = gramsPerPiece;
        this.gramsPerMl = gramsPerMl;
        this.createdAt = Instant.now();
    }

    /**
     * Convenience constructor for an ingredient a USER invents from the combobox.
     * They typed a name and nothing else, so it has no category and — importantly —
     * no conversion factors. See the NULL discussion above: this is the ingredient
     * that will show up as two separate shopping-list lines, exactly as intended.
     */
    public Ingredient(String name) {
        this(name, null, null, null, null);
    }

    // Getters only, matching the Recipe entity's deliberate no-setter style: nothing
    // can quietly mutate a saved row. The catalog has no edit endpoint in this
    // version, so there is not even a controlled mutator yet — when you add one, give
    // it a named method like Recipe.applyChanges rather than five setters.
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getCategory() {
        return category;
    }

    public Unit getDefaultUnit() {
        return defaultUnit;
    }

    public Double getGramsPerPiece() {
        return gramsPerPiece;
    }

    public Double getGramsPerMl() {
        return gramsPerMl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
