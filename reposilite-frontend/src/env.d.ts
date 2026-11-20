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

/// <reference types="vite/client" />

// Declarations only: nothing here reaches the bundle. The {{REPOSILITE.*}} placeholders are string literals that the
// server substitutes, not identifiers, so they need no declaration. Vitest runs without globals, so tests import
// their helpers.

// Development only: the port of the fake backend, defined by vite.config.ts. Absent under the test runner.
declare const __INGOT_FAKE_API_PORT__: string | undefined
