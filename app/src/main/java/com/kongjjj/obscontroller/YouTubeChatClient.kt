package com.kongjjj.obscontroller

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class YouTubeChatClient {
    private val tag = "YouTubeChatClient"
    private val http = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _connected = MutableStateFlow(value = false)
    val connected: StateFlow<Boolean> = _connected

    private val _viewerCount = MutableStateFlow<Int?>(null)
    val viewerCount: StateFlow<Int?> = _viewerCount

    private var currentChannelId: String? = null
    private var currentVideoId: String? = null
    private var apiKey: String? = null
    private var continuation: String? = null

    fun connect(channelId: String) {
        if ((channelId == currentChannelId) && _connected.value) return
        
        disconnect()
        currentChannelId = channelId
        _messages.value = emptyList()

        job = scope.launch {
            try {
                // If it's a channel ID (starts with UC), resolve it to a live video ID
                val videoId = if (channelId.startsWith("UC")) {
                    resolveLiveVideoId(channelId)
                } else {
                    channelId // Fallback for direct video ID
                }

                if (videoId != null && fetchInitialPage(videoId)) {
                    currentVideoId = videoId
                    _connected.value = true
                    pollChat()
                } else {
                    Log.e(tag, "Failed to resolve or fetch initial page for: $channelId")
                    _connected.value = false
                }
            } catch (e: Exception) {
                Log.e(tag, "Error connecting to YouTube chat", e)
                _connected.value = false
            }
        }
    }

    private fun resolveLiveVideoId(channelId: String): String? {
        val url = "https://www.youtube.com/channel/$channelId/live"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = http.newCall(request).execute()
            // YouTube redirects /live to the watch page. We want the final URL.
            val finalUrl = response.request.url.toString()
            
            // Extract v=VIDEO_ID from URL
            var videoId = Regex("[?&]v=([^&]+)").find(finalUrl)?.groupValues?.get(1)
            
            if (videoId == null) {
                // Try extracting from HTML if redirect didn't happen as expected
                val html = response.body?.string() ?: ""
                videoId = Regex("\"videoId\":\"([^\"]{11})\"").find(html)?.groupValues?.get(1)
            }
            
            videoId
        } catch (e: Exception) {
            Log.e(tag, "Error resolving live video ID", e)
            null
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        _connected.value = false
        currentChannelId = null
        currentVideoId = null
        apiKey = null
        continuation = null
    }

    private fun fetchInitialPage(videoId: String): Boolean {
        val url = "https://www.youtube.com/live_chat?v=$videoId"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) return false
            val html = response.body?.string() ?: ""

            apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            continuation = Regex("\"continuation\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)

            // Extract real-time concurrent viewers from ytInitialPlayerResponse
            val playerResponseMatch = Regex("var ytInitialPlayerResponse = (\\{.*?\\});").find(html)
            if (playerResponseMatch != null) {
                try {
                    val playerResponse = JSONObject(playerResponseMatch.groupValues[1])
                    val videoDetails = playerResponse.optJSONObject("videoDetails")
                    if (videoDetails?.optBoolean("isLive") == true) {
                        val concurrent = videoDetails.optString("concurrentViewers")
                        if (concurrent.isNotEmpty()) {
                            _viewerCount.value = concurrent.toIntOrNull()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing ytInitialPlayerResponse", e)
                }
            }

            // Extract initial messages from ytInitialData
            val initialDataMatch = Regex("window\\[\"ytInitialData\"] = (\\{.*?\\});").find(html)
            if (initialDataMatch != null) {
                val initialDataStr = initialDataMatch.groupValues[1]
                try {
                    val initialData = JSONObject(initialDataStr)
                    val contents = initialData.optJSONObject("contents")
                    val liveChatRenderer = contents?.optJSONObject("liveChatRenderer")
                    val actions = liveChatRenderer?.optJSONArray("actions")
                    if (actions != null) {
                        val initialMessages = mutableListOf<ChatMessage>()
                        for (i in 0 until actions.length()) {
                            val action = actions.getJSONObject(i)
                            parseChatItemAction(action)?.let { initialMessages.add(it) }
                        }
                        if (initialMessages.isNotEmpty()) {
                            _messages.value = initialMessages.takeLast(MAX_CHAT_MESSAGES)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing ytInitialData", e)
                }
            }

            apiKey != null && continuation != null
        } catch (e: Exception) {
            Log.e(tag, "Error fetching initial page", e)
            false
        }
    }

    private suspend fun pollChat() {
        while (job?.isActive == true && apiKey != null && continuation != null) {
            try {
                // Also poll viewer count while we're at it
                currentVideoId?.let { fetchViewerCount(it) }

                val url = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$apiKey"
                val json = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put(
                            "client",
                            JSONObject().apply {
                                put("clientName", "WEB")
                                put("clientVersion", "2.20210622.10.00")
                            },
                        )
                    })
                    put("continuation", continuation)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = http.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonObj = JSONObject(body)
                    
                    val continuationData = jsonObj.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
                    
                    // Extract exact concurrent viewer count from chat packet (handle both simpleText and runs)
                    val viewerCountObj = continuationData?.optJSONObject("viewerCount")
                        ?.optJSONObject("liveChatViewerCountRenderer")
                        ?.optJSONObject("viewerCount")
                    
                    val viewerCountText = viewerCountObj?.optString("simpleText")
                        ?: viewerCountObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

                    viewerCountText?.let { text ->
                        // text is like "71" or "71 watching"
                        val count = text.replace(Regex("[^0-9]"), "").toIntOrNull()
                        if (count != null) _viewerCount.value = count
                    }

                    continuation = continuationData?.optJSONArray("continuations")?.optJSONObject(0)
                        ?.optJSONObject("invalidationContinuationData")?.optString("continuation")
                        ?: continuationData?.optJSONArray("continuations")?.optJSONObject(0)
                        ?.optJSONObject("timedContinuationData")?.optString("continuation")

                    val actions = continuationData?.optJSONArray("actions")
                    if (actions != null) {
                        val newMessages = mutableListOf<ChatMessage>()
                        for (i in 0 until actions.length()) {
                            val action = actions.getJSONObject(i)
                            parseChatItemAction(action)?.let { newMessages.add(it) }
                        }
                        if (newMessages.isNotEmpty()) {
                            _messages.value = (_messages.value + newMessages).takeLast(MAX_CHAT_MESSAGES)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error polling chat", e)
            }
            delay(5.seconds) // Poll every 5 seconds to avoid rate limiting
        }
    }

    private fun parseChatItemAction(action: JSONObject): ChatMessage? {
        val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item")
        val textItem = item?.optJSONObject("liveChatTextMessageRenderer")

        if (textItem != null) {
            val authorName = textItem.optJSONObject("authorName")?.optString("simpleText") ?: "Unknown"

            // Parse badges
            val badgeTags = mutableListOf<String>()
            val authorBadges = textItem.optJSONArray("authorBadges")
            if (authorBadges != null) {
                for (j in 0 until authorBadges.length()) {
                    val badge = authorBadges.getJSONObject(j).optJSONObject("liveChatAuthorBadgeRenderer")
                    if (badge != null) {
                        val customThumbnail = badge.optJSONObject("customThumbnail")
                        if (customThumbnail != null) {
                            customThumbnail.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")?.let {
                                badgeTags.add(it)
                            }
                        } else {
                            val icon = badge.optJSONObject("icon")
                            val iconType = icon?.optString("iconType")
                            if (iconType != null) {
                                badgeTags.add("yt-$iconType")
                            }
                        }
                    }
                }
            }

            val messageParts = textItem.optJSONObject("message")?.optJSONArray("runs")
            val messageText = StringBuilder()
            val youtubeEmotes = mutableMapOf<String, String>()
            if (messageParts != null) {
                for (j in 0 until messageParts.length()) {
                    val run = messageParts.getJSONObject(j)
                    if (run.has("text")) {
                        messageText.append(run.optString("text"))
                    } else if (run.has("emoji")) {
                        val emoji = run.optJSONObject("emoji")
                        val shortcut = emoji?.optJSONArray("shortcuts")?.optString(0) ?: ":emoji:"
                        messageText.append(shortcut)

                        val url = emoji?.optJSONObject("image")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                        if (url != null) {
                            youtubeEmotes[shortcut] = url
                        }
                    }
                }
            }

            val timestampUsec = textItem.optString("timestampUsec").toLongOrNull() ?: 0L

            return ChatMessage(
                id = UUID.randomUUID().toString(),
                username = authorName,
                message = messageText.toString(),
                badgeTags = badgeTags,
                youtubeEmotes = youtubeEmotes,
                timestamp = timestampUsec / 1000,
                platform = "youtube"
            )
        }
        return null
    }

    private fun fetchViewerCount(videoId: String) {
        if (apiKey == null) return
        val url = "https://www.youtube.com/youtubei/v1/player?key=$apiKey"
        val json = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20210622.10.00")
                })
            })
            put("videoId", videoId)
        }
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()
        try {
            val response = http.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val jsonObj = JSONObject(body)
                val videoDetails = jsonObj.optJSONObject("videoDetails")
                
                // Only update if it's actually a live stream
                if (videoDetails?.optBoolean("isLive") == true) {
                    // Official-like structure for concurrent viewers exists in videoDetails for some InnerTube versions
                    val concurrent = videoDetails.optString("concurrentViewers")
                    
                    if (concurrent.isNotEmpty()) {
                        _viewerCount.value = concurrent.toIntOrNull()
                    }
                } else {
                    _viewerCount.value = null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching viewer count", e)
        }
    }
}
