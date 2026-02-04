package com.ravert.guitar_trainer.guitartrainer.parser

data class SectionBlock(
    val name: String,
    val body: String
)

fun splitIntoSectionBlocks(fullText: String): List<SectionBlock> {
    val lines = fullText.lines()
    val blocks = mutableListOf<SectionBlock>()

    var currentName: String? = null
    val currentLines = mutableListOf<String>()

    fun flush() {
        val name = currentName ?: return
        val bodyText = currentLines.joinToString("\n").trim('\n', ' ')
        blocks += SectionBlock(name = name, body = bodyText)
        currentLines.clear()
    }

    val headerRegex = Regex("""^\[(.+)]""")

    for (line in lines) {
        val match = headerRegex.matchEntire(line.trim())
        if (match != null) {
            // new header → flush previous
            flush()
            currentName = match.groupValues[1] // e.g. "Verse - Palm Muted", "Chorus"
        } else {
            if (currentName != null) {
                currentLines += line
            }
        }
    }
    flush()

    return blocks
}
