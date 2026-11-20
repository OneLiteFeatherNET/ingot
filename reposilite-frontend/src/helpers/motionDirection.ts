/**
 * The direction of a sibling change, read from the positions of the two siblings in their list. A later target moves the
 * page forward (1), an earlier one backward (-1), and the same position does not move (0). A pure function, so a test
 * needs no clock and no DOM.
 */
export type MotionDirection = -1 | 0 | 1

export const motionDirection = (from: number, to: number): MotionDirection => Math.sign(to - from) as MotionDirection

// The entries of a side navigation: each group holds the links of its entries, in the order the tree draws them.
export interface TreeGroup {
  links: readonly { key: string | undefined }[]
}

/**
 * The position of an entry in the flat list of all groups, read in the order the tree draws them, or -1 when the key is
 * undefined or no entry of the tree has it.
 */
export const entryPosition = (groups: readonly TreeGroup[], key: string | undefined): number => {
  if (key === undefined) {
    return -1
  }
  return groups.flatMap(group => group.links).findIndex(link => link.key === key)
}

/**
 * The direction of a change between two entries of the tree. An entry that is missing on either side, such as the index
 * before a page is open, has no direction, so the page is swapped without a sideways move.
 */
export const entryDirection = (groups: readonly TreeGroup[], from: string | undefined, to: string | undefined): MotionDirection => {
  const fromPosition = entryPosition(groups, from)
  const toPosition = entryPosition(groups, to)
  if (fromPosition < 0 || toPosition < 0) {
    return 0
  }
  return motionDirection(fromPosition, toPosition)
}
