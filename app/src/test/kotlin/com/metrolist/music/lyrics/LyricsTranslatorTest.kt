package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslatorTest {
    @Test
    fun vocalsOnlyLinesAreNotTranslated() {
        listOf("Ooh, ooh", "ooh ooh, ooh", "La la la", "(Yeah!)", "Mmm", "Na-na-na", "Oh-oh-oh", "Whoa", "Hey, hey", "Ah-ah", "…")
            .forEach { assertTrue(it, LyricsTranslator.isVocalsOnly(it)) }
    }

    @Test
    fun linesWithWordsAreTranslated() {
        listOf("Oh, I love you", "Aku cinta kamu", "He said hi", "A dream", "사랑해", "Hey you")
            .forEach { assertFalse(it, LyricsTranslator.isVocalsOnly(it)) }
    }

    @Test
    fun translationThatSaysTheSameIsHidden() {
        // A line already in the target language comes back unchanged, or with other punctuation.
        assertFalse(LyricsTranslator.shouldShow("Take me home tonight", "Take me home tonight."))
        assertFalse(LyricsTranslator.shouldShow("I love you", "i love you"))
        assertFalse(LyricsTranslator.shouldShow("Ooh, ooh", "Ooh, ooh"))
        assertFalse(LyricsTranslator.shouldShow("Aku cinta kamu", ""))
        assertTrue(LyricsTranslator.shouldShow("Aku cinta kamu", "I love you"))
    }

    @Test
    fun modelAnswersOnlyForTheLinesItTranslated() {
        // A mixed song: lines 1 and 4 were Indonesian, the others already English.
        val fenced = "```json\n{\"lines\": [{\"n\": 1, \"t\": \"You said love would never fade\"}, {\"n\": 4, \"t\": \"My heart is shattered\"}]}\n```"
        assertEquals(mapOf(1 to "You said love would never fade", 4 to "My heart is shattered"), LyricsTranslator.parseAiLines(fenced))
        // A song already in the target language: nothing to translate is a valid answer.
        assertEquals(emptyMap<Int, String>(), LyricsTranslator.parseAiLines("{\"lines\": []}"))
        // Not the JSON asked for: null, so Google Translate takes over.
        assertEquals(null, LyricsTranslator.parseAiLines("Sorry, I can't"))
    }

    @Test
    fun storedTranslationsRoundTrip() {
        val map = mapOf("Aku cinta kamu" to "I love you", "Bawa aku\tpulang" to "Take me\nhome")
        assertEquals(mapOf("Aku cinta kamu" to "I love you", "Bawa aku pulang" to "Take me home"), LyricsTranslator.decode(LyricsTranslator.encode(map)))
    }
}
