package com.kongjjj.obscontroller

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TwitchChatClientTest {

    private val client = TwitchChatClient()

    @Test
    fun testParsePrivMsgWithTags() {
        val line = "@badge-info=;badges=broadcaster/1;color=#FF4500;display-name=Slachy;emotes=;first-msg=0;id=123-456;mod=0;returning-chatter=0;room-id=789;subscriber=0;tmi-sent-ts=1700000000000;turbo=0;user-id=111;user-type= :slachy!slachy@slachy.tmi.twitch.tv PRIVMSG #slachy :Hello world!"
        val msg = client.parseTwitchIrcLine(line)
        
        assertNotNull(msg)
        assertEquals("Slachy", msg?.username)
        assertEquals("Hello world!", msg?.message)
        assertEquals("twitch", msg?.platform)
        assertEquals(MessageType.NORMAL, msg?.type)
        assertEquals("123-456", msg?.id)
    }

    @Test
    fun testParsePrivMsgWithColonsInContent() {
        val line = ":user!user@user.tmi.twitch.tv PRIVMSG #channel :Message with : colons"
        val msg = client.parseTwitchIrcLine(line)
        
        assertNotNull(msg)
        assertEquals("user", msg?.username)
        assertEquals("Message with : colons", msg?.message)
    }

    @Test
    fun testParseUserNoticeSub() {
        val line = "@badge-info=;badges=subscriber/0;color=;display-name=Subber;emotes=;id=abc;login=subber;mod=0;msg-id=sub;msg-param-cumulative-months=1;msg-param-months=0;msg-param-multimonth-duration=0;msg-param-multimonth-tenure=0;msg-param-should-share-streak=0;msg-param-sub-plan-name=Channel\\sSubscription;msg-param-sub-plan=1000;msg-param-was-gifted=false;room-id=123;subscriber=1;system-msg=Subber\\ssubscribed\\sat\\sTier\\s1.;tmi-sent-ts=1700000000000;user-id=444;user-type= :tmi.twitch.tv USERNOTICE #channel"
        val msg = client.parseTwitchIrcLine(line)
        
        assertNotNull(msg)
        assertEquals("Subber", msg?.username)
        assertEquals("", msg?.message)
        assertEquals(MessageType.USER_NOTICE, msg?.type)
        assertEquals("sub", msg?.twitchMsgId)
        assertEquals("Subber subscribed at Tier 1.", msg?.systemMsg)
        
        val localized = msg?.getLocalizedSystemMessage()
        assertEquals("Subber 使用 層級 1 訂閱了頻道！", localized)
    }

    @Test
    fun testParseUserNoticeResubWithMessage() {
        val line = "@badge-info=;badges=subscriber/6;color=#00FF7F;display-name=Resubber;emotes=;id=def;login=resubber;mod=0;msg-id=resub;msg-param-cumulative-months=6;msg-param-months=0;msg-param-should-share-streak=0;msg-param-sub-plan=Prime;room-id=123;subscriber=1;system-msg=Resubber\\ssubscribed\\swith\\sPrime.\\sThey've\\ssubscribed\\sfor\\s6\\smonths!;tmi-sent-ts=1700000000000;user-id=555;user-type= :tmi.twitch.tv USERNOTICE #channel :Resub message!"
        val msg = client.parseTwitchIrcLine(line)
        
        assertNotNull(msg)
        assertEquals("Resubber", msg?.username)
        assertEquals("Resub message!", msg?.message)
        assertEquals("Resubber subscribed with Prime. They've subscribed for 6 months!", msg?.systemMsg)
        
        val localized = msg?.getLocalizedSystemMessage()
        assertEquals("Resubber 已使用 Prime 訂閱。這位使用者已經訂閱了 6 個月！", localized)
    }

    @Test
    fun testParseActionMessage() {
        val line = ":user!user@user.tmi.twitch.tv PRIVMSG #channel :\u0001ACTION is dancing\u0001"
        val msg = client.parseTwitchIrcLine(line)
        
        assertNotNull(msg)
        assertEquals("is dancing", msg?.message)
    }

    @Test
    fun testParsePrivMsgWithoutColonSeparator() {
        // Simulating a format sometimes seen in history APIs where the leading colon for message is missing
        val line = "@display-name=Slachy;id=123 :slachy!slachy@slachy.tmi.twitch.tv PRIVMSG #slachy Hello world!"
        val msg = client.parseTwitchIrcLine(line)
        
        assertNotNull(msg)
        assertEquals("Slachy", msg?.username)
        assertEquals("Hello world!", msg?.message)
    }

    @Test
    fun testParseBitsMessage() {
        val line = "@bits=100;color=#00FF7F;display-name=Cheerer;id=bits123;tmi-sent-ts=1700000000000 :cheerer!cheerer@tmi.twitch.tv PRIVMSG #channel :Cheer100 keep up the great work!"
        val msg = client.parseTwitchIrcLine(line)

        assertNotNull(msg)
        assertEquals("Cheerer", msg?.username)
        assertEquals("Cheer100 keep up the great work!", msg?.message)
        assertEquals(100, msg?.bits)
    }

    @Test
    fun testSubAndBitsFilteringLogic() {
        val normalMsg = ChatMessage(id = "1", username = "User1", message = "Hi", platform = "twitch", bits = 0)
        val bitsMsg = ChatMessage(id = "2", username = "User2", message = "Cheer100 Hi", platform = "twitch", bits = 100)
        val subMsgNoComment = ChatMessage(id = "3", username = "User3", message = "", platform = "twitch", type = MessageType.USER_NOTICE, twitchMsgId = "sub")
        val subMsgWithComment = ChatMessage(id = "4", username = "User4", message = "Love the stream!", platform = "twitch", type = MessageType.USER_NOTICE, twitchMsgId = "resub")

        fun isSubOrBits(lastMsg: ChatMessage): Boolean {
            val isTwitch = lastMsg.platform == "twitch"
            val isBits = isTwitch && lastMsg.bits > 0
            val isSub = isTwitch && (
                lastMsg.twitchMsgId in SUB_TWITCH_MSG_IDS ||
                (lastMsg.type == MessageType.USER_NOTICE && lastMsg.twitchMsgId?.contains("sub", ignoreCase = true) == true)
            )
            return isBits || isSub
        }

        Assert.assertFalse(isSubOrBits(normalMsg))
        Assert.assertTrue(isSubOrBits(bitsMsg))
        Assert.assertTrue(isSubOrBits(subMsgNoComment))
        Assert.assertTrue(isSubOrBits(subMsgWithComment))
    }
}
