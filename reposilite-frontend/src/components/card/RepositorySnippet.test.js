import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RepositorySnippet from './RepositorySnippet.vue'

const data = {
  type: 'repository',
  repoId: 'releases-x',
  title: 'Ingot Repository',
  domain: 'http://localhost/releases'
}

const render = (lang) => mount(RepositorySnippet, { props: { configuration: { name: 'Maven', lang }, data } })

describe('RepositorySnippet Maven text', () => {
  it('copies the snippet exactly as written: one element per line, no blank line between them', () => {
    const text = render('xml').find('pre').element.textContent

    expect(text).toBe(
      '<repository>\n' +
      '  <id>releases-x</id>\n' +
      '  <name>Ingot Repository</name>\n' +
      '  <url>http://localhost/releases</url>\n' +
      '</repository>\n'
    )
  })

  it('keeps the url on one line, without break opportunities inside it', () => {
    const pre = render('xml').find('pre').element

    expect(pre.querySelectorAll('wbr'), 'the url is not split into pieces').toHaveLength(0)
    expect(pre.textContent).toContain('  <url>http://localhost/releases</url>\n')
  })
})

describe('RepositorySnippet url wrapping', () => {
  it.each(['xml', 'groovy', 'kotlin', 'scala'])('marks the url of the %s snippet as the part that may wrap', (lang) => {
    const url = render(lang).find('pre').element.querySelector('.snippet-url')

    expect(url, 'the url is wrapped in its own element').not.toBeNull()
    expect(url.textContent, 'the wrapped element holds the url and nothing else').toBe('http://localhost/releases')
    expect(url.classList, 'the url may break at any character').toContain('wrap-anywhere')
    expect(url.classList, 'the url is not held on one line').toContain('whitespace-normal')
  })

  it('wraps only the url, so the rest of the snippet keeps its line structure', () => {
    const pre = render('kotlin').find('pre').element

    expect(pre.querySelectorAll('.snippet-url'), 'exactly one wrapped part for one url').toHaveLength(1)
    expect(pre.textContent, 'the wrapper adds no character to the copied text').toContain('url = uri("http://localhost/releases")')
  })

  it('adds no zero-width space and no line break to the copied text', () => {
    const text = render('xml').find('pre').element.textContent

    expect(text, 'no zero-width space is copied with the url').not.toMatch(/\u200b/)
    expect(text).toBe(
      '<repository>\n' +
      '  <id>releases-x</id>\n' +
      '  <name>Ingot Repository</name>\n' +
      '  <url>http://localhost/releases</url>\n' +
      '</repository>\n'
    )
  })
})

describe('RepositorySnippet block wrapping', () => {
  it.each(['xml', 'groovy', 'kotlin', 'scala'])('lets the %s block wrap long lines instead of scrolling sideways', (lang) => {
    const pre = render(lang).find('pre').element

    expect(pre.classList, 'a long repository id on its own line may break at any character').toContain('wrap-anywhere')
    expect(pre.classList, 'the block keeps its indentation while it wraps').toContain('whitespace-pre-wrap')
  })
})

describe('RepositorySnippet Gradle and SBT text', () => {
  it('indents the Gradle Groovy block by four spaces and keeps its url whole', () => {
    const text = render('groovy').find('pre').element.textContent

    expect(text).toBe(
      'maven {\n' +
      '    name "releasesX"\n' +
      '    url "http://localhost/releases"\n' +
      '}\n'
    )
  })

  it('writes the SBT resolver as one line, the way it is pasted into build.sbt', () => {
    const text = render('scala').find('pre').element.textContent

    expect(text).toBe('resolvers += "releases-x" at "http://localhost/releases"\n')
  })
})
