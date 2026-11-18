/**
 * The direction of a sibling change, read from the positions of the two siblings in their list. A later target moves the
 * page forward (1), an earlier one backward (-1), and the same position does not move (0). A pure function, so a test
 * needs no clock and no DOM.
 */
export type MotionDirection = -1 | 0 | 1

export const motionDirection = (from: number, to: number): MotionDirection => Math.sign(to - from) as MotionDirection
