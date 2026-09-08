# JingYou HealthSystem

个人 Garmin 数据、原生 Android、长期健康数据库与 AI Coach 平台。

运行时模型调用遵守 **transport-only**：backend 负责健康/聊天/记忆数据读取、上下文选择、校验与持久化；AgentDock ACP 目前只作为可替换的模型 transport。

## 当前数据链

Garmin Watch → Garmin Connect → `garmin-py` / 本地数据库 → FastAPI backend → `model_transport` → 当前 AgentDock ACP/Codex（未来可替换 direct API）

## Runtime model boundary

所有产品运行时 ACP 调用都必须是 transport-only。模型不自行读取项目、数据库、文件、网页或 MCP，也不负责持久化和任务编排。当前唯一业务可见边界是 `server/model_transport.py`；ACP session/cwd/mode 等协议细节只允许存在于该 adapter 与 `server/run-health-agent.ps1` 内。

当前 Codex ACP 仍会注入少量固定 host bootstrap，项目业务不得依赖这些内容；详见 `docs/BACKEND_API.md`。

## 已接入能力

- HRV
- 睡眠及睡眠阶段/评分
- 静息心率、日汇总、步数
- Stress
- Body Battery
- SpO2
- Training Readiness
- Training Status / Acute Load / Chronic Load / ACWR / Load Focus
- VO2 Max、乳酸阈值、Endurance Score、Hill Score、比赛预测、个人记录
- 活动列表、详细指标、圈、心率区间、天气
- 活动逐采样 metric stream
- FIT/TCX/GPX/KML/CSV 下载
- 训练课表读取/创建/安排（后续使用）

## 首次安装

```powershell
cd C:\dev\jingyou-health-system
uv sync
```

## Garmin 登录

不要把 Garmin 密码写入 `.env` 或项目文件。首次只需在本机终端运行：

```powershell
.\scripts\garmin-login.ps1
```

成功后 token 保存在：

`data\users\<user_id>\garmin\garmin_tokens.json`

之后 AgentDock 和 CLI 都复用该会话。

## 验证数据

```powershell
.\scripts\garmin-doctor.ps1
.\scripts\garmin-smoke.ps1
```

指定日期：

```powershell
.\scripts\garmin-smoke.ps1 -Date 2026-09-05
```

## AgentDock

本机 AgentDock 已注册动态 MCP server：`garmin`。
当前基于 JingYou 增强 Garmin MCP，并把 `GARMIN_HOME` 指向当前认证用户自己的 `data\users\<user_id>\garmin`。

详细设计见 `docs/GARMIN_DATA_LAYER.md`。

## 原始数据层

除 49 个标准 Garmin MCP 工具外，JingYou 追加 raw read-only tools，避免 HRV/睡眠/Stress/Body Battery 被摘要 serializer 丢字段。见 `docs/GARMIN_RAW_TOOLS.md`。


