package com.adauto.marvel.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.model.ComicDetail
import com.adauto.marvel.core.model.stripHtml
import com.adauto.marvel.core.ui.component.ComicCover
import com.adauto.marvel.core.ui.component.ErrorState
import com.adauto.marvel.core.ui.component.shimmer

/**
 * Comic Detail Screen presenting:
 * - Parallax full-bleed hero artwork with vertical dark gradient scrim
 * - Floating back navigation button
 * - Issue number badge and prominent title
 * - Stats chips/pills (Issue, Pages, Onsale Date, Price)
 * - Sanitized synopsis description
 * - Creative team creators list
 * - Loading shimmer placeholders and error retry state
 */
@Composable
fun ComicDetailScreen(
    uiState: ComicDetailUiState,
    onAction: (ComicDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MarvelColors.DarkBackground)
    ) {
        when {
            uiState.isLoading -> {
                ComicDetailLoadingSkeleton(
                    modifier = Modifier.testTag("ComicDetailLoadingShimmer")
                )
                FloatingBackButton(
                    onClick = { onAction(ComicDetailAction.BackClicked) },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            uiState.errorMessage != null && uiState.comic == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = { onAction(ComicDetailAction.Retry) },
                        modifier = Modifier.testTag("DetailErrorState")
                    )
                }
                FloatingBackButton(
                    onClick = { onAction(ComicDetailAction.BackClicked) },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            uiState.comic != null -> {
                ComicDetailContent(
                    comic = uiState.comic,
                    modifier = Modifier.testTag("ComicDetailContent")
                )
                FloatingBackButton(
                    onClick = { onAction(ComicDetailAction.BackClicked) },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }
    }
}

@Composable
private fun ComicDetailContent(
    comic: ComicDetail,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val heroPosterHeight = 420.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Parallax Hero Artwork Poster
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroPosterHeight)
                .graphicsLayer {
                    translationY = scrollState.value * 0.5f
                }
                .testTag("HeroPoster")
        ) {
            ComicCover(
                coverUrl = comic.coverUrl,
                contentDescription = comic.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Vertical dark gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MarvelColors.DarkBackground.copy(alpha = 0.4f),
                                MarvelColors.DarkBackground.copy(alpha = 0.85f),
                                MarvelColors.DarkBackground
                            )
                        )
                    )
            )
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(heroPosterHeight - 80.dp))

            // Issue Badge
            val issueNumber = comic.issueNumber
            if (!issueNumber.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MarvelColors.MarvelRed,
                    modifier = Modifier.testTag("IssueBadge")
                ) {
                    Text(
                        text = issueNumber.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Comic Title
            Text(
                text = comic.title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp
                ),
                color = MarvelColors.TextPrimary,
                modifier = Modifier.testTag("DetailTitle")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata Pill Row (Issue, Pages, Onsale Date, Price)
            MetadataPillRow(
                comic = comic,
                modifier = Modifier.testTag("MetadataPillRow")
            )

            // Description / Synopsis Section
            val sanitizedDescription = stripHtml(comic.description)
            if (sanitizedDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "SINOPSE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MarvelColors.MarvelRed
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = sanitizedDescription,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp
                    ),
                    color = MarvelColors.TextSecondary,
                    modifier = Modifier.testTag("DetailDescription")
                )
            }

            // Creators Section
            if (comic.creators.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "CRIADORES",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MarvelColors.MarvelRed
                )
                Spacer(modifier = Modifier.height(12.dp))
                CreatorsSection(
                    creators = comic.creators,
                    modifier = Modifier.testTag("CreatorsSection")
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataPillRow(
    comic: ComicDetail,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    val issueNumber = comic.issueNumber
    if (!issueNumber.isNullOrBlank()) {
        StatsPill(label = "Edição", value = issueNumber)
    }
    if (comic.pageCount > 0) {
        StatsPill(label = "Páginas", value = "${comic.pageCount}")
    }
    val formattedDate = formatOnsaleDate(comic.onsaleDate)
    if (!formattedDate.isNullOrBlank()) {
        StatsPill(label = "Lançamento", value = formattedDate)
    }
    val printPrice = comic.printPrice
    if (!printPrice.isNullOrBlank()) {
        StatsPill(label = "Preço", value = printPrice)
    }
    }
}

@Composable
private fun StatsPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MarvelColors.SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, MarvelColors.SurfaceDark),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = MarvelColors.TextTertiary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MarvelColors.TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatorsSection(
    creators: List<String>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        creators.forEach { creator ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MarvelColors.SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, MarvelColors.SurfaceDark)
            ) {
                Text(
                    text = creator,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MarvelColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .statusBarsPadding()
            .padding(16.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(MarvelColors.DarkBackground.copy(alpha = 0.7f))
            .border(1.dp, MarvelColors.SurfaceCard, CircleShape)
            .testTag("BackButton")
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            tint = MarvelColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ComicDetailLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .shimmer()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Issue badge placeholder
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 24.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Title placeholders
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(30.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(24.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Pills placeholder row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 90.dp, height = 36.dp)
                            .shimmer(shape = RoundedCornerShape(12.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Description section header
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 18.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Description lines
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 3) 0.6f else 1f)
                        .height(16.dp)
                        .shimmer(shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

internal fun formatOnsaleDate(dateStr: String?): String? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val prefix = dateStr.take(10)
        val parts = prefix.split("-")
        if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            dateStr
        }
    } catch (_: Exception) {
        dateStr
    }
}
