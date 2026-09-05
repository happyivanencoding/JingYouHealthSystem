# Raw Garmin MCP extensions

JingYou 不把 `garmin-py` 的摘要 serializer 当作唯一数据来源。
`src/jingyou_garmin_mcp.py` 在原有 MCP server 上追加 raw read-only tools，保留 Garmin Connect 返回的原始 JSON。

## 新增 raw tools

- `health_hrv_raw(date)` — HRV summary + baseline + intranight `hrvReadings`
- `health_sleep_raw(date)` — 完整睡眠结构
- `health_heart_rate_raw(date)` — 全天心率原始序列
- `health_stress_raw(date)` — intraday stress 数组
- `health_spo2_raw(date)`
- `health_respiration_raw(date)`
- `health_training_readiness_raw(date)`
- `health_training_status_raw(date)`
- `health_daily_raw(date)` — stats / user summary / RHR / intensity / max metrics
- `health_body_battery_raw(start_date, end_date)` — 保留 Body Battery 时间序列
- `health_body_battery_events_raw(date)`
- `activity_details_raw(activity_id)`

原来的 normalized tools 仍保留，用于日常 Agent 问答；raw tools 用于建库、深入分析和检查摘要没有暴露的 Garmin 字段。
