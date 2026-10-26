package com.adauto.cinemahub.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme

const val DEFAULT_ATTRIBUTION_TEXT = "Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB."

/**
 * Footer displaying the TMDB API attribution notice.
 */
@Composable
fun AttributionFooter(
    text: String = DEFAULT_ATTRIBUTION_TEXT,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(CinemaHubTheme.spacing.medium)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@ThemePreviews
@Composable
private fun AttributionFooterPreview() {
    CinemaHubTheme {
        AttributionFooter()
    }
}
