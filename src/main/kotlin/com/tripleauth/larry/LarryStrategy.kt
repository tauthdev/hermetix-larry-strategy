package com.tripleauth.larry

import com.tripleauth.nexttrading.client.dto.Candle
import com.tripleauth.nexttrading.client.dto.CandleInterval
import com.tripleauth.nexttrading.strategy.Signal
import com.tripleauth.nexttrading.strategy.StrategyContext
import com.tripleauth.nexttrading.strategy.StrategySpec
import com.tripleauth.nexttrading.strategy.TradingStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Larry Williams 식 변동성 돌파 전략 (롱 온리, 다중 종목).
 *
 * 원본: turtle-trading 의 LarryTradingService (Bybit 백테스트, 롱/숏 양방향).
 * 모의투자 이관에서 바뀐 점:
 * - 공매도 불가 → 상승 돌파(양봉)만 진입한다
 * - 손절은 코어의 소프트웨어 브라켓에 위임한다 (stopLossPrice = 진입 캔들 시가)
 * - 만료 청산은 캔들 개수 대신 경과 시간으로 판정한다
 * - 종목별로 독립 상태를 유지하며, 예산은 종목 수로 나눠 배분한다
 *
 * 진입: 직전 완성 캔들의 몸통 |open-close| 이 앞선 lookback 개 캔들의 평균 몸통 x multiplier
 *      이상인 양봉이면, (주문 가능 현금 x budgetRatio / 종목 수) 만큼 시장가 매수.
 * 청산: 손절(시가) 또는 진입 후 expireHours 경과 시 시장가 전량 매도.
 */
@Component
class LarryStrategy(
    private val properties: LarryProperties,
) : TradingStrategy {

    private val logger = KotlinLogging.logger { }

    override val spec = StrategySpec(
        name = "larry",
        symbols = properties.symbols,
        candleInterval = CandleInterval.HOUR_1,
        candleLimit = properties.lookback + 2, // lookback + 판정 대상 + 진행 중 캔들
        pollInterval = Duration.ofSeconds(properties.pollSeconds),
    )

    /** 종목별 마지막 판정 캔들 시각 — 같은 캔들로 중복 진입하지 않기 위함 */
    internal val lastEvaluated = ConcurrentHashMap<String, Instant>()

    /** 종목별 진입 시각 — 만료 청산 판정용. 재시작 시 보유가 발견되면 그 시점부터 다시 센다 */
    internal val entryAt = ConcurrentHashMap<String, ZonedDateTime>()

    override fun decide(context: StrategyContext): List<Signal> =
        properties.symbols.flatMap { symbol -> decideForSymbol(symbol, context) }

    private fun decideForSymbol(symbol: String, context: StrategyContext): List<Signal> {
        if (context.hasPosition(symbol)) {
            return decideExit(symbol, context)
        }

        entryAt.remove(symbol)
        if (context.hasOpenOrder(symbol)) return emptyList()

        return decideEntry(symbol, context)
    }

    private fun decideEntry(symbol: String, context: StrategyContext): List<Signal> {
        val candles = context.candles(symbol)
        // 마지막 캔들은 진행 중이므로 제외하고, 완성된 캔들만 사용한다
        if (candles.size < properties.lookback + 2) return emptyList()
        val completed = candles.dropLast(1)

        val target = completed.last()
        if (lastEvaluated[symbol] == target.timestamp) return emptyList()
        lastEvaluated[symbol] = target.timestamp

        val history = completed.dropLast(1).takeLast(properties.lookback)
        val avgBody = history.map { it.body() }
            .reduce(BigDecimal::add)
            .divide(BigDecimal(history.size), 8, RoundingMode.HALF_EVEN)

        val targetBody = target.body()
        val threshold = avgBody.multiply(properties.multiplier)

        if (targetBody < threshold) return emptyList()

        // 롱 온리: 음봉 돌파(하락 방향)는 건너뛴다
        if (target.open >= target.close) {
            logger.info { "[$symbol] 하락 돌파 감지 - 롱 온리 전략이므로 스킵 / body=$targetBody avg=$avgBody" }
            return emptyList()
        }

        val price = context.quote(symbol)?.price ?: return emptyList()
        val budget = context.buyingPower
            .multiply(properties.budgetRatio)
            .divide(BigDecimal(properties.symbols.size), 8, RoundingMode.HALF_EVEN)
        val quantity = budget.divide(price, 0, RoundingMode.DOWN)

        if (quantity < BigDecimal.ONE) {
            logger.warn { "[$symbol] 예산 부족으로 진입 스킵 / budget=$budget price=$price" }
            return emptyList()
        }

        entryAt[symbol] = context.now

        logger.info { "[$symbol] 진입 / qty=$quantity price=$price 손절가=${target.open} (body=$targetBody >= $threshold)" }

        return listOf(
            Signal.Buy(
                symbol = symbol,
                quantity = quantity,
                stopLossPrice = target.open,
            ),
        )
    }

    private fun decideExit(symbol: String, context: StrategyContext): List<Signal> {
        // 재시작 등으로 진입 시각을 모르면 지금부터 만료 시계를 다시 센다
        val openedAt = entryAt.getOrPut(symbol) { context.now }

        if (Duration.between(openedAt, context.now) < Duration.ofHours(properties.expireHours)) {
            return emptyList()
        }

        val quantity = context.holding(symbol)?.quantity ?: return emptyList()

        logger.info { "[$symbol] 만료 청산 / qty=$quantity (진입 후 ${properties.expireHours}시간 경과)" }
        entryAt.remove(symbol)

        return listOf(Signal.Sell(symbol = symbol, quantity = quantity))
    }

    private fun Candle.body(): BigDecimal = (open - close).abs()
}
