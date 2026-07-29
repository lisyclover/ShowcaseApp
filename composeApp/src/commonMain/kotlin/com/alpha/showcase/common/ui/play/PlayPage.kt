package com.alpha.showcase.common.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.components.ScreenControlEffect
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.ui.celebration.FestivalOverlay
import com.alpha.showcase.common.ui.play.flip.FlipPager
import com.alpha.showcase.common.ui.play.flip.FlipPagerOrientation
import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.DisplayMode
import com.alpha.showcase.common.ui.settings.FrameWallMode
import com.alpha.showcase.common.ui.settings.Orientation
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_BENTO
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FRAME_WALL
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.ui.settings.SlideEffect
import com.alpha.showcase.common.ui.settings.getInterval
import com.alpha.showcase.common.ui.view.BackKeyHandler
import com.alpha.showcase.common.ui.view.DataNotFoundAnim
import com.alpha.showcase.common.ui.view.CircleLoadingIndicator
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.ui.vm.succeeded
import getScreenFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.close

const val LOADING_WARNING_TIME = 5000L
const val DEFAULT_PERIOD = 5000L

@Composable
fun PlayPage(remoteApi: RemoteApi, onBack: () -> Unit = {}) {
    var showCloseButton by remember { mutableStateOf(false) }
    var loadComplete by remember { mutableStateOf(false) }

    LaunchedEffect(showCloseButton) {
        if (showCloseButton) {
            delay(5000)
            showCloseButton = false
        }
    }

    val screenFeature = remember(remoteApi) {
        getScreenFeature()
    }

    ScreenControlEffect(
        screenFeature = screenFeature,
        keepScreenOn = true,
        fullScreen = true
    )

    BackKeyHandler(onBack = onBack) {
        Surface(Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.isNotEmpty()) {
                        showCloseButton = true
                    }
                }
            }
        }) {
            var settingsState: UiState<Settings> by remember(remoteApi) {
                mutableStateOf(UiState.Loading)
            }
            var pagingState: UiState<PagingPlayItems> by remember(remoteApi) {
                mutableStateOf(UiState.Loading)
            }
            val pagingScope = rememberCoroutineScope()

            LaunchedEffect(remoteApi) {
                settingsState = UiState.Content(SettingPreferenceRepo().getSettings())
            }

            LaunchedEffect(remoteApi, settingsState) {
                val settings = (settingsState as? UiState.Content)?.data ?: return@LaunchedEffect
                val lsJob = launch {
                    pagingState = PlayViewModel.getPagedImageFileInfo(
                        remoteApi,
                        settings.recursiveDirContent,
                        settings.supportVideo && settings.showcaseMode != SHOWCASE_MODE_FRAME_WALL,
                        settings.sortRule,
                        pagingScope
                    )
                }

                launch {
                    delay(LOADING_WARNING_TIME)
                    if (lsJob.isActive) {
                        println("Showcase: media loading, remote=${remoteApi::class.simpleName}")
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose { PlayViewModel.onClear() }
            }

            pagingState.let {
                when (it) {
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            DataNotFoundAnim(it.msg ?: "")
                        }
                    }
                    UiState.Loading -> CircleLoadingIndicator()
                    is UiState.Content -> {
                        if (pagingState.succeeded && settingsState.succeeded) {
                            val settings = (settingsState as UiState.Content).data
                            if (it.data.size > 0) {
                                MainPlayContentPage(it.data, settings)
                                loadComplete = true
                            } else {
                                DataNotFoundAnim
