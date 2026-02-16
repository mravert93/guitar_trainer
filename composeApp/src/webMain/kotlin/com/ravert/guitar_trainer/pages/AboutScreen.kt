package com.ravert.guitar_trainer.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ravert.guitar_trainer.components.PhoneSiteHeader
import com.ravert.guitar_trainer.components.SelectedTab
import com.ravert.guitar_trainer.components.SiteHeader
import guitar_trainer.composeapp.generated.resources.Res
import guitar_trainer.composeapp.generated.resources.daniel

import org.jetbrains.compose.resources.painterResource

@Composable
fun AboutScreen(
    onTabsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onGearClick: () -> Unit,
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
                    selectedTab = SelectedTab.ABOUT,
                    onHomeClick = onHomeClick,
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = onGearClick,
                    onAboutClick = { }
                )
            } else {
                SiteHeader(
                    selectedTab = SelectedTab.ABOUT,
                    onHomeClick = onHomeClick,
                    onTabsClick = onTabsClick,
                    onArtistsClick = onArtistsClick,
                    onAdminClick = onAdminClick,
                    onGearClick = onGearClick,
                    onAboutClick = { }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFDF7800))
                    .verticalScroll(scrollState),
            ) {
                Spacer(modifier = Modifier.height(50.dp))

                Row (
                    modifier = Modifier.fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                            .padding(start = 30.dp),
                    ) {
                        Spacer(modifier = Modifier.height(50.dp))

                        Text(
                            text = "A little about me.",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(900.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "I’m Daniel. I started learning guitar when I was fifteen after hearing a friend play one of my favorite songs. He taught me the basics - guitar terminology and how to read tabs- and even let me borrow one of his guitars. I used Ultimate Guitar and 911 Tabs for a while before I noticed how inconsistent the quality was. I decided I’d try to learn by watching the artists play their songs live on YouTube.\n" +
                                    "\n" +
                                    "About 12 years ago, I started a YouTube channel and posted my first video teaching one of my favorite songs, Everchanging. I thought maybe a few people might watch it, and knowing I taught someone would give me a sense of fulfillment. I did not know how much support and song requests I would get from that one video. \n" +
                                    "\n" +
                                    "Fast forward to today, and I have a little over 4000 subscribers on YouTube and 1.3 million views. Not bad for a small channel, eh? I haven’t made any content recently, but I figured I’d start investing some time into the channel again. I still learn songs occasionally and always look for new ones.\n" +
                                    "\n" +
                                    "If you like my content and would like to request a particular song, don’t hesitate to reach out to me on the contact page. I don’t always reply, but I read all my comments.\n" +
                                    "\n" +
                                    "- Daniel",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.width(750.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                            .fillMaxHeight()
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.daniel),
                            contentDescription = "about me image",
                            modifier = Modifier.fillMaxSize()
                                .padding(2.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}