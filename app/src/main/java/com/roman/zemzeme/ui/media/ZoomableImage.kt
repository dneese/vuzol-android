package com.roman.zemzeme.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Image with pinch-to-zoom, drag-to-pan and a smooth double-tap zoom.
 *
 * While the image is not zoomed, horizontal gestures are left to the parent so
 * that the pager can swipe between photos. Panning is only enabled once the
 * image is actually zoomed in.
 */
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    onScaleChange: (Float) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val minScale = 1f
    val maxScale = 5f

    // Size of the picture as it is drawn inside the container (ContentScale.Fit)
    val fittedSize = remember(boxSize, bitmap.width, bitmap.height) {
        if (boxSize.width == 0 || boxSize.height == 0 || bitmap.width == 0 || bitmap.height == 0) {
            IntSize.Zero
        } else {
            val factor = min(
                boxSize.width.toFloat() / bitmap.width.toFloat(),
                boxSize.height.toFloat() / bitmap.height.toFloat()
            )
            IntSize((bitmap.width * factor).toInt(), (bitmap.height * factor).toInt())
        }
    }

    fun clampOffset(value: Offset, currentScale: Float): Offset {
        val maxX = fittedSize.width * (currentScale - 1f) / 2f
        val maxY = fittedSize.height * (currentScale - 1f) / 2f
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY)
        )
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        animationJob?.cancel()
        animationJob = scope.launch {
            val startScale = scale
            val startOffset = offset
            val steps = 14
            repeat(steps) { index ->
                val t = (index + 1f) / steps
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                scale = startScale + (targetScale - startScale) * eased
                offset = Offset(
                    x = startOffset.x + (targetOffset.x - startOffset.x) * eased,
                    y = startOffset.y + (targetOffset.y - startOffset.y) * eased
                )
                delay(16)
            }
            scale = targetScale
            offset = targetOffset
            onScaleChange(scale)
        }
    }

    // Reset the zoom when the viewer moves to another picture
    LaunchedEffect(resetKey) {
        animationJob?.cancel()
        scale = minScale
        offset = Offset.Zero
        onScaleChange(minScale)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, centroid ->
        animationJob?.cancel()
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(minScale, maxScale)
        if (oldScale <= 0f) return@rememberTransformableState
        val ratio = newScale / oldScale
        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
        // keep the point under the fingers in place
        val anchored = (centroid - center) * (1f - ratio) + offset * ratio + panChange
        scale = newScale
        offset = if (newScale <= minScale + 0.01f) Offset.Zero else clampOffset(anchored, newScale)
        onScaleChange(scale)
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .transformable(
                state = transformState,
                canPan = { scale > minScale + 0.01f },
                lockRotationOnZoomPan = true
            )
            .pointerInput(resetKey) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                        if (scale > minScale + 0.05f) {
                            animateTo(minScale, Offset.Zero)
                        } else {
                            val target = 2.5f
                            val targetOffset = clampOffset((tap - center) * (1f - target), target)
                            animateTo(target, targetOffset)
                        }
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
