import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  DETAILS_COLLAPSED_KEY,
  defaultDetailsCollapsed,
  readDetailsCollapsed,
  writeDetailsCollapsed
} from './detailsCollapsed'

// A window whose media query answers for the screen width the test chooses.
const screen = ({ wide }) => ({ matchMedia: () => ({ matches: wide }) })

describe('defaultDetailsCollapsed', () => {
  it('keeps the details open on a wide screen', () => {
    expect(defaultDetailsCollapsed(screen({ wide: true })), 'a wide screen starts with the card open').toBe(false)
  })

  it('folds the details away on a narrow screen', () => {
    expect(defaultDetailsCollapsed(screen({ wide: false })), 'a narrow screen starts with the card folded').toBe(true)
  })

  it('keeps the details open when the browser cannot answer the media query', () => {
    expect(defaultDetailsCollapsed({}), 'without matchMedia nothing is hidden by surprise').toBe(false)
  })
})

describe('readDetailsCollapsed and writeDetailsCollapsed', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('uses the screen default until the viewer has chosen', () => {
    expect(readDetailsCollapsed(screen({ wide: false })), 'nothing stored, narrow screen').toBe(true)
  })

  it('remembers a collapsed choice for the next visit', () => {
    writeDetailsCollapsed(true)

    expect(readDetailsCollapsed(screen({ wide: true })), 'the stored choice wins over the screen default').toBe(true)
  })

  it('remembers an expanded choice on a narrow screen as well', () => {
    writeDetailsCollapsed(false)

    expect(readDetailsCollapsed(screen({ wide: false })), 'an open card stays open after a reload').toBe(false)
  })

  it('stores the choice under its own key', () => {
    writeDetailsCollapsed(true)

    expect(localStorage.getItem(DETAILS_COLLAPSED_KEY)).toBe('true')
  })

  it('ignores a stored value it does not recognise', () => {
    localStorage.setItem(DETAILS_COLLAPSED_KEY, 'maybe')

    expect(readDetailsCollapsed(screen({ wide: true })), 'a garbage value falls back to the default').toBe(false)
  })

  it('falls back to the screen default when storage cannot be read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })

    expect(() => readDetailsCollapsed(screen({ wide: true })), 'a blocked store must not throw').not.toThrow()
    expect(readDetailsCollapsed(screen({ wide: true }))).toBe(false)
  })

  it('does not throw when storage refuses the write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('full', 'QuotaExceededError')
    })

    expect(() => writeDetailsCollapsed(true), 'a full or blocked store must not break the toggle').not.toThrow()
  })
})
