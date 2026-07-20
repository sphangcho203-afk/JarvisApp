from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRAIN = ROOT / "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt"
text = BRAIN.read_text(encoding="utf-8")


def replace_required(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Provider mesh {label} anchor missing: {old[:180]!r}")
    text = text.replace(old, new, 1)


replace_required(
    "    private val youtube = YouTubeDataClient(integrationStore)\n"
    "    private val capabilityRouter = FridayCapabilityRouter(appContext)\n",
    "    private val youtube = YouTubeDataClient(integrationStore)\n"
    "    private val universalProviderStore = SecureProviderMeshRegistry(appContext)\n"
    "    private val universalProviderClient = UniversalProviderMeshClient(universalProviderStore)\n"
    "    private val providerMeshCommands = ProviderMeshCommandRouter(appContext)\n"
    "    private val capabilityRouter = FridayCapabilityRouter(appContext)\n",
    "field",
)

replace_required(
    "    fun isCloudConfigured(): Boolean =\n"
    "        registryStore.load().configuredProfiles().isNotEmpty() || deepSeek.isConfigured()\n",
    "    fun isCloudConfigured(): Boolean =\n"
    "        registryStore.load().configuredProfiles().isNotEmpty() ||\n"
    "            deepSeek.isConfigured() ||\n"
    "            universalProviderClient.isConfigured()\n",
    "cloud-state",
)

replace_required(
    "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n"
    "            append(\" // ${capabilityRouter.statusLabel()}\")\n",
    "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n"
    "            append(\" // Universal ${universalProviderClient.activeLabel()}\")\n"
    "            append(\" // ${capabilityRouter.statusLabel()}\")\n",
    "model-summary",
)

replace_required(
    "        localMeshCommand(input)?.let { return it }\n\n"
    "        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }\n",
    "        localMeshCommand(input)?.let { return it }\n\n"
    "        providerMeshCommands.intercept(input, memory.summary())?.let { return it }\n"
    "        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }\n",
    "command-router",
)

if "SCORING LEGACY + UNIVERSAL PROVIDER NODES" not in text:
    start = text.find("    private fun resilientCortexResponse(")
    end = text.find("    private fun cortexResponse(", start)
    if start < 0 or end < 0:
        raise RuntimeError("Provider mesh resilient routing function anchor missing")
    resilient = '''    private fun resilientCortexResponse(
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
            return universalProviderResponse(input, onToken).also {
                JarvisOperationBus.clear("UNIVERSAL PROVIDER RESPONSE VERIFIED")
            }
        }
        JarvisOperationBus.clear("NO COGNITIVE ROUTE COMPLETED")
        return configurationRequiredResponse()
    }

'''
    text = text[:start] + resilient + text[end:]

if "private fun universalProviderResponse(" not in text:
    marker = "    private fun deepSeekResponse(input: String, onToken: ((String) -> Unit)?): BrainResponse {\n"
    if marker not in text:
        raise RuntimeError("Provider mesh universal response insertion anchor missing")
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
    text = text.replace(marker, universal_method + marker, 1)

replace_required(
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
    "knowledge-fallback",
)

replace_required(
    "        val integrations = integrationStore.load()\n"
    "        val voice = SecureVoiceRegistry(appContext).load()\n",
    "        val integrations = integrationStore.load()\n"
    "        val universal = universalProviderStore.load()\n"
    "        val voice = SecureVoiceRegistry(appContext).load()\n",
    "status-registry",
)

replace_required(
    "            appendLine(\"GMAIL // ${integrations.gmailHealthLabel()}\")\n"
    "            appendLine(\"VOICE MESH // ${voice.healthLabel()}\")\n",
    "            appendLine(\"GMAIL // ${integrations.gmailHealthLabel()}\")\n"
    "            appendLine(\"UNIVERSAL PROVIDERS // ${universal.profiles.count { it.isConfigured() }}/${ProviderMeshCatalog.presets.size} CONFIGURED // DEFAULT ${ProviderMeshCatalog.byId(universal.defaultAiProviderId)?.label ?: \"NOT SELECTED\"}\")\n"
    "            appendLine(\"VOICE MESH // ${voice.healthLabel()}\")\n",
    "status-display",
)

replace_required(
    "            spoken = \"The operations core is active. The cortex mesh has ${configured.size} configured nodes, with $online currently online. Voice has ${voice.configuredKeys().size} encrypted routes, Sir.\",\n",
    "            spoken = \"The operations core is active. The legacy cortex has ${configured.size} configured nodes, the universal mesh has ${universal.profiles.count { it.isConfigured() }} configured providers, and voice has ${voice.configuredKeys().size} encrypted routes, Sir.\",\n",
    "status-speech",
)

old_spoken = '        spoken = "The owner identity core and encrypted local memory are active, but no reasoning route is configured. Say configure APIs to add Gemini, Groq, or DeepSeek, Sir.",\n'
new_spoken = '        spoken = "The owner identity core and encrypted local memory are active, but no reasoning route is configured. Say open API setup, or name OpenAI, Anthropic, Gemini, DeepSeek, Groq, Mistral, xAI, Cohere, Perplexity, or OpenRouter, Sir.",\n'
replace_required(old_spoken, new_spoken, "configuration-help-speech")

old_display = '        display = "${OwnerIdentityCore.VERSION} // ACTIVE\\nCOGNITIVE ROUTE CONFIGURATION REQUIRED\\nSAY // CONFIGURE APIS",\n'
new_display = '        display = "${OwnerIdentityCore.VERSION} // ACTIVE\\nUNIVERSAL PROVIDER ROUTE REQUIRED\\nSAY // OPEN API SETUP",\n'
replace_required(old_display, new_display, "configuration-help-display")

BRAIN.write_text(text, encoding="utf-8")
print("Universal provider mesh integrated into JarvisBrain.kt")
