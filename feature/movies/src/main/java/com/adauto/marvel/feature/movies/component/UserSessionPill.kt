package com.adauto.marvel.feature.movies.component

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adauto.marvel.core.designsystem.preview.DevicePreviews
import com.adauto.marvel.core.designsystem.preview.ThemePreviews
import com.adauto.marvel.core.designsystem.theme.MarvelColors
import com.adauto.marvel.core.designsystem.theme.MarvelTheme
import com.adauto.marvel.core.model.SessionState
import com.adauto.marvel.core.ui.component.MovieCover

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
                color = MarvelColors.SurfaceDark,
                border = BorderStroke(1.dp, MarvelColors.SurfaceCard)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MarvelColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Convidado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MarvelColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(14.dp)
                            .width(1.dp)
                            .background(MarvelColors.SurfaceCard)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Conectar",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MarvelColors.MarvelRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Conectar conta TMDB",
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
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Menu do usuário ${sessionState.username}",
                            onClick = { menuExpanded = !menuExpanded }
                        )
                        .testTag("UserSessionPill_Authenticated"),
                    shape = RoundedCornerShape(20.dp),
                    color = MarvelColors.SurfaceDark,
                    border = BorderStroke(1.dp, MarvelColors.SurfaceCard)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!sessionState.avatarUrl.isNullOrBlank()) {
                            MovieCover(
                                imageUrl = sessionState.avatarUrl,
                                contentDescription = "Avatar de ${sessionState.username}",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MarvelColors.MarvelRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "@${sessionState.username}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MarvelColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MarvelColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(MarvelColors.SurfaceDark)
                        .testTag("UserSessionMenu")
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sair da conta",
                                color = MarvelColors.MarvelRed,
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
    MarvelTheme {
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
    MarvelTheme {
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
