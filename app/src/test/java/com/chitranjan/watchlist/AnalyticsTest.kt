package com.chitranjan.watchlist

import com.chitranjan.watchlist.data.Analytics
import com.chitranjan.watchlist.data.Analytics.Input
import com.chitranjan.watchlist.data.Close
import com.chitranjan.watchlist.data.Holding
import com.chitranjan.watchlist.data.Line
import com.chitranjan.watchlist.data.Member
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.data.Sectors
import com.chitranjan.watchlist.util.inr
import com.chitranjan.watchlist.util.num
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    private fun line(vararg p: Pair<Int, Double>) = Line(IntArray(p.size) { p[it].first }, DoubleArray(p.size) { p[it].second })

    private fun member(ticker: String, price: Double, prev: Double, sector: String = "IT", h: Holding? = null): Member {
        val q = Quote("L", ticker, ticker, price = price, change = price - prev, changepct = (price - prev) / prev * 100, closeyest = prev)
        return Member(q, sector, h)
    }

    @Test fun dateKeyParsesIsoDates() {
        assertEquals(20261009, Analytics.dateKey("2026-10-09"))
        assertEquals(0, Analytics.dateKey("bad"))
    }

    @Test fun equalWeightedMovesCancelOut() {
        // A +10%, B -10%: each started with the same amount, so the group is flat.
        val a = line(20260101 to 100.0, 20260102 to 110.0)
        val b = line(20260101 to 200.0, 20260102 to 180.0)
        val idx = Analytics.index(listOf(Input(a, null), Input(b, null)), 20260101)!!
        assertEquals(0.0, idx.last, 1e-9)
    }

    @Test fun holdingsWeightTheMove() {
        // 10 shares of A (100→110) and 1 share of B (200→180): +100 and -20 on 1200 → +6.67%.
        val a = line(20260101 to 100.0, 20260102 to 110.0)
        val b = line(20260101 to 200.0, 20260102 to 180.0)
        val idx = Analytics.index(listOf(Input(a, 10.0), Input(b, 1.0)), 20260101)!!
        assertEquals(80.0 / 1200 * 100, idx.last, 1e-9)
    }

    @Test fun laterListingDoesNotCauseAJump() {
        val a = line(20260101 to 100.0, 20260102 to 100.0, 20260103 to 100.0)
        val b = line(20260102 to 50.0, 20260103 to 50.0)   // joins on day 2 with no move
        val idx = Analytics.index(listOf(Input(a, null), Input(b, null)), 20260101)!!
        idx.pct.forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test fun startsFromTheCloseOnOrBeforeTheStartDate() {
        val a = line(20260101 to 100.0, 20260110 to 120.0, 20260120 to 132.0)
        val idx = Analytics.index(listOf(Input(a, null)), 20260115)!!
        assertEquals(20260110, idx.keys.first())
        assertEquals(10.0, idx.last, 1e-9)
        assertEquals(10.0, Analytics.periodReturn(a, 20260115)!!, 1e-9)
    }

    @Test fun flatSeriesGivesNoReturnAndSinglePointIsNull() {
        assertNull(Analytics.periodReturn(line(20260101 to 5.0), 20260101))
        assertNull(Analytics.index(listOf(Input(line(20260101 to 5.0), null)), 20260101))
    }

    @Test fun statsFindBestWorstAndDrawdown() {
        val a = line(1 + 20260100 to 100.0, 2 + 20260100 to 110.0, 3 + 20260100 to 99.0, 4 + 20260100 to 104.0)
        val idx = Analytics.index(listOf(Input(a, null)), 20260101)!!
        val st = Analytics.stats(idx)!!
        assertEquals(10.0, st.bestDay, 1e-9)
        assertEquals(20260102, st.bestKey)
        assertEquals(-10.0, st.worstDay, 1e-9)
        assertEquals(-10.0, st.maxDrawdown, 1e-9)
    }

    @Test fun liveQuoteReplacesOrExtendsTheLastDay() {
        val closes = listOf(Close("2026-10-08", 100.0), Close("2026-10-09", 101.0))
        assertEquals(105.0, Analytics.line(closes, 105.0, 20261009)!!.prices.last(), 0.0)
        assertEquals(3, Analytics.line(closes, 105.0, 20261012)!!.prices.size)
        assertEquals(2, Analytics.line(closes, null, 20261012)!!.prices.size)
    }

    @Test fun unsortedAndBadRowsAreHandled() {
        val closes = listOf(Close("2026-10-09", 101.0), Close("garbage", 5.0), Close("2026-10-08", 100.0), Close("2026-10-07", -1.0))
        val l = Analytics.line(closes, null, 20261009)!!
        assertEquals(listOf(20261008, 20261009), l.keys.toList())
    }

    @Test fun reportSplitsExchangesAndSectors() {
        val ms = listOf(
            member("NSE:AAA", 110.0, 100.0, "IT"), member("BOM:500001", 99.0, 100.0, "IT"),
            member("NSE:BBB", 100.0, 100.0, "Banks"), member("NSE:CCC", 90.0, 100.0, "Banks"),
        )
        val r = Analytics.report(ms)
        assertEquals(4, r.all.count); assertEquals(1, r.all.up); assertEquals(2, r.all.down); assertEquals(1, r.all.flat)
        assertEquals(3, r.nse.count); assertEquals(1, r.bse.count)
        assertEquals(0.0, r.nse.pct!!, 1e-9)           // (+10, 0, -10) / 3
        assertEquals(-1.0, r.bse.pct!!, 1e-9)
        assertEquals(-0.25, r.all.pct!!, 1e-9)
        val it = r.sectors.first { s -> s.name == "IT" }
        assertEquals(4.5, it.all.pct!!, 1e-9)
        assertEquals(10.0, it.nse.pct!!, 1e-9)
        assertEquals(-1.0, it.bse.pct!!, 1e-9)
        assertFalse(r.weighted)
        assertEquals("NSE:AAA", r.gainers.first().ticker)
        assertEquals("NSE:CCC", r.losers.first().ticker)
    }

    private fun assertFalse(b: Boolean) = assertTrue(!b)

    @Test fun weightedAndMoneyOnlyWhenHoldingsExist() {
        val ms = listOf(
            member("NSE:AAA", 110.0, 100.0, h = Holding(10.0, 90.0)),
            member("NSE:BBB", 180.0, 200.0, h = Holding(1.0, 250.0)),
        )
        val r = Analytics.report(ms)
        assertTrue(r.weighted)
        assertEquals(80.0 / 1200 * 100, r.all.pct!!, 1e-9)
        val m = r.money!!
        assertEquals(1280.0, m.value, 1e-9)
        assertEquals(1150.0, m.invested, 1e-9)
        assertEquals(80.0, m.dayPnl, 1e-9)
        assertEquals(130.0, m.totalPnl, 1e-9)
        assertNull(Analytics.report(listOf(member("NSE:AAA", 1.0, 1.0))).money)
        assertFalse(Analytics.report(listOf(ms[0], member("NSE:ZZZ", 1.0, 1.0))).weighted)
    }

    @Test fun emptyListIsSafe() {
        val r = Analytics.report(emptyList())
        assertNull(r.all.pct); assertEquals(0, r.all.count); assertNull(r.money)
        assertNotNull(Analytics.historyReport(emptyList(), emptyMap(), 20260101, false))
    }

    @Test fun historyReportCoversExchangesAndSectors() {
        val ms = listOf(member("NSE:AAA", 110.0, 100.0, "IT"), member("BOM:500001", 110.0, 100.0, "Banks"), member("NSE:NOHIST", 1.0, 1.0, "IT"))
        val lines = mapOf(
            "NSE:AAA" to line(20260101 to 100.0, 20260102 to 110.0),
            "BOM:500001" to line(20260101 to 100.0, 20260102 to 90.0),
        )
        val h = Analytics.historyReport(ms, lines, 20260101, false)
        assertEquals(2, h.covered); assertEquals(3, h.total)
        assertEquals(10.0, h.nse!!.last, 1e-9)
        assertEquals(-10.0, h.bse!!.last, 1e-9)
        assertEquals(0.0, h.combined!!.last, 1e-9)
        assertEquals(10.0, h.sectorReturns.getValue("IT"), 1e-9)
        assertEquals("NSE:AAA", h.stockReturns.first().first.ticker)
    }

    @Test fun largeListIsFast() {
        val rnd = java.util.Random(1)
        val inputs = (0 until 200).map {
            val keys = IntArray(400) { d -> Analytics.dateKey(java.time.LocalDate.of(2025, 1, 1).plusDays(d.toLong())) }
            var p = 100.0
            Input(Line(keys, DoubleArray(400) { p *= 1 + rnd.nextGaussian() / 100; p }), null)
        }
        val t0 = System.nanoTime()
        repeat(10) { Analytics.index(inputs, 20250301)!! }
        val ms = (System.nanoTime() - t0) / 10 / 1e6
        println("index of 200 stocks x 350 days: %.1f ms".format(ms))
        assertTrue("too slow: $ms ms", ms < 200)
    }

    @Test fun sectorsRefineAndGuess() {
        assertEquals("Banks", Sectors.refine("Financial Services", "HDFC Bank Limited"))
        assertEquals("Insurance", Sectors.refine("Financial Services", "ICICI Prudential Life Insurance Company"))
        assertEquals("Financial Services", Sectors.refine("Financial Services", "Bajaj Finance Ltd"))
        assertEquals("Power", Sectors.refine("Power", "Tata Power"))
        assertEquals("Healthcare", Sectors.refine(null, "Sun Pharmaceutical Industries"))
        assertEquals("Oil Gas & Consumable Fuels", Sectors.refine(null, "Hindustan Petroleum Corporation"))
        assertEquals("Other", Sectors.refine(null, "Zzyzx Ventures"))
        assertEquals("Oil & Gas", Sectors.short("Oil Gas & Consumable Fuels"))
    }

    @Test fun formatting() {
        assertEquals("12,34,567.50", num(1234567.5))
        assertEquals("₹1.23 Cr", inr(12_300_000.0))
        assertEquals("-₹4.50 L", inr(-450_000.0))
        assertEquals("+₹1,250", inr(1250.0, signed = true))
        assertEquals("₹ ••••", inr(1250.0, hide = true))
    }
}
