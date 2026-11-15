/*
 * Whether the repository details card is folded away. The choice belongs to this viewer, so it
 * is kept in localStorage. Storage can be blocked, full or missing, so every access is guarded:
 * a failure falls back to the screen default instead of breaking the page.
 */
export const DETAILS_COLLAPSED_KEY = 'repository-details-collapsed'

// The lg breakpoint, where the details sit beside the listing. Below it the card stacks above.
const WIDE_SCREEN_QUERY = '(min-width: 1024px)'

// The part of the window the choice reads: the media query. Tests pass a stand-in for it.
interface MediaWindow {
  matchMedia?: (query: string) => { matches: boolean }
}

// Narrow screens start with the card folded, so the listing comes first. Without matchMedia
// nothing is hidden by surprise, so the card starts open.
const defaultDetailsCollapsed = (win: MediaWindow | undefined = globalThis.window): boolean => {
  try {
    if (typeof win?.matchMedia !== 'function') {
      return false
    }
    return !win.matchMedia(WIDE_SCREEN_QUERY).matches
  } catch {
    return false
  }
}

const readDetailsCollapsed = (win: MediaWindow | undefined = globalThis.window): boolean => {
  try {
    const stored = globalThis.localStorage.getItem(DETAILS_COLLAPSED_KEY)
    if (stored === 'true' || stored === 'false') {
      return stored === 'true'
    }
  } catch {
    // Storage is blocked: the screen default applies.
  }
  return defaultDetailsCollapsed(win)
}

const writeDetailsCollapsed = (collapsed: boolean): void => {
  try {
    globalThis.localStorage.setItem(DETAILS_COLLAPSED_KEY, String(collapsed))
  } catch {
    // Storage is blocked or full: the choice lasts until the page is left.
  }
}

export {
  defaultDetailsCollapsed,
  readDetailsCollapsed,
  writeDetailsCollapsed
}
