package com.tripleauth.larry

import com.tripleauth.nexttrading.client.dto.AccountResponse
import com.tripleauth.nexttrading.client.dto.Candle
import com.tripleauth.nexttrading.client.dto.Holding
import com.tripleauth.nexttrading.client.dto.Quote
import com.tripleauth.nexttrading.strategy.Signal
import com.tripleauth.nexttrading.strategy.StrategyContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZonedDateTime

class LarryStrategyTest {

    private val properties = LarryProperties(
        symbol = "AAPL",
        lookback = 5,
        multiplier = BigDecimal("1.2"),
        expireHours = 48,
        budgetRatio = BigDecimal("0.5"),
    )

    private lateinit var strategy: LarryStrategy

    @BeforeEach
    fun setUp() {
        strategy = LarryStrategy(properties)
    }

    /** 몸통 1 크기의 평탄한 캔들 lookback 개 + 판정 대상 + 진행 중 캔들 */
    private fun candles(targetOpen: String, targetClose: String): List<Candle> {
        val base = Instant.parse("2026-08-03T00:00:00Z")
        val flat = (0 until properties.lookback).map { i ->
            candle(base.plusSeconds(i * 3600L), open = "100", close = "101")
        }
        val target = candle(base.plusSeconds(properties.lookback * 3600L), open = targetOpen, close = targetClose)
        val inProgress = candle(base.plusSeconds((properties.lookback + 1) * 3600L), open = targetClose, close = targetClose)
        return flat + target + inProgress
    }

    private fun candle(ts: Instant, open: String, close: String) = Candle(
        timestamp = ts,
        open = BigDecimal(open),
        high = BigDecimal(close).max(BigDecimal(open)),
        low = BigDecimal(open).min(BigDecimal(close)),
        close = BigDecimal(close),
        volume = 1000,
    )

    private fun context(
        candles: List<Candle>,
        price: String = "108",
        holdingQty: String? = null,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) = StrategyContext(
        now = now,
        quotes = mapOf(
            "AAPL" to Quote(
                symbol = "AAPL", price = BigDecimal(price), bidPrice = null, askPrice = null,
                volume = 0, change = null, changeRate = null, timestamp = Instant.now(),
            ),
        ),
        candles = mapOf("AAPL" to candles),
        account = AccountResponse("acc_main", null, "USD", BigDecimal("10000"), BigDecimal("10000"), "ACTIVE"),
        holdings = holdingQty?.let {
            mapOf(
                "AAPL" to Holding("AAPL", BigDecimal(it), BigDecimal("100"), null, null, null, null),
            )
        } ?: emptyMap(),
        openOrders = emptyList(),
        buyingPower = BigDecimal("10000"),
    )

    @Test
    fun `평균 몸통의 1_2배 이상 양봉이면 시가를 손절가로 매수한다`() {
        // 평균 몸통=1, 대상 몸통=8 (100→108 양봉)
        val signals = strategy.decide(context(candles(targetOpen = "100", targetClose = "108")))

        assertThat(signals).hasSize(1)
        val buy = signals[0] as Signal.Buy
        // 예산 10000 * 0.5 / 현재가 108 = 46주
        assertThat(buy.quantity).isEqualByComparingTo(BigDecimal("46"))
        assertThat(buy.stopLossPrice).isEqualByComparingTo(BigDecimal("100"))
    }

    @Test
    fun `하락 돌파는 롱 온리이므로 진입하지 않는다`() {
        val signals = strategy.decide(context(candles(targetOpen = "108", targetClose = "100")))

        assertThat(signals).isEmpty()
    }

    @Test
    fun `몸통이 작으면 진입하지 않는다`() {
        val signals = strategy.decide(context(candles(targetOpen = "100", targetClose = "101.1")))

        assertThat(signals).isEmpty()
    }

    @Test
    fun `같은 캔들로 두 번 진입하지 않는다`() {
        val data = candles(targetOpen = "100", targetClose = "108")

        assertThat(strategy.decide(context(data))).hasSize(1)
        assertThat(strategy.decide(context(data))).isEmpty()
    }

    @Test
    fun `보유 중이고 만료 시간이 지나면 전량 매도한다`() {
        val now = ZonedDateTime.now()
        strategy.entryAt = now.minusHours(49)

        val signals = strategy.decide(context(candles("100", "101"), holdingQty = "46", now = now))

        assertThat(signals).hasSize(1)
        val sell = signals[0] as Signal.Sell
        assertThat(sell.quantity).isEqualByComparingTo(BigDecimal("46"))
    }

    @Test
    fun `보유 중이지만 만료 전이면 아무것도 하지 않는다`() {
        val now = ZonedDateTime.now()
        strategy.entryAt = now.minusHours(1)

        val signals = strategy.decide(context(candles("100", "101"), holdingQty = "46", now = now))

        assertThat(signals).isEmpty()
    }

    @Test
    fun `재시작으로 진입 시각을 모르면 발견 시점부터 만료를 다시 센다`() {
        val now = ZonedDateTime.now()

        val signals = strategy.decide(context(candles("100", "101"), holdingQty = "46", now = now))

        assertThat(signals).isEmpty()
        assertThat(strategy.entryAt).isNotNull()
    }
}
