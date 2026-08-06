import type { UnitCode, UnitOption } from '../api/types'
import { IngredientCombobox } from './IngredientCombobox'
// The draft type and its factory live in rowDrafts.ts so this file exports only
// components — see that file for why Fast Refresh cares.
import type { IngredientDraft } from './rowDrafts'
import { blankIngredientRow } from './rowDrafts'

type Props = {
  rows: IngredientDraft[]
  units: UnitOption[]
  onChange: (rows: IngredientDraft[]) => void
}

export function IngredientRows({ rows, units, onChange }: Props) {
  /**
   * Every operation below builds a NEW array rather than mutating the existing one.
   *
   * This is not stylistic. React decides whether to re-render by comparing the old
   * state value to the new one by reference. rows.push(...) returns the same array
   * object, so React sees no change and the screen does not update — the data is
   * right and the UI is stale, which is a confusing bug to chase. Producing a new
   * array makes the change visible.
   */
  function updateRow(key: string, patch: Partial<IngredientDraft>) {
    onChange(rows.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  }

  function addRow() {
    onChange([...rows, blankIngredientRow()])
  }

  function removeRow(key: string) {
    onChange(rows.filter((row) => row.key !== key))
  }

  return (
    <fieldset className="rows-fieldset">
      {/* fieldset + legend is the native way to group related form controls and give
          the group a name. A screen reader announces "Ingredients" when entering the
          group, which a plain <h3> would not do. */}
      <legend>Ingredients</legend>

      <ol className="rows">
        {rows.map((row, index) => {
          // Suggest the ingredient's own default unit once one is chosen, but never
          // overwrite a unit the user picked themselves.
          const unitValue = row.unit || row.ingredient?.defaultUnit || ''

          return (
            <li className="row" key={row.key}>
              <div className="row-main">
                <IngredientCombobox
                  label={`Ingredient ${index + 1}`}
                  hideLabel
                  value={row.ingredient}
                  onChange={(ingredient) =>
                    updateRow(row.key, {
                      ingredient,
                      // Adopt the new ingredient's default unit, but only if the user
                      // hasn't already made a choice of their own.
                      unit: row.unit || ingredient?.defaultUnit || '',
                    })
                  }
                />

                <input
                  className="row-quantity"
                  type="number"
                  // Cooking quantities are fractional — 0.5 tsp is normal. Without
                  // step, a number input only accepts integers and silently marks
                  // "0.5" as invalid.
                  step="any"
                  min="0"
                  placeholder="Qty"
                  aria-label={`Quantity for ingredient ${index + 1}`}
                  value={row.quantity}
                  onChange={(event) => updateRow(row.key, { quantity: event.target.value })}
                />

                <select
                  className="row-unit"
                  aria-label={`Unit for ingredient ${index + 1}`}
                  value={unitValue}
                  onChange={(event) =>
                    updateRow(row.key, { unit: event.target.value as UnitCode | '' })
                  }
                >
                  {/* The empty option is how a user expresses "to taste" — no
                      quantity, no unit. It has to be explicitly choosable. */}
                  <option value="">—</option>
                  {units.map((unit) => (
                    <option key={unit.code} value={unit.code}>
                      {unit.display}
                    </option>
                  ))}
                </select>

                <input
                  className="row-note"
                  type="text"
                  placeholder="Note (e.g. finely diced)"
                  aria-label={`Note for ingredient ${index + 1}`}
                  value={row.note}
                  onChange={(event) => updateRow(row.key, { note: event.target.value })}
                />

                <button
                  type="button"
                  className="btn-icon"
                  onClick={() => removeRow(row.key)}
                  // Disabled on the last remaining row: a recipe needs at least one
                  // ingredient, and the server enforces that with @NotEmpty. Stopping
                  // it here means the user cannot build a request that is guaranteed
                  // to be rejected — the form should never be able to produce an
                  // invalid submission.
                  disabled={rows.length === 1}
                  aria-label={`Remove ingredient ${index + 1}`}
                >
                  ×
                </button>
              </div>

              {/* An honest warning at the point of the decision, rather than a
                  surprise later. An ingredient with no conversion factors cannot have
                  its amounts combined across recipes, so the shopping list will show
                  them as separate lines. Explaining that here is much kinder than
                  letting the user wonder why their milk appears twice. */}
              {row.ingredient &&
                !row.ingredient.hasPieceWeight &&
                !row.ingredient.hasDensity && (
                  <p className="row-hint">
                    No conversion data for {row.ingredient.name} — amounts in different
                    unit types will be listed separately on a shopping list.
                  </p>
                )}
            </li>
          )
        })}
      </ol>

      <button type="button" className="btn-ghost" onClick={addRow}>
        + Add ingredient
      </button>
    </fieldset>
  )
}
