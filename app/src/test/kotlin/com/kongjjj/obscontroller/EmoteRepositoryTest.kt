package com.kongjjj.obscontroller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteRepositoryTest {

    @Test
    fun testParseYouTubeEmotesWithoutSpaces() {
        val message = "hello:heart:world"
        val youtubeEmotes = mapOf(":heart:" to "http://emoji/heart")
        
        val segments = parseMessageSegments(
            message = message,
            emotesTag = null,
            thirdPartyEmotes = emptyMap(),
            youtubeEmotes = youtubeEmotes,
            bits = 0
        )
        
        assertEquals(3, segments.size)
        assertTrue(segments[0] is MessageSegment.TextPart && (segments[0] as MessageSegment.TextPart).text == "hello")
        assertTrue(segments[1] is MessageSegment.EmotePart && (segments[1] as MessageSegment.EmotePart).name == ":heart:")
        assertTrue(segments[2] is MessageSegment.TextPart && (segments[2] as MessageSegment.TextPart).text == "world")
    }

    @Test
    fun testParseThirdPartyEmotesWithStrictBoundaries() {
        val message = "LUL helloLUL"
        val thirdPartyEmotes = mapOf("LUL" to "http://emoji/LUL")
        
        val segments = parseMessageSegments(
            message = message,
            emotesTag = null,
            thirdPartyEmotes = thirdPartyEmotes,
            youtubeEmotes = emptyMap(),
            bits = 0
        )
        
        // Only the first LUL should match because it has boundaries
        // stage1: [TextPart("LUL helloLUL")]
        // stageYoutube: same
        // stageThirdParty: [EmotePart("LUL"), TextPart(" helloLUL")]
        
        assertEquals(2, segments.size)
        assertTrue(segments[0] is MessageSegment.EmotePart && (segments[0] as MessageSegment.EmotePart).name == "LUL")
        assertTrue(segments[1] is MessageSegment.TextPart && (segments[1] as MessageSegment.TextPart).text == " helloLUL")
    }
}
