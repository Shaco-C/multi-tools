package com.pockettoolbox.feature.electricity.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class ElectricityBillWithShares(
    @Embedded
    val bill: ElectricityBillEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bill_id",
    )
    val shares: List<ElectricityShareEntity>,
) {
    val orderedShares: List<ElectricityShareEntity>
        get() = shares.sortedBy(ElectricityShareEntity::position)
}
