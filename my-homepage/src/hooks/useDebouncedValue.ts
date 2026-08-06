import { useEffect, useState } from 'react'

/**
 * A "debounce" delays reacting to a rapidly-changing value until it stops changing.
 *
 * The problem it solves here: typing "carrot" into the ingredient search fires six
 * renders, one per keystroke. Without debouncing that means six HTTP requests, five
 * of which are for prefixes the user has already moved past. The last one is the only
 * answer anybody wanted.
 *
 * With a 250 ms debounce, the requests only go out once typing pauses. A fast typist
 * produces exactly one request for the whole word.
 *
 * The mechanism is entirely in the cleanup function, and it's worth understanding
 * rather than copying. React runs an effect's cleanup BEFORE running the effect
 * again. So on every keystroke: cancel the timer set by the previous keystroke, then
 * start a fresh one. A timer only ever survives to fire if no new keystroke arrived
 * within its delay — which is precisely the definition of "the user stopped typing".
 *
 * A generic <T> because there is nothing string-specific about the idea. This same
 * hook debounces a number, an object, a filter state.
 */
export function useDebouncedValue<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)

    // THE CLEANUP. Returning a function from useEffect tells React "run this before
    // the next effect, and once more when the component unmounts". Clearing the timer
    // on unmount matters too: without it, a timer belonging to a component that no
    // longer exists still fires and calls setState on nothing.
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
