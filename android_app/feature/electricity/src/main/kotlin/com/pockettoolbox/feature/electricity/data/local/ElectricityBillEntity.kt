package com.pockettoolbox.feature.electricity.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "electricity_bill",
    indices = [
        Index(value = ["billing_month"], unique = true),
    ],
)
data class ElectricityBillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "billing_month")
    val billingMonth: String,
    @ColumnInfo(name = "total_amount_cents")
    val totalAmountCents: Long,
    @ColumnInfo(name = "total_usage")
    val totalUsage: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val note: String? = null,
)
