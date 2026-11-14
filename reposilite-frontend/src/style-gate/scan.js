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

// Pure text extraction for the style gate. Nothing here reads the disk, the clock or the network. The rules in rules.js
// compose these helpers, and the tests feed them strings.

// The anchor attributes of the dashboard are read by the anchor checks and by plugins. They are never a style.
export const stripExempt = (text) => text.replace(/data-ingot-[\w-]+\s*=\s*(?:"[^"]*"|'[^']*')/g, ' ')

const withoutComments = (text) => text.replace(/\/\*[\s\S]*?\*\//g, '')

// The string literals of one line of code, without their quotes. Template literals count as one literal.
export const stringLiterals = (text) => [...text.matchAll(/'([^']*)'|"([^"]*)"|`([^`]*)`/g)].map((match) => match[1] ?? match[2] ?? match[3])

// A class token is lower case outside brackets and parentheses, so prose such as "Use a colour" is not a class list.
// Brackets and parentheses may hold anything, because arbitrary values and the v4 shorthand are written inside them.
export const isClassToken = (token) => {
  const outside = token.replace(/\[[^\]]*\]|\([^)]*\)/g, '')

  return /^[\w!\-:/[\]().%#&>~*+,=@{}$]+$/.test(token) && /^[a-z0-9!\-:/.%#&>~*+,=@{}$]*$/.test(outside)
}

const splitClasses = (value) => value.split(/\s+/).filter((token) => token.length > 0)

// The class tokens of a component: a static class attribute is a list of names, and a bound one carries the names as
// string literals inside its expression.
export const vueClassTokens = (text) => {
  const staticLists = [...text.matchAll(/\sclass=(["'])(.*?)\1/gs)].map((match) => match[2])
  const boundLists = [...text.matchAll(/\s(?::|v-bind:)class=(["'])(.*?)\1/gs)].flatMap((match) => stringLiterals(match[2]))

  return [...staticLists, ...boundLists].flatMap(splitClasses).filter(isClassToken)
}

// Selector class names in a string: `.name` at the start of a selector or after a combinator, a comma or an opening
// bracket. A decimal, a file extension and a name inside a word are not selectors.
const SELECTOR_CLASS = /(?:^|[\s+~,([:])\.([a-zA-Z_][\w-]*)(?=[\s,+~:[)\]]|$)/g
const selectorNames = (text) => [...text.matchAll(SELECTOR_CLASS)].map((match) => match[1])

// A string that is one class name is a selector only when the name has a hyphen, as the dashboard names its hooks
// (settings-save-bar). A bare `.md5` or `.okta` is a value such as a file extension or a provider id, not a query.
const SINGLE_HYPHENATED_CLASS = /^\.[a-zA-Z_]\w*-[\w-]*$/
const SELECTOR_CONTEXT = /[\s+~,([:]/

// The code of a script, one line at a time. Lines that only hold a comment are skipped, so that prose in a comment is
// not read as a class list.
const scriptLines = (text) => text.split('\n').filter((line) => !/^\s*(\/\/|\/\*|\*)/.test(line))

// The class lists of a script: string literals whose words are class names and at least one of which the stylesheet
// defines. A literal that names no defined class is not a class list, so an ordinary string is never read as one.
export const scriptClassLists = (text, resolves) =>
  scriptLines(text)
    .flatMap(stringLiterals)
    .map(splitClasses)
    .filter((tokens) => tokens.length > 0 && tokens.every(isClassToken) && tokens.some(resolves))
    .flat()

// The class names that a script selects with a query, found in its string literals.
export const scriptSelectorNames = (text) =>
  scriptLines(text)
    .flatMap(stringLiterals)
    .flatMap((literal) => {
      const single = literal.trim()
      if (SINGLE_HYPHENATED_CLASS.test(single)) return [single.slice(1)]
      return SELECTOR_CONTEXT.test(literal) ? selectorNames(literal) : []
    })

// The class names that a stylesheet selects, in its selector text. Comments are removed first, and a name inside a
// word, a path or a decimal is not a selector.
export const stylesheetSelectorNames = (text) =>
  [...withoutComments(text).matchAll(/(?<![\w.\-/"'\\])\.([a-zA-Z_][\w-]*)/g)].map((match) => match[1])

// The text of every style element of a component.
export const styleBlocks = (text) => [...text.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((match) => match[1]).join('\n')

// Every match of a pattern, as its text.
const allMatches = (pattern, text) => [...text.matchAll(pattern)].map((match) => match[0])

// Colour literals: hex with 3, 4, 6 or 8 digits, rgb, rgba, hsl and hsla calls with one level of nesting, and a
// color-mix call with a numeric percentage.
const HEX_COLOUR = /(?<![\w&])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![\w-])/g
const COLOUR_FUNCTION = /\b(?:rgba?|hsla?)\((?:[^()]|\([^()]*\))*\)/g
const COLOUR_MIX = /color-mix\((?:[^()]|\([^()]*\))*\)/g
const NUMERIC_PERCENTAGE = /\d(?:\.\d+)?%/

export const colourLiterals = (text) => [
  ...allMatches(HEX_COLOUR, text),
  ...allMatches(COLOUR_FUNCTION, text),
  ...allMatches(COLOUR_MIX, text).filter((literal) => NUMERIC_PERCENTAGE.test(literal))
]

// The literal values of a style attribute or binding: a length with a unit, a colour, a z-index and a custom property
// that is declared. A template literal that ends in a unit is a literal too, because `${percent}%` is still a size
// written in the template.
const LITERAL_LENGTH = /(?<![\w$.-])-?\d+(?:\.\d+)?(?:px|%|rem|em|vh|vw|vmin|vmax|ch|pt)(?![\w-])/g
const TEMPLATE_LENGTH = /`[^`]*\}(?:%|px|rem|em|vh|vw)[^`]*`/g
const Z_INDEX = /z-index\s*:\s*-?\d+/g
const CUSTOM_DECLARATION = /['"]?--[\w-]+['"]?\s*:(?!\/)/g

export const inlineStyleLiterals = (value) => [
  ...allMatches(LITERAL_LENGTH, value),
  ...allMatches(TEMPLATE_LENGTH, value),
  ...allMatches(Z_INDEX, value),
  ...allMatches(CUSTOM_DECLARATION, value),
  ...colourLiterals(value)
]

// The values of the style attributes and bindings of a component, static or bound.
export const styleValues = (text) => [
  ...[...text.matchAll(/\sstyle=(["'])(.*?)\1/gs)].map((match) => match[2]),
  ...[...text.matchAll(/\s(?::|v-bind:)style=(["'])(.*?)\1/gs)].map((match) => match[2])
]

// Splits a class token into its variants and its utility. The colons that separate variants are the top level ones,
// so the colons inside an arbitrary value stay with the value. A leading `!` or `-` belongs to the utility.
export const utilityOf = (token) => {
  const parts = []
  let depth = 0
  let start = 0

  for (let index = 0; index < token.length; index++) {
    const character = token[index]
    if (character === '[' || character === '(') depth++
    if (character === ']' || character === ')') depth--
    if (character === ':' && depth === 0) {
      parts.push(token.slice(start, index))
      start = index + 1
    }
  }
  parts.push(token.slice(start))

  return parts[parts.length - 1].replace(/^[!-]+/, '')
}

// Runs one rule over one file. A file outside the rule's scope yields nothing. The matches are the offending texts,
// one entry per occurrence.
export function scanFile(rule, file, text, ctx) {
  if (!rule.appliesTo(file)) return []

  return rule.match({ file, text: stripExempt(text), ctx })
}

// Runs one rule over every source and returns one { file, match } entry per occurrence.
export function scanSources(rule, sources, ctx) {
  return sources.flatMap(({ file, text }) => scanFile(rule, file, text, ctx).map((match) => ({ file, match })))
}

// The class tokens of a file, from its class attributes, from the class lists of its scripts and from the class names
// that its scripts select. A token is kept as written, so a runtime-built name shows up with its template syntax.
export const classTokensOf = ({ file, text, ctx }) => {
  if (file.endsWith('.vue')) return vueClassTokens(text)
  if (file.endsWith('.js')) {
    return [...scriptClassLists(text, ctx.resolves), ...scriptSelectorNames(text)]
  }
  return []
}
