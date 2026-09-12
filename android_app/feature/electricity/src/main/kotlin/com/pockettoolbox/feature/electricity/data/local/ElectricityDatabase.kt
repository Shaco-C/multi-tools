package com.pockettoolbox.feature.electricity.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ElectricityBillEntity::class,
        ElectricityShareEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ElectricityDatabase : RoomDatabase() {
    abstract fun electricityBillDao(): ElectricityBillDao

    companion object {
        const val DATABASE_NAME = "electricity.db"

        fun open(context: Context): ElectricityDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ElectricityDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
