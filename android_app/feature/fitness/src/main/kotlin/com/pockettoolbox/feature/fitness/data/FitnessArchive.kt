package com.pockettoolbox.feature.fitness.data

import com.pockettoolbox.feature.fitness.domain.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class FitnessArchiveMedia(val path: String, val bytes: Long, val sha256: String)

@Serializable
data class FitnessArchiveManifest(
    val format: String = "pocket-toolbox-fitness-archive",
    val version: Int = 1,
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val contentVersion: String,
    val plans: Int,
    val events: Int,
    val metadataSha256: String,
    val media: List<FitnessArchiveMedia>,
)

data class InspectedFitnessArchive(
    val snapshot: FitnessSnapshot,
    val manifest: FitnessArchiveManifest,
    val images: Map<String, File>,
    val directory: File,
) : AutoCloseable {
    override fun close() { directory.deleteRecursively() }
}

/** 不依赖Android的ZIP结构检查，可在本机独立JVM验证。图片解码由平台适配器提供。 */
object FitnessArchive {
    const val MAX_IMAGE_BYTES = 3L * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    const val MAX_ENTRIES = 10000
    const val MAX_MANIFEST_BYTES = 512L * 1024
    private val imagePath = Regex("personal/[a-f0-9]{64}\\.(jpg|png|webp)")
    private val zipImagePath = Regex("media/[a-f0-9]{64}\\.(jpg|png|webp)")

    fun referencedImages(data: FitnessData): Set<String> = data.personalExercises.flatMap { it.images }.map { it.path }.toSet()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun write(snapshot: FitnessSnapshot, images: Map<String, File>, contentVersion: String, at: Long,
        output: OutputStream, work: File, validateImage: (File) -> Unit) {
        require(work.isDirectory || work.mkdirs()) { "无法创建备份工作目录" }
        require(images.keys == referencedImages(snapshot.data)) { "个人图片引用不完整" }
        val metadata = File(work, "metadata-${fitnessId()}.json")
        try {
            metadata.outputStream().use { FitnessBackupCodec.write(snapshot, it) }
            val media = images.map { (path, file) ->
                require(imagePath.matches(path) && file.isFile && file.length() in 1..MAX_IMAGE_BYTES) { "个人图片丢失或超限：$path" }
                validateImage(file)
                val hash = sha256(file)
                require(path.substringAfter('/').substringBefore('.') == hash) { "个人图片内容已损坏：$path" }
                FitnessArchiveMedia(path, file.length(), hash)
            }
            require(media.size + 2 <= MAX_ENTRIES && metadata.length() + media.sumOf { it.bytes } <= MAX_ARCHIVE_BYTES) { "备份超出当前容量范围" }
            val manifest = FitnessArchiveManifest(exportedAt = at, contentVersion = contentVersion, plans = snapshot.data.plans.size,
                events = snapshot.events.size, metadataSha256 = sha256(metadata), media = media)
            val manifestBytes = FitnessJson.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "备份媒体清单超过支持容量" }
            ZipOutputStream(FitnessLimitedOutput(output, MAX_ARCHIVE_BYTES)).use { zip ->
                fun entry(name: String, writer: (OutputStream) -> Unit) { zip.putNextEntry(ZipEntry(name)); writer(zip); zip.closeEntry() }
                entry("manifest.json") { it.write(manifestBytes) }
                entry("snapshot.json") { target -> metadata.inputStream().use { it.copyTo(target) } }
                for ((path, file) in images) entry("media/" + path.substringAfter('/')) { target -> file.inputStream().use { it.copyTo(target) } }
            }
        } finally { metadata.delete() }
    }

    fun inspect(input: InputStream, work: File, validateImage: (File) -> Unit): InspectedFitnessArchive {
        val directory = File(work, "fitness-import-${fitnessId()}")
        require(directory.mkdirs()) { "无法创建恢复暂存目录" }
        try {
            val names = mutableSetOf<String>()
            var expanded = 0L
            ZipInputStream(FitnessLimitedInput(input, MAX_ARCHIVE_BYTES)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && (entry.name == "manifest.json" || entry.name == "snapshot.json" || zipImagePath.matches(entry.name))) { "备份包含非法文件路径" }
                    require(names.add(entry.name) && names.size <= MAX_ENTRIES) { "备份存在重复或过多文件" }
                    val limit = when (entry.name) { "manifest.json" -> MAX_MANIFEST_BYTES; "snapshot.json" -> FitnessBackupCodec.MAX_METADATA_BYTES; else -> MAX_IMAGE_BYTES }
                    val file = File(directory, entry.name)
                    require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) { "备份路径越界" }
                    require(file.parentFile.isDirectory || file.parentFile.mkdirs()) { "暂存目录不可写" }
                    var size = 0L
                    file.outputStream().use { target ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            size += count; expanded += count
                            require(size <= limit && expanded <= MAX_ARCHIVE_BYTES) { "备份展开后超过支持容量" }
                            target.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            require("manifest.json" in names && "snapshot.json" in names) { "备份清单或业务资料缺失" }
            val manifest = FitnessJson.decodeFromString<FitnessArchiveManifest>(File(directory, "manifest.json").readText())
            require(manifest.format == "pocket-toolbox-fitness-archive" && manifest.version == 1 && manifest.schemaVersion == 1) { "备份工具或版本不支持" }
            val metadata = File(directory, "snapshot.json")
            require(sha256(metadata) == manifest.metadataSha256) { "业务资料校验失败" }
            val snapshot = metadata.inputStream().use(FitnessBackupCodec::read)
            require(snapshot.data.plans.size == manifest.plans && snapshot.events.size == manifest.events) { "清单与业务数量不一致" }
            val required = referencedImages(snapshot.data)
            require(manifest.media.map { it.path }.toSet() == required && manifest.media.size == required.size) { "个人图片清单不完整或重复" }
            require(names == setOf("manifest.json", "snapshot.json") + required.map { "media/" + it.substringAfter('/') }) { "备份缺图或包含未引用媒体" }
            val images = manifest.media.associate { media ->
                require(imagePath.matches(media.path)) { "个人图片路径无效" }
                val file = File(directory, "media/" + media.path.substringAfter('/'))
                require(file.isFile && file.length() == media.bytes && media.bytes in 1..MAX_IMAGE_BYTES && sha256(file) == media.sha256 &&
                    media.path.substringAfter('/').substringBefore('.') == media.sha256) { "个人图片校验失败" }
                validateImage(file)
                media.path to file
            }
            return InspectedFitnessArchive(snapshot, manifest, images, directory)
        } catch (error: Exception) { directory.deleteRecursively(); throw error }
    }
}
