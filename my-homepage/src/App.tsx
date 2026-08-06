// useState lets a component remember values between renders. useEffect lets us run
// side effects — here, fetching data from the backend when the app first appears.
import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { ApiError, api } from './api/client'
import type { RecipeDetail as RecipeDetailType, RecipeSummary, ShoppingList, UnitOption } from './api/types'
import { RecipeDetail } from './components/RecipeDetail'
import { RecipeForm } from './components/RecipeForm'
import { RecipeList } from './components/RecipeList'
import { ShoppingListPanel } from './components/ShoppingListPanel'

/**
 * App is now mostly PLUMBING: it owns the state that more than one component needs,
 * fetches data, and decides what to render. The actual UI lives in components/.
 *
 * That split is worth making deliberately. This file was previously the entire
 * application — form, list, fetching, and markup in one place. That is fine at 200
 * lines and unmanageable at 800, and the moment two pieces of UI need the same piece
 * of state (here: which recipes are selected), the structure has to change anyway.
 *
 * The rule used to decide what lives here: state goes to the LOWEST component that
 * can serve everyone who needs it. The combobox's typed query is nobody else's
 * business, so it stays inside the combobox. The selected recipe ids are needed by
 * the list, the button, and the shopping-list request, so they live here.
 */
function App() {
  // ---- Data from the server ------------------------------------------------
  const [recipes, setRecipes] = useState<RecipeSummary[]>([])
  // The unit list is static; we fetch it once at startup and pass it down.
  const [units, setUnits] = useState<UnitOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // ---- What the user is doing ----------------------------------------------
  // Which recipes are ticked for the shopping list.
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  // The recipe whose detail is expanded inline, if any.
  const [expanded, setExpanded] = useState<RecipeDetailType | null>(null)
  // The recipe being edited, if any. null means the form is in "create" mode.
  const [editing, setEditing] = useState<RecipeDetailType | null>(null)
  // The generated shopping list, or null when none has been requested.
  const [shoppingList, setShoppingList] = useState<ShoppingList | null>(null)
  const [building, setBuilding] = useState(false)

  /**
   * Reloads the recipe list.
   *
   * Wrapped in useCallback so the function identity stays stable between renders.
   * Without it, a new function object would be created every render, and any
   * useEffect listing it as a dependency would re-run every render — an infinite
   * fetch loop. This is the single most common way useEffect goes wrong.
   */
  const loadRecipes = useCallback(async () => {
    try {
      setRecipes(await api.recipes.list())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load recipes')
    }
  }, [])

  // The empty dependency array means "run once, after the first render" — the right
  // place to fetch data the whole app needs.
  useEffect(() => {
    async function loadEverything() {
      setLoading(true)
      // Promise.all runs both requests CONCURRENTLY rather than one after the other.
      // Awaiting them in sequence would take as long as both combined for no reason,
      // since neither depends on the other's result.
      await Promise.all([
        loadRecipes(),
        api.units
          .list()
          .then(setUnits)
          .catch(() => setError('Could not load measurement units')),
      ])
      setLoading(false)
    }
    void loadEverything()
  }, [loadRecipes])

  function toggleSelect(id: number) {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function handleExpand(id: number) {
    // Clicking "View" on the already-open recipe closes it.
    if (expanded?.id === id) {
      setExpanded(null)
      return
    }
    try {
      // The list only carries summaries, so the full recipe is fetched on demand.
      // That is the deliberate trade behind having two response shapes: the list
      // stays cheap, and the detail costs one small request when it's actually wanted.
      setExpanded(await api.recipes.get(id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load that recipe')
    }
  }

  async function handleEdit(id: number) {
    try {
      setEditing(await api.recipes.get(id))
      // Scroll the form into view — the list can be long, and silently switching the
      // form's contents somewhere off-screen looks like nothing happened.
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load that recipe')
    }
  }

  async function handleDelete(id: number) {
    // window.confirm is crude, but deleting is irreversible and an accidental click
    // costs the user real work. A styled modal would be nicer; no confirmation at all
    // would be a mistake.
    const recipe = recipes.find((candidate) => candidate.id === id)
    if (!window.confirm(`Delete “${recipe?.name ?? 'this recipe'}”? This cannot be undone.`)) {
      return
    }

    try {
      await api.recipes.remove(id)

      // Clean up every piece of state that referred to the now-deleted recipe.
      // Forgetting this is a classic source of ghost UI — an expanded panel showing a
      // recipe that no longer exists, or a selection that makes the shopping-list
      // request 404.
      if (expanded?.id === id) setExpanded(null)
      if (editing?.id === id) setEditing(null)
      setSelectedIds((current) => {
        const next = new Set(current)
        next.delete(id)
        return next
      })
      // The shopping list was computed from a set of recipes that has now changed, so
      // it is stale. Clearing it is more honest than showing a list built from a
      // recipe the user just removed.
      setShoppingList(null)

      await loadRecipes()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete that recipe')
    }
  }

  async function buildShoppingList() {
    setBuilding(true)
    setError(null)
    try {
      setShoppingList(await api.shoppingList.combine([...selectedIds]))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not build the shopping list')
    } finally {
      setBuilding(false)
    }
  }

  function handleSaved(saved: RecipeDetailType) {
    setEditing(null)
    // If the saved recipe is the one currently expanded, refresh that view too so it
    // doesn't keep showing the pre-edit version.
    if (expanded?.id === saved.id) setExpanded(saved)
    void loadRecipes()
  }

  return (
    <>
      <header className="app-header">
        <h1>Recipes</h1>
        <p className="muted">
          Build recipes from a shared ingredient catalog, then combine any of them into
          one shopping list.
        </p>
      </header>

      <main className="app-main">
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        {/* The key prop forces React to DISCARD and rebuild the form when switching
            between creating and editing, or between two different recipes. Without
            it React reuses the same component instance, and its internal state (the
            typed queries inside each combobox, for instance) would carry over from
            the previous recipe. Changing a key is the standard way to say "this is a
            different thing now, start fresh". */}
        <RecipeForm
          key={editing ? `edit-${editing.id}` : 'create'}
          units={units}
          initial={editing ?? undefined}
          onSaved={handleSaved}
          onCancel={editing ? () => setEditing(null) : undefined}
        />

        <section className="list-section">
          <div className="panel-header">
            <h2>Your recipes</h2>
            <button
              type="button"
              className="btn-primary"
              // Disabled with nothing selected, so the user cannot send a request the
              // server would reject with "Select at least one recipe".
              disabled={selectedIds.size === 0 || building}
              onClick={buildShoppingList}
            >
              {building ? 'Building…' : `Build shopping list (${selectedIds.size})`}
            </button>
          </div>

          {loading ? (
            <div className="card empty-state">
              <p className="muted">Loading…</p>
            </div>
          ) : (
            <RecipeList
              recipes={recipes}
              selectedIds={selectedIds}
              expandedId={expanded?.id ?? null}
              onToggleSelect={toggleSelect}
              onExpand={handleExpand}
              onEdit={handleEdit}
              onDelete={handleDelete}
            />
          )}

          {expanded && (
            <div className="card">
              <h3>{expanded.name}</h3>
              <RecipeDetail recipe={expanded} />
            </div>
          )}
        </section>

        {shoppingList && (
          <ShoppingListPanel list={shoppingList} onClear={() => setShoppingList(null)} />
        )}
      </main>
    </>
  )
}

export default App
