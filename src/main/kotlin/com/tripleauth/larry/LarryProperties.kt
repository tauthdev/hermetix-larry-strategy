package com.tripleauth.larry

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "larry")
data class LarryProperties(
    /** 감시 종목 목록. 종목마다 독립적으로 진입/청산한다 */
    val symbols: List<String> = listOf("AAPL"),
    /** 캔들 주기 (1m/5m/1h/1d). KRX 브로커(kis/kiwoom)는 1d 만 지원 */
    val candleInterval: String = "1h",
    /** 평균 몸통 크기를 계산할 직전 캔들 개수 */
    val lookback: Int = 24,
    /** 진입 조건: 몸통 크기 >= 평균 몸통 x multiplier */
    val multiplier: BigDecimal = BigDecimal("1.2"),
    /** 진입 후 이 시간(시간 단위)이 지나면 강제 청산 */
    val expireHours: Long = 48,
    /** 주문 가능 현금 중 진입에 사용할 비율. 종목 수로 나눠 배분된다 */
    val budgetRatio: BigDecimal = BigDecimal("0.5"),
    /** 전략 호출 주기 (초) */
    val pollSeconds: Long = 60,
)
