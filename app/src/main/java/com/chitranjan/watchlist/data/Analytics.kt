package com.chitranjan.watchlist.data

import java.time.LocalDate
import java.util.TreeSet
import kotlin.math.max
import kotlin.math.min

/*
 * Portfolio maths. Everything here is plain Kotlin (no Android) and linear in the number of stocks and days,
 * so a 100-stock list with a year of history is a few milliseconds.
 */

/** One stock of the list being analysed. */
class Member(val quote: Quote, val sector: String, val holding: Holding?) {
    val ticker: String get() = quote.ticker
}

/** How the day went for a group of stocks. */
class Agg(
    val count: Int,
    val up: Int,
    val down: Int,
    val flat: Int,
    /** Average move today in percent; null when no stock in the group has a price yet. */
    val pct: Double?,
    val best: Member?,
    val worst: Member?,
)

/** Money view of the stocks that have a holding entered. */
class Money(
    val value: Double,
    val invested: Double,
    val dayPnl: Double,
    val prevValue: Double,
    val covered: Int,
    val total: Int,
) {
    val dayPct: Double? get() = if (prevValue > 0) dayPnl / prevValue * 100 else null
    val totalPnl: Double get() = value - invested
    val totalPct: Double? get() = if (invested > 0) totalPnl / invested * 100 else null
}

class SectorRow(val name: String, val members: List<Member>, val all: Agg, val nse: Agg, val bse: Agg)

class ListReport(
    val members: List<Member>,
    /** True when every stock has a holding, so averages are weighted by money instead of one-stock-one-vote. */
    val weighted: Boolean,
    val all: Agg,
    val nse: Agg,
    val bse: Agg,
    val sectors: List<SectorRow>,
    val money: Money?,
    val gainers: List<Member>,
    val losers: List<Member>,
)

/** A stock's daily closes as parallel arrays: date keys (yyyymmdd, ascending) and prices. */
class Line(val keys: IntArray, val prices: DoubleArray)

/** A portfolio's path: percent gain since the first day. [days] are epoch days, used to place points on a time axis. */
class IndexLine(val keys: IntArray, val days: IntArray, val pct: DoubleArray) {
    val last: Double get() = pct.last()
}

class HistoryStats(val bestDay: Double, val bestKey: Int, val worstDay: Double, val worstKey: Int, val maxDrawdown: Double)

/** Everything the history section shows for one period. */
class HistoryReport(
    val combined: IndexLine?,
    val nse: IndexLine?,
    val bse: IndexLine?,
    val stats: HistoryStats?,
    /** Period gain per sector name, for sectors with enough history. */
    val sectorReturns: Map<String, Double>,
    /** Period gain per stock, best first. */
    val stockReturns: List<Pair<Member, Double>>,
    /** How many of the list's stocks had history to work with. */
    val covered: Int,
    val total: Int,
)

object Analytics {

    fun historyReport(members: List<Member>, lines: Map<String, Line>, startKey: Int, weighted: Boolean): HistoryReport {
        fun inputs(ms: List<Member>) = ms.mapNotNull { m ->
            lines[m.ticker]?.let { Input(it, if (weighted) m.holding?.qty else null) }
        }
        val all = inputs(members)
        val combined = index(all, startKey)
        val sectorReturns = HashMap<String, Double>()
        for ((name, ms) in members.groupBy { it.sector }) index(inputs(ms), startKey)?.let { sectorReturns[name] = it.last }
        val stocks = members.mapNotNull { m -> lines[m.ticker]?.let { l -> periodReturn(l, startKey)?.let { m to it } } }
            .sortedByDescending { it.second }
        return HistoryReport(
            combined = combined,
            nse = index(inputs(members.filter { it.quote.isNse }), startKey),
            bse = index(inputs(members.filter { it.quote.isBse }), startKey),
            stats = combined?.let { stats(it) },
            sectorReturns = sectorReturns, stockReturns = stocks,
            covered = all.size, total = members.size,
        )
    }

    /* ---------- today ---------- */

    fun aggregate(ms: List<Member>, weighted: Boolean): Agg {
        var up = 0; var down = 0; var flat = 0
        var sum = 0.0; var n = 0
        var num = 0.0; var den = 0.0
        var best: Member? = null; var worst: Member? = null
        for (m in ms) {
            val p = m.quote.changepct ?: continue
            n++
            when { p > 0 -> up++; p < 0 -> down++; else -> flat++ }
            sum += p
            val q = m.quote
            val h = m.holding
            if (weighted && h != null && q.price != null) {
                val prev = q.closeyest ?: (q.price - (q.change ?: 0.0))
                num += h.qty * (q.change ?: (q.price - prev))
                den += h.qty * prev
            }
            if (best == null || p > best.quote.changepct!!) best = m
            if (worst == null || p < worst.quote.changepct!!) worst = m
        }
        val pct = when {
            n == 0 -> null
            weighted && den > 0 -> num / den * 100
            else -> sum / n
        }
        return Agg(ms.size, up, down, flat, pct, best, worst)
    }

    fun money(ms: List<Member>): Money? {
        var value = 0.0; var invested = 0.0; var day = 0.0; var prev = 0.0; var covered = 0
        for (m in ms) {
            val h = m.holding ?: continue
            val price = m.quote.price ?: continue
            covered++
            value += h.qty * price
            invested += h.qty * h.avg
            val change = m.quote.change ?: 0.0
            day += h.qty * change
            prev += h.qty * (m.quote.closeyest ?: (price - change))
        }
        return if (covered == 0) null else Money(value, invested, day, prev, covered, ms.size)
    }

    fun report(ms: List<Member>): ListReport {
        val weighted = ms.isNotEmpty() && ms.all { it.holding != null }
        val nse = ms.filter { it.quote.isNse }
        val bse = ms.filter { it.quote.isBse }
        val sectors = ms.groupBy { it.sector }.map { (name, list) ->
            SectorRow(name, list.sortedByDescending { it.quote.changepct ?: -1e9 },
                aggregate(list, weighted), aggregate(list.filter { it.quote.isNse }, weighted), aggregate(list.filter { it.quote.isBse }, weighted))
        }
        val priced = ms.filter { it.quote.changepct != null }
        return ListReport(
            members = ms, weighted = weighted,
            all = aggregate(ms, weighted), nse = aggregate(nse, weighted), bse = aggregate(bse, weighted),
            sectors = sectors, money = money(ms),
            gainers = priced.filter { it.quote.changepct!! > 0 }.sortedByDescending { it.quote.changepct }.take(3),
            losers = priced.filter { it.quote.changepct!! < 0 }.sortedBy { it.quote.changepct }.take(3),
        )
    }

    /* ---------- history ---------- */

    fun dateKey(date: String): Int {
        // "2026-10-09" → 20261009, without allocating.
        if (date.length < 10) return 0
        fun d(i: Int) = date[i] - '0'
        return d(0) * 10_000_000 + d(1) * 1_000_000 + d(2) * 100_000 + d(3) * 10_000 + d(5) * 1000 + d(6) * 100 + d(8) * 10 + d(9)
    }

    fun dateKey(d: LocalDate): Int = d.year * 10_000 + d.monthValue * 100 + d.dayOfMonth

    fun epochDay(key: Int): Int = LocalDate.of(key / 10_000, key / 100 % 100, key % 100).toEpochDay().toInt()

    /**
     * Turns saved closes into a [Line], replacing or extending the last day with today's live price so a period
     * always ends at "now".
     */
    fun line(closes: List<Close>, livePrice: Double?, todayKey: Int): Line? {
        val pts = closes.mapNotNull { c -> dateKey(c.date).takeIf { it > 0 }?.let { it to c.price } }
            .filter { it.second > 0 }
            .sortedBy { it.first }
            .toMutableList()
        if (livePrice != null && livePrice > 0) {
            if (pts.isNotEmpty() && pts.last().first >= todayKey) pts[pts.size - 1] = todayKey to livePrice
            else pts.add(todayKey to livePrice)
        }
        if (pts.isEmpty()) return null
        return Line(IntArray(pts.size) { pts[it].first }, DoubleArray(pts.size) { pts[it].second })
    }

    /** Index of the last date at or before [key], or -1. */
    private fun floor(keys: IntArray, key: Int): Int {
        var lo = 0; var hi = keys.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] <= key) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** A stock's gain in percent from the close on or before [startKey] to its latest price. */
    fun periodReturn(line: Line, startKey: Int): Double? {
        if (line.keys.size < 2) return null
        val i = max(floor(line.keys, startKey), 0)
        val base = line.prices[i]
        val last = line.prices.last()
        if (i == line.keys.size - 1 || base <= 0) return null
        return (last / base - 1) * 100
    }

    class Input(val line: Line, val qty: Double?)

    /**
     * Path of a group of stocks from [startKey] to today, in percent.
     *
     * Each day's move is the money-weighted move of every stock that has a price on both that day and the day
     * before, and the days are chained together. With a quantity the weight is what you hold; without one every
     * stock starts with the same amount, so a rising stock naturally grows its share (buy and hold). Stocks that
     * start trading later join the average when their first price arrives, without causing a jump.
     */
    fun index(inputs: List<Input>, startKey: Int): IndexLine? {
        if (inputs.isEmpty()) return null
        var anchor = Int.MIN_VALUE
        for (inp in inputs) {
            val i = floor(inp.line.keys, startKey)
            if (i >= 0) anchor = max(anchor, inp.line.keys[i])
        }
        if (anchor == Int.MIN_VALUE) anchor = inputs.minOf { it.line.keys[0] }

        val axisSet = TreeSet<Int>()
        axisSet.add(anchor)
        for (inp in inputs) for (k in inp.line.keys) if (k > anchor) axisSet.add(k)
        if (axisSet.size < 2) return null
        val axis = axisSet.toIntArray()
        val m = axis.size

        // Price of every stock on every axis day, carried forward over days it did not trade; NaN before its first price.
        val n = inputs.size
        val grid = Array(n) { DoubleArray(m) { Double.NaN } }
        val unit = DoubleArray(n)
        for (s in 0 until n) {
            val keys = inputs[s].line.keys; val prices = inputs[s].line.prices
            var p = 0
            for (t in 0 until m) {
                while (p + 1 < keys.size && keys[p + 1] <= axis[t]) p++
                if (keys[p] <= axis[t]) grid[s][t] = prices[p]
            }
            val firstPrice = grid[s].firstOrNull { !it.isNaN() } ?: prices[0]
            unit[s] = inputs[s].qty ?: (1.0 / firstPrice)
        }

        val pct = DoubleArray(m)
        var level = 1.0
        for (t in 1 until m) {
            var num = 0.0; var den = 0.0
            for (s in 0 until n) {
                val a = grid[s][t - 1]; val b = grid[s][t]
                if (a.isNaN() || b.isNaN()) continue
                num += unit[s] * (b - a)
                den += unit[s] * a
            }
            if (den > 0) level *= 1 + num / den
            pct[t] = (level - 1) * 100
        }
        return IndexLine(axis, IntArray(m) { epochDay(axis[it]) }, pct)
    }

    fun stats(line: IndexLine): HistoryStats? {
        if (line.pct.size < 2) return null
        var best = Double.NEGATIVE_INFINITY; var bestKey = 0
        var worst = Double.POSITIVE_INFINITY; var worstKey = 0
        var peak = 1.0; var maxDd = 0.0
        for (t in 1 until line.pct.size) {
            val prev = 1 + line.pct[t - 1] / 100
            val cur = 1 + line.pct[t] / 100
            val r = (cur / prev - 1) * 100
            if (r > best) { best = r; bestKey = line.keys[t] }
            if (r < worst) { worst = r; worstKey = line.keys[t] }
            peak = max(peak, cur)
            maxDd = min(maxDd, (cur / peak - 1) * 100)
        }
        return HistoryStats(best, bestKey, worst, worstKey, maxDd)
    }

    fun startKey(days: Long, today: LocalDate): Int = dateKey(today.minusDays(days))
}
