package com.pockettoolbox.feature.fitness.data

import com.pockettoolbox.feature.fitness.domain.*

/** Stable recording definitions for the pinned source library; no teaching approval is implied. */
internal fun FitnessSourceExercise.recordingExercise(revision: String): FitnessExercise {
    val muscles = primaryMuscles.mapNotNull(sourceMuscles::get).distinct()
    val purpose = when (category) {
        "stretching" -> FitnessPurpose.Stretch
        "cardio", "plyometrics" -> FitnessPurpose.Cardio
        else -> FitnessPurpose.Strength
    }
    val equipmentKey = sourceEquipment[equipment] ?: "sourceEquipmentUnknown"
    val unit = if (purpose == FitnessPurpose.Stretch || category == "cardio") FitnessUnit.Seconds else FitnessUnit.Reps
    return FitnessExercise(
        id = "SRC-$id", name = name.take(60), english = name,
        aliases = (muscles.map { it.label } + secondaryMuscles.mapNotNull(sourceMuscles::get).map { it.label } +
            listOfNotNull(equipment?.let(sourceEquipmentNames::get), category?.let(sourceCategories::get))).distinct(),
        purpose = purpose, primaryPart = muscles.firstOrNull() ?: FitnessPart.Full,
        secondaryParts = (muscles.drop(1) + secondaryMuscles.mapNotNull(sourceMuscles::get)).distinct().filter { it != muscles.firstOrNull() },
        unit = unit, equipment = listOf(listOf(equipmentKey)),
        introduction = "${sourceCategories[category] ?: "来源动作"} · ${primaryMuscles.mapNotNull(sourceMuscles::get).distinct().joinToString("、") { it.label }}。保留来源原名、步骤和图片；可直接加入计划，次数/秒数及左右口径可在首次记录前调整。",
        steps = instructions, images = images,
        cues = listOf("器械按来源标注，附加训练凳、支架或固定条件请结合原图确认。"),
        contentRevision = "source-$revision-recording-1",
        references = listOf("https://github.com/yuhonas/free-exercise-db/tree/$revision/exercises/$id"),
    )
}

private val sourceMuscles = mapOf(
    "chest" to FitnessPart.Chest, "middle back" to FitnessPart.Back, "lower back" to FitnessPart.Back,
    "lats" to FitnessPart.Back, "traps" to FitnessPart.Back, "shoulders" to FitnessPart.Shoulder,
    "biceps" to FitnessPart.Biceps, "triceps" to FitnessPart.Triceps, "forearms" to FitnessPart.Forearm,
    "abdominals" to FitnessPart.Core, "glutes" to FitnessPart.Glutes, "hamstrings" to FitnessPart.Legs,
    "quadriceps" to FitnessPart.Legs, "adductors" to FitnessPart.Legs, "abductors" to FitnessPart.Legs,
    "calves" to FitnessPart.Calves, "neck" to FitnessPart.Neck,
)
private val sourceEquipment = mapOf(
    "body only" to "floor", "machine" to "machine", "foam roll" to "foamRoll",
    "kettlebells" to "kettlebell", "dumbbell" to "dumbbellPair", "cable" to "cable",
    "barbell" to "barbell", "bands" to "longBand", "medicine ball" to "medicineBall",
    "exercise ball" to "ball", "e-z curl bar" to "ezBar",
)
private val sourceEquipmentNames = mapOf(
    "body only" to "徒手", "machine" to "训练机", "foam roll" to "泡沫轴", "kettlebells" to "壶铃",
    "dumbbell" to "哑铃", "cable" to "绳索", "barbell" to "杠铃", "bands" to "弹力带",
    "medicine ball" to "药球", "exercise ball" to "健身球", "e-z curl bar" to "曲杆",
)
private val sourceCategories = mapOf(
    "strength" to "力量", "stretching" to "拉伸", "plyometrics" to "爆发体能", "strongman" to "力量体能",
    "powerlifting" to "力量举", "cardio" to "有氧", "olympic weightlifting" to "举重",
)
