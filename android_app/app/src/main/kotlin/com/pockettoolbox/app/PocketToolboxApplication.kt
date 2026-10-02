package com.pockettoolbox.app

import android.app.Application
import com.pockettoolbox.feature.electricity.ElectricityFeatureDependencies
import com.pockettoolbox.feature.diet.DietFeatureDependencies

class PocketToolboxApplication : Application() {
    val dietDependencies: DietFeatureDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DietFeatureDependencies.create(this)
    }
    val electricityDependencies: ElectricityFeatureDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ElectricityFeatureDependencies.create(this)
    }
}
