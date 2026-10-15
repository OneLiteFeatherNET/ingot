import {computed, markRaw, ref} from 'vue'
import isEqual from 'lodash/isEqual'
import isPlainObject from 'lodash/isPlainObject'
import { useSession } from './session'
import { createSuccessToast, createErrorToast, errorMessage } from '../helpers/toast'
import { createAjv } from '@jsonforms/core'
import { defaultStyles, mergeStyles, vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { default as ObjectRenderer, tester as objectTester } from '../components/renderers/ObjectRenderer.vue'
import { default as AllOfRenderer, tester as allOfTester } from '../components/renderers/AllOfRenderer.vue'
import { default as ArrayListRenderer, tester as arrayListTester } from '../components/renderers/ArrayListRenderer.vue'
import { default as OneOfRenderer, tester as oneOfTester } from '../components/renderers/OneOfRenderer.vue'
import { default as ConstantRenderer, tester as constantTester } from '../components/renderers/ConstantRenderer.vue'
import { default as OptionalRenderer, tester as optionalTester } from '../components/renderers/OptionalRenderer.vue'
import { default as SectionRenderer, tester as sectionTester } from '../components/renderers/SectionRenderer.vue'
import { default as BooleanSwitchRenderer, tester as booleanSwitchTester } from '../components/renderers/BooleanSwitchRenderer.vue'
import { default as MdTextFieldRenderer, tester as mdTextFieldTester } from '../components/renderers/MdTextFieldRenderer.vue'
import { collectionsOf, idText, validateNewId } from '../components/settings/collections'
import { default as MdSelectRenderer, tester as mdSelectTester, oneOfTester as mdOneOfSelectTester } from '../components/renderers/MdSelectRenderer.vue'
import { default as MdColourFieldRenderer, tester as mdColourFieldTester } from '../components/renderers/MdColourFieldRenderer.vue'
import { default as MdTextAreaRenderer, tester as mdTextAreaTester } from '../components/renderers/MdTextAreaRenderer.vue'
import { default as TagsFieldRenderer, tester as tagsFieldTester } from '../components/renderers/TagsFieldRenderer.vue'
import { default as AppearanceSectionRenderer, tester as appearanceSectionTester } from '../components/renderers/AppearanceSectionRenderer.vue'
import { invalidColourFields } from '../theme/settings'
import { setAppearance, applyAppearance } from './appearance'
import { etagOf, isPreconditionFailed } from './client'

const cloneJson = (value) => JSON.parse(JSON.stringify(value))

/*
 * The fields where a draft and the server state differ, as dotted paths. Objects are compared key by key,
 * arrays index by index, and a value present on one side only is reported with the other side undefined.
 */
export const diffFields = (local, server, path = '') => {
  if (isEqual(local, server)) return []
  const childPath = (key) => (path ? `${path}.${key}` : `${key}`)
  if (isPlainObject(local) && isPlainObject(server)) {
    return [...new Set([...Object.keys(local), ...Object.keys(server)])]
      .flatMap(key => diffFields(local[key], server[key], childPath(key)))
  }
  if (Array.isArray(local) && Array.isArray(server) && local.length === server.length) {
    return local.flatMap((value, index) => diffFields(value, server[index], childPath(index)))
  }
  return [{ path: path || '(whole domain)', local, server }]
}

/*
 * How many values differ between an edited configuration and the server snapshot. Objects are
 * compared key by key, arrays position by position, and a value present on only one side counts
 * once. So an edited field counts as one change, and an added or removed entry as one change too.
 */
export const countDifferences = (current, saved) => {
  if (isEqual(current, saved)) return 0
  if (Array.isArray(current) && Array.isArray(saved)) {
    const length = Math.max(current.length, saved.length)
    let count = 0
    for (let index = 0; index < length; index++) {
      count += index >= current.length || index >= saved.length ? 1 : countDifferences(current[index], saved[index])
    }
    return count
  }
  if (isPlainObject(current) && isPlainObject(saved)) {
    const keys = new Set([...Object.keys(current), ...Object.keys(saved)])
    let count = 0
    for (const key of keys) count += countDifferences(current[key], saved[key])
    return count
  }
  return 1
}

/*
 * One store per client. The factory keeps no module state of its own, so a test builds a
 * fresh store and nothing leaks between tests. The application uses the single instance at
 * the bottom of this file.
 *
 * `snapshots` holds what the server last confirmed for each domain. A domain is dirty when
 * its edited value differs from that snapshot, so the flag is derived from the data and
 * survives the settings view being unmounted and mounted again.
 */
export const createConfigurationStore = (client) => {
  const domains = ref([])
  const schemas = ref({})
  const configurations = ref({})
  const snapshots = ref({})
  const validity = ref({})
  const selectedDomain = ref('')
  const loadError = ref(null)
  const saveError = ref(null)
  const saving = ref(false)
  /* The tag each domain was last read or written with. A write sends it back, so the server can refuse a stale draft. */
  const etags = ref({})
  /* The domain whose stored settings changed elsewhere, or null. The conflict dialog renders it. */
  const conflict = ref(null)

  /* markRaw keeps Vue from wrapping the instance in a reactive proxy, which breaks ajv's code generation. */
  const configurationValidator = markRaw(createAjv({
    useDefaults: true,
    removeAdditional: false,
    formats: {
      'repositories.storageProvider.quota': /^([1-9]\d*)([KkMmGg][Bb]|%)$/,
      'repositories.id': {
        type: 'string',
        validate: (name) => name in configurations.value['maven'].repositories || name.startsWith(' ') || name.endsWith(' ')
      },
      'repositories.proxied.allowedGroups': /^(\w+\.)*\w+$/,
    }
  }))
  configurationValidator.addFormat("repositories.id", {
    type: "string",
    validate: (value) => !value.endsWith(' ')
  })

  const titleOf = (domain) => schemas.value[domain]?.title ?? domain

  /*
   * Ajv fills in schema defaults, and JsonForms does the same on mount and reports the result
   * as a change. The server snapshot gets the same defaults, or every tab would open dirty.
   * A schema that does not compile is left to the form to report, and the data stays as it is.
   */
  const withDefaults = (domain, data) => {
    try {
      configurationValidator.compile(schemas.value[domain])(data)
    } catch {
      // the form reports the schema problem itself
    }
    return data
  }

  const isDirty = (domain) => !isEqual(configurations.value[domain], snapshots.value[domain])
  const dirtyDomains = computed(() => domains.value.filter(isDirty))
  const hasChanges = computed(() => dirtyDomains.value.length > 0)
  const invalidDomains = computed(() => domains.value.filter(domain => validity.value[domain] === false))

  /* A domain with a difference always counts at least once, even when the diff finds nothing (for example an added undefined key). */
  const changeCount = computed(() => dirtyDomains.value.reduce(
    (total, domain) => total + Math.max(1, countDifferences(configurations.value[domain], snapshots.value[domain])),
    0
  ))
  const hasUnsavedChanges = computed(() => hasChanges.value)

  const fetchConfiguration = async () => {
    try {
      const { data: domainNames } = await client.value.settings.domains()
      const loaded = await Promise.all(domainNames.map(async domain => {
        const [schema, configuration] = await Promise.all([
          client.value.settings.schema(domain),
          client.value.settings.fetch(domain)
        ])
        return { domain, schema: schema.data, configuration: configuration.data, etag: etagOf(configuration) }
      }))

      domains.value = domainNames
      loaded.forEach(({ domain, schema, configuration, etag }) => {
        schemas.value[domain] = schema
        configurations.value[domain] = configuration
        etags.value[domain] = etag
      })
      loaded.forEach(({ domain }) => {
        const withDefaultValues = withDefaults(domain, cloneJson(configurations.value[domain]))
        configurations.value[domain] = withDefaultValues
        snapshots.value[domain] = cloneJson(withDefaultValues)
        delete validity.value[domain]
      })
      if (!domainNames.includes(selectedDomain.value)) selectedDomain.value = domainNames[0]

      loadError.value = null
      return true
    } catch (error) {
      loadError.value = `Cannot load configuration: ${errorMessage(error)}`
      createErrorToast(loadError.value)
      return false
    }
  }

  /* The stored state of a domain as the server has it now, with the schema defaults applied as on load, and its tag. */
  const loadServerState = async (domain) => {
    const response = await client.value.settings.fetch(domain)
    return { server: withDefaults(domain, cloneJson(response.data)), etag: etagOf(response) }
  }

  /* Makes the server state the draft and the snapshot of a domain, and keeps the tag it came with. */
  const adoptServerState = async (domain, server, etag) => {
    configurations.value[domain] = server
    snapshots.value[domain] = cloneJson(server)
    etags.value[domain] = etag
    // The dashboard shows its appearance from these settings, so the admin sees the result now.
    if (domain === 'frontend') {
      setAppearance(server.appearance)
      await applyAppearance()
    }
  }

  const openSettingsConflict = (domain) => {
    conflict.value = { kind: 'settings', name: domain, title: titleOf(domain), reviewed: false, differences: [] }
  }

  /*
   * Writes only the domains that differ from the server. Each one is written with the tag it was read with
   * and read back, so the snapshot holds what the server now has. One failing domain does not undo the others.
   * A domain the server refused with 412 changed elsewhere: it stays dirty and opens the conflict dialog.
   * Any other failure stays dirty too and is named in saveError.
   */
  const saveConfiguration = async () => {
    if (saving.value || invalidDomains.value.length > 0) return false
    const pending = dirtyDomains.value
    if (pending.length === 0) return true

    saving.value = true
    saveError.value = null
    const outcomes = await Promise.allSettled(pending.map(async domain => {
      // The payload is copied before the first await, so later edits do not leak into this write.
      await client.value.settings.update(domain, cloneJson(configurations.value[domain]), etags.value[domain])
      const { server, etag } = await loadServerState(domain)
      await adoptServerState(domain, server, etag)
    }))
    saving.value = false

    const failures = outcomes
      .map((outcome, index) => outcome.status === 'rejected' ? { domain: pending[index], error: outcome.reason } : null)
      .filter(Boolean)
    const stale = failures.filter(failure => isPreconditionFailed(failure.error))
    const others = failures.filter(failure => !isPreconditionFailed(failure.error))

    if (stale.length > 0) openSettingsConflict(stale[0].domain)
    if (others.length > 0) {
      const names = others.map(failure => titleOf(failure.domain)).join(', ')
      saveError.value = `Not saved: ${names}. ${errorMessage(others[0].error)}`
      createErrorToast(saveError.value)
      return false
    }
    if (stale.length > 0) return false

    createSuccessToast('Configuration updated')
    return true
  }

  /* Fetches what the server has now and lists the fields where it differs from the local draft. Nothing is changed. */
  const reviewConflict = async () => {
    const current = conflict.value
    if (current?.kind !== 'settings') return false
    try {
      const { server } = await loadServerState(current.name)
      conflict.value = {
        ...current,
        reviewed: true,
        differences: diffFields(configurations.value[current.name], server),
      }
      return true
    } catch (error) {
      saveError.value = `Cannot load ${titleOf(current.name)} from the server: ${errorMessage(error)}`
      createErrorToast(saveError.value)
      return false
    }
  }

  /* Takes the server state and drops the local draft of that domain. */
  const reloadConflict = async () => {
    const current = conflict.value
    if (current?.kind !== 'settings') return false
    try {
      const { server, etag } = await loadServerState(current.name)
      await adoptServerState(current.name, server, etag)
      delete validity.value[current.name]
      conflict.value = null
      return true
    } catch (error) {
      saveError.value = `Cannot load ${titleOf(current.name)} from the server: ${errorMessage(error)}`
      createErrorToast(saveError.value)
      return false
    }
  }

  /*
   * Sends the local draft of the conflicting domain without a precondition, which replaces the change made
   * elsewhere. The dialog asks for a second confirmation before this runs.
   */
  const overwriteConflict = async () => {
    const current = conflict.value
    if (current?.kind !== 'settings' || saving.value) return false
    saving.value = true
    saveError.value = null
    try {
      await client.value.settings.update(current.name, cloneJson(configurations.value[current.name]))
      const { server, etag } = await loadServerState(current.name)
      await adoptServerState(current.name, server, etag)
      delete validity.value[current.name]
      conflict.value = null
      createSuccessToast('Configuration updated')
      return true
    } catch (error) {
      saveError.value = `Not saved: ${titleOf(current.name)}. ${errorMessage(error)}`
      createErrorToast(saveError.value)
      return false
    } finally {
      saving.value = false
    }
  }

  /* Closes the dialog and keeps the draft. It stays dirty, so the next save meets the same conflict. */
  const dismissConflict = () => {
    conflict.value = null
  }

  /* Puts every edited domain back to its server snapshot. Nothing is sent. */
  const discardChanges = () => {
    dirtyDomains.value.forEach(domain => {
      configurations.value[domain] = cloneJson(snapshots.value[domain])
      delete validity.value[domain]
    })
    saveError.value = null
  }

  /* Empties every domain and saves it at once. The server treats an empty domain as its defaults. */
  const factoryReset = () => {
    domains.value.forEach(domain => {
      configurations.value[domain] = {}
      delete validity.value[domain]
    })
    return saveConfiguration()
  }

  const setDomainData = (domain, data) => {
    configurations.value[domain] = data
  }

  /*
   * Validity covers the whole domain, not the entry on screen. Only one entry's form is mounted
   * at a time, so a per-form report would forget a broken entry as soon as the user moved on.
   * A schema that does not compile is reported by the form, so it does not block the domain here.
   */
  const validateDomain = (domain) => {
    let valid
    try {
      valid = configurationValidator.compile(schemas.value[domain])(configurations.value[domain]) === true
    } catch {
      valid = true
    }
    validity.value[domain] = valid && idsAreUsable(domain) && invalidColourFields(configurations.value[domain]).length === 0
  }

  /*
   * Every entry needs an id that the new-entry dialog would accept: present, in the allowed
   * characters, and unique in its collection. An entry without one has no address, so the domain
   * cannot be saved or downloaded until it has one.
   */
  const idsAreUsable = (domain) => collectionsOf(schemas.value[domain]).every((collection) => {
    const entries = configurations.value[domain]?.[collection.key] ?? []
    return entries.every((entry, index) => {
      const others = entries.filter((_, other) => other !== index).map(idText)
      return validateNewId(idText(entry), others, collection.noun) === ''
    })
  })

  const entriesOf = (domain, collection) => {
    if (!configurations.value[domain]) configurations.value[domain] = {}
    if (!Array.isArray(configurations.value[domain][collection])) configurations.value[domain][collection] = []
    return configurations.value[domain][collection]
  }

  /*
   * The stable key of each entry, aligned with the entries by position. A key belongs to the entry
   * and not to its id, so the list and the detail pane can follow an entry while its fields change.
   * The keys are kept outside the reactive state: they are never sent to the server, and the view
   * only reads them.
   */
  let keySequence = 0
  const keysByCollection = new Map()
  // The live key list, kept at one key per entry. It is only written by the edits below, never by a read.
  const keysFor = (domain, collection, count) => {
    const name = `${domain}/${collection}`
    if (!keysByCollection.has(name)) keysByCollection.set(name, [])
    const keys = keysByCollection.get(name)
    while (keys.length < count) keys.push(`entry-${++keySequence}`)
    keys.length = count
    return keys
  }

  // A copy, read during render, so it writes nothing into the reactive state.
  const entryKeysOf = (domain, collection) => [...keysFor(domain, collection, configurations.value[domain]?.[collection]?.length ?? 0)]

  /* Replaces the entry at `index`. An edit that arrives without an id keeps the id the entry had, so a change of the form can never drop an entry from the list. */
  const setEntry = (domain, collection, index, entry) => {
    const entries = entriesOf(domain, collection)
    const previous = entries[index]
    const keepsId = isPlainObject(entry) && entry.id === undefined && isPlainObject(previous) && previous.id !== undefined
    entries[index] = keepsId ? { id: previous.id, ...entry } : entry
    validateDomain(domain)
  }

  /* Appends an entry and returns its position in the collection. */
  const addEntry = (domain, collection, entry) => {
    const entries = entriesOf(domain, collection)
    keysFor(domain, collection, entries.length)
    entries.push(entry)
    keysFor(domain, collection, entries.length)
    validateDomain(domain)
    return entries.length - 1
  }

  const removeEntry = (domain, collection, index) => {
    const entries = entriesOf(domain, collection)
    keysFor(domain, collection, entries.length).splice(index, 1)
    entries.splice(index, 1)
    validateDomain(domain)
  }

  const setValidity = (domain, valid) => {
    validity.value[domain] = valid
  }

  /* What the server last confirmed for a domain, or undefined before it has loaded. Unsaved edits never show here. */
  const savedConfiguration = (domain) => snapshots.value[domain]

  return {
    domains,
    schemas,
    configurations,
    selectedDomain,
    configurationValidator,
    loadError,
    saveError,
    saving,
    dirtyDomains,
    hasChanges,
    invalidDomains,
    isDirty,
    titleOf,
    fetchConfiguration,
    saveConfiguration,
    conflict,
    reviewConflict,
    reloadConflict,
    overwriteConflict,
    dismissConflict,
    discardChanges,
    factoryReset,
    setDomainData,
    setValidity,
    savedConfiguration,
    validateDomain,
    entryKeysOf,
    setEntry,
    addEntry,
    removeEntry,
    changeCount,
    hasUnsavedChanges
  }
}

const store = createConfigurationStore(useSession().client)

/* Lets a view receive another store instead of the shared one, which the tests use for a fresh store per test. */
export const configurationStoreKey = Symbol('configuration-store')

const renderers = markRaw([
  // Outlined fields for text, numbers and enums. They outrank the vanilla renderers, which they replace.
  { tester: mdSelectTester, renderer: MdSelectRenderer },
  { tester: mdOneOfSelectTester, renderer: MdSelectRenderer },
  // Colour fields outrank the text field they are strings of. They are not enums, so MdSelect does not take them.
  { tester: mdColourFieldTester, renderer: MdColourFieldRenderer },
  // The tags of a repository are chips; they outrank the generic list of strings, which they replace.
  { tester: tagsFieldTester, renderer: TagsFieldRenderer },
  // Prose with a length limit is a multi-line field with a counter, above the single line field.
  { tester: mdTextAreaTester, renderer: MdTextAreaRenderer },
  { tester: mdTextFieldTester, renderer: MdTextFieldRenderer },
  { tester: booleanSwitchTester, renderer: BooleanSwitchRenderer },
  { tester: arrayListTester, renderer: ArrayListRenderer },
  { tester: allOfTester, renderer: AllOfRenderer },
  { tester: oneOfTester, renderer: OneOfRenderer },
  { tester: constantTester, renderer: ConstantRenderer },
  { tester: optionalTester, renderer: OptionalRenderer },
  // The Appearance group of the dashboard settings, with a live preview of its colours under it.
  { tester: appearanceSectionTester, renderer: AppearanceSectionRenderer },
  // Groups become titled sections, replacing the fieldset of the vanilla group.
  { tester: sectionTester, renderer: SectionRenderer },
  {
    // needed because without it hangs TODO find out why
    tester: (uischema, schema) => {
      let rank = objectTester(uischema, schema)
      return rank === -1 || schema.title === 'Proxied Maven Repository' ? -1 : rank
    },
    renderer: ObjectRenderer
  },
  ...vanillaRenderers,
])

/*
 * The renderers of @dzikoysk/vue-vanilla take every class name they emit from a styles
 * object that SettingsView provides, which is the only supported way to style them from
 * the outside. Utilities written here therefore replace what used to be a stylesheet
 * reaching into the library's DOM with selectors like `.control .input`.
 *
 * mergeStyles appends to the defaults instead of replacing them, so each element keeps its
 * original class name as well. The few rules that cannot live here still select on those
 * names. The tab rows of the array and one-of renderers are SettingsTabs, which styles itself.
 */
const configurationStyles = mergeStyles(defaultStyles, {
  control: {
    /* Field chrome (outline, height, corner) is applied to text inputs in SettingsView, because a checkbox shares this key. */
    description: 'md-body-small text-on-surface-variant px-2 pb-1',
    error: 'md-body-small text-error px-2 pt-1',
    /* Focus is drawn by the outlined field rules in SettingsView: a primary border, 2px in total. */
    input: 'md-body-large text-on-surface',
    /* No background image: the forms plugin draws a chevron there, on top of the one the field shows. */
    select: 'md-body-large block w-full max-w-xl min-h-14 rounded-corner-extra-small border border-outline bg-surface bg-none appearance-none px-4 text-on-surface',
    wrapper: 'flex flex-col items-start py-1'
  },
  verticalLayout: {
    root: 'flex flex-col gap-4 py-2'
  },
  group: {
    /* An outlined card: the same surface, outline and corner as MdCard, applied through the styles object. */
    root: 'flex flex-col gap-4 rounded-corner-medium border border-outline-variant bg-surface p-4 min-w-0',
    label: 'md-title-medium text-on-surface px-1'
  },
  arrayList: {
    /* The fieldset carries the list; the legend holds the add button. Browser fieldset chrome is reset. */
    root: 'flex flex-col gap-4 p-0 m-0 min-w-0 border-0',
    legend: 'flex flex-row flex-wrap items-center justify-between gap-2 w-full mb-0',
    label: 'md-title-medium text-on-surface',
    /* Spelled out because the defaults leave this one unset, unlike every other key here. */
    description: 'description md-body-small text-on-surface-variant',
    noData: 'md-body-medium p-4 rounded-corner-medium bg-surface-container text-on-surface-variant',
    itemToolbar: 'flex flex-row items-center justify-end min-h-12',
    itemLabel: 'mr-auto hidden',
    /* The tab bar already moves entries around, so the two arrows stay out of the way. */
    itemMoveUp: 'hidden p-2',
    itemMoveDown: 'hidden p-2',
    itemDelete: 'shrink-0'
  },
  oneOf: {
    root: 'one-of-container flex flex-col gap-4'
  }
})

export function useConfiguration() {
  return {
    store,
    renderers,
    configurationStyles
  }
}
