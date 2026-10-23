package com.adauto.marvel.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adauto.marvel.core.designsystem.preview.ThemePreviews
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelTheme

const val DEFAULT_ATTRIBUTION_TEXT = "Data provided by Marvel. © 2026 MARVEL"

/**
 * Footer displaying the mandatory Marvel Comics API attribution notice.
 */
@Composable
fun AttributionFooter(
    text: String = DEFAULT_ATTRIBUTION_TEXT,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MarvelColors.TextTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@ThemePreviews
@Composable
private fun AttributionFooterPreview() {
    MarvelTheme {
        AttributionFooter()
    }
}
