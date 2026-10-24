package com.pockettoolbox.feature.fitness.data

import android.graphics.BitmapFactory
import com.pockettoolbox.feature.fitness.domain.FitnessImage
import com.pockettoolbox.feature.fitness.domain.FitnessLimitedInput
import com.pockettoolbox.feature.fitness.domain.fitnessId
import java.io.File
import java.io.InputStream

class FitnessMediaStore(private val root: File) {
    private val generationPattern = Regex("initial|gen-[a-f0-9-]{36}")

    fun directory(generation: String): File {
        require(generationPattern.matches(generation)) { "媒体代次无效" }
        return File(root, generation)
    }

    fun resolve(generation: String, path: String): File {
        require(path.matches(Regex("personal/[a-f0-9]{64}\\.(jpg|png|webp)"))) { "个人图片引用无效" }
        return File(directory(generation), path.substringAfter('/'))
    }

    fun validateImage(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) { "图片无法读取或像素过大" }
        val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> throw IllegalArgumentException("只支持JPEG、PNG、WebP图片") }
        val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 512) }
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "图片解码失败" }
        decoded.recycle()
        return extension
    }

    fun save(generation: String, input: InputStream): FitnessImage {
        require(root.isDirectory || root.mkdirs()) { "个人媒体目录不可写" }
        val temporary = File(root, "image-${fitnessId()}.tmp")
        try {
            input.use { source -> temporary.outputStream().use { target -> FitnessLimitedInput(source, FitnessArchive.MAX_IMAGE_BYTES).copyTo(target) } }
            val extension = validateImage(temporary)
            val hash = FitnessArchive.sha256(temporary)
            val target = resolve(generation, "personal/$hash.$extension")
            require(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "个人媒体目录不可写" }
            if (target.exists()) require(FitnessArchive.sha256(target) == hash) { "已有个人图片损坏" }
            else require(temporary.renameTo(target)) { "个人图片保存失败" }
            return FitnessImage("personal/$hash.$extension")
        } finally { temporary.delete() }
    }

    fun prepare(images: Map<String, File>): String {
        val generation = "gen-${fitnessId()}"
        val folder = directory(generation)
        require(folder.mkdirs()) { "无法准备恢复媒体" }
        try {
            for ((path, source) in images) {
                val target = resolve(generation, path)
                source.copyTo(target)
                require(FitnessArchive.sha256(source) == FitnessArchive.sha256(target)) { "恢复媒体复制不完整" }
            }
            return generation
        } catch (error: Exception) { folder.deleteRecursively(); throw error }
    }

    fun discard(generation: String) { directory(generation).deleteRecursively() }
    fun cleanupExcept(generation: String) {
        require(generationPattern.matches(generation))
        root.listFiles().orEmpty().filter { it.isDirectory && generationPattern.matches(it.name) && it.name != generation }.forEach { it.deleteRecursively() }
    }
}
