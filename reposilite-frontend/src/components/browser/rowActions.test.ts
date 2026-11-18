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

import { describe, it, expect } from 'vitest'
import { actionSlotClass, actionCountOf, rowActionsOf, widestActionCount, type RowActionOptions } from './rowActions'

const SUFFIXES = ['-javadoc.jar', '-groovydoc.jar']
const qualifier = { path: 'releases/net/example' }
const jar = { name: 'example-1.0.jar', type: 'FILE', contentLength: 2048 }
const xml = { name: 'maven-metadata.xml', type: 'FILE', contentType: 'application/xml', contentLength: 512 }
const javadoc = { name: 'example-1.0-javadoc.jar', type: 'FILE', contentLength: 4096 }
const widest = { name: 'example-1.0-javadoc.jar', type: 'FILE', contentType: 'application/xml', contentLength: 1024 }

const countOf = (file: RowActionOptions['file'], canDelete = false) =>
  actionCountOf({ file, qualifier, canDelete, javadocSuffixes: SUFFIXES })

describe('actionSlotClass', () => {
  it('reserves no width for a listing without any action', () => {
    expect(actionSlotClass(0), 'zero actions take no room').toBe('w-0')
  })

  it('reserves the width of one button from the medium window up for one action at most', () => {
    expect(actionSlotClass(1), 'one 40px button from 600 px, the own buttons of the row below').toBe('w-auto medium:w-10')
  })

  it('reserves the width of two buttons and a gap from the medium window up for two actions', () => {
    expect(actionSlotClass(2), 'two 40px buttons and one 4px gap from 600 px').toBe('w-auto medium:w-21')
  })

  it('reserves the width of three buttons and two gaps from the medium window up for three actions', () => {
    expect(actionSlotClass(3), 'three 40px buttons and two 4px gaps from 600 px').toBe('w-auto medium:w-32')
  })
})

describe('actionCountOf', () => {
  it('counts no action for a plain jar a signed-out viewer browses', () => {
    expect(countOf(jar), 'a jar has no view and no javadoc action').toBe(0)
  })

  it('counts the view action of a readable file', () => {
    expect(countOf(xml), 'an xml file offers view').toBe(1)
  })

  it('counts the javadoc action of a javadoc jar', () => {
    expect(countOf(javadoc), 'a javadoc jar offers the javadoc action').toBe(1)
  })

  it('counts the delete action for a signed-in viewer with write access', () => {
    expect(countOf(jar, true), 'delete is the only action of a jar').toBe(1)
  })

  it('counts all three actions of the widest row', () => {
    expect(countOf(widest, true), 'view, javadoc and delete').toBe(3)
  })
})

describe('rowActionsOf javadoc action', () => {
  const demo = { name: 'demo-1.0-javadoc.jar', type: 'FILE', contentLength: 4096 }

  it('is not offered when the server sends no javadoc suffixes', () => {
    expect(rowActionsOf({ file: demo, qualifier, canDelete: false, javadocSuffixes: [] }).javadoc, 'without the viewer there is no javadoc route').toBe(false)
  })

  it('is offered when the server sends the javadoc suffixes', () => {
    expect(rowActionsOf({ file: demo, qualifier, canDelete: false, javadocSuffixes: SUFFIXES }).javadoc, 'the viewer serves this jar').toBe(true)
  })
})

describe('widestActionCount', () => {
  it('is zero for a listing where no row has an action', () => {
    expect(widestActionCount([jar, { ...jar, name: 'other-1.0.jar' }], { qualifier, canDelete: false, javadocSuffixes: SUFFIXES }), 'nothing to reserve').toBe(0)
  })

  it('is the largest action count of any row in the listing', () => {
    expect(widestActionCount([jar, xml, javadoc], { qualifier, canDelete: false, javadocSuffixes: SUFFIXES }), 'the view or javadoc row decides').toBe(1)
  })

  it('follows the widest row when write access adds delete to every row', () => {
    expect(widestActionCount([jar, widest], { qualifier, canDelete: true, javadocSuffixes: SUFFIXES }), 'the widest row has three actions').toBe(3)
  })

  it('is zero for an empty listing', () => {
    expect(widestActionCount([], { qualifier, canDelete: false, javadocSuffixes: SUFFIXES }), 'nothing is listed').toBe(0)
  })
})
