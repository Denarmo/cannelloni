package dev.cannelloni.scorza.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cannelloni.scorza.BuildConfig
import dev.cannelloni.scorza.R
import dev.cannelloni.ui.ButtonStyle
import dev.cannelloni.ui.components.BottomBar
import dev.cannelloni.ui.components.screenPadding
import dev.cannelloni.ui.theme.LocalCannelloniTypography
import dev.cannelloni.ui.theme.Spacing
import dev.cannelloni.ui.theme.Success

@Composable
fun AboutOverlay(statusMessage: String? = null, updateAvailable: Boolean = false, buttonStyle: ButtonStyle = ButtonStyle()) {
    val typo = LocalCannelloniTypography.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(128.dp),
            )

            Text(
                text = stringResource(R.string.about_title),
                style = typo.titleLarge.copy(color = Color.White)
            )

            Text(
                text = stringResource(R.string.about_description),
                style = typo.bodyMedium.copy(color = Color.White, textAlign = TextAlign.Center)
            )

            Spacer(modifier = Modifier.height(Spacing.Lg))

            Text(
                text = "v${BuildConfig.VERSION_NAME}  •  ${BuildConfig.BUILD_DATE}  •  ${BuildConfig.GIT_HASH}",
                style = typo.bodyMedium.copy(color = Color.White)
            )

            Spacer(modifier = Modifier.height(Spacing.Lg))

            val uriHandler = LocalUriHandler.current
            val website = stringResource(R.string.about_website)
            Text(
                text = website,
                style = typo.bodyMedium.copy(color = Color.White),
                modifier = Modifier.clickable {
                    val url = if (website.startsWith("http://") || website.startsWith("https://")) {
                        website
                    } else {
                        "https://$website"
                    }
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {}
                }
            )

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.Md))
                Text(
                    text = statusMessage,
                    style = typo.labelSmall.copy(color = Success)
                )
            }
        }
        BottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(screenPadding),
            leftItems = listOf(buttonStyle.back to stringResource(R.string.label_back)),
            rightItems = buildList {
                if (updateAvailable) add(buttonStyle.west to stringResource(R.string.label_update))
                add(buttonStyle.north to stringResource(R.string.label_credits))
            }
        )
    }
}
