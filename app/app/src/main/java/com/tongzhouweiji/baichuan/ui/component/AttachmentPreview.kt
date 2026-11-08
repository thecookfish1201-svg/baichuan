package com.tongzhouweiji.baichuan.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tongzhouweiji.baichuan.core.db.entity.AttachmentEntity
import com.tongzhouweiji.baichuan.core.media.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 消息附件预览：图片显示缩略图，文件显示文件名。 */
@Composable
fun AttachmentPreview(attachment: AttachmentEntity, store: AttachmentStore) {
    if (attachment.type == "image") {
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, attachment.id) {
            value = withContext(Dispatchers.IO) {
                val file = store.fileOf(attachment.uri)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        }
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 180.dp, height = 180.dp),
            )
        } else {
            Text(
                text = "[图片] ${attachment.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text(
            text = "[文件] ${attachment.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
