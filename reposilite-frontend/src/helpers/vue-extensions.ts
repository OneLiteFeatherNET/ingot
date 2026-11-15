const property = <T>(type: T, required: boolean): { type: T; required: boolean } =>
  ({ type, required })

export {
  property
}
