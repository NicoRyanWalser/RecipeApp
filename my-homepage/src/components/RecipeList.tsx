import type { RecipeSummary } from '../api/types'

/**
 * The recipe list, with a selection checkbox per row.
 *
 * The checkboxes are what feed the shopping list: tick several recipes, press the
 * button, get a combined list back.
 *
 * This component is "CONTROLLED" — it holds no state of its own. Which recipes are
 * selected lives in App, and this component receives the set plus a callback. That
 * choice matters because the selection is needed elsewhere (the button's count, the
 * request itself), and state that two components need has to live above both of them.
 * The React name for moving it up is "lifting state up"; the rule of thumb is that
 * state belongs at the lowest point that can serve everyone who needs it.
 */

type Props = {
  recipes: RecipeSummary[]
  selectedIds: Set<number>
  expandedId: number | null
  onToggleSelect: (id: number) => void
  onExpand: (id: number) => void
  onEdit: (id: number) => void
  onDelete: (id: number) => void
}

export function RecipeList({
  recipes,
  selectedIds,
  expandedId,
  onToggleSelect,
  onExpand,
  onEdit,
  onDelete,
}: Props) {
  if (recipes.length === 0) {
    // An explicit empty state. Without one the user sees a blank area and cannot tell
    // whether the app is broken, still loading, or simply has nothing to show.
    return (
      <div className="card empty-state">
        <p>No recipes yet.</p>
        <p className="muted">Create one with the form above to get started.</p>
      </div>
    )
  }

  return (
    <div className="recipe-list">
      {recipes.map((recipe) => {
        const isSelected = selectedIds.has(recipe.id)
        const isExpanded = expandedId === recipe.id

        return (
          <article
            key={recipe.id}
            className={isSelected ? 'card recipe-card is-selected' : 'card recipe-card'}
          >
            <div className="recipe-card-main">
              {/* Wrapping the checkbox in its own <label> means clicking the recipe
                  name toggles the checkbox — a much bigger target than the 13px box
                  itself, and it comes free from the HTML rather than needing a click
                  handler. */}
              <label className="recipe-select">
                <input
                  type="checkbox"
                  checked={isSelected}
                  onChange={() => onToggleSelect(recipe.id)}
                />
                <span className="recipe-name">{recipe.name}</span>
              </label>

              <div className="recipe-card-actions">
                <span className="muted recipe-meta">
                  {recipe.ingredientCount} ingredients · {recipe.stepCount} steps · serves{' '}
                  {recipe.servings}
                </span>
                <button
                  type="button"
                  className="btn-ghost btn-small"
                  onClick={() => onExpand(recipe.id)}
                  // aria-expanded tells assistive technology that this button toggles
                  // something, and what state it is currently in. A button that
                  // reveals content without it just announces as "View".
                  aria-expanded={isExpanded}
                >
                  {isExpanded ? 'Hide' : 'View'}
                </button>
                <button
                  type="button"
                  className="btn-ghost btn-small"
                  onClick={() => onEdit(recipe.id)}
                >
                  Edit
                </button>
                <button
                  type="button"
                  className="btn-ghost btn-small btn-danger"
                  onClick={() => onDelete(recipe.id)}
                >
                  Delete
                </button>
              </div>
            </div>

            {recipe.description && <p className="muted">{recipe.description}</p>}
          </article>
        )
      })}
    </div>
  )
}
