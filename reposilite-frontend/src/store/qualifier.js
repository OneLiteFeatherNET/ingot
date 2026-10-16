/*
 * Copyright (c) 2023 dzikoysk
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

import { watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { useSession } from './session'
import { qualifierOf } from '../router/paths'

const qualifier = reactive({
  watchable: 0,
  path: ''
})

const refreshQualifier = () =>
  qualifier.watchable++

const { details } = useSession()

watch(
  () => details.value,
  () => refreshQualifier()
)

export default function useQualifier() {
  const route = useRoute()

  // The path comes from the browse route. Overview and the other sections have no qualifier,
  // which reads as the root of the repository. Every view that calls this reads the same path,
  // so only a change of path reloads the listing; a second view on the same path would
  // otherwise request the same directory again.
  watch(
    () => qualifierOf(route.params),
    newQualifier => {
      if (qualifier.path === newQualifier) {
        return
      }
      qualifier.path = newQualifier
      refreshQualifier()
    },
    { immediate: true }
  )

  return {
    qualifier,
    refreshQualifier
  }
}