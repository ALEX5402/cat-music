package com.toasterofbread.spmp.ui.layout.nowplaying

import LocalPlayerState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.toasterofbread.spmp.model.MusicTopBarMode
import com.toasterofbread.spmp.model.Settings
import com.toasterofbread.spmp.model.mediaitem.loader.SongLyricsLoader
import com.toasterofbread.spmp.platform.composeScope
import com.toasterofbread.spmp.ui.component.LikeDislikeButton
import com.toasterofbread.spmp.ui.layout.nowplaying.maintab.NOW_PLAYING_MAIN_PADDING
import com.toasterofbread.utils.common.setAlpha
import com.toasterofbread.utils.common.thenIf

@Composable
fun rememberTopBarShouldShowInQueue(mode: MusicTopBarMode): State<Boolean> {
    val player = LocalPlayerState.current
    val show_lyrics_in_queue: Boolean by Settings.KEY_TOPBAR_SHOW_LYRICS_IN_QUEUE.rememberMutableState()
    val show_visualiser_in_queue: Boolean by Settings.KEY_TOPBAR_SHOW_VISUALISER_IN_QUEUE.rememberMutableState()

    return remember(player.status.m_song?.id) {
        val lyrics_state = player.status.m_song?.let { song ->
            SongLyricsLoader.getItemState(song, player.context)
        }

        derivedStateOf {
            when (mode) {
                MusicTopBarMode.VISUALISER -> show_visualiser_in_queue
                MusicTopBarMode.LYRICS -> show_lyrics_in_queue && lyrics_state?.lyrics?.synced == true
            }
        }
    }
}

@Composable
private fun getMaxHeight(show_in_queue: Boolean): State<Dp> {
    val expansion = LocalNowPlayingExpansion.current
    return animateDpAsState(
        if (!show_in_queue) 40.dp * (2f - expansion.get().coerceIn(1f, 2f))
        else 40.dp
    )
}

@Composable
fun NowPlayingTopBar(modifier: Modifier = Modifier, onHeightChanged: (Dp) -> Unit = {}) {
    val player = LocalPlayerState.current
    val expansion = LocalNowPlayingExpansion.current
    val density = LocalDensity.current

    val show_in_queue by rememberTopBarShouldShowInQueue(expansion.top_bar_mode.value)
    var lyrics_showing: Boolean by remember { mutableStateOf(false) }

    val top_bar_height by remember { derivedStateOf {
        if (!show_in_queue || expansion.getBounded() < 1f) expansion.getAppearing() else 1f
    } }

    val max_height by getMaxHeight(show_in_queue)
    val alpha by remember { derivedStateOf { if (!show_in_queue || expansion.getBounded() < 1f) 1f - expansion.getDisappearing() else 1f } }
    val hide_content by remember { derivedStateOf { alpha <= 0f } }

    var bar_height: Dp by remember { mutableStateOf(40.dp) }

    Crossfade(
        player.status.m_song,
        modifier
            .fillMaxWidth()
            .heightIn(max = if (lyrics_showing) Dp.Infinity else minOf(40.dp * top_bar_height, max_height))
            .thenIf(hide_content) {
                requiredHeight(bar_height * top_bar_height)
            }
            .height(IntrinsicSize.Min)
            .padding(horizontal = NOW_PLAYING_MAIN_PADDING.dp)
            .graphicsLayer { this@graphicsLayer.alpha = alpha }
    ) { song ->
        if (hide_content || song == null) {
            return@Crossfade
        }

        Row(
            Modifier.fillMaxSize().onSizeChanged {
                bar_height = with(density) { it.height.toDp() }
                onHeightChanged(bar_height)
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val buttons_alpha by remember { derivedStateOf { (2f - expansion.getBounded()).coerceIn(0f, 1f) } }

            composeScope {
                Box(Modifier.width(40.dp * buttons_alpha)) {
                    val auth_state = player.context.ytapi.user_auth_state
                    if (auth_state != null) {
                        LikeDislikeButton(
                            song,
                            auth_state,
                            Modifier.fillMaxSize().graphicsLayer { this@graphicsLayer.alpha = buttons_alpha },
                            { 1f - expansion.getDisappearing() > 0f },
                            { player.getNPOnBackground().setAlpha(0.5f) }
                        )
                    }
                }
            }

            player.top_bar.MusicTopBarWithVisualiser(
                Settings.INTERNAL_TOPBAR_MODE_NOWPLAYING,
                Modifier.fillMaxSize().weight(1f),
                song = song,
                onShowingChanged = {
                    lyrics_showing = it
                }
            )

            composeScope {
                IconButton(
                    {
                        if (1f - expansion.getDisappearing() > 0f) {
                            player.onMediaItemLongClicked(song, player.status.m_index)
                        }
                    },
                    Modifier.graphicsLayer { this@graphicsLayer.alpha = buttons_alpha }.width(40.dp * buttons_alpha)
                ) {
                    Icon(Icons.Filled.MoreHoriz, null, tint = player.getNPOnBackground().setAlpha(0.5f))
                }
            }
        }
    }
}
