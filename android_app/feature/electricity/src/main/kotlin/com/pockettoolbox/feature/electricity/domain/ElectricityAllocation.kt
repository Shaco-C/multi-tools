package com.pockettoolbox.feature.electricity.domain

import com.pockettoolbox.core.common.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

data class MeterReadingInput(
    val id: String,
    val label: String,
    val isOwner: Boolean,
    val previousReading: BigDecimal,
    val currentReading: BigDecimal,
)

data class MeterAllocation(
    val id: String,
    val label: String,
    val isOwner: Boolean,
    val previousReading: BigDecimal,
    val currentReading: BigDecimal,
    val usage: BigDecimal,
    val ratio: BigDecimal,
    val amount: Money,
)

data class ElectricityAllocation(
    val totalAmount: Money,
    val totalUsage: BigDecimal,
    val shares: List<MeterAllocation>,
) {
    val ownerShare: MeterAllocation
        get() = shares.single { it.isOwner }
}

class ElectricityAllocationCalculator {
    fun calculate(
        totalAmount: Money,
        meters: List<MeterReadingInput>,
    ): ElectricityAllocation {
        require(meters.isNotEmpty()) { "至少需要一户电表" }
        require(meters.size <= MAX_HOUSEHOLDS) { "最多支持 $MAX_HOUSEHOLDS 户" }
        require(meters.count { it.isOwner } == 1) { "必须且只能有一户标记为本人" }
        require(meters.map { it.id }.distinct().size == meters.size) { "电表标识不能重复" }

        val usages = meters.map { meter ->
            require(meter.label.isNotBlank()) { "电表名称不能为空" }
            require(meter.previousReading.signum() >= 0) { "上期读数不能为负数" }
            require(meter.currentReading.signum() >= 0) { "本期读数不能为负数" }
            require(meter.currentReading >= meter.previousReading) {
                "${meter.label}的本期读数不能小于上期读数"
            }
            meter.currentReading.subtract(meter.previousReading)
        }

        val totalUsage = usages.fold(BigDecimal.ZERO, BigDecimal::add)
        require(totalUsage.signum() > 0) { "总用量为 0，无法按比例分摊" }

        // 先向下取整到“分”，再按小数余数从大到小补齐尾差。
        // 这样每户金额均为整数分，并且合计始终严格等于总电费。
        data class Draft(
            val index: Int,
            val floorCents: Long,
            val remainder: BigDecimal,
        )

        val drafts = usages.mapIndexed { index, usage ->
            val exactCents = BigDecimal.valueOf(totalAmount.cents)
                .multiply(usage)
                .divide(totalUsage, INTERNAL_SCALE, RoundingMode.DOWN)
            val floorCents = exactCents.setScale(0, RoundingMode.DOWN).longValueExact()
            Draft(
                index = index,
                floorCents = floorCents,
                remainder = exactCents.subtract(BigDecimal.valueOf(floorCents)),
            )
        }

        val allocatedCents = drafts.sumOf { it.floorCents }
        val extraCentIndexes = drafts
            .sortedWith(compareByDescending<Draft> { it.remainder }.thenBy { it.index })
            .take((totalAmount.cents - allocatedCents).toInt())
            .mapTo(mutableSetOf()) { it.index }

        val shares = meters.mapIndexed { index, meter ->
            val cents = drafts[index].floorCents + if (index in extraCentIndexes) 1 else 0
            MeterAllocation(
                id = meter.id,
                label = meter.label,
                isOwner = meter.isOwner,
                previousReading = meter.previousReading,
                currentReading = meter.currentReading,
                usage = usages[index],
                ratio = usages[index].divide(totalUsage, RATIO_SCALE, RoundingMode.HALF_UP),
                amount = Money.ofCents(cents),
            )
        }

        check(shares.sumOf { it.amount.cents } == totalAmount.cents) {
            "分摊金额之和必须等于总金额"
        }

        return ElectricityAllocation(
            totalAmount = totalAmount,
            totalUsage = totalUsage,
            shares = shares,
        )
    }

    private companion object {
        const val MAX_HOUSEHOLDS = 20
        const val INTERNAL_SCALE = 24
        const val RATIO_SCALE = 8
    }
}
