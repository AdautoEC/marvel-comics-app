package com.adauto.marvel.core.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.adauto.marvel.core.model.UiText

class ErrorStatePreviewParameterProvider : PreviewParameterProvider<UiText> {
    override val values: Sequence<UiText> = sequenceOf(
        UiText.Dynamic("Falha na conexão com a rede."),
        UiText.Dynamic("Limite de requisições diário da Marvel atingido (HTTP 429)."),
        UiText.Dynamic("Chave de autenticação inválida (HTTP 401).")
    )
}
