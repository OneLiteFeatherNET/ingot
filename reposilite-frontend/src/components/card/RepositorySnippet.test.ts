import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RepositorySnippet from './RepositorySnippet.vue'
import MdCopyableCode from '../md/MdCopyableCode.vue'
import type { RepositorySnippetData } from './snippetTypes'

const data: RepositorySnippetData = {
  type: 'repository',
  repoId: 'releases-x',
  title: 'Ingot Repository',
  domain: 'http://localhost/releases'
}

const render = (lang: string) => mount(RepositorySnippet, { props: { configuration: { name: 'Maven', lang }, data } })

const codeText = (wrapper: ReturnType<typeof render>) => wrapper.find('pre').element.textContent

describe('RepositorySnippet Maven text', () => {
  it('copies the snippet exactly as written: one element per line, no blank line between them', () => {
    const text = codeText(render('xml'))

    expect(text).toBe(
      '<repository>\n' +
      '  <id>releases-x</id>\n' +
      '  <name>Ingot Repository</name>\n' +
      '  <url>http://localhost/releases</url>\n' +
      '</repository>'
    )
  })
})

describe('RepositorySnippet url', () => {
  it.each(['xml', 'groovy', 'kotlin', 'scala'])('keeps the url of the %s snippet whole on one line', (lang) => {
    const text = codeText(render(lang))

    expect(text, 'the url is in the text as written').toContain('http://localhost/releases')
    expect(text, 'no zero-width space is copied with the url').not.toMatch(/\u200b/)
  })

  it('writes the Gradle Kotlin url as one call on one line', () => {
    expect(codeText(render('kotlin'))).toContain('url = uri("http://localhost/releases")')
  })
})

describe('RepositorySnippet block', () => {
  it.each(['xml', 'groovy', 'kotlin', 'scala'])('draws the %s snippet as one copyable code block under the snippet anchor', (lang) => {
    const wrapper = render(lang)

    expect(wrapper.attributes('data-ingot-component'), 'the anchor stays on the root').toBe('repository-snippet')
    expect(wrapper.findAllComponents(MdCopyableCode), 'one copyable code block per snippet').toHaveLength(1)
  })
})

describe('RepositorySnippet Gradle and SBT text', () => {
  it('indents the Gradle Groovy block by four spaces and keeps its url whole', () => {
    const text = codeText(render('groovy'))

    expect(text).toBe(
      'maven {\n' +
      '    name "releasesX"\n' +
      '    url "http://localhost/releases"\n' +
      '}'
    )
  })

  it('writes the SBT resolver as one line, the way it is pasted into build.sbt', () => {
    const text = codeText(render('scala'))

    expect(text).toBe('resolvers += "releases-x" at "http://localhost/releases"')
  })
})
