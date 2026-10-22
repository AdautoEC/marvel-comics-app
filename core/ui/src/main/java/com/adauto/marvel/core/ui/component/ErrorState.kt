package com.adauto.marvel.core.ui.component

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.util.asString

/**
 * Reusable error state component presenting an error message and a retry button.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "Ocorreu um erro",
    retryLabel: String = "Tentar novamente"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MarvelColors.MarvelRed,
            modifier = Modifier.size(48.dp)
        )

        if (!title.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = MarvelColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = MarvelColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MarvelColors.MarvelRed)
        ) {
            Text(
                text = retryLabel,
                color = MarvelColors.TextPrimary,
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
    title: UiText? = null,
    retryLabel: String = "Tentar novamente"
) {
    ErrorState(
        message = message.asString(),
        onRetry = onRetry,
        modifier = modifier,
        title = title?.asString(),
        retryLabel = retryLabel
    )
}
