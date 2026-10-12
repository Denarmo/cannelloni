package dev.cannelloni.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.cannelloni.ui.ButtonStyle
import dev.cannelloni.ui.R
import dev.cannelloni.ui.theme.LocalCannelloniTypography

@Composable
fun RestartOverlay(message: String, buttonStyle: ButtonStyle = ButtonStyle()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = LocalCannelloniTypography.current.bodyLarge.copy(color = Color.White)
        )
        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(screenPadding),
            leftItems = emptyList(),
            rightItems = listOf(buttonStyle.confirm to stringResource(R.string.label_continue))
        )
    }
}
