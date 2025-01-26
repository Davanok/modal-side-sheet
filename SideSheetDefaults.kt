package com.davanok.dvnkdnd.ui.components.sideSheet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp


fun CornerBasedShape.start() = copy(
    topEnd = CornerSize(0.0.dp),
    bottomEnd = CornerSize(0.0.dp)
)


@Stable
@ExperimentalMaterial3Api
object SideSheetDefaults {
    /** The default shape for Side sheets in a [Hidden] state. */
    val HiddenShape: Shape
        @Composable get() = RectangleShape

    /** The default shape for a Side sheets in [PartiallyExpanded] and [Expanded] states. */
    val ExpandedShape: Shape
        @Composable get() = MaterialTheme.shapes.extraLarge.start()

    /** The default container color for a Side sheet. */
    val ContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    /** The default elevation for a Side sheet. */
    val Elevation = 1.0.dp

    /** The default color of the scrim overlay for background content. */
    val ScrimColor: Color
        @Composable get() = MaterialTheme.colorScheme.scrim.copy(0.32f)

    /** The default peek height used by [SideSheetScaffold]. */
    val SheetPeekHeight = 56.dp

    /** The default max width used by [ModalSideSheet] and [SideSheetScaffold] */
    val SheetMaxWidth = 400.dp

    /** Default insets to be used and consumed by the [ModalSideSheet]'s content. */
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.End)
}
