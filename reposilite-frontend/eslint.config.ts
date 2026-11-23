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

import js from "@eslint/js"
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import type { Linter } from 'eslint'

// Every enabled rule reports as an error. The plugin presets ship some rules as warnings, and the bulk suppressions of
// ESLint record errors only, so those rules are raised here. A finding that is not in eslint-suppressions.json fails the run.
const raiseWarning = (value: Linter.RuleEntry<unknown[]> | undefined): Linter.RuleEntry<unknown[]> | undefined => {
  if (Array.isArray(value)) {
    return value[0] === 'warn' || value[0] === 1 ? ['error', ...value.slice(1)] : value
  }
  return value === 'warn' || value === 1 ? 'error' : value
}

const raiseWarnings = (config: Linter.Config): Linter.Config => {
  if (!config.rules) {
    return config
  }
  return {
    ...config,
    rules: Object.fromEntries(
      Object.entries(config.rules).map(([name, value]) => [name, raiseWarning(value)])
    ),
  }
}

const configs: Linter.Config[] = ([
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
  },
  // TypeScript, from the first .ts file on: the recommended type-aware rules apply to .ts files. A .vue file keeps its
  // JavaScript parser unless its script block says lang="ts", and then the TypeScript parser reads that block. The
  // JavaScript files keep the rules above and are not given type information.
  ...tseslint.configs.recommendedTypeChecked.map((config) => ({ ...config, files: ['**/*.ts'] })),
  {
    files: ['**/*.ts'],
    languageOptions: {
      parserOptions: {
        // Every .ts file belongs to one of the tsconfig projects, the configuration files included (tsconfig.node.json).
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  // Test doubles and fixtures are async by contract (a mocked API returns a promise); an async function without an await is
  // intended there.
  {
    files: ['**/*.test.ts', 'e2e/**/*.ts'],
    rules: {
      '@typescript-eslint/require-await': 'off',
    },
  },
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: { js: 'espree', ts: tseslint.parser, tsx: tseslint.parser, '<template>': 'espree' },
      },
    },
  },
  {
    files: ['**/*.ts', '**/*.vue'],
    plugins: { '@typescript-eslint': tseslint.plugin },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
] satisfies Linter.Config[]).map(raiseWarnings)

export default configs
