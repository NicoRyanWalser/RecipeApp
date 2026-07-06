// useState lets a component remember values between renders (form fields, the
// list of recipes, status messages). useEffect lets us run side effects — here,
// fetching data from the backend once when the component first appears.
import { useState, useEffect } from 'react'
import './App.css'
// NOTE: the reactLogo / viteLogo / heroImg imports were removed because they are
// only referenced by the commented-out hero sections below. TypeScript's
// "noUnusedLocals" rule fails the build on unused imports. If you uncomment those
// sections later, re-add the matching imports here.

// A TypeScript "type" describing the shape of a recipe as the backend returns it.
// This mirrors the RecipeResponse record in the Java code. Typing our API data
// means the compiler catches mistakes like reading a field that doesn't exist.
type Recipe = {
  id: number
  name: string
  ingredients: string
  instructions: string
  createdAt: string
}

function App() {
  // --- Form field state -----------------------------------------------------
  // Each input is a "controlled component": React holds the current value in
  // state, and the input displays that state. Typing fires onChange, which
  // updates state, which re-renders the input with the new value.
  const [name, setName] = useState('')
  const [ingredients, setIngredients] = useState('')
  const [instructions, setInstructions] = useState('')

  // --- UI status state ------------------------------------------------------
  const [recipes, setRecipes] = useState<Recipe[]>([]) // the saved recipes list
  const [error, setError] = useState<string | null>(null) // any error message
  const [submitting, setSubmitting] = useState(false) // disables the button mid-request

  // Loads all recipes from the backend and stores them in state.
  // GET /api/recipes -> the Vite proxy forwards it to Spring Boot on port 8080.
  async function loadRecipes() {
    try {
      const res = await fetch('/api/recipes')
      if (!res.ok) throw new Error(`Failed to load recipes (HTTP ${res.status})`)
      // res.json() parses the JSON body into JavaScript objects.
      const data: Recipe[] = await res.json()
      setRecipes(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load recipes')
    }
  }

  // useEffect with an empty dependency array "[]" runs exactly once, right after
  // the component first renders — the perfect place to fetch initial data.
  useEffect(() => {
    loadRecipes()
  }, [])

  // Runs when the form is submitted. It's "async" because it awaits a network call.
  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    // By default, submitting an HTML form reloads the whole page. In a React app
    // we handle it ourselves, so we prevent that default browser behavior.
    event.preventDefault()
    setError(null)
    setSubmitting(true)

    try {
      // POST the new recipe to the backend as JSON.
      const res = await fetch('/api/recipes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        // JSON.stringify turns our JS object into a JSON string for the request body.
        // The field names must match the Java RecipeRequest record exactly.
        body: JSON.stringify({ name, ingredients, instructions }),
      })

      // The backend returns 400 if validation fails, or 201 on success.
      if (!res.ok) throw new Error(`Could not save recipe (HTTP ${res.status})`)

      // Success: clear the form fields...
      setName('')
      setIngredients('')
      setInstructions('')
      // ...and refresh the list so the new recipe appears immediately.
      await loadRecipes()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save recipe')
    } finally {
      // Runs whether we succeeded or failed — re-enable the submit button.
      setSubmitting(false)
    }
  }

  return (
    <>
      {/* <section id="center">
        <div className="hero">
          <img src={heroImg} className="base" width="170" height="179" alt="" />
          <img src={reactLogo} className="framework" alt="React logo" />
          <img src={viteLogo} className="vite" alt="Vite logo" />
        </div>
        <div>
          <h1>Get started</h1>
          <p>
            Edit <code>src/App.tsx</code> and save to test <code>HMR</code>
          </p>
        </div>
      </section>

      <div className="ticks"></div> */}

      {/* <section id="next-steps">
        <div id="docs">
          <svg className="icon" role="presentation" aria-hidden="true">
            <use href="/icons.svg#documentation-icon"></use>
          </svg>
          <h2>Documentation</h2>
          <p>Your questions, answered</p>
          <ul>
            <li>
              <a href="https://vite.dev/" target="_blank">
                <img className="logo" src={viteLogo} alt="" />
                Explore Vite
              </a>
            </li>
            <li>
              <a href="https://react.dev/" target="_blank">
                <img className="button-icon" src={reactLogo} alt="" />
                Learn more
              </a>
            </li>
          </ul>
        </div>
      </section> */}

      <div className="ticks"></div>
      <section id="spacer"></section>

      <section id="RecipeForm">
        <div className="formContainer">
          <h2>Recipe Form</h2>

          {/* onSubmit wires the form to our handler above. */}
          <form onSubmit={handleSubmit}>
            <label htmlFor="recipeName">Recipe Name:</label>
            {/* value + onChange make this a controlled input tied to React state. */}
            <input
              type="text"
              id="recipeName"
              name="recipeName"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />

            <label htmlFor="ingredients">Ingredients:</label>
            <textarea
              id="ingredients"
              name="ingredients"
              value={ingredients}
              onChange={(e) => setIngredients(e.target.value)}
              required
            ></textarea>

            <label htmlFor="instructions">Instructions:</label>
            <textarea
              id="instructions"
              name="instructions"
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              required
            ></textarea>

            {/* The button is disabled while a request is in flight, so the user
                can't submit twice. */}
            <button type="submit" disabled={submitting}>
              {submitting ? 'Saving…' : 'Submit'}
            </button>
          </form>

          {/* Show an error message only when one exists. In JSX, "{condition &&
              <element>}" renders the element only if the condition is truthy. */}
          {error && <p style={{ color: 'red' }}>{error}</p>}

          {/* The list of saved recipes, loaded from the backend. */}
          <h3>Saved Recipes ({recipes.length})</h3>
          <ul>
            {/* .map turns each recipe object into a list item. React needs a
                unique "key" per item (we use the database id) to track them
                efficiently across re-renders. */}
            {recipes.map((recipe) => (
              <li key={recipe.id}>
                <strong>{recipe.name}</strong>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </>
  )
}

export default App
