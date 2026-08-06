import { useState } from 'react'
import type { ShoppingList, ShoppingListItem } from '../api/types'

/**
 * Renders the combined shopping list and the concatenated instructions.
 *
 * This is where the whole data model pays off visibly: because recipe lines point at
 * catalog ingredients by id, "800 g tomatoes" from one recipe and "1 kg tomatoes"
 * from another arrive here already added together as a single 1.8 kg line.
 */

type Props = {
  list: ShoppingList
  onClear: () => void
}

/** Items with no category are grouped last, under a heading of their own. */
const UNCATEGORIZED = 'Other'

export function ShoppingListPanel({ list, onClear }: Props) {
  /**
   * Which items the user has already put in their basket.
   *
   * This is PURELY LOCAL, in-memory state — it is not sent anywhere and does not
   * survive a reload, which is consistent with the shopping list itself being
   * computed on demand rather than stored. Ticking things off during one shop is
   * useful; persisting it would require the whole saved-list feature that was
   * deliberately deferred.
   */
  const [checked, setChecked] = useState<Set<number>>(new Set())

  function toggle(ingredientId: number) {
    // A new Set rather than mutating the existing one — same reference-equality rule
    // that applies to arrays in state. set.add(x) returns the same Set, so React
    // would see no change and skip the re-render.
    setChecked((current) => {
      const next = new Set(current)
      if (next.has(ingredientId)) next.delete(ingredientId)
      else next.add(ingredientId)
      return next
    })
  }

  // Group items by supermarket aisle, so you walk the shop once instead of criss-
  // crossing it. The server supplies the category; the grouping is a presentation
  // decision and belongs here.
  const grouped = new Map<string, ShoppingListItem[]>()
  for (const item of list.items) {
    const key = item.category ?? UNCATEGORIZED
    // Nullish-coalescing assignment: create the array on first use.
    const bucket = grouped.get(key) ?? []
    bucket.push(item)
    grouped.set(key, bucket)
  }

  // Sort categories alphabetically but always push "Other" to the end — a bucket of
  // leftovers reads badly in the middle of a list.
  const categories = [...grouped.keys()].sort((a, b) => {
    if (a === UNCATEGORIZED) return 1
    if (b === UNCATEGORIZED) return -1
    return a.localeCompare(b)
  })

  /**
   * Copies the list as plain text, for pasting into a notes app or a message.
   *
   * Fifteen lines, and disproportionately the most-used feature of any shopping list
   * tool — because the phone in the shop is rarely the machine the list was made on.
   */
  async function copyAsText() {
    const lines: string[] = []
    for (const category of categories) {
      lines.push(`== ${category} ==`)
      for (const item of grouped.get(category)!) {
        lines.push(`- ${describeAmounts(item)}`)
      }
      lines.push('')
    }
    await navigator.clipboard.writeText(lines.join('\n'))
  }

  return (
    <section className="card shopping-list" aria-labelledby="shopping-list-heading">
      <div className="panel-header">
        <h2 id="shopping-list-heading">
          Shopping list <span className="muted">({list.recipeCount} recipes)</span>
        </h2>
        <div className="panel-header-actions">
          <button type="button" className="btn-ghost btn-small" onClick={copyAsText}>
            Copy as text
          </button>
          <button type="button" className="btn-ghost btn-small" onClick={onClear}>
            Close
          </button>
        </div>
      </div>

      {categories.map((category) => (
        <div key={category} className="shopping-group">
          <h3 className="shopping-group-heading">{category}</h3>
          <ul className="plain-list">
            {grouped.get(category)!.map((item) => (
              <li key={item.ingredientId} className="shopping-item">
                <label className={checked.has(item.ingredientId) ? 'is-checked' : undefined}>
                  <input
                    type="checkbox"
                    checked={checked.has(item.ingredientId)}
                    onChange={() => toggle(item.ingredientId)}
                  />
                  <span className="shopping-amount">{describeAmounts(item)}</span>
                </label>

                {/* When an ingredient has more than one amount, explain WHY rather
                    than leaving the user to wonder whether the app is broken. The
                    honest answer — we don't know how to convert between these units
                    for this ingredient — is more useful than a confident wrong number
                    and is exactly what the backend refused to invent. */}
                {item.amounts.length > 1 && (
                  <p className="row-hint">
                    Listed separately because these units can’t be converted for this
                    ingredient.
                  </p>
                )}

                {item.notes.length > 0 && (
                  <p className="muted shopping-notes">{item.notes.join(', ')}</p>
                )}
                <p className="muted shopping-from">for {item.fromRecipes.join(', ')}</p>
              </li>
            ))}
          </ul>
        </div>
      ))}

      <div className="instructions-section">
        <h2>Instructions</h2>
        {/* Concatenated, never interleaved — two recipes are two separate acts of
            cooking, so merging their steps would produce confident nonsense. */}
        {list.instructionSections.map((section) => (
          <div key={section.recipeId} className="instruction-block">
            <h3>
              {section.recipeName} <span className="muted">· serves {section.servings}</span>
            </h3>
            <ol className="step-list">
              {section.steps.map((step, index) => (
                <li key={index}>{step}</li>
              ))}
            </ol>
          </div>
        ))}
      </div>
    </section>
  )
}

/**
 * Turns an item's amounts into one readable string.
 *
 *   one amount      -> "1.8 kg Tomato"
 *   two amounts     -> "500 ml + 200 g Yuzu"
 *   none, to taste  -> "Salt (to taste)"
 *
 * The " + " form is the visible face of the never-guess rule. It reads naturally
 * enough that most users won't think about it, and it is honest to anyone who does.
 */
function describeAmounts(item: ShoppingListItem): string {
  const amounts = item.amounts.map((amount) => amount.display).join(' + ')

  if (amounts === '') {
    // No measurable amount at all — every mention was "to taste".
    return `${item.ingredientName} (to taste)`
  }

  // Both a measured amount AND a to-taste mention: "1 tsp Salt (plus to taste)".
  const suffix = item.unquantified ? ' (plus to taste)' : ''
  return `${amounts} ${item.ingredientName}${suffix}`
}
