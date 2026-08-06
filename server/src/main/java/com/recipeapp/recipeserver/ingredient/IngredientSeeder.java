package com.recipeapp.recipeserver.ingredient;

import com.recipeapp.recipeserver.common.Slugs;
import com.recipeapp.recipeserver.unit.Unit;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
// NOTE THE PACKAGE: "tools.jackson", not "com.fasterxml.jackson".
// Spring Boot 4 ships Jackson 3, which moved to a new root package as part of its
// 3.0 release. Almost every tutorial and Stack Overflow answer you will find shows
// the old com.fasterxml.jackson.databind.ObjectMapper — that is Jackson 2, and the
// import simply will not resolve here. (Confusingly, the ANNOTATIONS did not move:
// @JsonInclude in ApiErrorResponse is still com.fasterxml.jackson.annotation.)
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Fills the ingredient catalog with a curated starter set on application startup,
 * reading from src/main/resources/data/ingredients.json.
 *
 * "SEEDING" means loading the baseline data an app needs to be usable at all. An
 * empty ingredient catalog would make the recipe form unusable on first run — every
 * single ingredient would have to be typed in by hand before you could save anything.
 *
 * WHY AN ApplicationRunner AND NOT data.sql?
 * Spring Boot will happily run a file called data.sql at startup, and that is the
 * first thing most tutorials reach for. Two problems here:
 *
 *   1. ORDERING. With spring.jpa.hibernate.ddl-auto=update, Spring runs data.sql
 *      BEFORE Hibernate creates the tables, so the inserts fail against tables that
 *      don't exist yet. The fix is a setting called
 *      spring.jpa.defer-datasource-initialization=true — a real footgun, because the
 *      name gives no hint that it's what you need, and the error message doesn't
 *      either. An ApplicationRunner sidesteps it entirely: runners execute after the
 *      whole application context, Hibernate included, is fully up.
 *
 *   2. RE-RUNNING. data.sql runs on EVERY boot, so plain INSERTs would either
 *      duplicate every row or crash on the unique constraint. You'd end up
 *      hand-writing PostgreSQL-specific "ON CONFLICT DO NOTHING", which ties your
 *      seed data to one database vendor.
 *
 * WHY NOT THE USUAL "if (count() > 0) return;" GUARD?
 * That's the common shortcut for idempotency, and it has a nasty long-term cost:
 * once one ingredient exists, the seeder never runs again, so adding ingredient #151
 * to the JSON does nothing until you wipe the whole database. Checking PER ROW
 * instead means the JSON file stays a living document — add a line, restart, it
 * appears, and nothing else is touched.
 *
 * "IDEMPOTENT" is the word for this property: running the operation once and running
 * it a hundred times produce the same result. It's worth recognizing by name, because
 * it's the difference between a startup task you can trust and one you have to
 * remember not to trigger twice.
 */
@Component
public class IngredientSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngredientSeeder.class);

    private static final String SEED_FILE = "data/ingredients.json";

    private final IngredientRepository ingredientRepository;

    // Jackson's ObjectMapper converts between JSON and Java objects. Spring Boot
    // already configures one for serializing HTTP responses, so we inject that same
    // instance rather than constructing our own — one configuration, used everywhere.
    private final ObjectMapper objectMapper;

    public IngredientSeeder(IngredientRepository ingredientRepository, ObjectMapper objectMapper) {
        this.ingredientRepository = ingredientRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * ApplicationRunner's single method. Spring calls this once, automatically, after
     * the application has finished starting and before it begins serving requests.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<IngredientSeed> seeds = readSeedFile();

        int added = 0;
        for (IngredientSeed seed : seeds) {
            // THE IDEMPOTENCY CHECK. We ask by slug rather than by name so that a
            // user who already created "shallot" by hand blocks the seeded "Shallot"
            // — which is what we want. One shallot, whoever added it first.
            if (ingredientRepository.existsBySlug(Slugs.of(seed.name()))) {
                continue;
            }
            ingredientRepository.save(new Ingredient(
                    seed.name(),
                    seed.category(),
                    seed.defaultUnit(),
                    seed.gramsPerPiece(),
                    seed.gramsPerMl()));
            added++;
        }

        // Worth logging, because on a fresh database this line is preceded by ~180
        // INSERT statements dumped to the console by spring.jpa.show-sql=true. That
        // wall of SQL is expected on first boot, not a bug — this line is the summary
        // that tells you so. On the second boot you should see "0 new".
        log.info("Ingredient catalog seeded: {} new, {} total", added, ingredientRepository.count());
    }

    /**
     * Loads and parses the JSON file bundled inside the application.
     */
    private List<IngredientSeed> readSeedFile() throws Exception {
        // A "classpath resource" is a file packaged alongside the compiled code —
        // anything under src/main/resources. Reading it this way rather than with a
        // filesystem path means it still works when the app is packaged into a single
        // .jar, where there is no "data/ingredients.json" file on disk to open.
        ClassPathResource resource = new ClassPathResource(SEED_FILE);

        // try-with-resources: the stream is closed automatically at the end of the
        // block, even if parsing throws. Leaked file handles are a classic slow leak.
        try (InputStream in = resource.getInputStream()) {
            // TypeReference exists to work around "type erasure" — at runtime, Java
            // discards the <IngredientSeed> part of List<IngredientSeed>, so Jackson
            // would otherwise have no idea what to build the list OUT of. The anonymous
            // subclass created by "new TypeReference<>() {}" preserves that information
            // where Jackson can read it. The empty braces are load-bearing.
            return objectMapper.readValue(in, new TypeReference<List<IngredientSeed>>() {
            });
        }
    }

    /**
     * The shape of one entry in ingredients.json.
     *
     * Jackson matches JSON keys to record components by name, so "gramsPerPiece" in
     * the file lands in gramsPerPiece here. Any component missing from the JSON is
     * simply left null — which is exactly how an ingredient ends up with no conversion
     * factors, and therefore how it ends up on the shopping list as separate lines.
     *
     * The Unit component is a nice illustration of what Jackson gives you for free:
     * the string "TABLESPOON" in the file is matched against the enum's constant names
     * and converted automatically. A typo there fails loudly at startup rather than
     * quietly storing garbage — which is the good outcome.
     */
    private record IngredientSeed(
            String name,
            String category,
            Unit defaultUnit,
            Double gramsPerPiece,
            Double gramsPerMl) {
    }
}
