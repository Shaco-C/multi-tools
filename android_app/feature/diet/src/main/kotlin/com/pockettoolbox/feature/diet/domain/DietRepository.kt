package com.pockettoolbox.feature.diet.domain

import kotlinx.coroutines.flow.Flow

interface DietRepository {
    val data: Flow<DietData>
    suspend fun initialize()
    suspend fun snapshot(): DietData
    suspend fun change(transform: (DietData) -> DietData): DietData
    suspend fun replace(data: DietData)
}
