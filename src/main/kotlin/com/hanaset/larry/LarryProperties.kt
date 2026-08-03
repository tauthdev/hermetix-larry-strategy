package com.hanaset.larry

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "larry")
data class LarryProperties(
    /** 감시 종목 */
    val symbol: String = "AAPL",
    /** 평균 몸통 크기를 계산할 직전 캔들 개수 */
    val lookback: Int = 24,
    /** 진입 조건: 몸통 크기 >= 평균 몸통 x multiplier */
    val multiplier: BigDecimal = BigDecimal("1.2"),
    /** 진입 후 이 시간(시간 단위)이 지나면 강제 청산 */
    val expireHours: Long = 48,
    /** 주문 가능 현금 중 진입에 사용할 비율 (0.5 = 50%) */
    val budgetRatio: BigDecimal = BigDecimal("0.5"),
    /** 전략 호출 주기 (초) */
    val pollSeconds: Long = 60,
)
