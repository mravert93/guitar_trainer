package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SelectedTab
import com.ravert.guitar_trainer.components.SiteHeader
import com.ravert.guitar_trainer.components.YoutubeEmbedSlot
import com.ravert.guitar_trainer.guitartrainer.managers.LibraryProvider
import guitar_trainer.composeapp.generated.resources.Res
import guitar_trainer.composeapp.generated.resources.guitarbg
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomepageScreen(
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onGearClick: () -> Unit,
    onAboutClick: () -> Unit,
    libraryProvider: LibraryProvider,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val isPhone = maxWidth < 900.dp

        Column {
            if (isPhone) {
                PhoneSiteHeader(
                    selectedTab = SelectedTab.HOME,
                    onHomeClick = {  },
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = onGearClick,
                    onAboutClick = onAboutClick,
                )
            } else {
                SiteHeader(
                    selectedTab = SelectedTab.HOME,
                    onHomeClick = {  },
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = onGearClick,
                    onAboutClick = onAboutClick,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 48.dp)
                    .verticalScroll(state = scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.height(500.dp)
                        .background(Color.White)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        painter = painterResource(Res.drawable.guitarbg),
                        contentDescription = "guitar background image",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )

                    Column(
                        modifier = Modifier.fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Learn your favorite songs on guitar",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(60.dp))

                        Text(
                            text = "THE WAY THE ARTIST PLAYS THEM",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(60.dp))

                        Button(
                            onClick = onArtistsClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDF7800),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Explore Artists", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                Text(
                    text = "THANKS FOR STOPPING BY!",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You’ll find all of the tabs I’ve written over the years on this site. You can find a list in the menu option “TABS” or browse the artist pages where the tabs are separated by albums.\n" +
                            "\n" +
                            "I’m always looking to learn new songs and hear new artists. If there is a song you’d like to learn, let me know by submitting a request below.\n" +
                            "\n" +
                            "Thank you, and happy learning!",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    modifier = Modifier.width(400.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onTabsClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDF7800),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(width = 200.dp, height = 72.dp)
                ) {
                    Text("TABS", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color.Black),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Check out my latest lesson",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color(0xFFDF7800),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                }
            }
        }
    }
}