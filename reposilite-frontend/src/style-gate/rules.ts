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

import {
  classTokensOf,
  colourLiterals,
  inlineStyleLiterals,
  scriptSelectorNames,
  stylesheetSelectorNames,
  styleBlocks,
  styleValues,
  utilityOf,
  type ScanRule
} from './scan'

// One rule of the style gate: its identity, the file extensions it reads, the allowlist file that holds its exceptions and
// the hint that the test prints beside each finding, plus the scan that finds its occurrences.
export interface Rule extends ScanRule {
  id: string
  number: number
  scope: string[]
  allowlist: string
  hint: string
}

// The eight rules of the dashboard style gate, in the order of the design. Each rule has a scope (the file extensions it
// reads), an allowlist file named after its id, and a hint that the test prints beside every finding.

const matchesOf = (pattern: RegExp, text: string): string[] => [...text.matchAll(pattern)].map((match) => match[0])

const inFolder = (file: string, folders: string[]): boolean => folders.some((folder) => file.startsWith(folder))

const scopedTo = (extensions: string[], exempt: (file: string) => boolean = () => false) => (file: string): boolean =>
  extensions.some((extension) => file.endsWith(extension)) && !exempt(file)

// Tailwind's default type, shape, elevation, motion, z-index and opacity names, and the palette colours. The MD3 names
// (rounded-corner-*, shadow-elevation-*, md-*) are not in the list, so they never match.
const DEFAULT_TYPE = /^text-(?:xs|sm|base|lg|xl|[2-9]xl)$/
const DEFAULT_LEADING = /^leading-(?:\d+|none|tight|snug|normal|relaxed|loose)$/
const DEFAULT_WEIGHT = /^font-(?:thin|extralight|light|normal|medium|semibold|bold|extrabold|black)$/
const DEFAULT_CORNER = /^rounded(?:-(?:t|r|b|l|s|e|tl|tr|br|bl|ss|se|ee|es))?(?:-(?:none|sm|md|lg|xl|2xl|3xl|full))?$/
const DEFAULT_SHADOW = /^shadow(?:-(?:xs|sm|md|lg|xl|2xl|inner))?$/
const DEFAULT_MOTION = /^(?:animate-(?:pulse|bounce|ping|spin)|duration-\d+|ease-(?:linear|in|out|in-out))$/
const DEFAULT_LAYER = /^-?z-\d+$/
const DEFAULT_OPACITY = /^opacity-\d+$/
const PALETTE_NEUTRAL = /^[a-z]+(?:-[a-z]+)*-(?:white|black)(?:\/[\d.]+)?$/
const PALETTE_HUE =
  /^[a-z]+(?:-[a-z]+)*-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-(?:50|100|200|300|400|500|600|700|800|900|950)(?:\/[\d.]+)?$/

const NON_MD3_DEFAULTS = [
  DEFAULT_TYPE,
  DEFAULT_LEADING,
  DEFAULT_WEIGHT,
  DEFAULT_CORNER,
  DEFAULT_SHADOW,
  DEFAULT_MOTION,
  DEFAULT_LAYER,
  DEFAULT_OPACITY,
  PALETTE_NEUTRAL,
  PALETTE_HUE
]

const isNonMd3Default = (token: string): boolean => {
  const utility = utilityOf(token)
  return NON_MD3_DEFAULTS.some((pattern) => pattern.test(utility))
}

// Plain class selectors that the design documents. They are allowed in the stylesheets of the dashboard and in scripts;
// every other class selector is a finding.
const DOCUMENTED_HOOKS = ['dark', 'vfm']
const DOCUMENTED_SHEETS = [/^src\/style\.css$/, /^src\/styles\/md3\/[^/]+\.css$/]
// The classes that Vue <Transition> adds to an element, named by the patterns of MdTransition (motion-transitions.css).
// They exist only while a transition runs, so a stylesheet has to name them. Only these exact names are documented: the
// back class of a pattern, and the scrim of the dialog pattern, which the modal of MdDialog takes as its overlay transition.
const TRANSITION_HOOK = /^motion-(?:(?:fade-through|shared-x|shared-y|menu|dialog|sheet|snackbar|expand|fade)(?:-back)?|dialog-scrim)-(?:enter|leave)-(?:active|from|to)$/

const selectorFindings = ({ file, text }: { file: string; text: string }): string[] => {
  if (file.endsWith('.vue')) {
    return stylesheetSelectorNames(styleBlocks(text)).map((name) => `.${name}`)
  }
  if (file.endsWith('.css')) {
    const documentedSheet = DOCUMENTED_SHEETS.some((pattern) => pattern.test(file))
    return stylesheetSelectorNames(text)
      .filter((name) => !(documentedSheet && (DOCUMENTED_HOOKS.includes(name) || TRANSITION_HOOK.test(name))))
      .map((name) => `.${name}`)
  }
  return scriptSelectorNames(text)
    .filter((name) => !DOCUMENTED_HOOKS.includes(name))
    .map((name) => `.${name}`)
}

export const RULES: Rule[] = [
  {
    id: 'style-block',
    number: 1,
    scope: ['.vue'],
    allowlist: 'style-block.json',
    hint: 'A component carries no style block: move the rule into a @utility or a stylesheet under src/styles/md3/.',
    appliesTo: scopedTo(['.vue']),
    match: ({ text }) => matchesOf(/<style(?=[\s>])/g, text)
  },
  {
    id: 'arbitrary-value',
    number: 2,
    scope: ['.vue', '.js', '.ts'],
    allowlist: 'arbitrary-value.json',
    hint: 'Arbitrary value: use a token, an MD3 role, or a window variant (medium:, expanded:, large:, xlarge:, max-medium:, max-expanded:) for a breakpoint.',
    appliesTo: scopedTo(['.vue', '.js', '.ts']),
    match: ({ file, text, ctx }) => classTokensOf({ file, text, ctx }).filter((token) => token.includes('['))
  },
  {
    id: 'raw-colour',
    number: 3,
    scope: ['.vue', '.js', '.ts', '.css'],
    allowlist: 'raw-colour.json',
    hint: 'Raw colour: use a role utility such as text-on-surface or bg-surface-container.',
    appliesTo: scopedTo(['.vue', '.js', '.ts', '.css'], (file) => file === 'src/styles/md3/color.css'),
    match: ({ text }) => colourLiterals(text)
  },
  {
    id: 'non-md3-default',
    number: 4,
    scope: ['.vue', '.js', '.ts'],
    allowlist: 'non-md3-default.json',
    hint: 'Tailwind default or palette colour: use md-* type classes, rounded-corner-*, shadow-elevation-*, the motion, z-index or opacity tokens, md-state-layer, or a role colour.',
    appliesTo: scopedTo(['.vue', '.js', '.ts']),
    match: ({ file, text, ctx }) => classTokensOf({ file, text, ctx }).filter(isNonMd3Default)
  },
  {
    id: 'inline-style',
    number: 5,
    scope: ['.vue'],
    allowlist: 'inline-style.json',
    hint: 'Literal inline style: use a class or a token. A value computed from data is allowlisted with a reason.',
    appliesTo: scopedTo(['.vue']),
    match: ({ text }) => styleValues(text).flatMap(inlineStyleLiterals)
  },
  {
    id: 'class-token',
    number: 6,
    scope: ['.vue', '.js', '.ts'],
    allowlist: 'class-token.json',
    hint: 'Class does not resolve: it is no Tailwind utility, @utility, @theme name or stylesheet rule. A runtime-built class is allowlisted by file with a reason.',
    appliesTo: scopedTo(['.vue', '.js', '.ts']),
    match: ({ file, text, ctx }) => classTokensOf({ file, text, ctx }).filter((token) => !ctx.resolves(token))
  },
  {
    id: 'class-selector',
    number: 7,
    scope: ['.vue', '.js', '.ts', '.css'],
    allowlist: 'class-selector.json',
    hint: 'Class selector: select with data-test or a role, or move the rule into a documented hook (.dark, .vfm).',
    appliesTo: scopedTo(['.vue', '.js', '.ts', '.css']),
    match: ({ file, text }) => selectorFindings({ file, text })
  },
  {
    id: 'inline-svg',
    number: 8,
    scope: ['.vue', '.js', '.ts'],
    allowlist: 'inline-svg.json',
    hint: 'Inline svg: use MdIcon, or a component in src/components/icons/.',
    appliesTo: scopedTo(['.vue', '.js', '.ts'], (file) => inFolder(file, ['src/components/icons/', 'src/components/md/'])),
    match: ({ text }) => matchesOf(/<svg(?=[\s>])/g, text)
  }
]
