import type { Ingredient, UnitCode } from '../api/types'

/**
 * The types and factory functions for the recipe form's editable rows.
 *
 * WHY THESE LIVE IN THEIR OWN FILE rather than beside the components that use them:
 * Vite's "Fast Refresh" updates a React component in the browser without reloading
 * the page or losing your form state — but it can only do that for files that export
 * COMPONENTS AND NOTHING ELSE. It has no way to know whether a re-exported plain
 * function is safe to swap out, so a single non-component export downgrades the whole
 * file to a full page reload.
 *
 * In a form this size that is a genuine annoyance: every tweak to IngredientRows
 * would wipe the half-filled recipe you were testing with. The linter flags it
 * (react/only-export-components), and the fix is exactly this — one file for
 * components, another for the values they share.
 */

/**
 * The working shape of one ingredient row WHILE THE USER IS EDITING IT.
 *
 * Deliberately NOT the same shape as the API payload, and every difference comes
 * from the same fact: a half-filled form is a valid state, but a half-filled API
 * request is not.
 *
 *   - `quantity` is a STRING, because that is what an <input> gives you. Storing a
 *     number would mean converting on every keystroke, and Number('') is 0 — which
 *     would silently turn an empty box into "0 g" instead of "to taste". Converting
 *     once at submit time, where the empty case is handled explicitly, is simpler
 *     and correct.
 *
 *   - `ingredient` may be null, because a freshly added row has nothing chosen yet.
 *
 *   - `key` exists purely for React; see below.
 */
export type IngredientDraft = {
  /**
   * A stable identity for this row, used as React's `key`.
   *
   * WHY NOT THE ARRAY INDEX? React uses the key to match DOM nodes to items across
   * re-renders. With index keys, deleting row 2 of four shifts every later row's key
   * down by one, so React concludes rows 2 and 3 changed their CONTENT rather than
   * that row 2 was removed. Visibly, the text you typed appears to jump up a row,
   * and component state below it (a combobox's typed query, say) attaches to the
   * wrong row. Any list whose rows can be deleted or reordered needs its own key.
   *
   * crypto.randomUUID() is built into browsers — no library required.
   */
  key: string
  ingredient: Ingredient | null
  quantity: string
  unit: UnitCode | ''
  note: string
}

export type StepDraft = {
  // Same reasoning as IngredientDraft.key — steps can be reordered and deleted.
  key: string
  text: string
}

export function blankIngredientRow(): IngredientDraft {
  return { key: crypto.randomUUID(), ingredient: null, quantity: '', unit: '', note: '' }
}

export function blankStep(): StepDraft {
  return { key: crypto.randomUUID(), text: '' }
}
