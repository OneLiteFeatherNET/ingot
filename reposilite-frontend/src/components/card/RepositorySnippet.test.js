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
