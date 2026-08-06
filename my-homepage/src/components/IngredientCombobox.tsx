import { useEffect, useId, useRef, useState } from 'react'
import { api } from '../api/client'
import type { Ingredient } from '../api/types'
import { useIngredientSearch } from '../hooks/useIngredientSearch'

/**
 * A "combobox" is a text input combined with a dropdown list: you type to filter, and
 * you pick from what appears. This one also lets you CREATE an ingredient that
 * doesn't exist yet, which is how the catalog grows without anyone having to
 * enumerate every food in the world up front.
 *
 * WHY THIS IS ~250 LINES AND NOT A <select>
 * A native <select> can't be typed into to filter, and with 180+ ingredients that
 * makes it unusable. A <datalist> can filter but can't be styled, can't show a
 * "create new" option, and behaves differently in every browser. So this is built by
 * hand — and building it by hand means taking on the job the native element was
 * doing for free: KEYBOARD AND SCREEN-READER SUPPORT.
 *
 * That job is defined by the WAI-ARIA Authoring Practices "combobox pattern", which
 * is worth reading once in your life. The essentials, all implemented below:
 *
 *   - The input carries role="combobox" plus state attributes (aria-expanded,
 *     aria-controls, aria-activedescendant) so assistive technology can describe it.
 *   - The list is role="listbox" and each option is role="option".
 *   - VIRTUAL FOCUS: the browser's focus never leaves the input. Instead,
 *     aria-activedescendant points at the id of the "highlighted" option. This is the
 *     single most important idea in the pattern — moving real focus into the list
 *     would mean the user could no longer type.
 *   - Arrow keys move the highlight, Enter selects, Escape closes.
 *
 * Getting this right once makes every design-system autocomplete you ever use
 * legible, because they are all implementing exactly this.
 */

type Props = {
  /** The currently selected ingredient, or null if the row is still empty. */
  value: Ingredient | null
  onChange: (ingredient: Ingredient | null) => void
  /** Rendered as the input's accessible label. */
  label: string
  /** Hides the visible label when the row is already inside a labelled table. */
  hideLabel?: boolean
}

export function IngredientCombobox({ value, onChange, label, hideLabel = false }: Props) {
  // What the user has typed. Separate from `value` (the committed selection),
  // because they legitimately differ: you can have "Carrot" selected and be halfway
  // through typing "Cauliflower" without having chosen it yet.
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)

  // WHICH OPTION IS HIGHLIGHTED, as an index into the options array. -1 means none.
  // This is the "virtual focus" position — no DOM element is actually focused.
  const [highlighted, setHighlighted] = useState(-1)

  const [creating, setCreating] = useState(false)

  const { results, loading, error } = useIngredientSearch(query)

  // useId generates an id that is unique across the whole page and stable across
  // renders. It matters here because this component appears once PER INGREDIENT ROW,
  // and duplicate ids would break the aria-controls / aria-activedescendant
  // references — assistive technology would follow them to the wrong row's list.
  // Hand-rolling this with a counter or Math.random() breaks under server rendering.
  const baseId = useId()
  const listboxId = `${baseId}-listbox`
  const inputId = `${baseId}-input`

  const rootRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLUListElement>(null)

  // Does the typed text exactly match something we already found? If so, there is no
  // point offering to create it. Compared case-insensitively and trimmed, mirroring
  // the server's slug rule so the UI and the backend agree about what "already
  // exists" means.
  const trimmedQuery = query.trim()
  const exactMatchExists = results.some(
    (ingredient) => ingredient.name.toLowerCase() === trimmedQuery.toLowerCase(),
  )
  const canCreate = trimmedQuery.length > 0 && !exactMatchExists && !loading

  // The "create new" entry is modelled as just another option at the end of the list.
  // That is deliberate: it means Arrow-Down/Enter reaches it by exactly the same code
  // path as any real ingredient, with no special-casing anywhere in the key handler.
  // Special cases in keyboard code are where accessibility bugs live.
  const optionCount = results.length + (canCreate ? 1 : 0)
  const createIndex = canCreate ? results.length : -1

  // ---- Close when the user clicks outside -----------------------------------
  useEffect(() => {
    if (!open) return

    function handlePointerDown(event: PointerEvent) {
      // .contains() walks the DOM tree, so this correctly treats a click on the input
      // or on any option as "inside".
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
        setHighlighted(-1)
      }
    }

    // pointerdown rather than click: it fires before focus moves, which avoids a
    // flicker where the list closes and instantly reopens.
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [open])

  // ---- Keep the highlighted option scrolled into view -----------------------
  useEffect(() => {
    if (highlighted < 0 || !listRef.current) return
    const node = listRef.current.children[highlighted] as HTMLElement | undefined
    // block: 'nearest' scrolls the minimum amount needed. Without it the browser
    // centres the option, so the whole list lurches on every arrow press.
    node?.scrollIntoView({ block: 'nearest' })
  }, [highlighted])

  function selectIngredient(ingredient: Ingredient) {
    onChange(ingredient)
    setQuery('')
    setOpen(false)
    setHighlighted(-1)
  }

  /**
   * Creates a brand-new catalog ingredient from what the user typed.
   *
   * The server treats POST /api/ingredients as get-or-create, so if someone else
   * added "Shallot" a moment ago we simply receive that existing row back. There is
   * no conflict case for this function to handle — which is exactly why the endpoint
   * was designed that way.
   */
  async function createIngredient() {
    if (!canCreate || creating) return
    setCreating(true)
    try {
      const created = await api.ingredients.create(trimmedQuery)
      selectIngredient(created)
    } catch {
      // Deliberately swallowed here rather than crashing the row: the parent form
      // surfaces save errors, and a failed create leaves the input exactly as the
      // user left it so they can simply try again.
    } finally {
      setCreating(false)
    }
  }

  function commitHighlighted() {
    if (highlighted < 0) return
    if (highlighted === createIndex) {
      void createIngredient()
    } else {
      selectIngredient(results[highlighted])
    }
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    switch (event.key) {
      case 'ArrowDown':
        // preventDefault stops the browser's own behaviour — here, moving the text
        // cursor to the end of the input. Without it the caret jumps around while
        // you navigate the list.
        event.preventDefault()
        if (!open) {
          setOpen(true)
          setHighlighted(0)
        } else {
          // The modulo makes the highlight WRAP from the last option back to the
          // first, which is what the ARIA pattern specifies and what users expect.
          setHighlighted((current) => (current + 1) % Math.max(optionCount, 1))
        }
        break

      case 'ArrowUp':
        event.preventDefault()
        if (!open) {
          setOpen(true)
          setHighlighted(optionCount - 1)
        } else {
          // Adding optionCount before the modulo keeps the result positive when
          // moving up from index 0. In JavaScript, -1 % 5 is -1, not 4 — a classic
          // off-by-one that manifests as "arrow-up at the top does nothing".
          setHighlighted((current) => (current - 1 + optionCount) % Math.max(optionCount, 1))
        }
        break

      case 'Home':
        if (open) {
          event.preventDefault()
          setHighlighted(0)
        }
        break

      case 'End':
        if (open) {
          event.preventDefault()
          setHighlighted(optionCount - 1)
        }
        break

      case 'Enter':
        if (open && highlighted >= 0) {
          // Without preventDefault this Enter would ALSO submit the surrounding
          // form, so picking an ingredient would save a half-filled recipe.
          event.preventDefault()
          commitHighlighted()
        }
        break

      case 'Escape':
        // Two-stage escape, as the ARIA pattern prescribes: the first press closes
        // the list, a second press clears what you typed. It means Escape is never
        // destructive by surprise.
        if (open) {
          event.preventDefault()
          setOpen(false)
          setHighlighted(-1)
        } else if (query) {
          setQuery('')
        }
        break

      case 'Tab':
        // Tab moves on to the next field. Close the list, but do NOT select the
        // highlighted option — tabbing away is a decision to leave, not to choose.
        setOpen(false)
        setHighlighted(-1)
        break

      default:
        break
    }
  }

  const showList = open && (optionCount > 0 || loading)

  return (
    <div className="combobox" ref={rootRef}>
      {/* A label is always rendered, even when visually hidden. "sr-only" keeps it
          available to screen readers while removing it from the visual layout —
          the correct way to omit a label, as opposed to just not having one. */}
      <label htmlFor={inputId} className={hideLabel ? 'sr-only' : 'field-label'}>
        {label}
      </label>

      <div className="combobox-control">
        <input
          id={inputId}
          type="text"
          className="combobox-input"
          // role="combobox" announces the widget's nature. The surrounding div is
          // NOT given role="combobox" — in the current ARIA spec the role belongs on
          // the input itself. Older tutorials show it on the wrapper; that was the
          // ARIA 1.1 pattern and it is now out of date.
          role="combobox"
          // Tells assistive tech whether the list is showing right now.
          aria-expanded={showList}
          // Points at the element this input controls.
          aria-controls={listboxId}
          // "list" means: suggestions appear in a list, and the input is not
          // auto-filled with the highlighted value as you arrow through.
          aria-autocomplete="list"
          // VIRTUAL FOCUS, the crux of the whole pattern. Real DOM focus stays in the
          // input at all times; this attribute names the id of the option that is
          // currently highlighted, and a screen reader announces that option as
          // though it were focused. Moving real focus into the list instead would
          // stop the user being able to type, which defeats the point of a combobox.
          aria-activedescendant={
            highlighted >= 0 ? `${baseId}-option-${highlighted}` : undefined
          }
          // autoComplete="off" stops the BROWSER's own saved-values dropdown from
          // appearing on top of ours. Two overlapping dropdowns is a real bug.
          autoComplete="off"
          // When something is selected, show its name as the placeholder so the row
          // still reads correctly while the input itself sits empty and ready.
          placeholder={value ? value.name : 'Search ingredients…'}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(true)
            // Reset the highlight on every edit. Keeping an old index would leave the
            // highlight pointing at whatever now happens to occupy that slot, so
            // pressing Enter would select something the user never looked at.
            setHighlighted(-1)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
        />

        {/* The current selection, shown as a removable chip. Without this there'd be
            no way to tell a chosen ingredient from an empty row once the input clears. */}
        {value && (
          <span className="combobox-selection">
            {value.name}
            <button
              type="button"
              className="combobox-clear"
              onClick={() => {
                onChange(null)
                setQuery('')
              }}
              // Buttons whose visible content is an icon need an accessible name, or
              // a screen reader announces only "button".
              aria-label={`Remove ${value.name}`}
            >
              ×
            </button>
          </span>
        )}
      </div>

      {showList && (
        <ul className="combobox-list" id={listboxId} role="listbox" ref={listRef}>
          {loading && results.length === 0 && (
            // aria-disabled marks this as a status row rather than a choosable option.
            <li className="combobox-status" role="option" aria-selected={false} aria-disabled>
              Searching…
            </li>
          )}

          {results.map((ingredient, index) => (
            <li
              key={ingredient.id}
              // This id is what aria-activedescendant points at, which is why it is
              // built from the same baseId and the option's index.
              id={`${baseId}-option-${index}`}
              role="option"
              aria-selected={index === highlighted}
              className={index === highlighted ? 'combobox-option is-highlighted' : 'combobox-option'}
              // onMouseDown, NOT onClick. mousedown fires before the input loses
              // focus; by the time a click event arrives, the blur has already closed
              // the list and the click lands on nothing. This is the single most
              // common reason a hand-built dropdown "doesn't respond to clicks".
              onMouseDown={(event) => {
                event.preventDefault()
                selectIngredient(ingredient)
              }}
              onMouseEnter={() => setHighlighted(index)}
            >
              <span className="combobox-option-name">{ingredient.name}</span>
              {ingredient.category && (
                <span className="combobox-option-meta">{ingredient.category}</span>
              )}
            </li>
          ))}

          {canCreate && (
            <li
              id={`${baseId}-option-${createIndex}`}
              role="option"
              aria-selected={createIndex === highlighted}
              className={
                createIndex === highlighted
                  ? 'combobox-option combobox-create is-highlighted'
                  : 'combobox-option combobox-create'
              }
              onMouseDown={(event) => {
                event.preventDefault()
                void createIngredient()
              }}
              onMouseEnter={() => setHighlighted(createIndex)}
            >
              {creating ? `Creating "${trimmedQuery}"…` : `+ Create "${trimmedQuery}"`}
              {/* A gentle nudge toward the existing options before inventing a
                  duplicate. The server's unique-slug rule catches case and spacing,
                  but it cannot know that "Tomatoes" and "Tomato" are the same thing —
                  plural forms are genuinely ambiguous ("Molasses" is not a plural).
                  Rather than guess with a stemming algorithm that will sometimes be
                  confidently wrong, we show the user what already exists and let
                  them decide. Prevention by visibility. */}
              {results.length > 0 && (
                <span className="combobox-option-meta">
                  {results.length} similar {results.length === 1 ? 'ingredient' : 'ingredients'} above
                </span>
              )}
            </li>
          )}

          {!loading && optionCount === 0 && (
            <li className="combobox-status" role="option" aria-selected={false} aria-disabled>
              No ingredients found
            </li>
          )}
        </ul>
      )}

      {error && <p className="field-error">{error}</p>}
    </div>
  )
}
