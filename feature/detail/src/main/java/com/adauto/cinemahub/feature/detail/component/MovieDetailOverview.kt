package com.adauto.cinemahub.feature.detail.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.ui.R

private const val PREVIEW_OVERVIEW =
    "Um homem deprimido que sofre de insônia conhece um vendedor estranho de sabonetes " +
        "e funda um clube de luta clandestino que se transforma em algo muito mais grandioso."

@Composable
fun MovieDetailOverview(
    overview: String,
    modifier: Modifier = Modifier
) {
    if (overview.isNotBlank()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.detail_synopsis),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@ThemePreviews
@Composable
private fun MovieDetailOverviewPreview() {
    CinemaHubTheme {
        MovieDetailOverview(overview = PREVIEW_OVERVIEW)
    }
}
