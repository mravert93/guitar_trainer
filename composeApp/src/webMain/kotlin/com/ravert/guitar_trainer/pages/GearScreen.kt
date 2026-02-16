package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SelectedTab
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.components.openLink
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider

@Composable
fun GearScreen(
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onAboutClick: () -> Unit,
    libraryProvider: LibraryProvider,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val gear by libraryProvider.gear.collectAsState(initial = emptyList())

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isPhone = maxWidth < 900.dp

        val numRows = if (isPhone) 2 else 4
        val rows = gear.chunked(numRows)

        Column {
            if (isPhone) {
                PhoneSiteHeader(
                    selectedTab = SelectedTab.GEAR,
                    onHomeClick = onHomeClick,
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = { },
                    onAboutClick = onAboutClick,
                )
            } else {
                SiteHeader(
                    selectedTab = SelectedTab.GEAR,
                    onHomeClick = onHomeClick,
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = { },
                    onAboutClick = onAboutClick,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .height(480.dp)
                        .background(Color.Black),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GEAR",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 175.sp,
                        color = Color(0xFFDF7800),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Not a whole lot to say here. I use these every day and would recommend them to anyone who plays guitar.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(900.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "As an Amazon Associate, I earn from qualifying purchases. Some links on this page are affiliate links, which means I may earn a small commission at no extra cost to you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(400.dp)
                    )
                }

                if (rows.isEmpty()) {
                    Spacer(modifier = Modifier.height(50.dp))

                    Text(
                        text = "No gear found",
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    rows.forEachIndexed { _, rowGear ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowGear.forEach { gear ->
                                key(gear.uuid) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(480.dp)
                                            .padding(horizontal = 8.dp)
                                            .clickable(onClick = {
                                                openLink(gear.buyLink)
                                            }),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RemoteImageCircle(
                                            name = gear.name,
                                            imageUrl = gear.imageUrl,
                                            squareShape = true,
                                            textColor = Color.Black,
                                            modifier = Modifier
                                                .size(250.dp)
                                        )

                                        Text(
                                            text = gear.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.Black,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = gear.brand,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.Black,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(Modifier.weight(1f))

                                        Button(
                                            onClick = {
                                                openLink(gear.buyLink)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFDF7800),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Buy on Amazon", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }

                            // Fill empty slots in the last row
                            if (rowGear.size < numRows) {
                                repeat(numRows - rowGear.size) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}