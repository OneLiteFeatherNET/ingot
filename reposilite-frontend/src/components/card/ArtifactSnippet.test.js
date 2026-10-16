import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ArtifactSnippet from './ArtifactSnippet.vue'

const data = {
  type: 'artifact',
  groupId: 'net.onelitefeather',
  artifactId: 'mycelium-bom',
  version: '1.8.9'
}

const render = (lang) => mount(ArtifactSnippet, { props: { configuration: { name: 'Maven', lang }, data } })

describe('ArtifactSnippet', () => {
  it('writes the Maven dependency over several lines with two spaces of indentation', () => {
    const text = render('xml').find('pre').element.textContent

    expect(text).toBe(
      '<dependency>\n' +
      '  <groupId>net.onelitefeather</groupId>\n' +
      '  <artifactId>mycelium-bom</artifactId>\n' +
      '  <version>1.8.9</version>\n' +
      '</dependency>\n'
    )
  })

  it('keeps the Gradle Groovy coordinate on one line', () => {
    const text = render('groovy').find('pre').element.textContent

    expect(text).toBe('implementation "net.onelitefeather:mycelium-bom:1.8.9"\n')
  })

  it('keeps the Gradle Kotlin coordinate on one line', () => {
    const text = render('kotlin').find('pre').element.textContent

    expect(text).toBe('implementation("net.onelitefeather:mycelium-bom:1.8.9")\n')
  })
})
