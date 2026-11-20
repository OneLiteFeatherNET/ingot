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

import AxeBuilder from '@axe-core/playwright'

// WCAG 2.0 and 2.1, level A and AA. Those are the rules the gate is written against.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']
const GATING_IMPACTS = ['serious', 'critical']

const summarize = (violation) => ({
  id: violation.id,
  impact: violation.impact,
  help: violation.help,
  nodes: violation.nodes.map(node => node.target.join(' ')),
})

/*
 * Runs axe on the page in its current state. Returns the gating findings (serious and critical) and
 * every other finding, so the caller can attach the lower impacts to the report without failing on them.
 */
const analyzeAccessibility = async (page) => {
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  const findings = results.violations.map(summarize)
  return {
    gating: findings.filter(finding => GATING_IMPACTS.includes(finding.impact)),
    reported: findings.filter(finding => !GATING_IMPACTS.includes(finding.impact)),
  }
}

export { analyzeAccessibility }
