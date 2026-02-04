package com.ravert.guitar_trainer.guitartrainer.samples

import com.ravert.guitar_trainer.guitartrainer.datamodels.PlaybackSong
import com.ravert.guitar_trainer.guitartrainer.parser.parseSectionBlockToPlayback
import com.ravert.guitar_trainer.guitartrainer.parser.parseSongWithSections

val tishomingoSnippet = """
[Verse - Palm Muted]


e|-0-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-0-------2-------0-------0-------|

D|-2-------2-------0-------2-------| Repeat

A|-3-------0-------2-------3-------|

E|-----------------3---------------|

   C       Am      G       C

   D D UDU D D UDU D D UDU D D UDU


Well, I don't think that the city moves slow enough for me

So I'm gonna leave now, and I ain't showin' no warnings

And I know that mama said her love will always lead me home

But I've been followin' this lonely road for way too long


[Chorus]


e|-1-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-2-------0-------0-------2-------|

D|-3-------2-------0-------2-------| Repeat

A|-3-------3-------2-------0-------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D D UDU


So won't you pray for me tonight?

I've been headin' down a dark cold road

I've been dreamin' of a porch swing with some lights

Hopin' I can find myself back home


[Interlude]


e|-1-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-2-------0-------0-------2-------|

D|-3-------2-------0-------2-------|

A|-3-------3-------2-------0-------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D D UDU


[Verse - Palm Muted]


e|-0-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-0-------2-------0-------0-------|

D|-2-------2-------0-------2-------| Repeat

A|-3-------0-------2-------3-------|

E|-----------------3---------------|

   C       Am      G       C

   D D UDU D D UDU D D UDU D D UDU


Now I'm breakin' horses out in Tishomingo

And every night I lay there and wonder where good men's dreams go

And most nights I wonder how far train cars can travel


e|-0-------0-------3-------0-------x-|

B|-1-------1-------0-------1-------x-|

G|-0-------2-------0-------0-------x-|

D|-2-------2-------0-------2-------x-|

A|-3-------0-------2-------3-------x-|

E|-----------------3---------------x-|

   C       Am      G       C

   D D UDU D D UDU D D UDU D D UDUD


Or how far a man can go before one's truly unraveled


[Chorus]


e|-1-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-2-------0-------0-------2-------|

D|-3-------2-------0-------2-------| Repeat

A|-3-------3-------2-------0-------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D D UDU


So won't you pray for me tonight?

I've been headin' down a dark cold road

And I've been dreamin' of a porch swing with some lights

Hopin' I can find myself back home


[Interlude]


e|-1-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-2-------0-------0-------2-------|

D|-3-------2-------0-------2-------|

A|-3-------3-------2-------0-------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D D UDU
   

[Verse - Palm Muted]


e|-0-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-0-------2-------0-------0-------|

D|-2-------2-------0-------2-------| Repeat

A|-3-------0-------2-------3-------|

E|-----------------3---------------|

   C       Am      G       C

   D D UDU D D UDU D D UDU D D UDU


Don't jump in so quick kid

You're gonna wind up hurt

She's with a new man in New York the last time I heard

And I know that mama said her love will always lead me home

But I've been followin' a lonely road for way too long


[Chorus]


e|-1-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-2-------0-------0-------2-------|

D|-3-------2-------0-------2-------| Repeat

A|-3-------3-------2-------0-------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D D UDU


So won't you pray for me tonight?

I've been headin' down a dark cold road

I've been dreamin' of you by my side

Prayin' I can get myself back home


[Outro]


e|-1-------0-------3-------0-------|

B|-1-------1-------0-------1-------|

G|-2-------0-------0-------2-------|

D|-3-------2-------0-------2-------|

A|-3-------3-------2-------0-------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D D UDU


e|-1-------0-------3-------0~------|

B|-1-------1-------0-------1~------|

G|-2-------0-------0-------2~------|

D|-3-------2-------0-------2~------|

A|-3-------3-------2-------0~------|

E|-1---------------3---------------|

   F       C       G       Am

   D D UDU D D UDU D D UDU D
""".trimIndent()



// Adjust BPM to taste
private const val verseBpm = 100

val tishomingoVersePlayback: PlaybackSong by lazy {
    parseSongWithSections(
        title = "Tishomingo – Verse + Chorus",
        fullText = tishomingoSnippet,
        bpmBySection = mapOf(
            "Verse - Palm Muted" to 110,
            "Chorus" to 110
        ),
        defaultBpm = 110
    )
}
