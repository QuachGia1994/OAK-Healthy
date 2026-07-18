package com.example.supplementtracker.presentation.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.supplementtracker.R

@Composable
fun OakLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    val shape = RoundedCornerShape(size * 0.22f)
    Image(
        painter = painterResource(R.drawable.oak_app_icon),
        contentDescription = androidx.compose.ui.res.stringResource(R.string.a11y_oak_logo),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .shadow(elevation = 14.dp, shape = shape)
            .clip(shape)
    )
}
