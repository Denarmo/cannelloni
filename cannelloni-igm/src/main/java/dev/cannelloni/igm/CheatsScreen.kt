package dev.cannelloni.igm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cannelloni.ui.components.BottomBar
import dev.cannelloni.ui.components.ListSection
import dev.cannelloni.ui.components.PillRowKeyValue
import dev.cannelloni.ui.components.ScreenBackground
import dev.cannelloni.ui.components.ScreenTitle
import dev.cannelloni.ui.components.SectionedList
import dev.cannelloni.ui.components.footerReservation
import dev.cannelloni.ui.components.pillItemHeight
import dev.cannelloni.ui.components.pillVerticalPadding
import dev.cannelloni.ui.components.screenInsets
import dev.cannelloni.ui.theme.Spacing

data class CheatRowUi(val label: String, val enabled: Boolean, val supported: Boolean)

@Composable
fun CheatsScreen(
    title: String,
    sections: List<ListSection<CheatRowUi>>,
    selectedIndex: Int,
    onLabel: String,
    offLabel: String,
    bottomBarLeft: List<Pair<String, String>>,
    bottomBarRight: List<Pair<String, String>>,
    fontSize: TextUnit = 22.sp,
    lineHeight: TextUnit = 32.sp,
) {
    val verticalPadding = pillVerticalPadding()
    val itemHeight = pillItemHeight(lineHeight, verticalPadding)

    ScreenBackground(backgroundImagePath = null, backgroundAlpha = 0.85f, backgroundColor = Color.Black) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenInsets())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = footerReservation())
            ) {
                ScreenTitle(text = title, fontSize = fontSize, lineHeight = lineHeight)
                Spacer(modifier = Modifier.height(Spacing.Sm))
                SectionedList(
                    sections = sections,
                    selectedIndex = selectedIndex,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    verticalPadding = verticalPadding,
                    itemHeight = itemHeight
                ) { _, item, isSelected ->
                    Box(modifier = if (item.supported) Modifier else Modifier.alpha(0.4f)) {
                        PillRowKeyValue(
                            label = item.label,
                            value = if (item.enabled) onLabel else offLabel,
                            isSelected = isSelected,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            verticalPadding = verticalPadding
                        )
                    }
                }
            }

            BottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                leftItems = bottomBarLeft,
                rightItems = bottomBarRight
            )
        }
    }
}
