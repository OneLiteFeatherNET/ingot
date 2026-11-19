package com.reposilite.frontend.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FilterBlockValidationTest {

    private val plugin = "example"
    private val list = PluginUiBlock.ListBlock("/api/plugins/example/items")

    private fun problemsOf(vararg blocks: PluginUiBlock): String =
        PluginUiValidator.pageProblems(
            plugin,
            PluginUiPage(id = "catalogue", title = "Catalogue", area = "maintenance", blocks = blocks.toList()),
        ).joinToString(separator = "\n")

    private fun filterOf(vararg controls: PluginUiBlock.FilterControl) = PluginUiBlock.FilterBlock(controls.toList())

    @Test
    fun `should accept a filter that is followed by a list`() {
        // when: a filter with one text control comes before the list it filters
        val problems = problemsOf(filterOf(PluginUiBlock.FilterControl.Text("query", "Search")), list)

        // then: no problem is reported
        assertThat(problems).describedAs("problems of a filter with a following list").isEmpty()
    }

    @Test
    fun `should refuse a filter that has no list after it`() {
        // when: the filter stands alone on the page
        val problems = problemsOf(filterOf(PluginUiBlock.FilterControl.Text("query", "Search")))

        // then: the problem says the filter needs a list after it
        assertThat(problems).describedAs("problems").contains("filter").contains("no list after it")
    }

    @Test
    fun `should refuse a filter that comes after its list`() {
        // when: the list comes first, so no list follows the filter
        val problems = problemsOf(list, filterOf(PluginUiBlock.FilterControl.Text("query", "Search")))

        // then: the problem says the filter needs a list after it
        assertThat(problems).describedAs("problems").contains("no list after it")
    }

    @Test
    fun `should refuse a filter control with the key cursor`() {
        // when: a control uses the key that paging reserves
        val problems = problemsOf(filterOf(PluginUiBlock.FilterControl.Text("cursor", "Page")), list)

        // then: the problem names the reserved key
        assertThat(problems).describedAs("problems").contains("'cursor'").contains("reserved")
    }

    @Test
    fun `should refuse two controls with one key`() {
        // when: two controls send the same query parameter
        val problems = problemsOf(
            filterOf(
                PluginUiBlock.FilterControl.Text("query", "Search"),
                PluginUiBlock.FilterControl.Select("query", "Loader", listOf(PluginUiBlock.FilterControl.Option("paper", "Paper"))),
            ),
            list,
        )

        // then: the problem names the repeated key
        assertThat(problems).describedAs("problems").contains("key 'query'").contains("more than one control")
    }

    @Test
    fun `should refuse a filter with seven controls`() {
        // when: the filter has one control over the limit of six
        val controls = (1..7).map { PluginUiBlock.FilterControl.Text("key$it", "Field $it") }
        val problems = problemsOf(filterOf(*controls.toTypedArray()), list)

        // then: the problem names the number of controls
        assertThat(problems).describedAs("problems").contains("7 controls")
    }

    @Test
    fun `should accept a filter with six controls`() {
        // when: the filter has the largest number of controls
        val controls = (1..6).map { PluginUiBlock.FilterControl.Text("key$it", "Field $it") }
        val problems = problemsOf(filterOf(*controls.toTypedArray()), list)

        // then: no problem is reported
        assertThat(problems).describedAs("problems").isEmpty()
    }
}
