import type { RecipeDetail as RecipeDetailType } from '../api/types'

/**
 * The expanded view of a single recipe: its ingredient lines and its steps.
 *
 * Everything rendered here came from a single GET — the ingredient names are nested
 * inside each line in the response, so no extra lookups are needed. That is the
 * payoff of the response DTO nesting the full IngredientResponse instead of a bare id.
 */

type Props = {
  recipe: RecipeDetailType
}

/**
 * Formats one ingredient line for display: "800 g tomato (chopped)".
 *
 * Kept as a small named function rather than inline JSX because the "to taste" case
 * makes it genuinely branchy, and a nested ternary inside markup is where this sort
 * of logic goes to become unreadable.
 */
function formatLine(quantity: number | null, unitDisplay: string | null, name: string) {
  // A line with no quantity is the "to taste" case — a real state, not missing data.
  if (quantity === null) return name
  // A unit can be absent while a quantity is present only if something upstream went
  // wrong; the server forbids it. Handling it anyway costs one branch and avoids
  // printing "2 null tomato" if it ever does.
  if (!unitDisplay) return `${quantity} ${name}`
  return `${quantity} ${unitDisplay} ${name}`
}

export function RecipeDetail({ recipe }: Props) {
  return (
    <div className="recipe-detail">
      <div className="recipe-detail-column">
        <h4>Ingredients</h4>
        <ul className="plain-list">
          {recipe.ingredients.map((line) => (
            <li key={line.id}>
              {formatLine(line.quantity, line.unitDisplay, line.ingredient.name)}
              {/* The note is styled as secondary text because it is a detail about
                  preparation, not part of what you buy. */}
              {line.note && <span className="muted"> — {line.note}</span>}
            </li>
          ))}
        </ul>
      </div>

      <div className="recipe-detail-column">
        <h4>Instructions</h4>
        {/* An <ol> gives the numbering from the markup rather than from hardcoded
            text, so it stays correct no matter how the steps are edited. */}
        <ol className="step-list">
          {recipe.steps.map((step, index) => (
            // A pure-text array has no id to key by. The index is acceptable HERE, in
            // a read-only list that is never reordered in place — the objection to
            // index keys is about React losing track of items that MOVE, which cannot
            // happen in a static render. (The editable StepRows does use real keys,
            // because there rows genuinely do move.)
            <li key={index}>{step}</li>
          ))}
        </ol>
      </div>
    </div>
  )
}
