package com.adauto.marvel.core.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.UiText
import com.adauto.marvel.core.ui.R

class ErrorStatePreviewParameterProvider : PreviewParameterProvider<UiText> {
    override val values: Sequence<UiText> = sequenceOf(
        UiText.Resource(R.string.error_no_connection),
        UiText.Resource(R.string.error_rate_limited),
        UiText.Resource(R.string.error_invalid_key)
    )
}
