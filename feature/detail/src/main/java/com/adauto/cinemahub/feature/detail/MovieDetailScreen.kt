package com.adauto.cinemahub.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.designsystem.preview.DevicePreviews
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.model.MovieDetail
import com.adauto.cinemahub.core.ui.R
import com.adauto.cinemahub.core.ui.component.ErrorState
import com.adauto.cinemahub.core.ui.component.rememberShimmerProgress
import com.adauto.cinemahub.core.ui.component.shimmer
import com.adauto.cinemahub.feature.detail.component.MovieDetailActions
import com.adauto.cinemahub.feature.detail.component.MovieDetailBackdrop
import com.adauto.cinemahub.feature.detail.component.MovieDetailCast
import com.adauto.cinemahub.feature.detail.component.MovieDetailGenres
import com.adauto.cinemahub.feature.detail.component.MovieDetailHeader
import com.adauto.cinemahub.feature.detail.component.MovieDetailOverview
import com.adauto.cinemahub.feature.detail.preview.MovieDetailUiStatePreviewParameterProvider

/**
 * Movie Detail Screen presenting:
 * - Parallax full-bleed hero artwork with vertical dark gradient scrim
 * - Floating back navigation button
 * - Action buttons (Favorito & Quero Assistir) with instant state toggle
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
            .background(MaterialTheme.colorScheme.background)
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

@Composable
private fun MovieDetailContent(
    movie: MovieDetail,
    onAction: (MovieDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        MovieDetailBackdrop(
            movie = movie,
            scrollOffset = scrollState.value
        )

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
                MovieDetailHeader(movie = movie)

                Spacer(modifier = Modifier.height(CinemaHubTheme.spacing.medium))

                MovieDetailActions(
                    isFavorite = movie.isFavorite,
                    isInWatchlist = movie.isInWatchlist,
                    onFavoriteToggle = { onAction(MovieDetailAction.FavoriteToggled) },
                    onWatchlistToggle = { onAction(MovieDetailAction.WatchlistToggled) }
                )

                Spacer(modifier = Modifier.height(CinemaHubTheme.spacing.large))

                MovieDetailOverview(overview = movie.overview)

                if (movie.overview.isNotBlank() && (movie.genres.isNotEmpty() || movie.cast.isNotEmpty())) {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                MovieDetailGenres(genres = movie.genres)

                if (movie.genres.isNotEmpty() && movie.cast.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                MovieDetailCast(cast = movie.cast)

                Spacer(modifier = Modifier.height(48.dp))
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
            .padding(CinemaHubTheme.spacing.medium)
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .testTag("BackButton")
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.detail_back),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MovieDetailLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmerProgress = rememberShimmerProgress()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .shimmer(progress = shimmerProgress)
        )

        SkeletonContentBody(shimmerProgress = shimmerProgress)
    }
}

@Composable
private fun SkeletonContentBody(
    shimmerProgress: State<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 24.dp)
                .shimmer(shape = RoundedCornerShape(4.dp), progress = shimmerProgress)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(30.dp)
                .shimmer(shape = RoundedCornerShape(4.dp), progress = shimmerProgress)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(24.dp)
                .shimmer(shape = RoundedCornerShape(4.dp), progress = shimmerProgress)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 32.dp)
                        .shimmer(shape = RoundedCornerShape(8.dp), progress = shimmerProgress)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 3) 0.6f else 1f)
                    .height(16.dp)
                    .shimmer(shape = RoundedCornerShape(4.dp), progress = shimmerProgress)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@ThemePreviews
@DevicePreviews
@Composable
private fun MovieDetailScreenPreview(
    @PreviewParameter(MovieDetailUiStatePreviewParameterProvider::class) uiState: MovieDetailUiState
) {
    CinemaHubTheme {
        MovieDetailScreen(
            uiState = uiState,
            onAction = {}
        )
    }
}
