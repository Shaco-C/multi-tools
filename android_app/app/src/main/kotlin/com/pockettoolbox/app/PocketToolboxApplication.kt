package com.pockettoolbox.app

import android.app.Application
import com.pockettoolbox.feature.electricity.ElectricityFeatureDependencies
import com.pockettoolbox.feature.diet.DietFeatureDependencies
import com.pockettoolbox.feature.fitness.FitnessFeatureDependencies

class PocketToolboxApplication : Application() {
    val fitnessDependencies: FitnessFeatureDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FitnessFeatureDependencies.create(this)
    }
    val dietDependencies: DietFeatureDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DietFeatureDependencies.create(this)
    }
    val electricityDependencies: ElectricityFeatureDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ElectricityFeatureDependencies.create(this)
    }
}
