package com.example.videograb

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
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

private fun svg(name: String, d: String): ImageVector = icon(name) {
    addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
}

val SkipNextIcon: ImageVector by lazy { svg("skipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }
val SkipPreviousIcon: ImageVector by lazy { svg("skipPrev", "M6,6h2v12H6zM9.5,12l8.5,6V6z") }
val RewindIcon: ImageVector by lazy { svg("rewind", "M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z") }
val ForwardIcon: ImageVector by lazy { svg("forward", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z") }
val FullscreenIcon: ImageVector by lazy {
    svg("fullscreen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z")
}
val FullscreenExitIcon: ImageVector by lazy {
    svg("fullscreenExit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z")
}
val PipIcon: ImageVector by lazy {
    svg("pip", "M19,7h-8v6h8V7zM21,3H3C1.9,3 1,3.9 1,5v14c0,1.1 0.9,1.98 2,1.98h18c1.1,0 2,-0.88 2,-1.98V5c0,-1.1 -0.9,-2 -2,-2zM21,19.01H3V4.98h18v14.03z")
}
val RepeatIcon: ImageVector by lazy {
    svg("repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
}
val RepeatOneIcon: ImageVector by lazy {
    svg("repeatOne", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15V9h-1l-2,1v1h1.5v4H13z")
}
