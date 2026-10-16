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

import { defineConfig } from "vitest/config"
import vue from "@vitejs/plugin-vue"

// Kept apart from vite.config.js on purpose: the build config carries the production
// placeholders and plugins that a unit test has no use for.
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.js"],
    // @material/material-color-utilities 0.4 imports some of its own modules without a file
    // extension. Node's loader cannot resolve those, so the package goes through Vite, which can.
    server: {
      deps: {
        inline: ["@material/material-color-utilities"],
      },
    },
  },
})
