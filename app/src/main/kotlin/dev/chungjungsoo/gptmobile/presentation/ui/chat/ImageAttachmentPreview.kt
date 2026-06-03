package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chungjungsoo.gptmobile.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberLocalImageBitmap(filePath: String): ImageBitmap? = produceState<ImageBitmap?>(null, filePath) {
    value = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(filePath)?.asImageBitmap()
    }
}.value

@Composable
internal fun AttachmentImagePreviewDialog(
    filePath: String,
    onDismissRequest: () -> Unit
) {
    val imageBitmap = rememberLocalImageBitmap(filePath)
    val containerSize = LocalWindowInfo.current.containerSize
    val screenHeight = with(LocalDensity.current) { containerSize.height.toDp() }
    val maxDialogHeight = (screenHeight - 96.dp).coerceAtLeast(160.dp)
    val maxImageHeight = (screenHeight - 112.dp).coerceAtLeast(144.dp)

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = maxDialogHeight)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = File(filePath).name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxImageHeight)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_image),
                            contentDescription = File(filePath).name,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
