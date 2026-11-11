/*
 * The settings domains of the instance, asked for once per client. A client belongs to one session (see session.js), so
 * the Settings page, the Access page and the other Administration pages share one request for the list, and a visitor
 * without the settings node never makes it (its callers check the node first).
 *
 * A failed request is not kept, so the next caller asks again.
 */

const domainNamesOf = new WeakMap()
const titlesOf = new WeakMap()

const once = (cache, client, load) => {
  if (!cache.has(client)) {
    const request = load(client)
    cache.set(client, request)
    request.catch(() => cache.delete(client))
  }
  return cache.get(client)
}

// The names of the settings domains, in the order the server lists them.
const settingsDomainNames = (client) => once(domainNamesOf, client, async (current) => {
  const { data } = await current.settings.domains()
  return data
})

// The title of each settings domain: the title of its schema, or its name when the schema does not give one.
const settingsDomainTitles = (client) => once(titlesOf, client, async (current) => {
  const names = await settingsDomainNames(current)
  const entries = await Promise.all(names.map(async (name) => {
    try {
      const { data } = await current.settings.schema(name)
      return [name, data?.title ?? name]
    } catch {
      return [name, name]
    }
  }))
  return Object.fromEntries(entries)
})

export { settingsDomainNames, settingsDomainTitles }
