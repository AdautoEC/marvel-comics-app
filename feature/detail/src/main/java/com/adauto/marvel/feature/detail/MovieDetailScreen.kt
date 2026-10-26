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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adauto.marvel.core.designsystem.preview.DevicePreviews
import com.adauto.marvel.core.designsystem.preview.ThemePreviews
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.model.MovieDetail
import com.adauto.marvel.core.ui.R
import com.adauto.marvel.core.ui.component.ErrorState
import com.adauto.marvel.core.ui.component.MovieCover
import com.adauto.marvel.core.ui.component.shimmer
import com.adauto.marvel.feature.detail.preview.MovieDetailUiStatePreviewParameterProvider

/**
 * Movie Detail Screen presenting:
 * - Parallax full-bleed hero artwork with vertical dark gradient scrim
 * - Floating back navigation button
 * - Action buttons (Favorito & Já assisti) with instant state toggle
 * - Stats chips/pills (Rating, Runtime, Release Date)
 * - Synopsis description
 * - Genres and Cast member list
 * - Loading shimmer placeholders and error retry state
 */
@Composable
fun MovieDetailScreen(
    uiState: MovieDetailUiState,
    onAction: (MovieDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MarvelColors.DarkBackground)
    ) {
        when {
            uiState.isLoading -> {
                MovieDetailLoadingSkeleton(
                    modifier = Modifier.testTag("MovieDetailLoadingShimmer")
                )
            }
            uiState.errorMessage != null && uiState.movie == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = { onAction(MovieDetailAction.Retry) },
                        modifier = Modifier.testTag("DetailErrorState")
                    )
                }
            }
            uiState.movie != null -> {
                MovieDetailContent(
                    movie = uiState.movie,
                    onAction = onAction,
                    modifier = Modifier.testTag("MovieDetailContent")
                )
            }
        }

        FloatingBackButton(
            onClick = { onAction(MovieDetailAction.BackClicked) },
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovieDetailContent(
    movie: MovieDetail,
    onAction: (MovieDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val heroPosterHeight = 400.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Parallax Backdrop / Poster Artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroPosterHeight)
                .graphicsLayer {
                    translationY = -scrollState.value * 0.5f
                }
                .testTag("HeroPoster")
        ) {
            MovieCover(
                imageUrl = movie.backdropUrl ?: movie.posterUrl,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Scrim overlay
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

        // Scrollable content details
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(290.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Year & Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    movie.releaseYear?.let { year ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MarvelColors.MarvelRed
                        ) {
                            Text(
                                text = year,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MarvelColors.SurfaceCard)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = movie.ratingFormatted,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    movie.runtimeFormatted?.let { runtime ->
                        Text(
                            text = runtime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MarvelColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        lineHeight = 32.sp
                    ),
                    color = MarvelColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row: Favorite (❤️) & Quero Assistir (🔖)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Favorite Button
                    Button(
                        onClick = { onAction(MovieDetailAction.FavoriteToggled) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("FavoriteButton"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (movie.isFavorite) MarvelColors.MarvelRed else MarvelColors.SurfaceCard
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (movie.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(
                                if (movie.isFavorite) R.string.cd_favorite_active else R.string.cd_favorite_inactive
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (movie.isFavorite) {
                                    R.string.detail_favorite_active
                                } else {
                                    R.string.detail_favorite_inactive
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Watchlist Button
                    OutlinedButton(
                        onClick = { onAction(MovieDetailAction.WatchlistToggled) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("WatchlistButton"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (movie.isWatched) Color(0xFF1B5E20) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (movie.isWatched) BookmarkIcon else BookmarkBorderIcon,
                            contentDescription = stringResource(
                                if (movie.isWatched) R.string.cd_watchlist_active else R.string.cd_watchlist_inactive
                            ),
                            tint = if (movie.isWatched) Color.White else MarvelColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_watchlist),
                            fontWeight = FontWeight.Bold,
                            color = if (movie.isWatched) Color.White else MarvelColors.TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Overview Synopsis
                if (movie.overview.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.detail_synopsis),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MarvelColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = movie.overview,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp
                        ),
                        color = MarvelColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Genres
                if (movie.genres.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_genres),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MarvelColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        movie.genres.forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MarvelColors.SurfaceCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MarvelColors.SurfaceDark)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MarvelColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Cast members
                if (movie.cast.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_cast),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MarvelColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CreatorsSection(creators = movie.cast)
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
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
            contentDescription = stringResource(R.string.detail_back),
            tint = MarvelColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MovieDetailLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
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
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 24.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 32.dp)
                            .shimmer(shape = RoundedCornerShape(8.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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

private val BookmarkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bookmark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(17f, 3f)
            horizontalLineTo(7f)
            curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
            verticalLineTo(21f)
            lineTo(12f, 18f)
            lineTo(19f, 21f)
            verticalLineTo(5f)
            curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
            close()
        }
    }.build()
}

private val BookmarkBorderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BookmarkBorder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(17f, 3f)
            horizontalLineTo(7f)
            curveTo(5.9f, 3f, 5f, 3.9f, 5f, 5f)
            verticalLineTo(21f)
            lineTo(12f, 18f)
            lineTo(19f, 21f)
            verticalLineTo(5f)
            curveTo(19f, 3.9f, 18.1f, 3f, 17f, 3f)
            close()
            moveTo(17f, 18f)
            lineTo(12f, 15.82f)
            lineTo(7f, 18f)
            verticalLineTo(5f)
            horizontalLineTo(17f)
            verticalLineTo(18f)
            close()
        }
    }.build()
}

@ThemePreviews
@DevicePreviews
@Composable
private fun MovieDetailScreenPreview(
    @PreviewParameter(MovieDetailUiStatePreviewParameterProvider::class) uiState: MovieDetailUiState
) {
    MarvelTheme {
        MovieDetailScreen(
            uiState = uiState,
            onAction = {}
        )
    }
}
