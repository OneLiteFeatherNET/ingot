import {computed, markRaw, ref, toRaw} from 'vue'
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

const { client } = useSession()
const domains = ref([])
const schemas = ref({})
const configurations = ref({})
const selectedDomain = ref('')
    
const fetchConfiguration = () => {
  return client.value.settings.domains()
    .then(domainsResponse => domains.value = domainsResponse.data)
    .then(() => Promise.all(domains.value.map(domain =>
      client.value.settings.schema(domain)
        .then(schemaResponse => schemas.value[domain] = schemaResponse.data)
        .then(() => client.value.settings.fetch(domain))
        .then(configurationResponse => configurations.value[domain] = configurationResponse.data)))
    )
    .then(() => selectedDomain.value = domains.value[0])
    .then(() => createSuccessToast('Configuration loaded'))
    .catch(error => createErrorToast(`Cannot load configuration: ${errorMessage(error)}`))
}

const updateConfiguration = () =>
  Promise.all(domains.value.map(domain =>
    client.value.settings.update(domain, toRaw(configurations.value[domain]))
      .then(() => client.value.settings.fetch(domain))
      .then(response => configurations.value[domain] = response.data)
  ))
    .then(() => createSuccessToast('Configuration updated'))
    .catch(error => createErrorToast(`Cannot update configuration: ${errorMessage(error)}`))

const renderers = markRaw([
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
 * names, and so does the vue3-tabs markup wrapped around the array and one-of renderers.
 */
const configurationStyles = mergeStyles(defaultStyles, {
  control: {
    description: 'pl-[0.45em] text-sm italic',
    error: 'text-red-500 px-2 font-bold',
    input: 'mx-2 rounded',
    select: 'mx-2 rounded pr-8 text-sm h-9 px-4 text-black',
    wrapper: 'flex py-2'
  },
  verticalLayout: {
    root: 'flex flex-col flex-wrap py-4 h-full gap-4'
  },
  group: {
    root: 'flex flex-col flex-wrap py-4 h-full gap-4'
  },
  arrayList: {
    /* No padding, because a fieldset carries the list and its legend holds the add button. */
    root: 'flex flex-col flex-wrap h-full gap-4 p-0',
    legend: 'flex flex-row-reverse gap-2 w-full mb-0',
    addButton: 'rounded-full h-6 w-6 leading-6 bg-blue-700 ml-auto text-white z-1',
    label: 'font-bold',
    /* Spelled out because the defaults leave this one unset, unlike every other key here. */
    description: 'description pl-[0.45em] text-sm italic',
    noData: 'p-4 bg-gray-200 dark:bg-gray-900 italic rounded-md',
    itemToolbar: 'flex flex-row items-baseline relative',
    itemLabel: 'mr-auto hidden',
    /* The tab bar already moves entries around, so the two arrows stay out of the way. */
    itemMoveUp: 'hidden p-2',
    itemMoveDown: 'hidden p-2',
    itemDelete: 'absolute right-0 top-2 p-2'
  },
  oneOf: {
    root: 'one-of-container h-full flex flex-col'
  }
})

const configurationValidator = computed(() => {
  const ajv = createAjv({
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
  })
  ajv.addFormat("repositories.id", {
    type: "string",
    validate: (value) => !value.endsWith(' ')
  })
  return ajv
})

export function useConfiguration() {
  return {
    fetchConfiguration,
    updateConfiguration,
    renderers,
    configurationStyles,
    configurationValidator,
    domains,
    configurations,
    schemas,
    selectedDomain
  }
}