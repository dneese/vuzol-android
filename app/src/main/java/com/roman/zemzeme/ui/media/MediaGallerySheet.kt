package com.roman.zemzeme.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roman.zemzeme.R
import com.roman.zemzeme.core.ui.component.sheet.ZemzemeBottomSheet
import com.roman.zemzeme.model.ZemzemeMessage
import com.roman.zemzeme.model.ZemzemeMessageType
import com.roman.zemzeme.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gallery of the photos received in the current conversation.
 *
 * Tapping a thumbnail opens the fullscreen viewer with the whole list, so the
 * user can swipe through everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGallerySheet(
    isPresented: Boolean,
    messages: List<ZemzemeMessage>,
    onDismiss: () -> Unit
) {
    if (!isPresented) return

    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val imagePaths = remember(messages) {
        messages.filter { it.type == ZemzemeMessageType.Image || it.type == ZemzemeMessageType.Media }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }
    }

    var viewerIndex by remember { mutableStateOf(-1) }

    ZemzemeBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.media_gallery_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            if (imagePaths.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.media_gallery_empty),
                        fontSize = 14.sp,
                        fontFamily = NunitoFontFamily,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(imagePaths) { path ->
                        GalleryThumbnail(
                            path = path,
                            onClick = { viewerIndex = imagePaths.indexOf(path) }
                        )
                    }
                }
            }
        }
    }

    if (viewerIndex >= 0 && imagePaths.isNotEmpty()) {
        com.roman.zemzeme.ui.media.FullScreenImageViewer(
            imagePaths = imagePaths,
            initialIndex = viewerIndex,
            onClose = { viewerIndex = -1 }
        )
    }
}

@Composable
private fun GalleryThumbnail(path: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return@withContext null
                var sample = 1
                while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 320) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
