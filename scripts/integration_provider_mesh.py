from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRAIN = ROOT / "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt"

text = BRAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Provider mesh integration anchor missing: {old[:180]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "    private val youtube = YouTubeDataClient(integrationStore)\n"
    "    private val capabilityRouter = FridayCapabilityRouter(appContext)\n",
    "    private val youtube = YouTubeDataClient(integrationStore)\n"
    "    private val universalProviderStore = SecureProviderMeshRegistry(appContext)\n"
    "    private val universalProviderClient = UniversalProviderMeshClient(universalProviderStore)\n"
    "    private val providerMeshCommands = ProviderMeshCommandRouter(appContext)\n"
    "    private val capabilityRouter = FridayCapabilityRouter(appContext)\n",
)

replace_once(
    "    fun isCloudConfigured(): Boolean =\n"
    "        registryStore.load().configuredProfiles().isNotEmpty() || deepSeek.isConfigured()\n",
    "    fun isCloudConfigured(): Boolean =\n"
    "        registryStore.load().configuredProfiles().isNotEmpty() ||\n"
    "            deepSeek.isConfigured() ||\n"
    "            universalProviderClient.isConfigured()\n",
)

replace_once(
    "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n"
    "            append(\" // ${capabilityRouter.statusLabel()}\")\n",
    "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n"
    "            append(\" // Universal ${universalProviderClient.activeLabel()}\")\n"
    "            append(\" // ${capabilityRouter.statusLabel()}\")\n",
)

replace_once(
    "        localMeshCommand(input)?.let { return it }\n\n"
    "        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }\n",
    "        localMeshCommand(input)?.let { return it }\n\n"
    "        providerMeshCommands.intercept(input, memory.summary())?.let { return it }\n"
    "        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }\n",
)

old_resilient = '''    private fun resilientCortexResponse(
        input: String,
        onToken: ((String) -> Unit)?
    ): BrainResponse {
        JarvisOperationBus.publish("COGNITIVE ROUTING", "SCORING AVAILABLE REASONING NODES", .18f)
        val meshConfigured = registryStore.load().configuredProfiles().isNotEmpty()
        if (meshConfigured) {
            val mesh = runCatching { cortexResponse(input, onToken) }
            mesh.onSuccess {
                JarvisOperationBus.clear("CORTEX RESPONSE VERIFIED")
                return it
            }
            val error = mesh.exceptionOrNull()
            if (error?.message?.contains("after speech began", ignoreCase = true) == true) throw error
            JarvisOperationBus.publish("CORTEX FAILOVER", "PRIMARY MESH DEGRADED // EVALUATING DEEPSEEK", .52f)
        }
        if (deepSeek.isConfigured()) {
            return deepSeekResponse(input, onToken).also { JarvisOperationBus.clear("DEEPSEEK RESPONSE VERIFIED") }
        }
        JarvisOperationBus.clear("NO COGNITIVE ROUTE COMPLETED")
        return configurationRequiredResponse()
    }
'''

new_resilient = '''    private fun resilientCortexResponse(
        input: String,
        onToken: ((String) -> Unit)?
    ): BrainResponse {
        JarvisOperationBus.publish("COGNITIVE ROUTING", "SCORING LEGACY + UNIVERSAL PROVIDER NODES", .18f)
        val universalRegistry = universalProviderStore.load()
        var universalAttempted = false
        if (universalRegistry.defaultAiProviderId.isNotBlank() && universalProviderClient.isConfigured()) {
            universalAttempted = true
            val preferred = runCatching { universalProviderResponse(input, onToken) }
            preferred.onSuccess {
                JarvisOperationBus.clear("UNIVERSAL PROVIDER RESPONSE VERIFIED")
                return it
            }
            JarvisOperationBus.publish("PROVIDER FAILOVER", "DEFAULT PROVIDER DEGRADED // EVALUATING LEGACY MESH", .34f)
        }

        val meshConfigured = registryStore.load().configuredProfiles().isNotEmpty()
        if (meshConfigured) {
            val mesh = runCatching { cortexResponse(input, onToken) }
            mesh.onSuccess {
                JarvisOperationBus.clear("CORTEX RESPONSE VERIFIED")
                return it
            }
            val error = mesh.exceptionOrNull()
            if (error?.message?.contains("after speech began", ignoreCase = true) == true) throw error
            JarvisOperationBus.publish("CORTEX FAILOVER", "PRIMARY MESH DEGRADED // EVALUATING DEEPSEEK", .52f)
        }
        if (deepSeek.isConfigured()) {
            val deepSeekResult = runCatching { deepSeekResponse(input, onToken) }
            deepSeekResult.onSuccess {
                JarvisOperationBus.clear("DEEPSEEK RESPONSE VERIFIED")
                return it
            }
            JarvisOperationBus.publish("CORTEX FAILOVER", "DEEPSEEK DEGRADED // EVALUATING UNIVERSAL MESH", .72f)
        }
        if (!universalAttempted && universalProviderClient.isConfigured()) {
            return universalProviderResponse(input, onToken).also { JarvisOperationBus.clear("UNIVERSAL PROVIDER RESPONSE VERIFIED") }
        }
        JarvisOperationBus.clear("NO COGNITIVE ROUTE COMPLETED")
        return configurationRequiredResponse()
    }
'''
replace_once(old_resilient, new_resilient)

universal_method = '''    private fun universalProviderResponse(input: String, onToken: ((String) -> Unit)?): BrainResponse {
        JarvisOperationBus.publish("UNIVERSAL PROVIDER MESH", "CONTACTING OWNER-SELECTED MODEL ROUTE", .78f)
        onToken?.invoke("Routing through the selected provider, Sir. ")
        val result = universalProviderClient.ask(input, memory.promptContext(input))
        val reply = OwnerIdentityCore.normalizeOperatorReference(result.reply)
        return BrainResponse(
            spoken = reply,
            display = reply,
            intent = "provider_mesh/response",
            confidence = .96f,
            mode = BrainMode.ONLINE,
            trace = listOf(
                "route=universal_provider_mesh",
                "provider=${result.providerLabel}",
                "model=${result.model}",
                "http=${result.statusCode}",
                "latency=${result.elapsedMs}ms"
            ),
            memory = memory.summary(),
            thoughts = listOf("The owner-selected provider route used only relevant encrypted local context."),
            entities = listOf("provider=${result.providerId}", "model=${result.model}"),
            decision = "owner_selected_universal_provider_response",
            action = BrainAction()
        )
    }

'''
replace_once(
    "    private fun deepSeekResponse(input: String, onToken: ((String) -> Unit)?): BrainResponse {\n",
    universal_method + "    private fun deepSeekResponse(input: String, onToken: ((String) -> Unit)?): BrainResponse {\n",
)

replace_once(
    '''            if (registryStore.load().configuredProfiles().isNotEmpty()) {
                val result = cortexMesh.ask(prompt, memory.promptContext(input), onToken)
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "cortex_mesh"
            } else {
                val result = deepSeek.ask(prompt, memory.promptContext(input), onToken)
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "deepseek"
            }
''',
    '''            if (registryStore.load().configuredProfiles().isNotEmpty()) {
                val result = cortexMesh.ask(prompt, memory.promptContext(input), onToken)
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "cortex_mesh"
            } else if (deepSeek.isConfigured()) {
                val result = deepSeek.ask(prompt, memory.promptContext(input), onToken)
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "deepseek"
            } else {
                val result = universalProviderClient.ask(prompt, memory.promptContext(input))
                OwnerIdentityCore.normalizeOperatorReference(result.reply) to "universal_provider_mesh"
            }
''',
)

replace_once(
    "        val integrations = integrationStore.load()\n"
    "        val voice = SecureVoiceRegistry(appContext).load()\n",
    "        val integrations = integrationStore.load()\n"
    "        val universal = universalProviderStore.load()\n"
    "        val voice = SecureVoiceRegistry(appContext).load()\n",
)

replace_once(
    "            appendLine(\"GMAIL // ${integrations.gmailHealthLabel()}\")\n"
    "            appendLine(\"VOICE MESH // ${voice.healthLabel()}\")\n",
    "            appendLine(\"GMAIL // ${integrations.gmailHealthLabel()}\")\n"
    "            appendLine(\"UNIVERSAL PROVIDERS // ${universal.profiles.count { it.isConfigured() }}/${ProviderMeshCatalog.presets.size} CONFIGURED // DEFAULT ${ProviderMeshCatalog.byId(universal.defaultAiProviderId)?.label ?: \"NOT SELECTED\"}\")\n"
    "            appendLine(\"VOICE MESH // ${voice.healthLabel()}\")\n",
)

replace_once(
    "            spoken = \"The operations core is active. The cortex mesh has ${configured.size} configured nodes, with $online currently online. Voice has ${voice.configuredKeys().size} encrypted routes, Sir.\",\n",
    "            spoken = \"The operations core is active. The legacy cortex has ${configured.size} configured nodes, the universal mesh has ${universal.profiles.count { it.isConfigured() }} configured providers, and voice has ${voice.configuredKeys().size} encrypted routes, Sir.\",\n",
)

replace_once(
    '''    private fun configurationRequiredResponse(): BrainResponse = integrationRequiredResponse(
        spoken = "The owner identity core and encrypted local memory are active, but no reasoning route is configured. Say configure APIs to add Gemini, Groq, or DeepSeek, Sir.",
        display = "${OwnerIdentityCore.VERSION} // ACTIVE\nCOGNITIVE ROUTE CONFIGURATION REQUIRED\nSAY // CONFIGURE APIS",
        intent = "cortex_config_required"
    )
''',
    '''    private fun configurationRequiredResponse(): BrainResponse = integrationRequiredResponse(
        spoken = "The owner identity core and encrypted local memory are active, but no reasoning route is configured. Say open API setup, or name a provider such as OpenAI, Anthropic, Gemini, DeepSeek, Groq, Mistral, xAI, Cohere, Perplexity, or OpenRouter, Sir.",
        display = "${OwnerIdentityCore.VERSION} // ACTIVE\nUNIVERSAL PROVIDER ROUTE REQUIRED\nSAY // OPEN API SETUP",
        intent = "cortex_config_required"
    )
''',
)

BRAIN.write_text(text, encoding="utf-8")
print("Universal provider mesh integrated into JarvisBrain.kt")
