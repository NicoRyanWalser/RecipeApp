// ============================================================================
//  The API client — the ONLY file in this app that calls fetch().
//
//  Why centralize it? Because every network call needs the same handful of things:
//  the right headers, a check that the response actually succeeded, JSON parsing,
//  and a sensible error. Written inline at each call site, that's four lines of
//  boilerplate repeated everywhere, and the copy that gets it wrong is the one you
//  don't notice.
//
//  It also gives you ONE place to change when something global changes. Adding an
//  auth header, pointing at a different base URL, adding a retry, logging every
//  request — all of those are a few lines here instead of an edit in sixteen
//  components. That single-point-of-change property is the real payoff, and it's
//  worth setting up early even when the app is small.
// ============================================================================

import type {
  ApiErrorBody,
  Ingredient,
  RecipeDetail,
  RecipePayload,
  RecipeSummary,
  ShoppingList,
  UnitOption,
} from './types'

/**
 * A typed error carrying everything the UI needs to react.
 *
 * `extends Error` means this behaves like any other JavaScript error — it can be
 * thrown, caught, and has a .message — while adding the two fields that let a caller
 * do something smarter than show a generic message: the HTTP status, and the
 * per-field validation errors when there are any.
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    // super(message) must come FIRST — `this` does not exist until the parent
    // constructor has run, so any assignment before it is a compile error.
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    // Setting name explicitly keeps console output and stack traces readable
    // ("ApiError: Recipe 999 was not found" rather than a generic "Error").
    this.name = 'ApiError'
  }

  /** True when the server rejected the submitted data field by field. */
  get isValidationError(): boolean {
    return this.status === 400 && Object.keys(this.fieldErrors).length > 0
  }
}

/**
 * The shared request helper every method below goes through.
 *
 * <T> is a "generic" — a placeholder for whatever type this particular call returns.
 * Writing request<RecipeDetail>(...) means the result is typed as RecipeDetail, so
 * the compiler catches a typo in a property name at the call site. Without the
 * generic, everything would come back as `any` and TypeScript would stop helping
 * exactly where the data enters the app, which is where it helps most.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response

  try {
    response = await fetch(path, {
      ...init,
      headers: {
        // Only send a Content-Type when there's actually a body. Sending it on a GET
        // is harmless but misleading, and some servers are fussier than ours.
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    })
  } catch {
    // fetch() only rejects when the request never completed — the server is down, DNS
    // failed, the network dropped. It does NOT reject on a 404 or a 500; those are
    // successful round trips that happen to carry an error status, and they're
    // handled below. Confusing the two is one of the most common fetch mistakes:
    // code that only uses .catch() silently treats every 500 as a success.
    throw new ApiError('Could not reach the server. Is the backend running?', 0)
  }

  // 204 No Content — a successful DELETE. There is no body to parse, and calling
  // .json() on it would throw a confusing syntax error on an empty string.
  if (response.status === 204) {
    return undefined as T
  }

  // Read the body ONCE as text. A Response body is a stream that can only be
  // consumed a single time, so calling .json() and then .text() on the same response
  // throws "body stream already read". Taking the text first lets us try to parse it
  // and still have something to report if it isn't valid JSON.
  const raw = await response.text()

  let parsed: unknown = null
  if (raw) {
    try {
      parsed = JSON.parse(raw)
    } catch {
      // A non-JSON body from an endpoint that should return JSON usually means a
      // proxy or server error page got in the way.
      if (!response.ok) {
        throw new ApiError(`Request failed (HTTP ${response.status})`, response.status)
      }
      throw new ApiError('The server sent a response that could not be understood.', response.status)
    }
  }

  if (!response.ok) {
    // The backend's GlobalExceptionHandler guarantees this envelope on every error,
    // which is what makes one generic handler sufficient here.
    const body = parsed as ApiErrorBody | null
    throw new ApiError(
      body?.message ?? `Request failed (HTTP ${response.status})`,
      response.status,
      body?.fieldErrors ?? {},
    )
  }

  return parsed as T
}

// ---------------------------------------------------------------------------
//  The endpoints, grouped by resource.
// ---------------------------------------------------------------------------

export const api = {
  units: {
    /** The unit dropdown's options. Static — fetch once at startup and keep it. */
    list: () => request<UnitOption[]>('/api/units'),
  },

  ingredients: {
    /**
     * Search the catalog. An empty query returns the first alphabetical page, which
     * is what the combobox shows before the user types.
     *
     * encodeURIComponent is not optional politeness: a query containing "&" or "#"
     * would otherwise be read as the start of another URL parameter, and the search
     * would silently be for the wrong thing.
     */
    search: (query: string, signal?: AbortSignal) =>
      request<Ingredient[]>(`/api/ingredients?q=${encodeURIComponent(query)}`, { signal }),

    /**
     * Create an ingredient — or get back the existing one if the name already maps
     * to a known slug. The server deliberately makes this get-or-create so the
     * combobox has a single code path and never has to handle a conflict.
     */
    create: (name: string) =>
      request<Ingredient>('/api/ingredients', {
        method: 'POST',
        body: JSON.stringify({ name }),
      }),
  },

  recipes: {
    list: () => request<RecipeSummary[]>('/api/recipes'),

    get: (id: number) => request<RecipeDetail>(`/api/recipes/${id}`),

    create: (payload: RecipePayload) =>
      request<RecipeDetail>('/api/recipes', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),

    update: (id: number, payload: RecipePayload) =>
      request<RecipeDetail>(`/api/recipes/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),

    remove: (id: number) =>
      request<void>(`/api/recipes/${id}`, { method: 'DELETE' }),
  },

  shoppingList: {
    /**
     * Combine several recipes. POST rather than GET because the selection travels in
     * the body — see the ShoppingListController comment for why that trade is made
     * despite the request changing nothing.
     */
    combine: (recipeIds: number[]) =>
      request<ShoppingList>('/api/shopping-list', {
        method: 'POST',
        body: JSON.stringify({ recipeIds }),
      }),
  },
}

// A NOTE ON THE URLs: every path here is relative ("/api/recipes"), never absolute
// ("http://localhost:8080/api/recipes"). That is what lets the Vite dev proxy work —
// the browser sends the request to the page's own origin, and Vite forwards anything
// starting with /api to the backend on port 8080. Because the browser only ever sees
// one origin, there is no cross-origin request and therefore no CORS to configure.
//
// The flip side, worth knowing before it bites: this only holds while the frontend is
// served by Vite. Serving a built dist/ from a different host means the /api paths no
// longer resolve, and you would need a real base URL here plus CORS configuration on
// the Spring side. One more reason for all the URLs to live in a single file.
