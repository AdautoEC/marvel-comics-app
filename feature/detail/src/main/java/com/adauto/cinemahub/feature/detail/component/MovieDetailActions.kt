package com.adauto.cinemahub.feature.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.ui.R

private val WatchlistGreen = Color(0xFF1B5E20)

@Composable
fun MovieDetailActions(
    isFavorite: Boolean,
    isInWatchlist: Boolean,
    onFavoriteToggle: () -> Unit,
    onWatchlistToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteToggle,
            modifier = Modifier.weight(1f)
        )

        WatchlistActionButton(
            isInWatchlist = isInWatchlist,
            onClick = onWatchlistToggle,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("FavoriteButton"),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = if (isFavorite) {
                Icons.Default.Favorite
            } else {
                Icons.Default.FavoriteBorder
            },
            contentDescription = stringResource(
                if (isFavorite) R.string.cd_favorite_active else R.string.cd_favorite_inactive
            ),
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (isFavorite) {
                    R.string.detail_favorite_active
                } else {
                    R.string.detail_favorite_inactive
                }
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun WatchlistActionButton(
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("WatchlistButton"),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isInWatchlist) WatchlistGreen else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = if (isInWatchlist) BookmarkIcon else BookmarkBorderIcon,
            contentDescription = stringResource(
                if (isInWatchlist) {
                    R.string.cd_watchlist_active
                } else {
                    R.string.cd_watchlist_inactive
                }
            ),
            tint = if (isInWatchlist) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.detail_watchlist),
            fontWeight = FontWeight.Bold,
            color = if (isInWatchlist) Color.White else MaterialTheme.colorScheme.onBackground
        )
    }
}

internal val BookmarkIcon: ImageVector by lazy {
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

internal val BookmarkBorderIcon: ImageVector by lazy {
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
