package com.chitranjan.watchlist.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

class EngineException(message: String) : Exception(message)

/** Talks to the Apps Script web app that sits on top of the "Stock Watchlist" Google Sheet. */
object Engine {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    suspend fun data(c: EngineConfig): Snapshot = snapshot(call(c, "data"))
    suspend fun add(c: EngineConfig, list: String, ticker: String) = snapshot(call(c, "add", "list" to list, "ticker" to ticker))
    suspend fun remove(c: EngineConfig, list: String, ticker: String) = snapshot(call(c, "remove", "list" to list, "ticker" to ticker))
    suspend fun move(c: EngineConfig, list: String, ticker: String, dir: Int) = snapshot(call(c, "move", "list" to list, "ticker" to ticker, "dir" to dir.toString()))
    suspend fun deleteList(c: EngineConfig, list: String) = snapshot(call(c, "deleteList", "list" to list))

    suspend fun history(c: EngineConfig, ticker: String): List<Close> =
        call(c, "history", "ticker" to ticker).jsonArray.mapNotNull { row ->
            val a = row as? JsonArray ?: return@mapNotNull null
            val d = a.getOrNull(0)?.jsonPrimitive?.content ?: return@mapNotNull null
            val p = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            Close(d, p)
        }

    private fun snapshot(e: JsonElement): Snapshot = json.decodeFromJsonElement(e)

    private suspend fun call(c: EngineConfig, action: String, vararg params: Pair<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val q = buildString {
                append(if (c.url.contains('?')) '&' else '?')
                append("action=").append(enc(action)).append("&key=").append(enc(c.key))
                params.forEach { (k, v) -> append('&').append(k).append('=').append(enc(v)) }
            }
            val text = try {
                // Apps Script sometimes fails the very first call after a cold start; try once more.
                try { get(c.url + q) } catch (e: IOException) { kotlinx.coroutines.delay(1500); get(c.url + q) }
            } catch (e: SocketTimeoutException) {
                throw EngineException("The price engine took too long to answer.")
            } catch (e: IOException) {
                throw EngineException("No internet connection, or the engine link is wrong.")
            }
            val body = try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw EngineException("The engine link did not return prices. Check it ends in /exec and is deployed for \"Anyone\".")
            }
            if (body["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                throw EngineException(body["error"]?.jsonPrimitive?.content ?: "Something went wrong in the engine.")
            }
            body["data"] ?: JsonObject(emptyMap())
        }

    /** Apps Script answers with a redirect to googleusercontent.com, so follow up to 5 hops by hand. */
    private fun get(start: String): String {
        var url = start
        repeat(5) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    url = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                    return@repeat
                }
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: throw IOException("HTTP $code")
                return stream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
