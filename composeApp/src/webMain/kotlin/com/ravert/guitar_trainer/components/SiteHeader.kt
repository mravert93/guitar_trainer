package com.ravert.guitar_trainer.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import guitar_trainer.composeapp.generated.resources.Res
import guitar_trainer.composeapp.generated.resources.dclogo
import org.jetbrains.compose.resources.painterResource

@Composable
fun SiteHeader(
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Spacer(Modifier.width(40.dp))

        Image(
            painter = painterResource(Res.drawable.dclogo),
            contentDescription = "logo",
            modifier = Modifier.size(70.dp)
        )

        Text(
            text = "Home",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    openLink("https://www.danielchaveztutorials.com/")
                }
        )

        Text(
            text = "Artists",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    onArtistsClick()
                }
        )

        Text(
            text = "Tabs",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    onTabsClick()
                }
        )

        Text(
            text = "Gear",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    openLink("https://www.danielchaveztutorials.com/gear")
                }
        )

        Text(
            text = "Support DCT",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    openLink("https://www.danielchaveztutorials.com/support-dct")
                }
        )

        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    openLink("https://www.danielchaveztutorials.com/about")
                }
        )

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Admin",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
                    .clickable {
                        onAdminClick()
                    }
            )
        }
    }
}

@Composable
fun PhoneSiteHeader(
    onArtistsClick: () -> Unit,
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Spacer(Modifier.width(40.dp))

        Image(
            painter = painterResource(Res.drawable.dclogo),
            contentDescription = "logo",
            modifier = Modifier.size(70.dp)
        )

        Text(
            text = "Home",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    openLink("https://www.danielchaveztutorials.com/")
                }
        )

        Text(
            text = "Artists",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
                .clickable {
                    onArtistsClick()
                }
        )

        Spacer(Modifier.weight(1f))

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Tabs") },
                    onClick = {
                        menuExpanded = false
                        onTabsClick()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Gear") },
                    onClick = {
                        menuExpanded = false
                        openLink("https://www.danielchaveztutorials.com/gear")
                    }
                )

                DropdownMenuItem(
                    text = { Text("Support DCT") },
                    onClick = {
                        menuExpanded = false
                        openLink("https://www.danielchaveztutorials.com/support-dct")
                    }
                )

                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = {
                        menuExpanded = false
                        openLink("https://www.danielchaveztutorials.com/about")
                    }
                )

                Divider()

                DropdownMenuItem(
                    text = { Text("Admin") },
                    onClick = {
                        menuExpanded = false
                        onAdminClick()
                    }
                )
            }
        }
    }
}

expect fun openLink(url: String)