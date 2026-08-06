import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Ingredient } from '../api/types'
import { useDebouncedValue } from './useDebouncedValue'

/**
 * Searches the ingredient catalog as the user types, with debouncing and — the part
 * that's easy to miss — protection against OUT-OF-ORDER RESPONSES.
 *
 * THE RACE CONDITION THIS EXISTS TO PREVENT:
 * You type "car", then "carrot". Two requests go out. There is no rule that says they
 * come back in the order they were sent — the "car" request might be slower and
 * arrive second. Naive code sets state on whatever arrives last, so the dropdown ends
 * up showing results for "car" while the input says "carrot". It looks like a caching
 * bug, it's intermittent, and it's essentially impossible to reproduce on a fast local
 * connection. It shows up for real users on real networks.
 *
 * The fix here is AbortController: before starting a new request, cancel the previous
 * one. An aborted fetch rejects rather than resolving, so a stale response can never
 * reach setState. The browser also stops waiting on a request nobody needs, which is
 * a small bonus.
 *
 * The alternative fix is a sequence number — increment a counter per request and
 * ignore any response that isn't the newest. That works too and is worth knowing,
 * since it also covers non-fetch async work. AbortController is cleaner when the
 * underlying API supports it.
 */
export function useIngredientSearch(query: string) {
  const debouncedQuery = useDebouncedValue(query, 250)

  const [results, setResults] = useState<Ingredient[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // An AbortController produces a "signal" that can be handed to fetch. Calling
    // .abort() on the controller cancels any request holding that signal.
    const controller = new AbortController()

    setLoading(true)
    setError(null)

    api.ingredients
      .search(debouncedQuery, controller.signal)
      .then((found) => {
        setResults(found)
        setLoading(false)
      })
      .catch((err: unknown) => {
        // An abort is an EXPECTED outcome, not a failure — it means the user kept
        // typing, which is the system working correctly. Showing an error for it
        // would make the UI flash "request cancelled" on every keystroke.
        if (controller.signal.aborted) return
        setError(err instanceof Error ? err.message : 'Could not search ingredients')
        setLoading(false)
      })

    // Cleanup: cancel the in-flight request whenever the query changes again or the
    // component unmounts.
    return () => controller.abort()
  }, [debouncedQuery])

  // isStale tells the UI that what it's showing belongs to an older query than what
  // is currently typed. Useful for dimming the list slightly rather than emptying it
  // — replacing results with a blank panel on every keystroke is far more jarring
  // than briefly showing slightly old ones.
  const isStale = query !== debouncedQuery

  return { results, loading, error, isStale }
}
