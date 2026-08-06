package com.recipeapp.recipeserver.recipe;

// These imports come from Jakarta Persistence (JPA) — the Java standard for
// mapping Java objects to database tables (this is called Object-Relational
// Mapping, or ORM). Spring Boot uses Hibernate as the actual implementation
// behind these annotations.
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/**
 * A Recipe is an "entity" — a plain Java object that JPA/Hibernate knows how to
 * save to and load from a database table.
 *
 * A recipe is no longer a single row. It is an AGGREGATE: one recipes row, plus its
 * ingredient lines in recipe_ingredients, plus its steps in recipe_steps. The Recipe
 * object is the single entry point to all of it — you never load or modify a line
 * except through its recipe. That rule is what keeps positions contiguous and
 * prevents half-updated recipes.
 *
 * THE TWO CHILD COLLECTIONS ARE MAPPED DIFFERENTLY ON PURPOSE, and the contrast is
 * the most useful thing in this file:
 *
 *   - ingredients is a @OneToMany of RecipeIngredient ENTITIES. A line has its own
 *     identity (a primary key) and REFERENCES SOMETHING OUTSIDE ITSELF — a row in
 *     the ingredient catalog that other recipes also point at.
 *
 *   - steps is an @ElementCollection of plain Strings — VALUES, not entities. The
 *     text "Preheat the oven to 200C" has no identity, is owned entirely by this
 *     recipe, is referenced by nothing, and becomes meaningless the moment the
 *     recipe is deleted.
 *
 * That distinction — entity versus value object — is one of the central ideas in
 * data modelling. The test is: does this thing need to be referred to from
 * elsewhere, and would you ever ask "which one is it?" If yes, it's an entity and
 * needs an id. If it's just a piece of data belonging to its parent, it's a value.
 */
@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // A one-line summary for the recipe card. Optional.
    @Column(length = 200)
    private String description;

    /**
     * How many people this recipe feeds.
     *
     * Nothing uses this yet — the shopping list adds recipes at their stated size.
     * It is here anyway because "combine four recipes into a shopping list" leads
     * almost immediately to "...but for six people", and adding a NOT NULL column to
     * a table that already contains rows is a migration, while adding it now is free.
     * Cheap insurance, taken deliberately rather than by accident.
     */
    @Column(nullable = false)
    private int servings = 1;

    /**
     * The ingredient lines, in display order.
     *
     * Three annotations are doing real work here:
     *
     * mappedBy = "recipe" — this is the INVERSE side of the relationship. The foreign
     *   key lives on RecipeIngredient.recipe (the owning side). mappedBy says "the
     *   link is already stored over there; don't create a second one for me."
     *   Omitting it makes Hibernate invent a THIRD join table, which is a classic
     *   confusing bug: everything saves, but the schema has a table you never wanted.
     *
     * cascade = ALL — operations on the recipe flow through to its lines. Saving a
     *   new recipe saves its lines; deleting a recipe deletes them. Without it you
     *   would have to save every line by hand, and deleting a recipe would fail on a
     *   foreign key violation because its children still pointed at it.
     *
     * orphanRemoval = true — the subtle and important one. If a line is REMOVED FROM
     *   THIS LIST, delete its row. Cascade alone only propagates operations you
     *   perform on the parent; it has nothing to say about a child that quietly fell
     *   out of the collection. Without orphanRemoval, editing a recipe to drop an
     *   ingredient would leave the row behind with its recipe_id still set — an
     *   invisible ghost that reappears the next time you load the recipe.
     */
    @OneToMany(
            mappedBy = "recipe",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    // @BatchSize is Hibernate's answer to the N+1 SELECT PROBLEM.
    //
    // "N+1" is what happens when you run 1 query to fetch a list of N recipes, and
    // then N more queries — one per recipe — as you touch each one's ingredients.
    // Fifty recipes become fifty-one round trips to the database, and the app feels
    // fine on your laptop with three recipes and falls over in production.
    //
    // The textbook fix is a JOIN FETCH, and for ONE collection it is the right answer.
    // But you cannot JOIN FETCH both collections in a single query: Hibernate throws
    // MultipleBagFetchException at startup, because two Lists produce a cartesian
    // product whose row count is ambiguous. (Hibernate calls an unordered List a
    // "bag" — hence the otherwise baffling error message. Expect to meet it.)
    //
    // @BatchSize sidesteps that entirely: when Hibernate needs to load one recipe's
    // ingredients, it loads them for up to 25 recipes at once, using a single
    // WHERE recipe_id IN (...). N+1 becomes 1 + 2 queries for both collections
    // together, with no exception and no query gymnastics.
    @BatchSize(size = 25)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    /**
     * The instruction steps, in order.
     *
     * @ElementCollection maps a collection of VALUES (here, Strings) into their own
     * table, without those values being entities. There is no RecipeStep class, no id
     * column, no repository — steps exist only as part of a recipe.
     *
     * @OrderColumn tells Hibernate to store the list INDEX in a column and keep it
     * contiguous automatically. This is the difference between a List and a Set made
     * physical: without it the "list" would come back in whatever order the database
     * felt like, which for cooking instructions is a real problem.
     *
     * THE COST, so it isn't a surprise later: @OrderColumn makes Hibernate delete and
     * re-insert everything after an insertion point when you add a step in the middle.
     * At a dozen steps per recipe this is irrelevant. At ten thousand it would not be.
     *
     * WHEN TO PROMOTE THIS TO A REAL @Entity: the moment a step needs data of its own
     * — a duration for a timer, a photo, a link to the specific ingredients it uses.
     * At that point it stops being a value ("some text") and starts being a thing you
     * refer to, so it earns an id. Knowing the trigger in advance is worth more than
     * guessing which one you need today.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_steps", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "position")
    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    @BatchSize(size = 25)
    private List<String> steps = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Recipe() {
        // For JPA only.
    }

    public Recipe(String name, String description, int servings) {
        this.name = name;
        this.description = description;
        this.servings = servings;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * The ONE legal way to change a recipe.
     *
     * The original version of this class had no setters at all, on the grounds that
     * nothing should be able to quietly mutate a saved recipe. Supporting edits does
     * not require abandoning that idea — it requires extending it.
     *
     * A setter announces "any caller may change this one field, at any time, for any
     * reason." Five setters mean thirty-two possible half-applied combinations, and
     * nothing forces a caller to remember updatedAt. A single named method announces
     * "here is what it means to edit a recipe", applies the change as one atomic unit,
     * and cannot forget the bookkeeping because the bookkeeping is inside it.
     *
     * This is a good pattern to recognize in general: prefer methods named after what
     * the caller is trying to DO over setters named after your fields.
     */
    public void applyChanges(String name, String description, int servings,
                             List<RecipeIngredient> newIngredients, List<String> newSteps) {
        this.name = name;
        this.description = description;
        this.servings = servings;
        replaceIngredients(newIngredients);
        replaceSteps(newSteps);
        this.updatedAt = Instant.now();
    }

    /**
     * Used when first creating a recipe, to attach the lines the client supplied.
     */
    public void setInitialContent(List<RecipeIngredient> newIngredients, List<String> newSteps) {
        replaceIngredients(newIngredients);
        replaceSteps(newSteps);
    }

    /**
     * Swaps the ingredient lines for a new set.
     *
     * THE TRAP THIS METHOD EXISTS TO AVOID:
     *
     *     this.ingredients = newIngredients;   // <-- looks obvious, throws at runtime
     *
     * That line fails with "A collection with cascade='all-delete-orphan' was no
     * longer referenced by the owning entity instance". The reason is that Hibernate
     * does not track your FIELD, it tracks the specific collection OBJECT it handed
     * you when it loaded the entity — that object is a Hibernate-managed wrapper that
     * records additions and removals. Assigning a plain new ArrayList throws that
     * wrapper away, and with it every record of what was deleted.
     *
     * So: mutate the collection you were given. clear() is what orphanRemoval turns
     * into DELETE statements; then add the new lines back.
     *
     * The error message is genuinely unhelpful and the failure happens at flush time,
     * far from the assignment that caused it. Worth remembering as a shape: with a
     * managed collection, mutate in place, never reassign.
     */
    private void replaceIngredients(List<RecipeIngredient> newIngredients) {
        this.ingredients.clear();
        for (int i = 0; i < newIngredients.size(); i++) {
            RecipeIngredient line = newIngredients.get(i);
            // attachTo sets BOTH halves of the relationship at once: the back-reference
            // to this recipe (without which the foreign key would be null) and the
            // position (derived from the loop index, so the order the client sent is
            // the order that gets stored — no gaps, no duplicates, nothing to validate).
            line.attachTo(this, i);
            this.ingredients.add(line);
        }
    }

    private void replaceSteps(List<String> newSteps) {
        // Same in-place rule as above, for the same reason.
        this.steps.clear();
        this.steps.addAll(newSteps);
    }

    // A TRADE-OFF WORTH NAMING: this "replace everything" approach deletes and
    // re-inserts every line on every edit, so a line's database id is not stable
    // across updates. That is completely fine here, because nothing in the app refers
    // to a line by id. The payoff is that the update logic is trivially correct —
    // there is no diffing, no "which rows changed" bookkeeping, and no way to end up
    // with a stale row. An app that attached anything to a specific line (per-line
    // comments, analytics, a photo) would have to diff by id instead, and would be
    // meaningfully more complicated for it.

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getServings() {
        return servings;
    }

    /**
     * Returns an UNMODIFIABLE view of the lines.
     *
     * Handing out the live list would let any caller do recipe.getIngredients().add(...)
     * and bypass every guarantee replaceIngredients makes about positions and
     * back-references. Wrapping it means such a call throws immediately, at the line
     * that's wrong, instead of producing a recipe with two lines at position 3.
     */
    public List<RecipeIngredient> getIngredients() {
        return Collections.unmodifiableList(ingredients);
    }

    public List<String> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
