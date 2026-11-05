package com.freeclaudecode.android.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerStatus(
    val isRunning: Boolean = false,
    val status: String = "Stopped",
    val serverUrl: String = "http://127.0.0.1:8080",
    val adminUrl: String = "http://127.0.0.1:8080/admin",
    val instanceId: String? = null,
    val providerStatus: List<ProviderStatus> = emptyList(),
    val cachedModels: Map<String, String> = emptyMap(),
    val error: String? = null
)

@Serializable
data class ProviderStatus(
    val id: String,
    val name: String,
    val status: String,
    val models: List<String> = emptyList()
)

@Serializable
data class AdminConfig(
    val providers: List<ProviderConfig> = emptyList(),
    val modelConfig: ModelConfig? = null
)

@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val models: List<String> = emptyList()
)

@Serializable
data class ModelConfig(
    val fable: String? = null,
    val opus: String? = null,
    val sonnet: String? = null,
    val haiku: String? = null
)

sealed class ServerState {
    data class Stopped(val error: String? = null) : ServerState()
    data class Starting(val message: String = "Starting...") : ServerState()
    data class Running(
        val serverUrl: String,
        val adminUrl: String,
        val instanceId: String? = null
    ) : ServerState()
    data class Error(val message: String) : ServerState()
}