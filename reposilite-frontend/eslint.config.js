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

import js from "@eslint/js";
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

// Warn phase: every enabled rule reports as a warning, so findings are printed without failing the run.
// Off rules stay off, so the rule set is unchanged. Remove this helper to restore the configured severities.
const isOff = (severity) => severity === 0 || severity === 'off'

const toWarn = (value) => {
  if (Array.isArray(value)) {
    return isOff(value[0]) ? value : ['warn', ...value.slice(1)]
  }
  return isOff(value) ? value : 'warn'
}

const warnPhase = (config) => {
  if (!config.rules) {
    return config
  }
  return {
    ...config,
    rules: Object.fromEntries(
      Object.entries(config.rules).map(([name, value]) => [name, toWarn(value)])
    ),
  }
}

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
      },
      parserOptions: {
        "ecmaVersion": 2020,
        "ecmaFeatures": {
          "jsx": true
        }
      },
    },
    rules: {
      'vue/no-unused-vars': 'error',
      'vue/multi-word-component-names': 'error',
      'vue/jsx-uses-vars': 'error',
      'semi': ['error', 'never'],
    }
  }
].map(warnPhase)
