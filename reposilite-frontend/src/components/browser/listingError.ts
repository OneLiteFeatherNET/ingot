/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Sorts a failed directory listing into the case the user can act on:
 *
 *   not-found  the server answered 404: the path does not exist
 *   forbidden  the server answered 403: the token may not read it
 *   network    no response at all: the repository was not reached
 *   unknown    anything else, including failures that are not HTTP errors
 *
 * Reads optionally, because a failure is not always an HTTP failure. The network case needs
 * an error that carries a request but no response; a plain exception with neither is a bug,
 * and it stays "unknown" rather than being reported as a connection problem.
 */
const classifyListingError = (error) => {
  const status = error?.response?.status

  if (status === 404) return 'not-found'
  if (status === 403) return 'forbidden'
  if (!error?.response && error?.request) return 'network'

  return 'unknown'
}

export {
  classifyListingError
}
