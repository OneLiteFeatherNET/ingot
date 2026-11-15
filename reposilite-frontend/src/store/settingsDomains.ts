/*
 * The settings domains of the instance, asked for once per client. A client belongs to one session (see session.js), so
 * the Settings page, the Access page and the other Administration pages share one request for the list, and a visitor
 * without the settings node never makes it (its callers check the node first).
 *
 * A failed request is not kept, so the next caller asks again.
 */

import type { AxiosResponse } from 'axios'
import type { createClient } from './client'

type Client = ReturnType<typeof createClient>

// The part of a settings schema the dashboard reads. The generated spec types the schema body as plain text.
interface SettingsSchemaBody {
  title?: string | null
}

const domainNamesOf = new WeakMap<Client, Promise<string[]>>()
const titlesOf = new WeakMap<Client, Promise<Record<string, string>>>()

const once = <T>(cache: WeakMap<Client, Promise<T>>, client: Client, load: (current: Client) => Promise<T>): Promise<T> => {
  if (!cache.has(client)) {
    const request = load(client)
    cache.set(client, request)
    request.catch(() => cache.delete(client))
  }
  return cache.get(client) as Promise<T>
}

const requestDomainNames = (client: Client): Promise<string[]> =>
  client.settings.domains().then((response: AxiosResponse<string[]>) => response.data)

const requestSchemaTitle = (client: Client, name: string): Promise<string | null | undefined> =>
  client.settings.schema(name).then((response: AxiosResponse<SettingsSchemaBody | undefined>) => response.data?.title)

// The names of the settings domains, in the order the server lists them.
const settingsDomainNames = (client: Client): Promise<string[]> => once(domainNamesOf, client, async (current) => {
  const names: string[] = await requestDomainNames(current)
  return names
})

// The title of each settings domain: the title of its schema, or its name when the schema does not give one.
const settingsDomainTitles = (client: Client): Promise<Record<string, string>> => once(titlesOf, client, async (current) => {
  const names = await settingsDomainNames(current)
  const entries = await Promise.all(names.map(async (name): Promise<[string, string]> => {
    try {
      const title = await requestSchemaTitle(current, name)
      return [name, title ?? name]
    } catch {
      return [name, name]
    }
  }))
  return Object.fromEntries(entries)
})

export { settingsDomainNames, settingsDomainTitles }
