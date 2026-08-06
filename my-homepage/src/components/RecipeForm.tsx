import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { RecipeDetail, RecipePayload, UnitCode, UnitOption } from '../api/types'
import { IngredientRows } from './IngredientRows'
import { StepRows } from './StepRows'
import type { IngredientDraft, StepDraft } from './rowDrafts'
import { blankIngredientRow, blankStep } from './rowDrafts'

/**
 * The create/edit form. ONE component serves both, because the difference between
 * creating and editing a recipe is genuinely small: whether the fields start empty
 * or pre-filled, and whether submitting POSTs or PUTs.
 *
 * Writing two nearly identical components is a common instinct and a trap — they
 * drift, and a fix applied to one silently misses the other. The presence or absence
 * of `initial` is enough to distinguish the modes.
 */

type Props = {
  units: UnitOption[]
  /** Present when editing; absent when creating. */
  initial?: RecipeDetail
  onSaved: (recipe: RecipeDetail) => void
  onCancel?: () => void
}

export function RecipeForm({ units, initial, onSaved, onCancel }: Props) {
  const isEditing = initial !== undefined

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  // Servings is a string for the same reason quantities are: it's what the input
  // holds, and an empty box must not silently become 0.
  const [servings, setServings] = useState('4')
  const [rows, setRows] = useState<IngredientDraft[]>([blankIngredientRow()])
  const [steps, setSteps] = useState<StepDraft[]>([blankStep()])

  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  /**
   * Load an existing recipe into the form when editing.
   *
   * The dependency is `initial`, so switching from editing recipe 3 to recipe 7
   * refills the form. Without that, clicking Edit on a second recipe would show the
   * first one's data — a bug that only appears on the second click, which is the kind
   * that survives casual testing.
   */
  useEffect(() => {
    if (!initial) {
      setName('')
      setDescription('')
      setServings('4')
      setRows([blankIngredientRow()])
      setSteps([blankStep()])
      return
    }

    setName(initial.name)
    setDescription(initial.description ?? '')
    setServings(String(initial.servings))
    setRows(
      initial.ingredients.map((line) => ({
        key: crypto.randomUUID(),
        ingredient: line.ingredient,
        // Back to a string for the input. `?? ''` handles the "to taste" case, where
        // quantity is legitimately null.
        quantity: line.quantity === null ? '' : String(line.quantity),
        unit: (line.unit ?? '') as UnitCode | '',
        note: line.note ?? '',
      })),
    )
    setSteps(initial.steps.map((text) => ({ key: crypto.randomUUID(), text })))
  }, [initial])

  /**
   * Converts the form's working state into the shape the API expects.
   *
   * This function is where the draft-vs-payload distinction pays off — every
   * conversion that was deferred while editing happens here, in one place, with the
   * empty cases handled explicitly.
   */
  function buildPayload(): RecipePayload {
    return {
      name: name.trim(),
      // Empty description means "none", which the API expresses as null rather than
      // an empty string. Being consistent about which one means "absent" saves a lot
      // of confused `if (x === '' || x === null)` checks later.
      description: description.trim() === '' ? null : description.trim(),
      servings: Number(servings) || 1,

      ingredients: rows
        // Drop rows the user added but never filled in. Leaving a blank row at the
        // bottom of a form is completely normal behaviour, and it should not be an
        // error — it should just be ignored.
        .filter((row) => row.ingredient !== null)
        .map((row) => {
          const trimmedQuantity = row.quantity.trim()

          // THE Number('') TRAP, handled explicitly. In JavaScript, Number('') is 0
          // — not NaN, as most people expect. Passing the raw value through would
          // turn every empty quantity box into a literal zero, and "0 g salt" would
          // appear on shopping lists instead of "to taste". The server also rejects
          // a zero quantity (@Positive), so this would surface as a confusing 400
          // rather than silently wrong data — but it should never get that far.
          const quantity = trimmedQuantity === '' ? null : Number(trimmedQuantity)

          return {
            // The non-null assertion is safe because of the .filter above; TypeScript
            // cannot see that connection across the two calls.
            ingredientId: row.ingredient!.id,
            quantity,
            // A quantity and a unit must appear together or not at all — the server
            // enforces exactly this rule with @AssertTrue. Deriving the unit from
            // whether a quantity exists means the two can never disagree.
            unit: quantity === null ? null : (row.unit || null),
            note: row.note.trim() === '' ? null : row.note.trim(),
          }
        }),

      steps: steps.map((step) => step.text.trim()).filter((text) => text !== ''),
    }
  }

  const payloadPreview = buildPayload()
  // Mirror the server's @NotEmpty rules so the button is disabled rather than the
  // request being rejected. The server still checks — client-side validation is a
  // convenience for the user, never a security boundary, because anyone can send a
  // request that never went through this form.
  const canSubmit =
    name.trim() !== '' &&
    payloadPreview.ingredients.length > 0 &&
    payloadPreview.steps.length > 0 &&
    !saving

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    // Without this the browser reloads the page on submit, which in a single-page
    // app throws away all state and looks like a crash.
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setSaving(true)

    try {
      const payload = buildPayload()
      const saved = isEditing
        ? await api.recipes.update(initial.id, payload)
        : await api.recipes.create(payload)
      onSaved(saved)
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
        // The per-field messages the backend sent. Because the error envelope is
        // consistent across every endpoint, this one branch covers all of them.
        setFieldErrors(err.fieldErrors)
      } else {
        setError('Something went wrong while saving.')
      }
    } finally {
      // finally, so the button is re-enabled whether the save succeeded or failed.
      // Putting this only in the success path is a classic way to leave a form
      // permanently stuck on "Saving…" after one error.
      setSaving(false)
    }
  }

  return (
    <form className="card recipe-form" onSubmit={handleSubmit}>
      <h2>{isEditing ? `Edit “${initial.name}”` : 'New recipe'}</h2>

      <div className="field-grid">
        <div className="field">
          <label className="field-label" htmlFor="recipe-name">
            Name
          </label>
          <input
            id="recipe-name"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          {/* Show the server's message directly under the field it belongs to. */}
          {fieldErrors.name && <p className="field-error">{fieldErrors.name}</p>}
        </div>

        <div className="field field-servings">
          <label className="field-label" htmlFor="recipe-servings">
            Serves
          </label>
          <input
            id="recipe-servings"
            type="number"
            min="1"
            value={servings}
            onChange={(event) => setServings(event.target.value)}
          />
          {fieldErrors.servings && <p className="field-error">{fieldErrors.servings}</p>}
        </div>
      </div>

      <div className="field">
        <label className="field-label" htmlFor="recipe-description">
          Description <span className="field-optional">(optional)</span>
        </label>
        <input
          id="recipe-description"
          type="text"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </div>

      <IngredientRows rows={rows} units={units} onChange={setRows} />
      {fieldErrors.ingredients && <p className="field-error">{fieldErrors.ingredients}</p>}

      <StepRows steps={steps} onChange={setSteps} />
      {fieldErrors.steps && <p className="field-error">{fieldErrors.steps}</p>}

      {error && (
        // role="alert" makes a screen reader announce this the moment it appears,
        // without the user having to go looking for it. Essential for an error that
        // shows up somewhere other than where focus currently is.
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      <div className="form-actions">
        <button type="submit" className="btn-primary" disabled={!canSubmit}>
          {saving ? 'Saving…' : isEditing ? 'Save changes' : 'Create recipe'}
        </button>
        {onCancel && (
          <button type="button" className="btn-ghost" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
