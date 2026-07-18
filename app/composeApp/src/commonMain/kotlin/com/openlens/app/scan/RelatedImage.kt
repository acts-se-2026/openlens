package com.openlens.app.scan

/**
 * One "similar image" result. [imageUrl] is the (thumbnail) preview to display; [sourceUrl] is the
 * page it came from (opened when the thumbnail is tapped). [title] is the page title, used as the
 * image's content description.
 */
data class RelatedImage(
    val title: String,
    val imageUrl: String,
    val sourceUrl: String,
)
