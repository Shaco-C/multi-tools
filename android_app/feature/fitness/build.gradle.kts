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
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("user.home", gradle.gradleUserHomeDir.absolutePath)
            it.systemProperty("java.io.tmpdir", gradle.gradleUserHomeDir.resolve("robolectric/tmp").apply { mkdirs() }.absolutePath)
            it.systemProperty("maven.repo.local", gradle.gradleUserHomeDir.resolve("robolectric/repository").absolutePath)
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("fitness.screenshot.dir", layout.buildDirectory.dir("reports/fitness-ui").get().asFile.absolutePath)
        }
    }
}

// Exercise the same storage contracts on the host and on Android devices.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("test").kotlin.srcDir("src/androidTest/kotlin/com/pockettoolbox/feature/fitness/data")
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
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

val fitnessContentDirectory = rootProject.file("../content/fitness/android")
val fitnessContentProperties = Properties().apply {
    fitnessContentDirectory.resolve("content.properties").inputStream().use(::load)
}
val fitnessReleaseProperties = Properties().apply {
    fitnessContentDirectory.resolve("release.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val fitnessArchive = rootProject.file("../" + fitnessContentProperties.getProperty("sourceArchive"))
val verifyFitnessSourceArchive = tasks.register("verifyFitnessSourceArchive") {
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
abstract class FitnessAssetsTask : Sync() {
    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty
    init { into(assetsDirectory) }
}
val prepareFitnessAssets = tasks.register<FitnessAssetsTask>("prepareFitnessAssets") {
    dependsOn(verifyFitnessSourceArchive)
    assetsDirectory.set(layout.buildDirectory.dir("generated/fitnessAssets"))
    from(fitnessContentDirectory) { include("catalog.json", "content.properties"); into("fitness") }
    from(rootProject.file("../content/fitness/pilot/media")) { into("fitness/media") }
    from(rootProject.file("../content/fitness/pilot/muscles")) { into("fitness/muscles") }
    from(rootProject.file("../content/fitness/sources/free-exercise-db/LICENSE.source.md")) { into("fitness/attributions") }
    from(rootProject.file("../content/fitness/pilot/README.md")) { into("fitness/attributions"); rename { "pilot-sources.md" } }
    from({ zipTree(fitnessArchive) }) {
        val prefix = "free-exercise-db-${fitnessContentProperties.getProperty("sourceRevision")}/exercises/"
        include("$prefix**/*.jpg", "$prefix**/*.jpeg", "$prefix**/*.png", "$prefix**/*.webp")
        // Copy details already include the destination prefix at this point.
        eachFile {
            require(path.contains(prefix)) { "Unexpected source image path: $path" }
            path = "fitness/source-media/" + path.substringAfter(prefix)
        }
        includeEmptyDirs = false
        into("fitness/source-media")
    }
}
val fitnessReleaseContentApproved = fitnessContentProperties.getProperty("releaseApproved") == "true"
val fitnessReleaseEnabled = fitnessReleaseProperties.getProperty("enabled") == "true"
android.defaultConfig.buildConfigField("boolean", "FITNESS_RELEASE_ENABLED", fitnessReleaseEnabled.toString())
android.defaultConfig.buildConfigField("boolean", "FITNESS_CONTENT_REVIEWED", fitnessReleaseContentApproved.toString())
androidComponents.beforeVariants(androidComponents.selector().withBuildType("release")) {
    (it as com.android.build.api.variant.HasHostTestsBuilder).hostTests
        .getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = true
}
androidComponents.onVariants { variant ->
    if (variant.buildType == "debug" || fitnessReleaseEnabled) {
        variant.sources.assets?.addGeneratedSourceDirectory(prepareFitnessAssets, FitnessAssetsTask::assetsDirectory)
    }
}

// AGP 9.4's local-test resource APK can stay up-to-date after generated asset
// paths change. Track these inputs explicitly and wait for their merged output.
tasks.matching { it.name in listOf("packageDebugUnitTestForUnitTest", "packageReleaseUnitTestForUnitTest") }.configureEach {
    dependsOn(if (name.contains("Release")) "mergeReleaseUnitTestAssets" else "mergeDebugUnitTestAssets")
    inputs.dir(prepareFitnessAssets.flatMap { it.assetsDirectory })
        .withPropertyName("fitnessGeneratedAssets").withPathSensitivity(PathSensitivity.RELATIVE)
}

val verifyFitnessReleaseContent = tasks.register("verifyFitnessReleaseContent") {
    inputs.file(fitnessContentDirectory.resolve("content.properties"))
    inputs.file(fitnessContentDirectory.resolve("release.properties"))
    onlyIf { fitnessReleaseEnabled }
    doLast {
        require(fitnessReleaseProperties.getProperty("contentVersion") == fitnessContentProperties.getProperty("version") &&
            fitnessReleaseProperties.getProperty("sourceRevision") == fitnessContentProperties.getProperty("sourceRevision") &&
            fitnessReleaseProperties.getProperty("sourceArchiveSha256") == fitnessContentProperties.getProperty("sourceArchiveSha256") &&
            fitnessContentProperties.getProperty("exerciseCount").toInt() > 0 &&
            fitnessContentProperties.getProperty("sourceCount").toInt() > 0) {
            "正式发布的健身内容与锁定版本不一致，请核对release.properties及来源包。"
        }
        if (fitnessReleaseContentApproved) require(
            fitnessContentProperties.getProperty("reviewedCount").toInt() == fitnessContentProperties.getProperty("exerciseCount").toInt() &&
            fitnessContentProperties.getProperty("sourceProcessedCount").toInt() == fitnessContentProperties.getProperty("sourceCount").toInt()) {
            "教学审校数量不完整，不能标记为已专业审校。"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyFitnessReleaseContent) }
