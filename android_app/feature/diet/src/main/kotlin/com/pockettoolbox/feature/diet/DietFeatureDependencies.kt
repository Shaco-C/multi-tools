package com.pockettoolbox.feature.diet

import android.content.Context
import com.pockettoolbox.feature.diet.data.DietDatabase
import com.pockettoolbox.feature.diet.data.RoomDietRepository

class DietFeatureDependencies private constructor(val repository: RoomDietRepository) {
    companion object {
        fun create(context: Context) = DietFeatureDependencies(RoomDietRepository(DietDatabase.open(context)))
    }
}
