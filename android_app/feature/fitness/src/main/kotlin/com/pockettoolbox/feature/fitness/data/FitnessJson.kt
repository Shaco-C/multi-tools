package com.pockettoolbox.feature.fitness.data

import kotlinx.serialization.json.Json

internal val FitnessJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
}
