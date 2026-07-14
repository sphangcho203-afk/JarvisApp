package com.seongja.jarvis

import java.util.Locale

class CortexMeshException(message: String) : Exception(message)

class CortexMeshClient(private val store: SecureCortexRegistry) {
    private val transport = CortexProviderTransport()

    fun ask(userInput: String, memoryContext: String): CortexMeshResult {
        val registry = store.load()
        val task = CortexTaskClassifier.classify(userInput)
        val now = System.currentTimeMillis()
        val configured = registry.profiles.filter { it.isConfigured() }

        if (configured.isEmpty()) {
            throw CortexMeshException("No Gemini or Groq cortex node is configured.")
        }

        val eligible = orderedProfiles(configured, task, now)
        if (eligible.isEmpty()) {
            val soonest = configured.minByOrNull { it.cooldownUntilMs }
            val seconds = soonest?.let {
                ((it.cooldownUntilMs - now) / 1_000L).coerceAtLeast(1L)
            } ?: 1L
            throw CortexMeshException(
                "Every configured cortex node is cooling down. Retry in about $seconds seconds."
            )
        }

        val attempts = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val blockedProviders = mutableSetOf<CortexProvider>()
        val deadlineMs = System.currentTimeMillis() +
            JarvisRuntimeConfig.TOTAL_REQUEST_BUDGET_MS
        var networkAttempts = 0

        profileLoop@ for (profile in eligible) {
            if (profile.provider in blockedProviders) continue
            if (System.currentTimeMillis() >= deadlineMs) break
            if (networkAttempts >= JarvisRuntimeConfig.MAX_NETWORK_ATTEMPTS) break

            val systemEnvelope = OwnerIdentityCore.systemEnvelope(
                editablePrompt = registry.systemPrompt,
                taskDirective = JarvisDirective.instructionFor(userInput, task),
                memoryContext = memoryContext
            )
            val models = CortexModelCatalog.candidates(profile, task)
                .take(JarvisRuntimeConfig.MAX_MODELS_PER_PROFILE)
            var finalFailure: CortexProviderFailure? = null
            var failureRecorded = false

            for (model in models) {
                if (System.currentTimeMillis() >= deadlineMs) break@profileLoop
                if (networkAttempts >= JarvisRuntimeConfig.MAX_NETWORK_ATTEMPTS) {
                    break@profileLoop
                }

                networkAttempts++
                attempts += "${profile.label}:$model"
                try {
                    val response = transport.request(
                        profile = profile,
                        model = model,
                        systemEnvelope = systemEnvelope,
                        userInput = userInput,
                        temperature = JarvisRuntimeConfig.temperatureFor(task),
                        maxOutputTokens = JarvisRuntimeConfig.tokenBudgetFor(task),
                        deadlineMs = deadlineMs
                    )
                    markSuccess(profile, response)
                    return CortexMeshResult(
                        reply = response.reply,
                        profileId = profile.id,
                        profileLabel = profile.label,
                        provider = profile.provider,
                        model = response.model,
                        statusCode = response.statusCode,
                        elapsedMs = response.elapsedMs,
                        attempts = attempts.toList(),
                        task = task
                    )
                } catch (error: CortexProviderFailure) {
                    finalFailure = error
                    failures += failureLabel(profile, error)
                    when (error.kind) {
                        CortexFailureKind.MODEL -> {
                            // The key may be valid while this model is unavailable
                            // for the project. Try the next verified model on it.
                            continue
                        }
                        CortexFailureKind.RATE_LIMIT -> {
                            if (
                                profile.provider.quotaScope ==
                                CortexQuotaScope.ORGANIZATION
                            ) {
                                val until = System.currentTimeMillis() +
                                    error.retryAfterMs.coerceAtLeast(
                                        JarvisRuntimeConfig.MIN_GROQ_ORG_COOLDOWN_MS
                                    )
                                store.applyProviderCooldown(
                                    provider = profile.provider,
                                    triggeringProfileId = profile.id,
                                    cooldownUntilMs = until,
                                    statusCode = error.statusCode,
                                    error = error.message.orEmpty()
                                )
                                blockedProviders += profile.provider
                                failureRecorded = true
                            } else {
                                markFailure(profile, error)
                                failureRecorded = true
                            }
                            break
                        }
                        CortexFailureKind.AUTH -> {
                            markFailure(profile, error)
                            failureRecorded = true
                            break
                        }
                        CortexFailureKind.SERVER,
                        CortexFailureKind.NETWORK,
                        CortexFailureKind.OTHER -> break
                    }
                }
            }

            if (!failureRecorded && finalFailure != null) {
                markFailure(profile, finalFailure)
            }
        }

        val summary = failures
            .distinct()
            .takeLast(8)
            .joinToString(" | ")
            .take(900)
        throw CortexMeshException(
            if (summary.isBlank()) {
                "No cortex node completed the request within the time budget."
            } else {
                "All eligible model routes failed. $summary"
            }
        )
    }

    fun testProfile(profileId: String): CortexMeshResult {
        val registry = store.load()
        val profile = registry.profiles.firstOrNull { it.id == profileId }
            ?: throw CortexMeshException("Unknown cortex profile: $profileId")
        if (!profile.isConfigured()) {
            throw CortexMeshException("${profile.label} is not fully configured.")
        }

        val deadlineMs = System.currentTimeMillis() +
            JarvisRuntimeConfig.DIAGNOSTIC_BUDGET_MS
        val attempts = mutableListOf<String>()
        var lastFailure: CortexProviderFailure? = null

        CortexModelCatalog.candidates(profile, CortexTask.FAST)
            .take(JarvisRuntimeConfig.MAX_DIAGNOSTIC_MODELS)
            .forEach { model ->
                attempts += "${profile.label}:$model"
                try {
                    val response = transport.request(
                        profile = profile,
                        model = model,
                        systemEnvelope =
                            "Connection diagnostic. Reply with exactly: CORTEX NODE ONLINE",
                        userInput =
                            "Reply with exactly: CORTEX NODE ONLINE",
                        temperature = JarvisRuntimeConfig.DIAGNOSTIC_TEMPERATURE,
                        maxOutputTokens = 32,
                        deadlineMs = deadlineMs
                    )
                    markSuccess(profile, response)
                    return CortexMeshResult(
                        reply = response.reply,
                        profileId = profile.id,
                        profileLabel = profile.label,
                        provider = profile.provider,
                        model = response.model,
                        statusCode = response.statusCode,
                        elapsedMs = response.elapsedMs,
                        attempts = attempts.toList(),
                        task = CortexTask.FAST
                    )
                } catch (error: CortexProviderFailure) {
                    lastFailure = error
                    if (error.kind == CortexFailureKind.MODEL) return@forEach
                    if (
                        error.kind == CortexFailureKind.RATE_LIMIT &&
                        profile.provider.quotaScope == CortexQuotaScope.ORGANIZATION
                    ) {
                        store.applyProviderCooldown(
                            provider = profile.provider,
                            triggeringProfileId = profile.id,
                            cooldownUntilMs = System.currentTimeMillis() +
                                error.retryAfterMs.coerceAtLeast(
                                    JarvisRuntimeConfig.MIN_GROQ_ORG_COOLDOWN_MS
                                ),
                            statusCode = error.statusCode,
                            error = error.message.orEmpty()
                        )
                    } else {
                        markFailure(profile, error)
                    }
                    throw CortexMeshException(
                        "${profile.label}: ${error.message ?: error.kind.name}"
                    )
                }
            }

        lastFailure?.let { markFailure(profile, it) }
        throw CortexMeshException(
            "${profile.label}: no verified model accepted this key. " +
                (lastFailure?.message ?: "No model response")
        )
    }

    private fun orderedProfiles(
        configured: List<CortexProfile>,
        task: CortexTask,
        nowMs: Long
    ): List<CortexProfile> {
        val providerOrder = when (task) {
            CortexTask.FAST,
            CortexTask.CODING ->
                listOf(CortexProvider.GROQ, CortexProvider.GEMINI)
            CortexTask.GENERAL,
            CortexTask.REASONING ->
                listOf(CortexProvider.GEMINI, CortexProvider.GROQ)
        }

        val queues = providerOrder.associateWith { provider ->
            configured
                .filter { it.provider == provider && !it.isCoolingDown(nowMs) }
                .sortedByDescending { CortexMath.score(it, task, nowMs) }
                .toMutableList()
        }
        val output = mutableListOf<CortexProfile>()
        while (queues.values.any { it.isNotEmpty() }) {
            providerOrder.forEach { provider ->
                val queue = queues[provider] ?: return@forEach
                if (queue.isNotEmpty()) output += queue.removeAt(0)
            }
        }
        return output
    }

    private fun markSuccess(
        profile: CortexProfile,
        response: CortexProviderResponse
    ) {
        store.updateProfile(
            profile.copy(
                successes = profile.successes + 1,
                failureStreak = 0,
                requestCount = profile.requestCount + 1,
                lastLatencyMs = response.elapsedMs,
                lastUsedAtMs = System.currentTimeMillis(),
                cooldownUntilMs = 0L,
                lastStatusCode = response.statusCode,
                lastError = ""
            )
        )
    }

    private fun markFailure(
        profile: CortexProfile,
        error: CortexProviderFailure
    ) {
        val now = System.currentTimeMillis()
        val disableKey = error.kind == CortexFailureKind.AUTH
        val cooldown = when (error.kind) {
            CortexFailureKind.AUTH -> 0L
            CortexFailureKind.RATE_LIMIT ->
                error.retryAfterMs.coerceAtLeast(15_000L)
            CortexFailureKind.MODEL -> 5 * 60_000L
            CortexFailureKind.SERVER ->
                error.retryAfterMs.coerceAtLeast(30_000L)
            CortexFailureKind.NETWORK ->
                error.retryAfterMs.coerceAtLeast(15_000L)
            CortexFailureKind.OTHER ->
                error.retryAfterMs.coerceAtLeast(20_000L)
        }
        store.updateProfile(
            profile.copy(
                enabled = if (disableKey) false else profile.enabled,
                failures = profile.failures + 1,
                failureStreak = profile.failureStreak + 1,
                requestCount = profile.requestCount + 1,
                lastUsedAtMs = now,
                cooldownUntilMs = if (cooldown > 0L) now + cooldown else 0L,
                lastStatusCode = error.statusCode,
                lastError = error.message.orEmpty().take(240)
            )
        )
    }

    private fun failureLabel(
        profile: CortexProfile,
        error: CortexProviderFailure
    ): String = buildString {
        append(profile.label)
        append('/')
        append(error.model)
        append(" -> ")
        append(error.kind.name.lowercase(Locale.US))
        if (error.statusCode > 0) append(" HTTP ${error.statusCode}")
        error.message?.takeIf { it.isNotBlank() }?.let {
            append(": ")
            append(it.take(120))
        }
    }
}
