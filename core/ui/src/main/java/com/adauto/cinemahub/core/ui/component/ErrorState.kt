package com.adauto.cinemahub.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.ui.R
import com.adauto.cinemahub.core.ui.preview.ErrorStatePreviewParameterProvider
import com.adauto.cinemahub.core.ui.util.asString

/**
 * Reusable error state component presenting an error message and a retry button.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = stringResource(R.string.error_generic),
    retryLabel: String = stringResource(R.string.error_retry)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CinemaHubTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        if (!title.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(CinemaHubTheme.spacing.medium))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(CinemaHubTheme.spacing.small))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(CinemaHubTheme.spacing.large))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text = retryLabel,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Overload of [ErrorState] accepting [UiText] for localized and architecture-compliant messages.
 */
@Composable
fun ErrorState(
    message: UiText,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: UiText? = UiText.Resource(R.string.error_generic),
    retryLabel: String = stringResource(R.string.error_retry)
) {
    ErrorState(
        message = message.asString(),
        onRetry = onRetry,
        modifier = modifier,
        title = title?.asString(),
        retryLabel = retryLabel
    )
}

@ThemePreviews
@Composable
private fun ErrorStatePreview(
    @PreviewParameter(ErrorStatePreviewParameterProvider::class) message: UiText
) {
    CinemaHubTheme {
        ErrorState(
            message = message,
            onRetry = {}
        )
    }
}
