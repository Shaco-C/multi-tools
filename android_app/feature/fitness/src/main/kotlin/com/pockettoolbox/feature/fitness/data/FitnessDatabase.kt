package com.pockettoolbox.feature.fitness.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FitnessSettingsRow::class, FitnessPlanRow::class, FitnessDayRow::class, FitnessItemRow::class,
    FitnessEventRow::class, FitnessEventPartRow::class, FitnessPartRow::class, FitnessExerciseRow::class, FitnessTemplateRow::class],
    version = 1, exportSchema = true)
abstract class FitnessDatabase : RoomDatabase() {
    abstract fun fitnessDao(): FitnessDao

    companion object {
        const val DATABASE_NAME = "fitness.db"
        fun open(context: Context): FitnessDatabase = Room.databaseBuilder(context.applicationContext, FitnessDatabase::class.java, DATABASE_NAME).build()
    }
}
