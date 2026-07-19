package com.seongja.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMeshCommandParserTest {
    @Test
    fun opensNamedAiProviderSetup() {
        val command = ProviderMeshCommandParser.parse("Open DeepSeek API setup")
        assertEquals(ProviderMeshCommand.Open("deepseek"), command)
    }

    @Test
    fun opensOpenAiWithNaturalSpacing() {
        val command = ProviderMeshCommandParser.parse("Friday, open Open AI set-up")
        assertEquals(ProviderMeshCommand.Open("openai"), command)
    }

    @Test
    fun opensWeatherCategory() {
        val command = ProviderMeshCommandParser.parse("Open weather API setup")
        assertEquals(ProviderMeshCommand.Open(category = ProviderMeshCategory.WEATHER), command)
    }

    @Test
    fun opensResearchCategory() {
        val command = ProviderMeshCommandParser.parse("Open research APIs setup")
        assertEquals(ProviderMeshCommand.Open(category = ProviderMeshCategory.RESEARCH), command)
    }

    @Test
    fun requestsLiveModelDiscovery() {
        val command = ProviderMeshCommandParser.parse("List OpenRouter models")
        assertEquals(ProviderMeshCommand.Open("openrouter", discoverModels = true), command)
    }

    @Test
    fun activatesNamedModel() {
        val command = ProviderMeshCommandParser.parse("Use Anthropic model claude-sonnet-custom")
        assertEquals(ProviderMeshCommand.Activate("anthropic", "claude-sonnet-custom"), command)
    }

    @Test
    fun allPresetBaseUrlsAreSecureOrCustom() {
        assertTrue(ProviderMeshCatalog.presets.all { it.baseUrl.isBlank() || it.baseUrl.startsWith("https://") })
    }

    @Test
    fun catalogCoversAllOperationalCategories() {
        val categories = ProviderMeshCatalog.presets.map { it.category }.toSet()
        assertTrue(categories.containsAll(ProviderMeshCategory.entries))
    }
}
