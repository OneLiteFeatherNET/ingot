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

/**
 * The font sizes of the MD3 type scale in pixels, for code that cannot take a type scale class, such as the
 * options of a chart drawn on a canvas. Markup uses the `md-<role>` classes instead. The numbers are the
 * `--md-sys-typescale-<role>-size` tokens of `src/styles/md3/typography.css`, and `typescale.test.ts` checks
 * that the two agree.
 */
export type TypescaleRole =
  | 'displayLarge' | 'displayMedium' | 'displaySmall'
  | 'headlineLarge' | 'headlineMedium' | 'headlineSmall'
  | 'titleLarge' | 'titleMedium' | 'titleSmall'
  | 'labelLarge' | 'labelMedium' | 'labelSmall'
  | 'bodyLarge' | 'bodyMedium' | 'bodySmall'

export const typescale: Readonly<Record<TypescaleRole, { readonly size: number }>> = {
  displayLarge: { size: 57 },
  displayMedium: { size: 45 },
  displaySmall: { size: 36 },
  headlineLarge: { size: 32 },
  headlineMedium: { size: 28 },
  headlineSmall: { size: 24 },
  titleLarge: { size: 22 },
  titleMedium: { size: 16 },
  titleSmall: { size: 14 },
  labelLarge: { size: 14 },
  labelMedium: { size: 12 },
  labelSmall: { size: 11 },
  bodyLarge: { size: 16 },
  bodyMedium: { size: 14 },
  bodySmall: { size: 12 }
}
