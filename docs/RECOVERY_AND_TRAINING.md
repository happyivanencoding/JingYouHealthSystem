# JingYou recovery and training reference

JingYou exposes one personal recovery reference index from the backend. The dashboard, trends, and Coach receive the same calculation. The public API identifies it as `source="jingyou"` and `formula_version="personal-v1"`.

This is an engineering composite for one person's historical data. It is not a clinical score, diagnosis, treatment recommendation, or medically validated universal scale. The result is useful for consistent personal trend interpretation; it should not be compared with another person's score as if the two scores had a shared clinical meaning.

## Date anchor and baseline windows

For a target date `D`, the strict baseline is the 42 calendar days before `D`, `[D-42, D-1]`. The target date is never included in its own baseline. Dashboard `D` is the latest meaningful sleep record at or before the current date, so an empty same-day placeholder does not become the current recovery date. Historical trends pass each sleep date as an explicit target and recompute the same formula from the same in-memory snapshot.

The sleep short-term mean uses the three calendar dates `[D-2, D]`. It includes only actual valid sleep values from those dates and never reaches farther back to replace a missing night. Its `sample_count` reports how many of those three dates had a value.

Future rows are excluded from a target-date calculation. Each authenticated user is calculated from that user's own `health.db`; no cross-user rows participate.

## Components

The response always keeps the component keys `sleep`, `hrv`, `rhr`, and `load`. A component with insufficient data has a null `score`. `coverage` is the number of components with a score. The overall score is available only when sleep has a score and at least one other component has a score. The available component weights are then renormalized.

### Sleep

Convert each valid baseline `sleep_time_sec` to hours. At least seven baseline samples are required. The target is the baseline 75th percentile, clamped to 7–9 hours:

```text
target = max(7, min(9, P75(baseline sleep hours)))
```

If the target date has a valid sleep duration and the three-day window has at least one valid value:

```text
SleepScore = 100 * (
    0.6 * min(today_hours / target, 1)^2
  + 0.4 * min(last3validMean_hours / target, 1)^2
)
```

Zero is retained as a measured sleep duration and therefore produces a low score. Missing is different from zero and produces a missing component when the target date has no valid duration.

### HRV

Use positive nightly HRV values and transform them with the natural logarithm. At least seven baseline values are required, and the target-date recent window is the seven calendar days `[D-6, D]` with at least three values. Let `m` be the baseline median and `MAD` the median absolute deviation:

```text
scale = max(1.4826 * MAD, 0.06)
z = (mean(log(recent HRV)) - m) / scale
HRVScore = clamp(80 + 10 * min(z, 1) - 22 * max(-z, 0), 0, 95)
```

The component `value` and `baseline` are reported back in the original HRV units; the comparison is performed in log space.

### Resting heart rate

Only positive resting-heart-rate values are valid. A recorded zero is not treated as a physiological heart-rate observation. The recent window is the three calendar days `[D-2, D]`; the baseline is the strict prior 42 days. At least seven baseline values and one recent value are required:

```text
scale = max(1.4826 * MAD(baseline RHR), 1.5)
z = (mean(recent RHR) - median(baseline RHR)) / scale
RHRScore = clamp(80 - 18 * max(z, 0) + 6 * min(max(-z, 0), 1), 0, 95)
```

### Internal training load

JingYou uses one internal unit consistently:

```text
internal_load_AU = duration_minutes * effective_RPE
```

The user's reported RPE is preferred. When no report exists, the backend assigns a category default only for estimation:

| category | default RPE | source |
| --- | ---: | --- |
| `easy_aerobic` | 3 | `estimated` |
| `hard_aerobic` | 6 | `estimated` |
| `anaerobic` | 8 | `estimated` |
| `strength` | 6 | `estimated` |

The default is never presented as the user's actual effort. `effort_source="reported"` means a user supplied the RPE; `effort_source="estimated"` means the category default was used. Strength and weight-training activities are classified as `strength` unless the user supplies a category override. A self-report matters especially for strength training because heart-rate-derived training effect does not describe the external work, set difficulty, or proximity to failure reliably enough to replace the person's perceived effort.

For the load component, recent load is the sum of actual activity records in `[D-2, D]`. Missing activity records do not create synthetic workouts. The prior 28-day reference is `[D-28, D-1]`. Coverage is the union of valid `daily_metrics` dates, valid `sleep_sessions` dates, and actual activity dates in that prior window. A recorded non-exercise day contributes zero load; an unknown date is excluded. At least 14 observed dates are required:

```text
baseline_3day_AU = (prior28_activity_AU / observed_prior28_dates) * 3
ratio = recent3_activity_AU / baseline_3day_AU
LoadScore = clamp(85 - 18 * max(ratio - 1, 0), 20, 95)
```

When the observed baseline is zero, a positive recent load receives the lower clamp; zero recent load has ratio zero. The load component reports the estimated/reported source proportions used for the observed activity records. Garmin's native `activity_training_load` is retained as source data and is never added to this AU value.

## Overall score and interpretation

The fixed component weights are sleep `0.4`, HRV `0.3`, RHR `0.2`, and load `0.1`. If the missing-data rule permits a result, the available weights are normalized to sum to one. The API uses `high` for scores at least 80, `moderate` for scores from 60 up to 80, and `low` below 60.

The choices above are deliberately transparent and auditable. They combine sleep duration, personal HRV/RHR baselines, and training load for this app's longitudinal view; they do not establish medical readiness or a universal health threshold.

## Reference material

The implementation uses these primary references for the underlying ideas, while keeping the JingYou formula itself an explicitly local engineering choice:

- Foster C, et al. Session rating of perceived exertion as a method for quantifying training load. [PubMed PMID 11708692](https://pubmed.ncbi.nlm.nih.gov/11708692/)
- American Academy of Sleep Medicine and Sleep Research Society. Adult sleep duration health advisory. [AASM position statement](https://aasm.org/advocacy/position-statements/adult-sleep-duration-health-advisory/)
- Soligard T, et al. How much is too much? (IOC consensus statement on load and injury/illness risk). [British Journal of Sports Medicine 50(5):281](https://bjsm.bmj.com/content/50/5/281)
