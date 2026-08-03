# larry-strategy

Larry Williams 식 **변동성 돌파 전략** (롱 온리) — [next-trading-core](https://github.com/hanaset/next-trading-core) 기반 넥스트증권 모의투자 봇.

원본은 [turtle-trading](https://github.com/hanaset) 프로젝트의 Bybit 백테스트 전략이며, 주식 모의투자 환경(공매도 불가)에 맞게 롱 방향만 이식했습니다.

## 전략 로직

- **진입**: 직전 완성된 1시간봉의 몸통(|시가-종가|)이 앞선 `lookback`개 캔들의 평균 몸통 × `multiplier` 이상인 **양봉**이면, 주문 가능 현금의 `budget-ratio` 만큼 시장가 매수
- **손절**: 진입 캔들의 시가 (코어의 소프트웨어 브라켓이 자동 실행)
- **만료 청산**: 진입 후 `expire-hours` 경과 시 시장가 전량 매도

## 실행

```bash
export NEXT_CLIENT_ID=pk_test_...
export NEXT_CLIENT_SECRET=sk_test_...
./gradlew bootRun
```

## 설정 (application.yml)

```yaml
larry:
  symbol: AAPL        # 감시 종목
  lookback: 24        # 평균 몸통 계산 캔들 수
  multiplier: 1.2     # 돌파 배수
  expire-hours: 48    # 만료 청산 시간
  budget-ratio: 0.5   # 진입 예산 비율
  poll-seconds: 60    # 판정 주기
```
