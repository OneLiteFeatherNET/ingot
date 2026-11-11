/*
 * The settings domains of the Administration tree, for the pages that draw the tree without the settings store (Access,
 * Audit, Console, Maintenance, Analytics and the support pages). They list the same domains with the same titles as the
 * Settings page, so every Administration page shows one tree.
 *
 * The list is requested only while the page draws the tree (`active`) and only to a visitor with the settings node. A
 * visitor without the node gets an empty list and causes no request that would answer 403.
 */

import { shallowRef, watch } from 'vue'
import { useSession } from '../../store/session'
import { ADMIN_NODES } from '../../store/nodeAccess'
import { settingsDomainNames, settingsDomainTitles } from '../../store/settingsDomains'
import { settingsTabDomains } from './accessDomains'

const useSettingsDomains = (active = () => true) => {
  const { can, client } = useSession()
  const domains = shallowRef([])
  const titles = shallowRef({})

  const load = async () => {
    const current = client?.value
    if (!active() || !can(ADMIN_NODES.settings) || !current?.settings) {
      domains.value = []
      titles.value = {}
      return
    }
    try {
      const names = await settingsDomainNames(current)
      titles.value = await settingsDomainTitles(current)
      domains.value = settingsTabDomains(names)
    } catch {
      domains.value = []
      titles.value = {}
    }
  }

  watch(() => [active(), can(ADMIN_NODES.settings), client?.value], load, { immediate: true })

  return {
    domains,
    titleOf: (domain) => titles.value[domain] ?? domain
  }
}

export { useSettingsDomains }
