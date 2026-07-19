package com.openlens.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.scan.RelatedImage
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmapSampled

/** Lifecycle of the lazily-fetched similar-images grid. [Ready] with an empty list = "none found". */
sealed interface SimilarImagesState {
    data object Loading : SimilarImagesState
    data class Ready(val images: List<RelatedImage>) : SimilarImagesState
    data object Failed : SimilarImagesState
}

private val GRID_SPACING = 10.dp
private val CORNER = 14.dp
private const val PLACEHOLDER_ASPECT = 1f

// Tiles render at roughly half the sheet width; decoding to at most this edge keeps each bitmap small
// (≈a few MB max) so ~8 concurrent thumbnails can't blow the heap on a big source image.
private const val THUMBNAIL_MAX_DIMENSION_PX = 1024

/**
 * "Similar images" section for the detail card: a two-column masonry of thumbnails. It lays out inline
 * (no scroll of its own) so it scrolls as one with the rest of the card. While the search is in flight
 * it shows a skeleton of spinning placeholders; once [SimilarImagesState.Ready], each tile fetches +
 * decodes its own bytes via [loadImage] (spinning until it lands, then fading in at its natural aspect
 * ratio). Tapping a tile opens its source page.
 */
@Composable
fun SimilarImagesSection(
    state: SimilarImagesState,
    loadImage: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = "Similar images",
            color = OpenLensColors.TextLo,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (state) {
            SimilarImagesState.Loading -> SkeletonGrid()
            SimilarImagesState.Failed -> PlaceholderText("Couldn't load similar images")
            is SimilarImagesState.Ready ->
                if (state.images.isEmpty()) {
                    PlaceholderText("No similar images found")
                } else {
                    MasonryGrid(images = state.images, loadImage = loadImage)
                }
        }
    }
}

/**
 * Two side-by-side columns, images dealt into them alternately (stable, index-based). Each tile sizes
 * to its own aspect ratio, giving the staggered "masonry" look while remaining a plain, measurable
 * layout — so the whole thing nests happily inside the card's single vertical scroll.
 */
@Composable
private fun MasonryGrid(
    images: List<RelatedImage>,
    loadImage: suspend (String) -> ByteArray?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
        for (columnIndex in 0..1) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            ) {
                images.filterIndexed { index, _ -> index % 2 == columnIndex }
                    .forEach { image -> SimilarImageTile(image = image, loadImage = loadImage) }
            }
        }
    }
}

/**
 * One masonry tile. Fetches its bytes off the (third-party) host and decodes them; shows a spinner
 * until the image is ready, then fades it in at its own aspect ratio (clamped so nothing gets absurdly
 * tall or wide). Opens the source page when tapped.
 */
@Composable
private fun SimilarImageTile(
    image: RelatedImage,
    loadImage: suspend (String) -> ByteArray?,
) {
    val uriHandler = LocalUriHandler.current
    var bitmap by remember(image.imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(image.imageUrl) { mutableStateOf(false) }

    LaunchedEffect(image.imageUrl) {
        val bytes = loadImage(image.imageUrl)
        val decoded = bytes?.let { decodeImageBitmapSampled(it, THUMBNAIL_MAX_DIMENSION_PX) }
        if (decoded == null) failed = true else bitmap = decoded
    }

    val bmp = bitmap
    // Height follows the decoded aspect ratio (masonry); a square placeholder holds the slot meanwhile.
    val aspect = bmp?.let { (it.width.toFloat() / it.height.toFloat()).coerceIn(0.6f, 1.5f) }
        ?: PLACEHOLDER_ASPECT
    val fade by animateFloatAsState(
        targetValue = if (bmp != null) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "tile-fade",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(CORNER))
            .background(OpenLensColors.Bg)
            .clickable(enabled = bmp != null) { uriHandler.openUri(image.sourceUrl) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = image.title.ifBlank { "Similar image" },
                contentScale = ContentScale.Crop,
                alpha = fade,
                modifier = Modifier.fillMaxSize(),
            )
            failed -> Text("!", color = OpenLensColors.TextLo, fontSize = 20.sp)
            else -> Spinner()
        }
    }
}

/** A 2×2 grid of spinning placeholder tiles shown while the search runs (it's quick, so this is brief). */
@Composable
private fun SkeletonGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(PLACEHOLDER_ASPECT)
                            .clip(RoundedCornerShape(CORNER))
                            .background(OpenLensColors.Bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Spinner()
                    }
                }
            }
        }
    }
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        color = OpenLensColors.Accent,
        strokeWidth = 2.dp,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun PlaceholderText(text: String) {
    Text(text = text, color = OpenLensColors.TextLo, fontSize = 13.sp)
}
