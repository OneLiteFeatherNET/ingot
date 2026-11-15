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
 * The property keys whose values are masked in the settings. The match is made on the last
 * segment of the path, case-insensitively: `secretKey`, `Proxy_PASSWORD`, `accessKey`,
 * `access-key`, `endpoint`. Add a key here and every string control in the settings follows.
 */
export const SENSITIVE_KEY_PATTERN = /secret|password|access[-_]?key|endpoint/i

/* The key a control is bound to: the last segment of its path, such as `secretKey` in `storage.secretKey`. */
export const keyOf = (path) => String(path ?? '').split('.').pop()

/* A value is sensitive when its key matches the pattern, or its schema declares the password format. */
export const isSensitive = (path, schema) => schema?.format === 'password' || SENSITIVE_KEY_PATTERN.test(keyOf(path))
