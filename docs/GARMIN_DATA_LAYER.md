# Garmin 数据获取层

## 结论

JingYou HealthSystem V1 的 Garmin 主数据源直接读取 Garmin Connect 账户数据，而不是把 Health Connect 当主来源。

当前实现使用 MIT 许可的 `garmin-py` 2.11.2 作为 Garmin Connect adapter。它底层使用 `python-garminconnect`，并直接暴露 MCP server，因此无需再写一层重复的 endpoint wrapper。

## 为什么不是 Garmin 官方 Connect Developer API

Garmin 官方 Connect Developer Program 是企业/业务用途申请制。个人项目在获得官方 OAuth 2.0 API 资格前，当前 adapter 可以马上覆盖个人账户的大部分 Garmin Connect 指标。

如果以后获得官方 API 资格，只替换 ingestion adapter；AgentDock 上层工具契约和健康数据库不需要跟着重写。

## AgentDock 工具面

AgentDock 动态 MCP server 名称：`garmin`。

核心只读工具包括：

- `health_hrv`
- `health_sleep`
- `health_resting_hr`
- `health_stress`
- `health_body_battery`
- `health_spo2`
- `health_readiness`
- `health_training_status`
- `health_daily_summary`
- `performance_vo2max`
- `performance_thresholds`
- `performance_endurance_score`
- `performance_hill_score`
- `activity_list`
- `activity_get`
- `activity_metrics_describe`
- `activity_detail_metrics`
- `activity_download`
- `coach_snapshot`

## HRV

HRV 是一级数据，不做二级降级处理。当前 Garmin endpoint 能读取至少：

- 日历日期
- weekly average
- last-night average
- Garmin HRV status

底层 Garmin Connect 返回还包含夜间 HRV reading 数据与 baseline 字段；后续持久化层应保留原始响应/细粒度时间序列，不只保存 UI 摘要。

## 活动原始数据

对于具体活动，先用 `activity_metrics_describe` 获取该活动实际记录的 metric schema，再一次性调用 `activity_detail_metrics` 拉取需要的时间对齐通道。完整原始文件用 `activity_download(fmt=original)` 保存 FIT。

## 认证

认证只在用户机器本地执行。项目不保存 Garmin email/password。

会话目录：

`C:\dev\jingyou-health-system\data\users\<user_id>\garmin`

首次登录：

`powershell -ExecutionPolicy Bypass -File .\scripts\garmin-login.ps1`

若 Garmin 要求 MFA，先触发登录，再通过 `garmin:submit_mfa_code` 或 CLI 完成一次性验证码；成功后的 token 会被复用。

## 当前限制

这条个人账户 adapter 使用 Garmin Connect 的非公开 web endpoints，不等同于 Garmin 官方 Developer Program API。Garmin 改动内部 endpoint 时，上游库可能需要更新。这是当前实现真实存在的维护风险，但不影响我们把上层系统设计成稳定 adapter 边界。

