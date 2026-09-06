# JingYou 训练负荷与恢复方法建议（v1）

本文只定义 wellness / training coach 的透明计算口径，不诊断过度训练、过度使用综合征或受伤风险，也不把任何阈值称为安全区。工程阈值用于决定首页提示的强弱，不能替代医学评估或教练判断。

## 首页应该回答什么

首页同时展示三件事：最近 7 个日历日各训练类型的负荷总量、此前独立 28 个日历日形成的每周参考、以及恢复信号是否支持今天按计划训练。负荷上升只能说明近期安排变密或变重；它不能单独证明训练不足、过度训练或受伤风险。

首页建议显示：

- `recent7Total`：最近 7 天总负荷，按有氧、力量和其它类别分别列出，并显示每类 session 数、活动日数和覆盖率。
- `prior28WeeklyReference`：不包含最近 7 天的此前 28 天总负荷除以 4，作为较早的每周参考。
- `loadShift`：最近 7 天日均负荷相对此前 28 天日均参考的变化方向，同时显示绝对 AU 和覆盖天数。
- `recoverySignals`：睡眠、HRV、RHR 和主观感受的个人基线偏离，每项保留来源、有效记录数和缺测状态。
- `todayAction`：按用户目标、最近 session 类型、最近一次高强度训练距今时间和恢复信号，给出“按计划 / 降低强度或量 / 先恢复并观察”。

## 八条方法约束

1. session-RPE 负荷使用 `duration_min × session_RPE_0_to_10`，单位写作 AU。这个公式来自 Foster 等人的原始方法；它是个人内部负荷指标，不是跨人或跨运动项目的统一物理量。[Foster et al., 2001](https://pubmed.ncbi.nlm.nih.gov/11708692/)

2. Garmin `trainingLoad`、session-RPE AU、心率 TRIMP 或其它来源不能直接相加。每条负荷必须带 `source` 和 `loadType`；同一 session 如果同时有多个来源，首页选一个主指标，其余作为对照。

3. 不使用 ACWR 作为风险分数、伤病预防处方或“安全区”；v1的相对负荷只用于描述习惯变化，动作层另外结合恢复、感受和目标。ACWR 的因果解释、比值统计性质、窗口选择和伤病方向性都存在基本问题，现有批评不支持把它用于降低伤病风险的训练推荐。[Impellizzeri et al., 2020](https://pubmed.ncbi.nlm.nih.gov/32502973/)，[Impellizzeri et al., 2020, Part 2](https://pubmed.ncbi.nlm.nih.gov/32991699/)

4. 最近 7 天和此前 28 天必须是两个独立窗口；缺少同步覆盖的日期不能默认为休息日。低覆盖时显示“无法可靠比较”，不输出方向性动作。

5. HRV、RHR、睡眠和主观感受都使用用户自己的历史基线。不要把单次 HRV 下降、RHR 升高或一晚睡短解释成过度训练；个体差异和不同信号之间的不一致是常见的。[Monitoring Sleep and Nightly Recovery](https://pubmed.ncbi.nlm.nih.gov/39860902/)，[Monitoring Individual Sleep and Nocturnal HRV](https://pubmed.ncbi.nlm.nih.gov/33981255/)

6. 力量和有氧保持两条负荷轨道。可以展示各自的 AU、session 数和趋势，不能把力量 session 与有氧 session 的 AU 合成一个百分比后说“力量占总训练 40%”。用户目标决定优先看哪条轨道；ACSM 也强调训练量和负荷应按目标个体化。[ACSM 2026 resistance guidance](https://acsm.org/resistance-training-guidelines-update-2026/)

7. “该练还是恢复”是工程动作建议，不是训练不足或过度训练诊断。长期表现下降、疲劳、情绪和睡眠异常的诊断没有单一被普遍接受的 marker，过度训练综合征仍需要排除其它原因。[ECSS/ACSM consensus](https://pubmed.ncbi.nlm.nih.gov/23247672/)

8. 目标、最近 session 类别和最后一次高强度训练必须参与方向判断。没有用户目标时给中性的“继续观察/选择轻松 session”，不要因为最近负荷低就推断用户训练不足。

## 负荷计算口径

对每个类别 `c`，先得到每天的已观测负荷 `L_c(d)`。没有可靠 session 或同步覆盖的日期保留缺测；只有在数据层明确知道该日有完整活动覆盖且没有 session 时，才可记为 0。

以目标日 `T` 为例：

```text
recent7Total(c) = sum L_c(d), d = T-6 ... T
prior28Total(c) = sum L_c(d), d = T-34 ... T-7
prior28WeeklyReference(c) = prior28Total(c) / observedActivityDays(T-34...T-7) * 7
recent7DailyAverage(c) = recent7Total(c) / 7
loadShiftPct(c) = 100 * (recent7DailyAverage(c) - prior28WeeklyReference(c) / 7)
                 / (prior28WeeklyReference(c) / 7)
```

实际实现中应先检查覆盖率：最近窗口至少 `7/7` 个日历日、此前窗口至少 `24/28` 个日历日，且主负荷来源在这些日期有同步覆盖。否则保留总量和覆盖信息，但不计算 `loadShiftPct` 的动作结论。

`loadShiftPct` 的 v1 展示边界：

- `> +25%`：近期负荷明显上升，提示结合恢复信号观察；
- `-25% <= value <= +25%`：近期与个人参考接近；
- `< -25%`：近期负荷低于个人参考。

这三个区间是产品提示阈值，不是 ACWR 安全区，也不表示受伤概率。分母接近 0、此前负荷过少或覆盖不足时只显示 AU 和“参考不足”。

首页还应分开显示 session 数、活动日数、最近一次 session 日期、最近一次高强度 session 日期。这样“总量相同但集中在两天”和“平均分布在六天”不会被一个 AU 数字抹平。

## 个人恢复 aggregate

建议在每个记录日以前取 28 天有效记录建立个人参考，至少需要 14 个有效日。HRV、RHR、睡眠时长/睡眠评分和主观感受分别计算个人偏离；不把 Garmin 或 WHOOP 的总体分数当作真值。可用的稳健标准化是：

```text
robustZ(x) = 0.6745 * (x - personalMedian) / MAD
```

其中 HRV 越高通常方向越好，RHR 越低通常方向越好；睡眠和主观感受的方向要按具体字段定义。`MAD=0` 或有效记录不足时，该分量为空，不填 0。

当前个人版方法固定使用 `personal-v1` 权重，并在界面显示每项权重和覆盖：

```text
sleep            40%
HRV              30%
RHR              20%
recent load      10%
```

`30/25/20/25`（睡眠/HRV/RHR/主观感受）是曾考虑但当前不采用的候选方案。主观感受不进入这个数值分数；它作为独立的行动修正项，与负荷变化和最近强度一起影响“今天训练还是恢复”的建议。睡眠项内部可合并睡眠时长与评分，但不要把时长和评分当成两个独立的大权重，因为它们可能共享设备测量。

若产品需要一个 Whoop 式总分，可将每个稳健分量先映射为 `clip(50 + 25 * robustZ, 0, 100)`，再按 `personal-v1` 权重加权。该分数只叫“个人恢复参考”，同时展示原始分量、有效天数和方向；不要让一个 0–100 数字掩盖缺测或冲突信号。主观感受仍应单独显示，并作为建议修正项。WHOOP 官方资料可作为交互结构参考：其 Recovery 在睡眠后整合 HRV、RHR、睡眠和呼吸等信号；这不等于公开了可复用的临床权重。[WHOOP Recovery](https://support.whoop.com/s/article/WHOOP-Recovery?language=en_US)

## v1 动作阈值

以下是可实现的工程经验阈值，必须在 UI 标注“v1 规则，不是临床边界”：

| 条件 | 首页动作 | 解释边界 |
|---|---|---|
| 覆盖合格、恢复 aggregate `>= +0.5`，且负荷变化 `<= +25%` | 按用户目标训练 | 表示当前记录支持按计划，不代表一定安全 |
| 负荷变化 `> +25%`，或 aggregate 在 `-0.5..+0.5`，或仅一个恢复分量偏低 | 保留目标但降低强度/量，优先轻松 session | 不把一次变化叫过度训练 |
| aggregate `<= -1.0` 且至少两个独立分量偏低；或主观感受 `<=2/5` 连续两天并且负荷明显上升 | 先恢复、补充睡眠和轻量活动，再观察 | 如果持续或伴随症状，提示专业评估；不下诊断 |
| 覆盖不足、基线不足或负荷来源混杂 | 暂不判断，先补齐记录 | 不用 0 填缺测，也不因缺数据推荐加量 |

如果只有 HRV 低、RHR 高或睡眠短中的一项，不直接进入“恢复”档；先显示该分量及其个人历史位置。连续性比单日颜色更重要。

如果产品额外显示便于理解的比值 `recent7DailyAverage / prior28DailyAverage`，`>1.25` 或 `<0.75` 只能作为“明显高于/低于近期习惯”的描述阈值。它不是 ACWR，也不是安全区、危险区或伤病预测器；动作仍由恢复分量、最近强度、主观感受和用户目标共同决定。

## 训练方向逻辑

方向由 `userGoal × categoryLane × lastSession × recovery` 组成：

- 力量目标优先读取力量 session 的最近 7/28 日轨道、最近一次力量训练距今时间和恢复状态；没有近期力量 session 时提示“可以安排一次轻量/技术性力量训练”，不要说“训练不足”。
- 耐力目标优先读取有氧 session 的轨道、最近一次高强度有氧距今时间和恢复状态；有氧负荷低不自动推断能力下降。
- 混合目标同时显示力量和有氧两条轨道，允许用户看到某一类上升而另一类稳定。
- 最近 24 小时内有高强度 session 且恢复进入 caution/recover 时，优先建议轻松训练或恢复；这是 v1 操作规则，不是生理阈值。
- 没有目标时不替用户选择“应该增肌还是跑步”，只给负荷方向、恢复证据和一个可选的低风险下一步。

## 不采用的做法

- 不显示 `0.8–1.3` 之类的 ACWR“安全区”。ACWR 的因果用途和推荐价值没有得到支持，且比值会制造统计伪影。[ACWR conceptual pitfalls](https://pubmed.ncbi.nlm.nih.gov/32502973/)
- 不用“连续低恢复”直接诊断 OTS、NFOR 或 injury risk。ECSS/ACSM 共识指出现有 marker 没有一个满足普遍接受的全部标准。[Overtraining consensus](https://pubmed.ncbi.nlm.nih.gov/23247672/)
- 不把力量和有氧的 AU 做跨类别百分比排名。
- 不把缺失日补成休息日，不把 wearable proprietary score 拆成看似科学的固定因果权重。
- 不把“最近训练少”解释为“训练不足”；训练方向必须结合目标、类别、最近 session 和恢复。

## 来源与适用边界

1. [Foster et al., A new approach to monitoring exercise training, PubMed](https://pubmed.ncbi.nlm.nih.gov/11708692/)：session-RPE 作为多种运动形式的训练量化方法；支持 sRPE AU 的工程输入，不证明跨项目可比或可诊断。
2. [IOC consensus on load in sport and risk of injury, BJSM/PubMed](https://pubmed.ncbi.nlm.nih.gov/27535989/)：负荷管理应同时考虑训练、竞赛、心理负荷、well-being 和伤病监测；这是监测框架，不是个人伤病预测公式。
3. [Impellizzeri et al., ACWR conceptual issues and fundamental pitfalls, PubMed](https://pubmed.ncbi.nlm.nih.gov/32502973/)；[Part 2 methodological pitfalls](https://pubmed.ncbi.nlm.nih.gov/32991699/)：不支持把 ACWR 用作降低伤病风险的训练推荐。
4. [Meeusen et al., ECSS/ACSM overtraining consensus, PubMed](https://pubmed.ncbi.nlm.nih.gov/23247672/)：NFOR 与 OTS 难以区分，现有 marker 没有普遍接受的诊断标准。
5. [Olstad et al., wearable sleep and nightly recovery during intensified training, PubMed](https://pubmed.ncbi.nlm.nih.gov/39860902/)：主观 strain/muscle soreness 与睡眠和 nightly recovery 的变化不总是一致，且个体差异明显；支持保留多信号与主观感受，不支持单一指标决定训练。
6. [Monitoring individual sleep and nocturnal HRV in soccer players, PubMed](https://pubmed.ncbi.nlm.nih.gov/33981255/)：睡眠和夜间 HRV 存在明显个体差异；支持个人基线和个体内比较。
7. [WHOOP Recovery 官方说明](https://support.whoop.com/s/article/WHOOP-Recovery?language=en_US)：仅作为“夜间恢复 aggregate + 可解释分量”的产品交互参考，不把 WHOOP 的专有实现当作公开科学标准。
8. [ACSM 2026 resistance guidance](https://acsm.org/resistance-training-guidelines-update-2026/)：训练应按目标个体化，规律性和可持续性优先；支持把力量作为独立目标轨道，不把低负荷直接解释成训练不足。
