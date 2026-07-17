package com.openlens.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The official four-colour Google "G", built in code as an [ImageVector] from its SVG path data
 * (48×48 viewport). Done this way so we don't need the composeResources pipeline for one icon —
 * [PathParser] turns each SVG path string into vector nodes we hand to the builder.
 */
val GoogleLogo: ImageVector by lazy {
    fun nodes(d: String) = PathParser().parsePathString(d).toNodes()
    ImageVector.Builder(
        name = "GoogleG",
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = 48f,
        viewportHeight = 48f,
    )
        .addPath(
            pathData = nodes(
                "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 " +
                    "14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            ),
            fill = SolidColor(Color(0xFFEA4335)),
        )
        .addPath(
            pathData = nodes(
                "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 " +
                    "7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            ),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        .addPath(
            pathData = nodes(
                "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 " +
                    "16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            ),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        .addPath(
            pathData = nodes(
                "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 " +
                    "0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
            ),
            fill = SolidColor(Color(0xFF34A853)),
        )
        .build()
}
