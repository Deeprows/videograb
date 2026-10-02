package com.example.videograb

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

val DownloadIcon: ImageVector by lazy {
    icon("download") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 9f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineTo(9f)
            verticalLineToRelative(6f); horizontalLineTo(5f); lineToRelative(7f, 7f); lineToRelative(7f, -7f); close()
            moveTo(5f, 18f); verticalLineToRelative(2f); horizontalLineToRelative(14f); verticalLineToRelative(-2f)
            horizontalLineTo(5f); close()
        }
    }
}

val PauseIcon: ImageVector by lazy {
    icon("pause") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); verticalLineToRelative(14f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineToRelative(-4f); close()
        }
    }
}

val GlobeIcon: ImageVector by lazy {
    icon("globe") {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
            moveTo(12f, 2.5f)
            arcTo(9.5f, 9.5f, 0f, true, true, 12f, 21.5f)
            arcTo(9.5f, 9.5f, 0f, true, true, 12f, 2.5f)
            close()
            moveTo(12f, 2.5f)
            arcTo(4.5f, 9.5f, 0f, true, true, 12f, 21.5f)
            arcTo(4.5f, 9.5f, 0f, true, true, 12f, 2.5f)
            close()
            moveTo(2.5f, 12f); lineTo(21.5f, 12f)
        }
    }
}
