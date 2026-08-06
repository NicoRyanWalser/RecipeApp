package com.recipeapp.recipeserver.recipe;

import com.recipeapp.recipeserver.ingredient.Ingredient;
import com.recipeapp.recipeserver.unit.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One LINE in a recipe's ingredient list: "800 g chopped tomatoes".
 *
 * This is the table that connects recipes to the ingredient catalog, and it is worth
 * understanding what each of its three parts does:
 *
 *   - a link to the RECIPE this line belongs to      ("which recipe?")
 *   - a link to the catalog INGREDIENT               ("what food?")
 *   - the QUANTITY, UNIT and NOTE                    ("how much, and how prepared?")
 *
 * A table like this — one that exists to connect two other tables — is called a JOIN
 * TABLE or, when it carries extra data of its own like the quantity here, an
 * ASSOCIATION ENTITY. That extra data is exactly why this has to be a real @Entity:
 * a plain many-to-many mapping could record "this recipe uses tomatoes" but has
 * nowhere to put "800 g of them, chopped".
 *
 * Notice what this class does NOT have: a String for the ingredient's name. That
 * absence is the entire point of the redesign. There is no free-text field here to
 * misspell, so "carot" cannot exist, and combining two recipes' tomatoes is an
 * integer comparison of ingredient_id rather than a hopeful string match.
 */
@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * MANY lines belong to ONE recipe — hence @ManyToOne, read from this side.
     *
     * The side holding @JoinColumn is called the OWNING SIDE of the relationship: it
     * is the one whose table physically stores the foreign key column (recipe_id).
     * The other side (Recipe.ingredients) is the "inverse" side and is marked
     * mappedBy — it stores nothing, it just reads the same link backwards.
     *
     * That asymmetry surprises people, so it's worth stating plainly: a relationship
     * between two tables is stored ONCE, in one column, on one table. Both Java
     * classes can see it, but only one of them owns it, and Hibernate only writes
     * changes it observes on the owning side. Forgetting to set this field is the
     * single most common cause of "I saved it but the foreign key is null".
     */
    @ManyToOne(
            // LAZY means "don't load the Recipe from the database until somebody
            // actually calls getRecipe()". The default for @ManyToOne is EAGER, which
            // would fetch the full parent recipe every time a line is loaded — a huge
            // amount of pointless SQL. Setting LAZY explicitly on every @ManyToOne is
            // a habit worth forming.
            fetch = FetchType.LAZY,
            // optional = false says a line CANNOT exist without a recipe, which lets
            // Hibernate skip a null check and generates NOT NULL in the schema.
            optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    /**
     * MANY lines can point at the SAME catalog row — "Onion" is referenced by every
     * recipe that uses onions. This is the foreign key that makes the shopping list
     * possible.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /**
     * How much. NULLABLE ON PURPOSE — "salt, to taste" is a real ingredient line with
     * no quantity at all, and forcing a number here would mean writing 0, which then
     * silently prints as "0 g salt" on the shopping list.
     *
     * BigDecimal rather than double: binary floating point cannot represent most
     * decimal fractions exactly (in Java, 0.1 + 0.2 == 0.30000000000000004). Money and
     * measurements are the two classic cases where that error is unacceptable.
     * precision = 10, scale = 3 means "up to 10 total digits, 3 of them after the
     * decimal point" — so 9,999,999.999 is the ceiling, and thousandths are the
     * finest resolution. Ample for cooking.
     */
    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    /**
     * Which unit the quantity is in. Nullable for the same "to taste" reason: a line
     * with no quantity has no unit either.
     *
     * EnumType.STRING again — see the long explanation in Ingredient.defaultUnit for
     * why the ORDINAL default is a silent data-corruption bug waiting to happen.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Unit unit;

    /**
     * Free-text preparation detail: "finely diced", "at room temperature", "plus extra
     * for greasing".
     *
     * Free text is fine HERE, precisely because nothing computes on it. The note is
     * carried along and displayed; it never participates in matching or arithmetic.
     * That is the useful distinction to draw — free text is dangerous when it's an
     * identity ("which ingredient is this?") and harmless when it's a description.
     */
    @Column(length = 120)
    private String note;

    /**
     * Display order within the recipe, 0-based. Without it, the database would be free
     * to return "add onion" before "heat the oil", because SQL rows have no inherent
     * order — a set of rows is genuinely unordered unless you ORDER BY something.
     *
     * The client never sends this. The server assigns it from the array index of the
     * submitted list, which makes gaps and duplicates structurally impossible.
     */
    @Column(nullable = false)
    private int position;

    protected RecipeIngredient() {
        // For JPA only.
    }

    /**
     * Note that recipe and position are NOT parameters here. A line is built from the
     * client's data first, then attached to its parent by attachTo() below. Splitting
     * it this way means the service can construct lines without knowing their final
     * order, and the ordering logic lives in exactly one place.
     */
    public RecipeIngredient(Ingredient ingredient, BigDecimal quantity, Unit unit, String note) {
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
        this.note = (note == null || note.isBlank()) ? null : note.trim();
    }

    /**
     * Links this line to its parent recipe and fixes its place in the order.
     *
     * Called only by Recipe.replaceIngredients, which is what guarantees the two
     * things that must always be true together: every line points back at its recipe
     * (the owning-side rule above), and positions run 0, 1, 2... with no gaps.
     */
    void attachTo(Recipe recipe, int position) {
        this.recipe = recipe;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public String getNote() {
        return note;
    }

    public int getPosition() {
        return position;
    }
}
