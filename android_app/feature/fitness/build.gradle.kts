import java.util.Properties
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pockettoolbox.feature.fitness"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions.unitTests.isIncludeAndroidResources = true
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidsvg)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

val fitnessContentDirectory = rootProject.file("../content/fitness/android")
val fitnessContentProperties = Properties().apply {
    fitnessContentDirectory.resolve("content.properties").inputStream().use(::load)
}
val fitnessArchive = rootProject.file("../" + fitnessContentProperties.getProperty("sourceArchive"))
val verifyFitnessSourceArchive by tasks.registering {
    inputs.file(fitnessArchive)
    inputs.file(fitnessContentDirectory.resolve("content.properties"))
    val stamp = layout.buildDirectory.file("fitness/source-archive.ok")
    outputs.file(stamp)
    doLast {
        require(fitnessArchive.isFile) { "请携带content/fitness/source-archives中的完整来源ZIP，再构建健身模块" }
        val digest = MessageDigest.getInstance("SHA-256")
        fitnessArchive.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == fitnessContentProperties.getProperty("sourceArchiveSha256")) { "健身来源ZIP校验失败，请重新携带原包" }
        stamp.get().asFile.apply { parentFile.mkdirs(); writeText(actual) }
    }
}
val generatedFitnessAssets = layout.buildDirectory.dir("generated/fitnessAssets")
val prepareFitnessAssets by tasks.registering(Sync::class) {
    dependsOn(verifyFitnessSourceArchive)
    into(generatedFitnessAssets)
    from(fitnessContentDirectory) { include("catalog.json", "content.properties"); into("fitness") }
    from(rootProject.file("../content/fitness/pilot/media")) { into("fitness/media") }
    from(rootProject.file("../content/fitness/pilot/muscles")) { into("fitness/muscles") }
    from(rootProject.file("../content/fitness/sources/free-exercise-db/LICENSE.source.md")) { into("fitness/attributions") }
    from(rootProject.file("../content/fitness/pilot/README.md")) { into("fitness/attributions"); rename { "pilot-sources.md" } }
    from({ zipTree(fitnessArchive) }) {
        val prefix = "free-exercise-db-${fitnessContentProperties.getProperty("sourceRevision")}/exercises/"
        include("$prefix**/*.jpg", "$prefix**/*.jpeg", "$prefix**/*.png", "$prefix**/*.webp")
        eachFile { path = path.removePrefix(prefix) }
        includeEmptyDirs = false
        into("fitness/source-media")
    }
}
val fitnessReleaseContentApproved = fitnessContentProperties.getProperty("releaseApproved") == "true"
android.defaultConfig.buildConfigField("boolean", "FITNESS_RELEASE_CONTENT", fitnessReleaseContentApproved.toString())
android.sourceSets.getByName("debug").assets.srcDir(generatedFitnessAssets)
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(prepareFitnessAssets) }
if (fitnessReleaseContentApproved) {
    android.sourceSets.getByName("release").assets.srcDir(generatedFitnessAssets)
    tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(prepareFitnessAssets) }
}

val verifyFitnessReleaseContent by tasks.registering {
    inputs.file(fitnessContentDirectory.resolve("content.properties"))
    onlyIf { fitnessReleaseContentApproved }
    doLast {
        require(fitnessContentProperties.getProperty("releaseApproved") == "true" &&
            fitnessContentProperties.getProperty("reviewedCount").toInt() == fitnessContentProperties.getProperty("exerciseCount").toInt() &&
            fitnessContentProperties.getProperty("exerciseCount").toInt() > 0 &&
            fitnessContentProperties.getProperty("sourceProcessedCount").toInt() == fitnessContentProperties.getProperty("sourceCount").toInt()) {
            "健身内容仍为开发/待审状态，不能打入正式release；请完成全量映射、中文和逐项审核。debug可用于内部验证。"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyFitnessReleaseContent) }
