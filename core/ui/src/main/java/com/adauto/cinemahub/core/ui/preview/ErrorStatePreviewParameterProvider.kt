package com.adauto.cinemahub.core.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.cinemahub.core.model.UiText
import com.adauto.cinemahub.core.ui.R

class ErrorStatePreviewParameterProvider : PreviewParameterProvider<UiText> {
    override val values: Sequence<UiText> = sequenceOf(
        UiText.Resource(R.string.error_no_connection),
        UiText.Resource(R.string.error_rate_limited),
        UiText.Resource(R.string.error_invalid_key)
    )
}
