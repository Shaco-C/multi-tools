package com.pockettoolbox.feature.electricity.data.local

data class ElectricityBillWriteSnapshot(
    val bill: ElectricityBillEntity,
    val shares: List<ElectricityShareEntity>,
)
