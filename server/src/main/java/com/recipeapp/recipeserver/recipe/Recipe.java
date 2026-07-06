package com.recipeapp.recipeserver.recipe;

// These imports come from Jakarta Persistence (JPA) — the Java standard for
// mapping Java objects to database tables (this is called Object-Relational
// Mapping, or ORM). Spring Boot uses Hibernate as the actual implementation
// behind these annotations.
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A Recipe is an "entity" — a plain Java object that JPA/Hibernate knows how to
 * save to and load from a database table. Each field below becomes a column,
 * and each instance of this class becomes a row in the "recipes" table.
 */
// @Entity tells JPA "this class maps to a database table". Without it, Hibernate
// would ignore this class entirely.
@Entity
// @Table lets us name the table explicitly. If we left this off, the table would
// default to "recipe" (the class name). We use the plural "recipes" by convention.
@Table(name = "recipes")
public class Recipe {

    // @Id marks this field as the table's PRIMARY KEY — the unique identifier
    // for each row.
    @Id
    // @GeneratedValue tells the database to generate the id for us automatically.
    // GenerationType.IDENTITY means "use the database's auto-increment column"
    // (PostgreSQL creates a sequence and fills this in on insert). We never set
    // the id ourselves — the database owns it.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column customizes how a field maps to its column. nullable = false adds a
    // NOT NULL constraint in the database, so a recipe can never be saved without
    // a name.
    @Column(nullable = false)
    private String name;

    // columnDefinition = "TEXT" overrides the default column type. A plain String
    // maps to VARCHAR(255) (max 255 characters). Ingredients and instructions can
    // be long, so we use PostgreSQL's TEXT type, which has no practical length limit.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ingredients;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructions;

    // Instant is a point in time (UTC). updatable = false means once a row is
    // inserted, this column can never be changed by an update — a created-at
    // timestamp should be permanent.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // JPA requires a no-argument constructor so it can create empty instances when
    // loading rows from the database (it creates the object first, then fills in
    // the fields via reflection). It's "protected" so our own application code
    // won't accidentally create a blank, invalid Recipe — only JPA uses it.
    protected Recipe() {
    }

    // This is the constructor OUR code uses to create a new recipe. Notice there's
    // no "id" parameter (the database assigns it) and createdAt is set to "now"
    // automatically — the caller only has to supply the meaningful content.
    public Recipe(String name, String ingredients, String instructions) {
        this.name = name;
        this.ingredients = ingredients;
        this.instructions = instructions;
        this.createdAt = Instant.now();
    }

    // Getters expose the field values. JPA and Spring (for JSON serialization)
    // read the object's state through these. We deliberately provide NO setters:
    // this makes a Recipe effectively immutable after creation, which is safer —
    // nothing can quietly mutate a saved recipe.
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIngredients() {
        return ingredients;
    }

    public String getInstructions() {
        return instructions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
