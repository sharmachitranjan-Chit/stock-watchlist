package com.chitranjan.watchlist.util

import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.data.Snapshot
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

/** 1234567.5 → "12,34,567.50" (Indian grouping). */
fun num(n: Double?, decimals: Int = 2): String {
    if (n == null || n.isNaN()) return "—"
    val neg = n < 0
    val fixed = String.format(Locale.US, "%.${decimals}f", abs(n))
    val int = fixed.substringBefore('.')
    val frac = if (decimals > 0) "." + fixed.substringAfter('.') else ""
    val grouped = if (int.length <= 3) int else {
        val last3 = int.takeLast(3)
        val rest = int.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$rest,$last3"
    }
    return (if (neg) "-" else "") + grouped + frac
}

fun volume(n: Double?): String = when {
    n == null || n == 0.0 -> "—"
    n >= 1e7 -> num(n / 1e7) + " Cr"
    n >= 1e5 -> num(n / 1e5) + " L"
    else -> num(n, 0)
}

fun signPct(p: Double?): String = if (p == null) "—" else (if (p > 0) "+" else "") + num(p) + "%"
fun arrow(c: Double?): String = when { c == null -> ""; c > 0 -> "▲"; c < 0 -> "▼"; else -> "•" }

enum class Dir { UP, DOWN, FLAT }
fun dirOf(c: Double?): Dir = when { c == null || c == 0.0 -> Dir.FLAT; c > 0 -> Dir.UP; else -> Dir.DOWN }

val INDEX_NAMES = mapOf("INDEXNSE:NIFTY_50" to "Nifty 50", "INDEXBOM:SENSEX" to "Sensex", "INDEXNSE:NIFTY_BANK" to "Bank Nifty")
val INDEX_SHORT = mapOf("INDEXNSE:NIFTY_50" to "NIFTY", "INDEXBOM:SENSEX" to "SENSEX", "INDEXNSE:NIFTY_BANK" to "BANKNIFTY")

/* ---------- India Standard Time ---------- */

fun istNow(): ZonedDateTime = ZonedDateTime.now(IST)
fun ist(iso: String?): ZonedDateTime? = try { if (iso.isNullOrEmpty()) null else Instant.parse(iso).atZone(IST) } catch (e: Exception) { null }
fun hm(t: ZonedDateTime?): String = t?.format(HM) ?: "—"
fun day(t: ZonedDateTime?): String = t?.format(DAY) ?: "—"

fun lastTradeLabel(q: Quote?): String {
    val t = ist(q?.tradetime) ?: return "—"
    return if (t.toLocalDate() == istNow().toLocalDate()) "${hm(t)} IST" else "${day(t)}, ${hm(t)}"
}

fun latestTrade(s: Snapshot?): ZonedDateTime? =
    s?.let { (it.indices + it.rows).mapNotNull { q -> ist(q.tradetime) }.maxOrNull() }

enum class MarketKind { LIVE, PRE, CLOSED }
data class Market(val kind: MarketKind, val label: String)

fun market(s: Snapshot?): Market {
    val n = istNow()
    val mins = n.hour * 60 + n.minute
    if (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) return Market(MarketKind.CLOSED, "Closed · weekend")
    val last = latestTrade(s)
    if (last != null && mins >= 9 * 60 + 30 && last.toLocalDate() != n.toLocalDate()) return Market(MarketKind.CLOSED, "Closed · holiday")
    return when {
        mins in 540 until 555 -> Market(MarketKind.PRE, "Pre-open")
        mins in 555 until 930 -> Market(MarketKind.LIVE, "Live")
        mins < 540 -> Market(MarketKind.CLOSED, "Opens 9:15 AM")
        else -> Market(MarketKind.CLOSED, "Closed")
    }
}

fun sessionNote(s: Snapshot?): String {
    val m = market(s)
    val n = istNow()
    val mins = n.hour * 60 + n.minute
    return when {
        m.kind == MarketKind.LIVE -> { val left = 930 - mins; "Normal trading until 3:30 PM · ${left / 60}h ${left % 60}m left" }
        m.kind == MarketKind.PRE -> "Pre-open session · normal trading starts at 9:15 AM"
        n.dayOfWeek == DayOfWeek.SATURDAY -> "Opens Monday at 9:15 AM (pre-open from 9:00 AM)"
        n.dayOfWeek == DayOfWeek.SUNDAY -> "Opens tomorrow at 9:15 AM (pre-open from 9:00 AM)"
        m.label.contains("holiday") -> "Market holiday today · prices are from the last trading day"
        mins < 540 -> "Pre-open from 9:00 AM · normal trading 9:15 AM to 3:30 PM"
        n.dayOfWeek == DayOfWeek.FRIDAY -> "Opens Monday at 9:15 AM"
        else -> "Opens tomorrow at 9:15 AM"
    }
}
