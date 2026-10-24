package com.pockettoolbox.feature.fitness.domain

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@Serializable
data class FitnessBackupDocument(
    val format: String = "pocket-toolbox-fitness-data",
    val schemaVersion: Int = 1,
    val snapshot: FitnessSnapshot,
)

/** 媒体作为ZIP独立文件，不把图片Base64塞进业务JSON。 */
object FitnessBackupCodec {
    const val MAX_METADATA_BYTES = 128L * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }

    @OptIn(ExperimentalSerializationApi::class)
    fun write(snapshot: FitnessSnapshot, output: OutputStream) {
        FitnessValidation.snapshot(snapshot)
        json.encodeToStream(FitnessBackupDocument(snapshot = snapshot), FitnessLimitedOutput(output, MAX_METADATA_BYTES))
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun read(input: InputStream): FitnessSnapshot {
        val document = json.decodeFromStream<FitnessBackupDocument>(FitnessLimitedInput(input, MAX_METADATA_BYTES))
        require(document.format == "pocket-toolbox-fitness-data" && document.schemaVersion == 1) { "健身备份格式或版本不支持" }
        FitnessValidation.snapshot(document.snapshot)
        return document.snapshot
    }
}

class FitnessLimitedInput(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = `in`.read().also { if (it >= 0) accept(1) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also { if (it > 0) accept(it) }
    private fun accept(value: Int) { count += value; require(count <= maximum) { "备份超过支持的容量范围，原资料未改变" } }
}

class FitnessLimitedOutput(output: OutputStream, private val maximum: Long) : FilterOutputStream(output) {
    private var count = 0L
    override fun write(value: Int) { accept(1); out.write(value) }
    override fun write(buffer: ByteArray, offset: Int, length: Int) { accept(length); out.write(buffer, offset, length) }
    private fun accept(value: Int) { count += value; require(count <= maximum) { "备份超过支持的容量范围，文件未完成" } }
}
