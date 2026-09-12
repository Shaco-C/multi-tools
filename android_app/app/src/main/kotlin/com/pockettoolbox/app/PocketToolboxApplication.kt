package com.pockettoolbox.app

import android.app.Application
import com.pockettoolbox.feature.electricity.ElectricityFeatureDependencies

class PocketToolboxApplication : Application() {
    val electricityDependencies: ElectricityFeatureDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ElectricityFeatureDependencies.create(this)
    }
}
