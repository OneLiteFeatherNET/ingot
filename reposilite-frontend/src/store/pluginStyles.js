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
 * Keeps one stylesheet link per plugin that the manifest lists, at the end of the document head. The server serves the
 * sheet from a path that is named by its content, so a link whose path does not match the pattern is not added, and a
 * link whose sheet fails to load removes itself. A plugin can ship CSS only, never script: nothing here runs plugin code.
 */

const ATTRIBUTE = 'data-ingot-plugin-style'

// The path of a stylesheet the server serves: a plugin name and the first 16 hex characters of the sheet's digest.
const STYLE_PATH = /^\/api\/frontend\/plugin-styles\/([a-z0-9-]{1,32})\/[0-9a-f]{16}\.css$/

// The links this module added, by plugin. The map is the record, so the document is not queried for them.
const linksByPlugin = new Map()

/** The stylesheet entries of a manifest whose path matches the served pattern, keyed by the plugin the path names. */
const wantedStyles = (stylesheets) => {
  const wanted = new Map()
  if (!Array.isArray(stylesheets)) return wanted
  for (const entry of stylesheets) {
    const match = typeof entry?.path === 'string' ? STYLE_PATH.exec(entry.path) : null
    if (match) wanted.set(match[1], entry.path)
  }
  return wanted
}

/**
 * Makes the plugin links of the document match [stylesheets], the list of the manifest. A link of a plugin that is no
 * longer listed, or whose path changed, is removed. A listed path without a link gets one, appended to the head.
 */
const applyPluginStyles = (stylesheets) => {
  const wanted = wantedStyles(stylesheets)

  for (const [plugin, { path, link }] of linksByPlugin) {
    if (wanted.get(plugin) !== path) {
      link.remove()
      linksByPlugin.delete(plugin)
    }
  }

  for (const [plugin, path] of wanted) {
    if (linksByPlugin.has(plugin)) continue
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = path
    link.setAttribute(ATTRIBUTE, plugin)
    // A sheet that does not load is removed, so the page keeps its own styles and the next manifest may add it again.
    link.addEventListener('error', () => {
      link.remove()
      if (linksByPlugin.get(plugin)?.link === link) linksByPlugin.delete(plugin)
    })
    document.head.append(link)
    linksByPlugin.set(plugin, { path, link })
  }
}

export { applyPluginStyles }
