package com.adauto.cinemahub.feature.movies.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adauto.cinemahub.core.designsystem.preview.DevicePreviews
import com.adauto.cinemahub.core.designsystem.preview.ThemePreviews
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubColors
import com.adauto.cinemahub.core.designsystem.theme.CinemaHubTheme
import com.adauto.cinemahub.core.model.SessionState
import com.adauto.cinemahub.core.ui.R
import com.adauto.cinemahub.core.ui.component.MovieCover

@Composable
fun UserSessionPill(
    sessionState: SessionState,
    onConnectClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (sessionState) {
        is SessionState.Guest -> {
            Surface(
                modifier = modifier
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("UserSessionPill_Guest"),
                shape = RoundedCornerShape(20.dp),
                color = CinemaHubColors.SurfaceDark,
                border = BorderStroke(1.dp, CinemaHubColors.SurfaceCard)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = CinemaHubColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.session_guest),
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaHubColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(14.dp)
                            .width(1.dp)
                            .background(CinemaHubColors.SurfaceCard)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val connectLabel = stringResource(R.string.session_connect_label)
                    Text(
                        text = stringResource(R.string.session_connect),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CinemaHubColors.CinemaHubRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = connectLabel,
                                onClick = onConnectClicked
                            )
                            .testTag("UserSessionPill_ConnectBtn")
                    )
                }
            }
        }
        is SessionState.Authenticated -> {
            var menuExpanded by remember { mutableStateOf(false) }

            Box(modifier = modifier) {
                val menuLabel = stringResource(R.string.session_user_menu_label, sessionState.username)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = menuLabel,
                            onClick = { menuExpanded = !menuExpanded }
                        )
                        .testTag("UserSessionPill_Authenticated"),
                    shape = RoundedCornerShape(20.dp),
                    color = CinemaHubColors.SurfaceDark,
                    border = BorderStroke(1.dp, CinemaHubColors.SurfaceCard)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!sessionState.avatarUrl.isNullOrBlank()) {
                            MovieCover(
                                imageUrl = sessionState.avatarUrl,
                                contentDescription = stringResource(
                                    R.string.session_user_avatar_cd,
                                    sessionState.username
                                ),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = CinemaHubColors.CinemaHubRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "@${sessionState.username}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CinemaHubColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = CinemaHubColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(CinemaHubColors.SurfaceDark)
                        .testTag("UserSessionMenu")
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.session_logout),
                                color = CinemaHubColors.CinemaHubRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onLogoutClicked()
                        },
                        modifier = Modifier.testTag("UserSessionLogoutItem")
                    )
                }
            }
        }
    }
}

@ThemePreviews
@DevicePreviews
@Composable
private fun UserSessionPillGuestPreview() {
    CinemaHubTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            UserSessionPill(
                sessionState = SessionState.Guest,
                onConnectClicked = {},
                onLogoutClicked = {}
            )
        }
    }
}

@ThemePreviews
@DevicePreviews
@Composable
private fun UserSessionPillAuthenticatedPreview() {
    CinemaHubTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            UserSessionPill(
                sessionState = SessionState.Authenticated(
                    accountId = 12345,
                    username = "adautosouza",
                    sessionId = "dummy_session",
                    avatarUrl = "https://image.tmdb.org/t/p/w200/avatar.jpg"
                ),
                onConnectClicked = {},
                onLogoutClicked = {}
            )
        }
    }
}
