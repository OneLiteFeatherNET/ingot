/*
 * Copyright (c) 2023 dzikoysk
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

import { defineConfig } from "vite"
import vue from "@vitejs/plugin-vue"
import vueJsx from "@vitejs/plugin-vue-jsx"
import tailwindcss from "@tailwindcss/vite"
import { visualizer } from "rollup-plugin-visualizer"

// https://vitejs.dev/config/
export default defineConfig({
  server: {
    port: 8888,
  },
  plugins: [
    vue(),
    vueJsx(),
    tailwindcss(),
    visualizer(),
  ],
  // Served by the server itself, the placeholder is substituted on the way out and the
  // asset URLs come out right. Served by anything else nothing substitutes it, and since
  // the URLs sit in script tags they have to resolve before any of our code runs, so a
  // detached build has to bake the real prefix in. INGOT_BASE_PATH is how the dashboard
  // image does that.
  base:
    process.env.INGOT_BASE_PATH ||
    (process.env.NODE_ENV === "production" ? "{{REPOSILITE.VITE_BASE_PATH}}" : "/"),
  build: {
    minify: true,
    emptyOutDir: true,
    outDir: "build/frontend/reposilite-frontend",
    chunkSizeWarningLimit: 768,
  },
  css: {
    preprocessorOptions: {
      css: {
        charset: false,
      },
    },
    postcss: {
      plugins: [
        {
          postcssPlugin: "internal:charset-removal",
          AtRule: {
            charset: (atRule) => {
              if (atRule.name === "charset") {
                atRule.remove()
              }
            },
          },
        },
      ],
    },
  },
})
