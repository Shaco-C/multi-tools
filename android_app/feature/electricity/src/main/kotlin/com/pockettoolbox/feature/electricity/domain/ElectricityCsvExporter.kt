package com.pockettoolbox.feature.electricity.domain

interface ElectricityCsvExporter {
    suspend fun createCsv(): String
}
