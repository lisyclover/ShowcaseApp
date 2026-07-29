package com.alpha.showcase.common.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alpha.showcase.common.ui.ext.UrlWithAuth
import com.alpha.showcase.common.ui.ext.DataWithType
import com.alpha.showcase.common.utils.isImage

@Composable
fun PagerItem(
    modifier: Modifier = Modifier,
    data: Any,
    fitSize: Boolean = false,
    parentType: Int = -1,
    onComplete: (Any) -> Unit = {}
) {
    val scale = if (fitSize) ContentScale.Fit else ContentScale.Crop

    if (data.isImage()) {
        Box(modifier = modifier) {
            AsyncImage(
                model = ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                    .data(
                        when (data) {
                            is DataWithType -> data.data
                            is UrlWithAuth -> data.url
                            else -> data
                        }
                    )
                    .size(1280, 720)
                    .crossfade(300)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = scale,
                onSuccess = { onComplete(data) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
