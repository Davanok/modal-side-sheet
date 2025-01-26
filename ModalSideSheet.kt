
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dvnkdnd.composeapp.generated.resources.Res
import dvnkdnd.composeapp.generated.resources.close_drawer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

@Composable
@ExperimentalMaterial3Api
fun ModalSideSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalSideSheetState(),
    sheetMaxWidth: Dp = SideSheetDefaults.SheetMaxWidth,
    shape: Shape = SideSheetDefaults.ExpandedShape,
    containerColor: Color = SideSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = SideSheetDefaults.ScrimColor,
    contentWindowInsets: @Composable () -> WindowInsets = { SideSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    header: @Composable (() -> Unit),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animateToDismiss: () -> Unit = {
        if (sheetState.anchoredDraggableState.confirmValueChange(SideSheetValue.Hidden)) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    onDismissRequest()
                }
            }
        }
    }
    val settleToDismiss: (velocity: Float) -> Unit = {
        scope.launch { sheetState.settle(it) }
            .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }

    val predictiveBackProgress = remember { Animatable(initialValue = 0f) }

    ModalBottomSheetDialog(
        properties = properties,
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        },
        predictiveBackProgress = predictiveBackProgress,
    ) {
        Box(modifier = Modifier.fillMaxSize().imePadding().semantics { isTraversalGroup = true }) {
            Scrim(
                color = scrimColor,
                onDismissRequest = animateToDismiss,
                visible = sheetState.targetValue != SideSheetValue.Hidden,
            )
            ModalSideSheetContent(
                predictiveBackProgress,
                settleToDismiss,
                modifier,
                sheetState,
                sheetMaxWidth,
                shape,
                containerColor,
                contentColor,
                tonalElevation,
                header,
                contentWindowInsets,
                content
            )
        }
    }
    if (sheetState.hasExpandedState) {
        LaunchedEffect(sheetState) { sheetState.show() }
    }
}

@ExperimentalMaterial3Api
enum class SideSheetValue {
    /** The sheet is not visible. */
    Hidden,

    /** The sheet is visible at full width. */
    Expanded
}

@Composable
@ExperimentalMaterial3Api
fun rememberSheetState(
    confirmValueChange: (SideSheetValue) -> Boolean = { true },
    initialValue: SideSheetValue = SideSheetValue.Hidden,
    skipHiddenState: Boolean = false,
): SheetState {
    val density = LocalDensity.current
    return rememberSaveable(
        confirmValueChange, skipHiddenState, saver = SheetState.Saver(
            confirmValueChange = confirmValueChange,
            density = density,
            skipHiddenState = skipHiddenState,
        )
    ) {
        SheetState(
            density,
            initialValue,
            confirmValueChange,
            skipHiddenState,
        )
    }
}

@Composable
@ExperimentalMaterial3Api
fun rememberModalSideSheetState(
    confirmValueChange: (SideSheetValue) -> Boolean = { true },
) = rememberSheetState(
    confirmValueChange = confirmValueChange,
    initialValue = SideSheetValue.Hidden,
)

private val BottomSheetAnimationSpec: AnimationSpec<Float> =
    tween(durationMillis = 300, easing = FastOutSlowInEasing)

@Stable
@OptIn(ExperimentalFoundationApi::class)
@ExperimentalMaterial3Api
class SheetState(
    density: Density,
    initialValue: SideSheetValue = SideSheetValue.Hidden,
    confirmValueChange: (SideSheetValue) -> Boolean = { true },
    val skipHiddenState: Boolean = false,
) {
    init {
        if (skipHiddenState) {
            require(initialValue != SideSheetValue.Hidden) {
                "The initial value must not be set to Hidden if skipHiddenState is set to true."
            }
        }
    }


    val currentValue: SideSheetValue
        get() = anchoredDraggableState.currentValue


    val targetValue: SideSheetValue
        get() = anchoredDraggableState.targetValue


    val isVisible: Boolean
        get() = anchoredDraggableState.currentValue != SideSheetValue.Hidden


    fun requireOffset(): Float = anchoredDraggableState.requireOffset()


    val hasExpandedState: Boolean
        get() = anchoredDraggableState.anchors.hasAnchorFor(SideSheetValue.Expanded)


    suspend fun expand() {
        anchoredDraggableState.animateTo(SideSheetValue.Expanded)
    }


    suspend fun show() {
        animateTo(SideSheetValue.Expanded)
    }


    suspend fun hide() {
        check(!skipHiddenState) {
            "Attempted to animate to hidden when skipHiddenState was enabled. Set skipHiddenState" + " to false to use this function."
        }
        animateTo(SideSheetValue.Hidden)
    }


    suspend fun animateTo(
        targetValue: SideSheetValue, velocity: Float = anchoredDraggableState.lastVelocity
    ) {
        anchoredDraggableState.animateTo(targetValue, velocity)
    }


    suspend fun snapTo(targetValue: SideSheetValue) {
        anchoredDraggableState.snapTo(targetValue)
    }


    suspend fun settle(velocity: Float) {
        anchoredDraggableState.settle(velocity)
    }

    var anchoredDraggableState = AnchoredDraggableState(
        initialValue = initialValue,
        animationSpec = BottomSheetAnimationSpec,
        confirmValueChange = confirmValueChange,
        positionalThreshold = { with(density) { 56.dp.toPx() } },
        velocityThreshold = { with(density) { 125.dp.toPx() } },
    )

    val offset: Float?
        get() = anchoredDraggableState.offset

    companion object {
        fun Saver(
            confirmValueChange: (SideSheetValue) -> Boolean,
            density: Density,
            skipHiddenState: Boolean,
        ) = Saver<SheetState, SideSheetValue>(save = { it.currentValue }, restore = { savedValue ->
            SheetState(
                density,
                savedValue,
                confirmValueChange,
                skipHiddenState,
            )
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ModalBottomSheetDialog(
    onDismissRequest: () -> Unit,
    properties: ModalBottomSheetProperties,
    predictiveBackProgress: Animatable<Float, AnimationVector1D>,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest, properties = DialogProperties(
            dismissOnBackPress = properties.shouldDismissOnBackPress,
            usePlatformDefaultWidth = false,
        ), content = content
    )
}

@Composable
private fun Scrim(color: Color, onDismissRequest: () -> Unit, visible: Boolean) {
    if (color.isSpecified) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = TweenSpec()
        )
        val closeSheet = stringResource(Res.string.close_drawer)
        val dismissSheet = if (visible) {
            Modifier.pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                .semantics(mergeDescendants = true) {
                    traversalIndex = 1f
                    contentDescription = closeSheet
                    onClick {
                        onDismissRequest()
                        true
                    }
                }
        } else {
            Modifier
        }
        Canvas(Modifier.fillMaxSize().then(dismissSheet)) {
            drawRect(color = color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}

private val PredictiveBackMaxScaleXDistance = 48.dp
private val PredictiveBackMaxScaleYDistance = 24.dp
private val PredictiveBackChildTransformOrigin = TransformOrigin(0.5f, 0f)

private fun GraphicsLayerScope.calculatePredictiveBackScaleX(progress: Float): Float {
    val width = size.width
    return if (width.isNaN() || width == 0f) {
        1f
    } else {
        1f - lerp(0f, min(PredictiveBackMaxScaleXDistance.toPx(), width), progress) / width
    }
}

private fun GraphicsLayerScope.calculatePredictiveBackScaleY(progress: Float): Float {
    val height = size.height
    return if (height.isNaN() || height == 0f) {
        1f
    } else {
        1f - lerp(0f, min(PredictiveBackMaxScaleYDistance.toPx(), height), progress) / height
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@ExperimentalMaterial3Api
fun BoxScope.ModalSideSheetContent(
    predictiveBackProgress: Animatable<Float, AnimationVector1D>,
    settleToDismiss: (velocity: Float) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalSideSheetState(),
    sheetMaxWidth: Dp = SideSheetDefaults.SheetMaxWidth,
    shape: Shape = SideSheetDefaults.ExpandedShape,
    containerColor: Color = SideSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = SideSheetDefaults.Elevation,
    header: @Composable (() -> Unit),
    contentWindowInsets: @Composable () -> WindowInsets = { SideSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit
) {
    val bottomSheetPaneTitle = "TODO: BottomSheetPaneTitle"
    Surface(
        modifier = modifier.align(Alignment.CenterEnd).widthIn(max = sheetMaxWidth).fillMaxSize()
            .draggableAnchors(
                sheetState.anchoredDraggableState, Orientation.Horizontal
            ) { sheetSize, constraints ->
                val fullWidth = constraints.maxWidth.toFloat()
                val newAnchors = DraggableAnchors {
                    SideSheetValue.Hidden at fullWidth
                    if (sheetSize.width != 0) {
                        SideSheetValue.Expanded at max(0f, fullWidth - sheetSize.width)
                    }
                }
                val newTarget = when (sheetState.anchoredDraggableState.targetValue) {
                    SideSheetValue.Hidden -> SideSheetValue.Hidden
                    SideSheetValue.Expanded -> {
                        if (newAnchors.hasAnchorFor(SideSheetValue.Expanded))
                            SideSheetValue.Expanded
                        else
                            SideSheetValue.Hidden
                    }
                }
                return@draggableAnchors newAnchors to newTarget
            }.draggable(
                state = sheetState.anchoredDraggableState.draggableState,
                orientation = Orientation.Horizontal,
                enabled = sheetState.isVisible,
                startDragImmediately = sheetState.anchoredDraggableState.isAnimationRunning,
                onDragStopped = { settleToDismiss(it) }
            ).semantics {
                paneTitle = bottomSheetPaneTitle
                traversalIndex = 0f
            }.graphicsLayer {
                val sheetOffset = sheetState.anchoredDraggableState.offset
                val sheetWidth = size.width
                if (!sheetOffset.isNaN() && !sheetWidth.isNaN() && sheetWidth != 0f) {
                    val progress = predictiveBackProgress.value
                    scaleX = calculatePredictiveBackScaleX(progress)
                    scaleY = calculatePredictiveBackScaleY(progress)
                    transformOrigin =
                        TransformOrigin(0.5f, (sheetOffset + sheetWidth) / sheetWidth)
                }
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
    ) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(contentWindowInsets()).graphicsLayer {
            val progress = predictiveBackProgress.value
            val predictiveBackScaleX = calculatePredictiveBackScaleX(progress)
            val predictiveBackScaleY = calculatePredictiveBackScaleY(progress)

            // Preserve the original aspect ratio and alignment of the child content.
            scaleX =
                if (predictiveBackScaleX != 0f) predictiveBackScaleY / predictiveBackScaleX
                else 1f
            transformOrigin = PredictiveBackChildTransformOrigin
        }) {
            header()
            content()
        }
    }
}
