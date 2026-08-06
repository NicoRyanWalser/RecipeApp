// ============================================================================
//  TypeScript mirrors of the backend's DTOs.
//
//  Every type here corresponds to a Java record on the server. They are written
//  BY HAND, which means they can drift out of sync — if the backend renames a
//  field and this file isn't updated, TypeScript will happily compile code that
//  reads a property the server never sends, and you get "undefined" at runtime.
//
//  That is the one real weakness of this approach, so it's worth knowing the
//  alternative: tools like openapi-typescript can GENERATE this file from the
//  backend's OpenAPI description, making drift impossible. That's the right move
//  once an API is large or shared between teams. For an app this size, hand-written
//  types are less machinery and read better, and keeping them in ONE file means
//  there is exactly one place to update when the backend changes.
//
//  IMPORTANT for imports elsewhere: this project sets "verbatimModuleSyntax": true
//  in tsconfig, which means anything imported only for its TYPE must be imported
//  with `import type { Recipe } from './api/types'`. A plain `import { Recipe }`
//  fails the build, because the compiler refuses to guess whether you wanted a
//  runtime value or a type-only annotation.
// ============================================================================

// ---------------------------------------------------------------------------
//  Units
// ---------------------------------------------------------------------------

// The three kinds of quantity a unit can measure. Mirrors the Java Dimension enum.
export type Dimension = 'MASS' | 'VOLUME' | 'COUNT'

// A "union of string literals" — this says the value must be exactly one of these
// strings, not just any string. Writing unit: 'GRMA' becomes a compile error rather
// than a 400 from the server at runtime.
//
// It must stay in step with the Java Unit enum. The dropdown itself is populated
// from GET /api/units at runtime, so a missing entry here won't hide a unit from the
// user — it only weakens the type checking, which is the failure mode you want.
export type UnitCode =
  | 'MILLIGRAM' | 'GRAM' | 'KILOGRAM' | 'OUNCE' | 'POUND'
  | 'MILLILITER' | 'CENTILITER' | 'DECILITER' | 'LITER'
  | 'TEASPOON' | 'TABLESPOON' | 'FLUID_OUNCE' | 'CUP' | 'PINCH'
  | 'PIECE' | 'DOZEN'

export type UnitOption = {
  code: UnitCode
  display: string
  dimension: Dimension
}

// ---------------------------------------------------------------------------
//  Ingredients
// ---------------------------------------------------------------------------

export type Ingredient = {
  id: number
  name: string
  // null for ingredients a user created from the combobox — they only supply a name.
  category: string | null
  defaultUnit: UnitCode | null
  // Whether this ingredient can convert between count/weight and volume/weight.
  // Used to warn the user, before they get a split shopping-list line, that amounts
  // for this ingredient may not combine.
  hasPieceWeight: boolean
  hasDensity: boolean
}

// ---------------------------------------------------------------------------
//  Recipes
// ---------------------------------------------------------------------------

// The lightweight shape returned by GET /api/recipes, for the list view.
export type RecipeSummary = {
  id: number
  name: string
  description: string | null
  servings: number
  ingredientCount: number
  stepCount: number
  // Instants arrive as ISO-8601 strings ("2026-08-06T00:08:14.449Z"). JSON has no
  // date type, so every timestamp crossing the wire is a string; converting it to a
  // real Date is the caller's job, done at the point of display.
  createdAt: string
}

export type RecipeIngredientLine = {
  id: number
  position: number
  ingredient: Ingredient
  // null means "to taste" — a real, intentional state, not missing data.
  quantity: number | null
  unit: UnitCode | null
  unitDisplay: string | null
  note: string | null
}

// The full shape from GET /api/recipes/{id}, POST, and PUT.
export type RecipeDetail = {
  id: number
  name: string
  description: string | null
  servings: number
  ingredients: RecipeIngredientLine[]
  steps: string[]
  createdAt: string
  updatedAt: string
}

// What we SEND when creating or updating. Note it is a different shape from
// RecipeDetail: ingredients are identified by id only, and there is no position
// field, because the server derives position from the array index.
export type RecipeIngredientPayload = {
  ingredientId: number
  quantity: number | null
  unit: UnitCode | null
  note: string | null
}

export type RecipePayload = {
  name: string
  description: string | null
  servings: number
  ingredients: RecipeIngredientPayload[]
  steps: string[]
}

// ---------------------------------------------------------------------------
//  Shopping list
// ---------------------------------------------------------------------------

export type AmountLine = {
  quantity: number
  unit: UnitCode
  unitDisplay: string
  // Pre-formatted by the server, e.g. "1.4 kg". Rendering this rather than
  // re-assembling quantity + unitDisplay keeps the rounding rules in one place.
  display: string
}

export type ShoppingListItem = {
  ingredientId: number
  ingredientName: string
  category: string | null
  // USUALLY ONE ENTRY. More than one when the server could not convert between
  // dimensions — e.g. 500 ml and 200 g of an ingredient with no known density. The
  // server deliberately shows both rather than guessing, so the UI must be prepared
  // to render a list here, not a single value.
  amounts: AmountLine[]
  // True when at least one recipe asked for this without a quantity ("to taste").
  unquantified: boolean
  notes: string[]
  fromRecipes: string[]
}

export type InstructionSection = {
  recipeId: number
  recipeName: string
  servings: number
  steps: string[]
}

export type ShoppingList = {
  recipeCount: number
  items: ShoppingListItem[]
  instructionSections: InstructionSection[]
}

// ---------------------------------------------------------------------------
//  Errors
// ---------------------------------------------------------------------------

// The single error envelope every failing endpoint returns. Because the backend is
// consistent about this, the frontend needs exactly one error-handling path.
export type ApiErrorBody = {
  status: number
  message: string
  // Present only for validation failures: {"name": "Recipe name is required"}.
  // This is what lets the form show a message under the specific offending input
  // instead of one vague banner at the top.
  fieldErrors?: Record<string, string>
  timestamp: string
}
