import type { StepDraft } from './rowDrafts'
import { blankStep } from './rowDrafts'

/**
 * The dynamic list of instruction steps.
 *
 * Simpler than the ingredient rows because a step is just text — which is exactly
 * the distinction the backend encodes by storing steps as an @ElementCollection of
 * Strings while ingredient lines are full entities. The shape of the data and the
 * shape of the UI end up mirroring each other, which is usually a sign the model is
 * right.
 */

type Props = {
  steps: StepDraft[]
  onChange: (steps: StepDraft[]) => void
}

export function StepRows({ steps, onChange }: Props) {
  function updateStep(key: string, text: string) {
    onChange(steps.map((step) => (step.key === key ? { ...step, text } : step)))
  }

  function addStep() {
    onChange([...steps, blankStep()])
  }

  function removeStep(key: string) {
    onChange(steps.filter((step) => step.key !== key))
  }

  /**
   * Moves a step up or down by one position.
   *
   * The implementation is a copy plus two swapped entries. Notice again that there is
   * NO "position" field being maintained anywhere — a step's position IS its index in
   * this array, and the server assigns the stored position from that index at submit
   * time. Reordering is therefore just moving array elements, with no bookkeeping to
   * get out of step. Had position been a field on each row, this function would also
   * have to renumber every affected row, and every bug in that renumbering would show
   * up as steps in the wrong order after a save.
   */
  function move(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= steps.length) return
    const next = [...steps]
    ;[next[index], next[target]] = [next[target], next[index]]
    onChange(next)
  }

  return (
    <fieldset className="rows-fieldset">
      <legend>Instructions</legend>

      {/* An <ol> rather than a <ul>: these are ordered steps, and the numbering is
          part of the meaning rather than decoration. It also gives the step numbers
          for free, and they renumber themselves when a row is removed. */}
      <ol className="rows">
        {steps.map((step, index) => (
          <li className="row" key={step.key}>
            <div className="row-main">
              {/* A textarea rather than an input, because instructions run long and a
                  single-line input hides everything past its width. */}
              <textarea
                className="row-step"
                rows={2}
                placeholder={`Step ${index + 1}`}
                aria-label={`Step ${index + 1}`}
                value={step.text}
                onChange={(event) => updateStep(step.key, event.target.value)}
              />

              <div className="row-actions">
                <button
                  type="button"
                  className="btn-icon"
                  onClick={() => move(index, -1)}
                  disabled={index === 0}
                  aria-label={`Move step ${index + 1} up`}
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="btn-icon"
                  onClick={() => move(index, 1)}
                  disabled={index === steps.length - 1}
                  aria-label={`Move step ${index + 1} down`}
                >
                  ↓
                </button>
                <button
                  type="button"
                  className="btn-icon"
                  onClick={() => removeStep(step.key)}
                  // As with ingredients: the server requires at least one step, so
                  // the form refuses to let you reach a state it would reject.
                  disabled={steps.length === 1}
                  aria-label={`Remove step ${index + 1}`}
                >
                  ×
                </button>
              </div>
            </div>
          </li>
        ))}
      </ol>

      <button type="button" className="btn-ghost" onClick={addStep}>
        + Add step
      </button>
    </fieldset>
  )
}
