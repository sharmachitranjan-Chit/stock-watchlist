package com.chitranjan.watchlist.data

import kotlinx.serialization.Serializable

/** One row from the Google Sheet engine: a stock or an index. */
@Serializable
data class Quote(
    val list: String,
    val ticker: String,
    val name: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val changepct: Double? = null,
    val closeyest: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val high52: Double? = null,
    val low52: Double? = null,
    val volume: Double? = null,
    val tradetime: String? = null,
) {
    val key: String get() = "$list|$ticker"
    val isNse: Boolean get() = ticker.startsWith("NSE:")
    val isBse: Boolean get() = ticker.startsWith("BOM:")
    val exchange: String get() = if (ticker.startsWith("BOM:") || ticker.startsWith("INDEXBOM:")) "BSE" else "NSE"
    val code: String get() = ticker.substringAfter(':')
}

@Serializable
data class Snapshot(
    val rows: List<Quote> = emptyList(),
    val indices: List<Quote> = emptyList(),
    val at: String = "",
)

/** A day's closing price, from the engine's history action: ["2026-10-09", 1170.9]. */
data class Close(val date: String, val price: Double)

data class EngineConfig(val url: String, val key: String)
