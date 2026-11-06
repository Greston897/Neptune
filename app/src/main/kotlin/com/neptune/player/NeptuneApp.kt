package com.neptune.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.GlassBackdrop
import com.neptune.player.ui.GlassStyle
import com.neptune.player.ui.LocalGlass
import com.neptune.player.ui.LocalGlassStyle
import com.neptune.player.ui.Ne
import com.neptune.player.ui.NeptuneMiniPlayer
import com.neptune.player.ui.NeptuneNavBar
import com.neptune.player.ui.Tab
import com.neptune.player.ui.drawAurora
import com.neptune.player.ui.screens.LibraryScreen
import com.neptune.player.ui.screens.NowPlayingScreen
import com.neptune.player.ui.screens.SearchScreen
import com.neptune.player.ui.screens.TuneScreen

@Composable
fun NeptuneApp(state: NeptuneState) {
    val settings = state.settings
    val rootSize = remember { mutableStateOf(Size.Zero) }

    val wavesDuration = if (settings.animations) {
        (54_000f / settings.speed).toInt().coerceIn(8_000, 400_000)
    } else {
        10_000_000
    }
    val transition = rememberInfiniteTransition(label = "neptune")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(wavesDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val rawPalette = state.current.palette
    val spec = tween<androidx.compose.ui.graphics.Color>(900, easing = FastOutSlowInEasing)
    val c0 by animateColorAsState(rawPalette[0], spec, label = "p0")
    val c1 by animateColorAsState(rawPalette[1], spec, label = "p1")
    val c2 by animateColorAsState(rawPalette[2], spec, label = "p2")
    val palette = listOf(c0, c1, c2)

    // transport clock
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) state.tick((now - last) / 1_000_000L)
                last = now
            }
        }
    }

    CompositionLocalProvider(
        LocalGlass provides GlassBackdrop(
            palette = palette,
            phase = phase,
            intensity = settings.bg,
            rootSize = rootSize,
        ),
        LocalGlassStyle provides GlassStyle(
            blur = settings.blur.dp,
            tint = settings.tint,
            radius = settings.radius.dp,
            grain = settings.grain,
            glow = settings.glow,
            accent = settings.accent.color,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ne.Void)
                .onSizeChanged { rootSize.value = Size(it.width.toFloat(), it.height.toFloat()) }
        ) {
            // living backdrop, tinted by the current track
            Canvas(Modifier.fillMaxSize()) {
                drawAurora(size, palette, phase.value, settings.bg)
            }

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = state.tab,
                        transitionSpec = {
                            fadeIn(tween(260, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(170))
                        },
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            Tab.Now -> NowPlayingScreen(state)
                            Tab.Library -> LibraryScreen(state)
                            Tab.Search -> SearchScreen(state)
                            Tab.Tune -> TuneScreen(state)
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = if (settings.floatingNav) 10.dp else 2.dp)
                ) {
                    if (settings.miniPlayer && state.tab != Tab.Now) {
                        NeptuneMiniPlayer(state = state, onOpen = { state.tab = Tab.Now })
                        Spacer(Modifier.height(8.dp))
                    }
                    NeptuneNavBar(
                        tab = state.tab,
                        onSelect = { state.tab = it },
                        labels = settings.labels,
                    )
                }
            }
        }
    }
}
