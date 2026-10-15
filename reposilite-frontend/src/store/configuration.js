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
import { default as BooleanSwitchRenderer, tester as booleanSwitchTester } from '../components/renderers/BooleanSwitchRenderer.vue'
import { default as MdTextFieldRenderer, tester as mdTextFieldTester } from '../components/renderers/MdTextFieldRenderer.vue'
import { default as MdSelectRenderer, tester as mdSelectTester } from '../components/renderers/MdSelectRenderer.vue'

const cloneJson = (value) => JSON.parse(JSON.stringify(value))

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
        return { domain, schema: schema.data, configuration: configuration.data }
      }))

      domains.value = domainNames
      loaded.forEach(({ domain, schema, configuration }) => {
        schemas.value[domain] = schema
        configurations.value[domain] = configuration
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

  /*
   * Writes only the domains that differ from the server. Each one is written and read back, so
   * the snapshot holds what the server now has. One failing domain does not undo the others;
   * it stays dirty and is named in saveError.
   */
  const saveConfiguration = async () => {
    if (saving.value || invalidDomains.value.length > 0) return false
    const pending = dirtyDomains.value
    if (pending.length === 0) return true

    saving.value = true
    saveError.value = null
    const outcomes = await Promise.allSettled(pending.map(async domain => {
      // The payload is copied before the first await, so later edits do not leak into this write.
      await client.value.settings.update(domain, cloneJson(configurations.value[domain]))
      const response = await client.value.settings.fetch(domain)
      const confirmed = withDefaults(domain, cloneJson(response.data))
      configurations.value[domain] = confirmed
      snapshots.value[domain] = cloneJson(confirmed)
    }))
    saving.value = false

    const failures = outcomes
      .map((outcome, index) => outcome.status === 'rejected' ? { domain: pending[index], error: outcome.reason } : null)
      .filter(Boolean)
    if (failures.length === 0) {
      createSuccessToast('Configuration updated')
      return true
    }

    const names = failures.map(failure => titleOf(failure.domain)).join(', ')
    saveError.value = `Not saved: ${names}. ${errorMessage(failures[0].error)}`
    createErrorToast(saveError.value)
    return false
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
    validity.value[domain] = valid
  }

  const entriesOf = (domain, collection) => {
    if (!configurations.value[domain]) configurations.value[domain] = {}
    if (!Array.isArray(configurations.value[domain][collection])) configurations.value[domain][collection] = []
    return configurations.value[domain][collection]
  }

  /* Replaces the entry at `index`. Schema defaults are filled in by the validation, as on load. */
  const setEntry = (domain, collection, index, entry) => {
    entriesOf(domain, collection)[index] = entry
    validateDomain(domain)
  }

  /* Appends an entry and returns its position in the collection. */
  const addEntry = (domain, collection, entry) => {
    const entries = entriesOf(domain, collection)
    entries.push(entry)
    validateDomain(domain)
    return entries.length - 1
  }

  const removeEntry = (domain, collection, index) => {
    entriesOf(domain, collection).splice(index, 1)
    validateDomain(domain)
  }

  const setValidity = (domain, valid) => {
    validity.value[domain] = valid
  }

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
    discardChanges,
    factoryReset,
    setDomainData,
    setValidity,
    validateDomain,
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
  { tester: mdTextFieldTester, renderer: MdTextFieldRenderer },
  { tester: booleanSwitchTester, renderer: BooleanSwitchRenderer },
  { tester: arrayListTester, renderer: ArrayListRenderer },
  { tester: allOfTester, renderer: AllOfRenderer },
  { tester: oneOfTester, renderer: OneOfRenderer },
  { tester: constantTester, renderer: ConstantRenderer },
  { tester: optionalTester, renderer: OptionalRenderer },
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
    select: 'md-body-large block w-full max-w-xl min-h-14 rounded-corner-extra-small border border-outline bg-surface px-4 text-on-surface',
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
