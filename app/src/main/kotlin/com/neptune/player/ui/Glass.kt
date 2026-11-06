package com.neptune.player.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ---------------------------------------------------------------------------------------------- */
/* Shape                                                                                          */
/* ---------------------------------------------------------------------------------------------- */

/** Continuous-curvature rounded rectangle (squircle) — the silhouette of every glass surface. */
class SquircleShape(private val radius: Dp, private val smoothing: Float = 0.62f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceIn(0f, min(size.width, size.height) / 2f)
        val path = Path()
        if (r <= 0.5f) {
            path.addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            return Outline.Generic(path)
        }
        val s = r * smoothing
        val w = size.width
        val h = size.height
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        path.cubicTo(w - r + s, 0f, w, r - s, w, r)
        path.lineTo(w, h - r)
        path.cubicTo(w, h - r + s, w - r + s, h, w - r, h)
        path.lineTo(r, h)
        path.cubicTo(r - s, h, 0f, h - r + s, 0f, h - r)
        path.lineTo(0f, r)
        path.cubicTo(0f, r - s, r - s, 0f, r, 0f)
        path.close()
        return Outline.Generic(path)
    }
}

fun squircle(radius: Dp): Shape = SquircleShape(radius)

/** Outline → Path (Compose only ships the reverse). */
internal fun Outline.toNePath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

/* ---------------------------------------------------------------------------------------------- */
/* Backdrop: animated aurora sampled in root space so glass truly refracts what is behind it       */
/* ---------------------------------------------------------------------------------------------- */

@Immutable
class GlassBackdrop(
    val palette: List<Color>,
    val phase: State<Float>,
    val intensity: Float,
    val rootSize: State<Size>,
)

val LocalGlass = staticCompositionLocalOf {
    GlassBackdrop(
        palette = listOf(Color(0xFF2B3A8F), Color(0xFF6D5BFF), Color(0xFFFF5FA2)),
        phase = mutableStateOf(0f),
        intensity = 1f,
        rootSize = mutableStateOf(Size.Zero),
    )
}

@Immutable
data class GlassStyle(
    val blur: Dp = 24.dp,
    val tint: Float = 0.14f,
    val radius: Dp = 26.dp,
    val grain: Boolean = true,
    val glow: Boolean = true,
    val accent: Color = Accent.ICE.color,
)

val LocalGlassStyle = compositionLocalOf { GlassStyle() }

private data class Blob(
    val x: Float,
    val y: Float,
    val r: Float,
    val colorIndex: Int,
    val alpha: Float,
    val speed: Float,
    val amp: Float,
)

private val blobs = listOf(
    Blob(0.16f, 0.10f, 0.90f, 0, 0.62f, 0.62f, 0.07f),
    Blob(0.94f, 0.20f, 0.72f, 1, 0.55f, 0.93f, 0.06f),
    Blob(0.50f, 0.82f, 1.05f, 2, 0.48f, 0.74f, 0.05f),
    Blob(0.86f, 0.92f, 0.62f, 1, 0.40f, 1.31f, 0.05f),
    Blob(0.28f, 0.55f, 0.55f, 2, 0.30f, 1.07f, 0.06f),
)

/** Paints the aurora in *root* coordinates. Glass panels reuse it verbatim → real refraction. */
fun DrawScope.drawAurora(size: Size, palette: List<Color>, phase: Float, intensity: Float) {
    drawRect(Ne.Void)
    val twoPi = 6.2831855f
    val p = phase * twoPi
    val maxSide = maxOf(size.width, size.height)
    blobs.forEachIndexed { i, b ->
        val cx = (b.x + b.amp * sin(p * b.speed + i * 1.7f)) * size.width
        val cy = (b.y + b.amp * cos(p * b.speed * 0.83f + i * 2.1f)) * size.height
        val radius = maxSide * b.r
        val c = palette[b.colorIndex % palette.size]
        val a = (b.alpha * intensity).coerceIn(0f, 1f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to c.copy(alpha = a),
                0.45f to c.copy(alpha = a * 0.55f),
                1f to c.copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
    // depth: vignette so type stays readable
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.34f),
            0.35f to Color.Transparent,
            0.72f to Color.Black.copy(alpha = 0.28f),
            1f to Color.Black.copy(alpha = 0.62f),
        )
    )
}

/* ---------------------------------------------------------------------------------------------- */
/* Grain                                                                                          */
/* ---------------------------------------------------------------------------------------------- */

private val grainBitmap: ImageBitmap by lazy {
    val n = 128
    val px = IntArray(n * n)
    val rnd = kotlin.random.Random(23)
    for (i in px.indices) {
        val v = rnd.nextInt(256)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

internal val grainBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated))
}

/* ---------------------------------------------------------------------------------------------- */
/* The glass                                                                                      */
/* ---------------------------------------------------------------------------------------------- */

@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    blurRadius: Dp = LocalGlassStyle.current.blur,
    tint: Float = LocalGlassStyle.current.tint,
    glow: Boolean = LocalGlassStyle.current.glow,
    highlight: Float = 1f,
    accentEdge: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val style = LocalGlassStyle.current
    val backdrop = LocalGlass.current
    val resolved: Shape = shape ?: remember(style.radius) { SquircleShape(style.radius) }
    val origin = remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier
            .onGloballyPositioned { coords ->
                val p = coords.positionInRoot()
                val o = origin.value
                if (kotlin.math.abs(p.x - o.x) > 0.5f || kotlin.math.abs(p.y - o.y) > 0.5f) origin.value = p
            }
            .then(
                if (glow) Modifier.shadow(
                    elevation = 20.dp,
                    shape = resolved,
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                ) else Modifier
            )
            .drawWithContent {
                drawContent()
                // lens rim: specular light along the top-left edge
                val path = resolved.createOutline(size, layoutDirection, this).toNePath()
                drawPath(
                    path,
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.46f * highlight),
                        0.30f to Color.White.copy(alpha = 0.13f * highlight),
                        0.62f to Color.White.copy(alpha = 0.05f),
                        1f to Color.White.copy(alpha = 0.16f * highlight),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    style = Stroke(width = 1.15.dp.toPx()),
                )
                drawPath(path, color = Color.White.copy(alpha = 0.045f), style = Stroke(width = 2.6.dp.toPx()))
            }
    ) {
        Box(Modifier.matchParentSize().clip(resolved)) {
            // 1 — backdrop sample (blurred)
            Canvas(
                Modifier
                    .matchParentSize()
                    .then(if (canBlur && blurRadius > 0.5.dp) Modifier.blur(blurRadius, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else Modifier)
            ) {
                val root = backdrop.rootSize.value
                val o = origin.value
                if (root.width > 1f) {
                    translate(-o.x, -o.y) {
                        drawAurora(root, backdrop.palette, backdrop.phase.value, backdrop.intensity * 1.12f)
                    }
                } else {
                    drawRect(Ne.Ink)
                }
            }

            // 2 — material tint + light lift
            Canvas(Modifier.matchParentSize()) {
                drawRect(Color.White.copy(alpha = (tint * 0.42f).coerceIn(0f, 0.5f)))
                drawRect(Ne.Ink.copy(alpha = 0.16f))
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.13f * highlight),
                        0.45f to Color.White.copy(alpha = 0.02f),
                        1f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width * 1.15f, size.height * 1.25f),
                    )
                )
                if (accentEdge) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to style.accent.copy(alpha = 0.16f),
                        )
                    )
                }
            }

            // 3 — grain
            if (style.grain) {
                Canvas(Modifier.matchParentSize()) {
                    drawRect(brush = grainBrush, alpha = 0.05f, blendMode = BlendMode.Overlay)
                }
            }

            content()
        }
    }
}

/* ---------------------------------------------------------------------------------------------- */
/* Controls                                                                                       */
/* ---------------------------------------------------------------------------------------------- */

@Composable
fun rememberHaptics(): () -> Unit {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) {
        { feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Composable
fun GlassAction(
    icon: NeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 20.dp,
    color: Color = Ne.Text,
    active: Boolean = false,
    iconWeight: Float = 1.85f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.6f),
        label = "press",
    )
    val accent = LocalGlassStyle.current.accent
    Box(modifier.size(size)) {
        LiquidGlass(
            modifier = Modifier.matchParentSize(),
            shape = CircleShape,
            highlight = if (pressed) 2.1f else 1f,
            accentEdge = active,
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            NeIconView(
                icon = icon,
                size = iconSize,
                color = if (active) accent else color,
                weight = iconWeight,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = 100.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    LiquidGlass(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
            else Modifier
        ),
        shape = CircleShape,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Capsule switch — the only toggle language in Neptune. */
@Composable
fun PillToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalGlassStyle.current.accent,
) {
    val haptic = rememberHaptics()
    val thumb by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.72f),
        label = "toggle",
    )
    val trackW = 48.dp
    val trackH = 28.dp
    val knob = 22.dp
    Box(
        modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic()
                onChange(!checked)
            }
            .drawWithContent {
                drawContent()
                val k = knob.toPx()
                val pad = (trackH.toPx() - k) / 2f
                val travel = size.width - k - pad * 2f
                val cx = pad + travel * thumb + k / 2f
                // knob glow
                if (checked) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to accent.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                            center = Offset(cx, size.height / 2f),
                            radius = k * 1.5f,
                        ),
                        radius = k * 1.5f,
                        center = Offset(cx, size.height / 2f),
                    )
                }
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Color.White, Color(0xFFD8DEE9)),
                        start = Offset(cx - k / 2f, 0f),
                        end = Offset(cx + k / 2f, size.height),
                    ),
                    radius = k / 2f,
                    center = Offset(cx, size.height / 2f),
                )
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.height / 2f
            drawRoundRect(
                brush = if (checked) {
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.55f)))
                } else {
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.07f)))
                },
                cornerRadius = CornerRadius(r),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (checked) 0.30f else 0.14f),
                cornerRadius = CornerRadius(r),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** Custom slider — glass groove, accent plasma fill, glowing thumb. */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalGlassStyle.current.accent,
    trackHeight: Dp = 8.dp,
    thumb: Dp = 15.dp,
    showThumb: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { onValueChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onValueChange((it.x / size.width).coerceIn(0f, 1f)) },
                ) { change, _ ->
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = trackHeight.toPx()
            val cy = size.height / 2f
            val rad = CornerRadius(h / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(0f, cy - h / 2f),
                size = Size(size.width, h),
                cornerRadius = rad,
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(0f, cy - h / 2f),
                size = Size(size.width, h),
                cornerRadius = rad,
                style = Stroke(width = 1.dp.toPx()),
            )
            val fw = (size.width * value).coerceIn(0f, size.width)
            if (fw > 0.5f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.72f), accent, Color.White.copy(alpha = 0.92f)),
                        startX = 0f,
                        endX = fw.coerceAtLeast(1f),
                    ),
                    topLeft = Offset(0f, cy - h / 2f),
                    size = Size(fw, h),
                    cornerRadius = rad,
                )
            }
            if (showThumb) {
                val tr = thumb.toPx() / 2f
                val tx = fw
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to accent.copy(alpha = 0.50f),
                        1f to Color.Transparent,
                        center = Offset(tx, cy),
                        radius = tr * 2.6f,
                    ),
                    radius = tr * 2.6f,
                    center = Offset(tx, cy),
                )
                drawCircle(Color.White, tr, Offset(tx, cy))
                drawCircle(accent, tr * 0.42f, Offset(tx, cy))
            }
        }
    }
}

/** Pill segmented control with a sliding selection lens. */
@Composable
fun GlassSegments(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptics()
    val accent = LocalGlassStyle.current.accent
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.055f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic()
                        onSelect(index)
                    }
                    .background(if (active) Color.White.copy(alpha = 0.13f) else Color.Transparent)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = label,
                    color = if (active) Ne.Text else Ne.Faint,
                    fontFamily = Manrope,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 0.1.sp,
                    maxLines = 1,
                )
                if (active) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRoundRect(
                            color = accent.copy(alpha = 0.30f),
                            cornerRadius = CornerRadius(size.height / 2f),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

/** Small animated equaliser shown next to the playing track. */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = LocalGlassStyle.current.accent,
    playing: Boolean = true,
    bars: Int = 4,
    maxHeight: Dp = 14.dp,
) {
    val time = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
    val t by time.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "eqT",
    )
    Canvas(modifier.size(maxHeight, maxHeight)) {
        val w = size.width / (bars * 2f - 1f)
        for (i in 0 until bars) {
            val h = if (playing) {
                val phase = i * 1.7f + t * 6.2831855f * (0.8f + i * 0.18f)
                (0.30f + 0.70f * (0.5f + 0.5f * sin(phase))) * size.height
            } else {
                size.height * 0.28f
            }
            drawRoundRect(
                color = color.copy(alpha = if (playing) 0.95f else 0.45f),
                topLeft = Offset(i * w * 2f, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}
