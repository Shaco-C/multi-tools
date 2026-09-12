package com.pockettoolbox.feature.electricity.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "electricity_share",
    foreignKeys = [
        ForeignKey(
            entity = ElectricityBillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["bill_id"]),
        Index(value = ["bill_id", "position"], unique = true),
        Index(value = ["bill_id", "meter_key"], unique = true),
    ],
)
data class ElectricityShareEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "bill_id")
    val billId: Long = 0,
    @ColumnInfo(name = "meter_key")
    val meterKey: String,
    val position: Int,
    val label: String,
    @ColumnInfo(name = "is_owner")
    val isOwner: Boolean,
    @ColumnInfo(name = "previous_reading")
    val previousReading: String,
    @ColumnInfo(name = "current_reading")
    val currentReading: String,
    val usage: String,
    @ColumnInfo(name = "allocated_amount_cents")
    val allocatedAmountCents: Long,
)
