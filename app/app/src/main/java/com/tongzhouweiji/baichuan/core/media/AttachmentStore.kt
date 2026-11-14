package com.tongzhouweiji.baichuan.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class StoredAttachment(
    val relativePath: String,
    val mime: String,
    val size: Long,
    val name: String,
    val type: String,
)

/**
 * 附件存储：外部选择的图片/文件在导入时复制进应用私有目录，
 * 数据库只保存相对路径，避免 content URI 授权过期或重启后失效。
 */
class AttachmentStore(private val context: Context) {

    private val root: File by lazy {
        File(context.filesDir, "attachments").apply { mkdirs() }
    }

    /**
     * 把数据库中的相对路径解析为应用私有附件目录内的文件。
     * 恢复的备份可能包含被篡改的路径，必须做边界校验，避免读取私有目录外的文件。
     */
    private fun resolve(relativePath: String): File {
        require(relativePath.isNotBlank()) { "附件路径为空" }
        require(!relativePath.startsWith("/")) { "附件路径不能是绝对路径" }
        require(!relativePath.contains("..")) { "附件路径不能包含 .." }
        require(!relativePath.contains('\\')) { "附件路径不能包含反斜杠" }
        val base = root.canonicalFile
        val target = File(context.filesDir, relativePath).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "附件路径越界：$relativePath" }
        return target
    }

    fun fileOf(relativePath: String): File = resolve(relativePath)

    fun storeImage(uri: Uri, conversationId: Long): StoredAttachment? {
        val bytes = compressImage(uri) ?: return null
        val relative = "attachments/$conversationId/${UUID.randomUUID()}.jpg"
        val target = File(context.filesDir, relative)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { it.write(bytes) }
        return StoredAttachment(
            relativePath = relative,
            mime = "image/jpeg",
            size = bytes.size.toLong(),
            name = displayName(uri) ?: "image.jpg",
            type = "image",
        )
    }

    fun storeFile(uri: Uri, conversationId: Long): StoredAttachment? = runCatching {
        val name = displayName(uri) ?: "file"
        val extension = name.substringAfterLast('.', "").take(8)
        val relative = "attachments/$conversationId/${UUID.randomUUID()}" + if (extension.isNotEmpty()) ".$extension" else ""
        val target = File(context.filesDir, relative)
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(target).use { target ->
                source.copyTo(target)
            }
        }
        StoredAttachment(
            relativePath = relative,
            mime = context.contentResolver.getType(uri) ?: "application/octet-stream",
            size = target.length(),
            name = name,
            type = "file",
        )
    }.getOrNull()

    fun readBase64(relativePath: String): String? = runCatching {
        val file = resolve(relativePath)
        if (!file.exists()) return null
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    private fun compressImage(uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val scaled = scaleDown(bitmap, 1280)
        val output = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        output.toByteArray()
    }.getOrNull()

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val max = maxOf(width, height)
        if (max <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / max
        return Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
