# Sleep review and metric exploration

The sleep experience starts with a question: “Why didn’t I sleep well?” It helps the user inspect the available observations, compare them with their own history, and prepare a more specific question for Coach. It does not infer a cause from a single score or correlation.

## User paths

- **Today → sleep review:** open the latest sleep record, see its date, duration and Garmin score, then inspect personal comparisons, sleep-stage totals and recurring associations.
- **Sleep review → metric exploration:** open a contributor with its record date preserved. Explore sleep duration, HRV, resting heart rate or stress over 7, 30 or 90 calendar days. The date cursor and chart use the same calendar positions; missing days remain visible gaps.
- **Sleep review → Coach:** choose a suggested question or the main Coach action. The app prepares an editable draft containing the sleep record date and the requested context. The user sends the draft explicitly.
- **Trends → exploration:** select a metric and a calendar window, inspect individual dates and the available-record count, and open the methods disclosure or prepare a Coach question.

The methods disclosure is available alongside both sleep review and metric exploration. The main view prioritizes visual comparisons; formulas, paired sample counts and Pearson r are revealed in the expanded explanation.

## Visual reading

- **Current value against personal history:** Today and sleep contributors use a current-value dot over a band representing the middle 50% of preceding observations. Plain-language labels describe whether the reading falls below, within or above that range. Visible delta notation has been removed.
- **Recent sleep at a glance:** the sleep entry combines a Garmin score arc with seven calendar nights of duration bars. A dashed personal-median reference appears when sufficient baseline data exists. Missing nights remain gaps.
- **Sleep composition:** a donut shows the recorded deep, REM and light shares of time asleep, accompanied by their values. Available deep/REM history supplies personal median references. Awake duration and its separate proportion remain outside the asleep composition. This is an aggregate composition, not an overnight stage timeline.
- **One relationship at a time:** the association panel lets the user select HRV, resting heart rate or previous-day stress. Its scatterplot shows one point per valid date-aligned pair, with sleep duration on the horizontal axis. The plain-language relationship comes first; the coefficient and pair count are available on expansion.
- **Trends with context:** the trend line sits over a personal-range band and median reference for the inspected date. A date cursor moves across real calendar positions, and each inspected date uses its own preceding baseline period. Missing values remain gaps rather than being connected across missing days.

## Dates and personal comparisons

Each dashboard component can have a different latest record date. The app preserves component dates and does not treat the dashboard’s overall date as proof that every metric belongs to the same day.

For a sleep record dated **D**:

| Signal | Date used |
| --- | --- |
| Sleep duration and stages | Garmin sleep record date D |
| HRV | D |
| Resting heart rate | D |
| Previous-day stress | D − 1 |

Pairs are joined by date, never by array position. Missing values are not interpolated. The sleep date follows Garmin’s record date; the frontend does not derive a new sleep date from an assumed timezone.

The **personal baseline** is the median of valid observations in the 28 calendar days preceding the compared record. The compared day is excluded, and at least 7 valid records are required before displaying a comparison. The visual **usual range** spans the 25th to 75th percentiles, the middle 50% of this history. Percentiles use linear interpolation at position `(n − 1) × p` in the sorted valid values. This baseline and range are separate from Garmin’s HRV baseline and are not medical thresholds.

## Associations and ratios

The association panel calculates descriptive **Pearson r** between sleep duration and the selected aligned signal in the chosen calendar window. Its scatterplot and coefficient consume the same date-aligned pair list, so the visible observations and statistical sample count agree. The expanded explanation displays r only when there are at least 14 valid pairs and both series have nonzero variance. Invalid dates and nonfinite values are excluded, and records after the window’s end are excluded. The coefficient remains unavailable for insufficient pairs or zero variance.

The formula is `cov(x, y) / (σx × σy)`, with a range of −1 to 1. The interface uses `|r| < 0.3` only as a descriptive boundary for a weak linear relationship. It is not a significance test, confidence level or causal threshold. Device-derived signals may share measurement inputs, so an observed association does not identify why a person slept poorly.

- Deep-sleep and REM proportions use recorded time asleep as the denominator.
- Awake proportion uses recorded time asleep plus recorded awake duration.
- A missing duration or an invalid denominator leaves the proportion unavailable; it is not converted to zero.
- Awake proportion is not labelled as sleep efficiency or an awakening count.

## Current API boundaries

The frontend uses the existing dashboard and trends endpoints. No new backend analysis endpoint is required for the calculations above.

| Available data | Boundary |
| --- | --- |
| Latest sleep duration, score, deep/REM/light/awake totals | No overnight stage timeline or awakening-count endpoint |
| Sleep duration, score, deep and REM trends | Historical awake and light durations are not currently exposed by the trends endpoint |
| HRV, resting heart rate and average stress trends | Daily summaries cannot establish the timing or cause of an overnight event |
| Daily Body Battery charged/drained totals | These represent calendar-day totals, not overnight recovery |
| Latest Body Battery reading and timestamp | A current reading does not measure how much was recovered during sleep |
| Latest readiness snapshot and date | No readiness history is exposed for historical correlation |
| Component freshness dates/timestamps | Different component dates must remain visible and distinct |

`GET /api/trends?days=N` currently returns each series’ latest **N records**, rather than guaranteeing N consecutive calendar days. The frontend requests 90 records by default and filters its 7/30/90-day views by calendar date. Coverage counts and gaps reflect the observations actually returned.

Sleep start/end fields currently retain source values without a sufficiently explicit timezone/source contract for reliable frontend conversion. The sleep review therefore relies on the record date and recorded durations rather than inventing precise local clock times.

## Travel atmosphere and app symbol

The optional blue-tile atmosphere is supplied locally at `.local/private-assets/travel/azulejo.jpg`. This private asset is excluded from the public repository. Public builds without the photo retain the ambient gradient and glass treatment as a usable fallback. The atmosphere preference is stored on the device.

The new `jingyou_symbol_v2.png` app symbol uses an opening and two ripples. It contains no portrait. Image-generation prompts remain in private local documentation and are not part of this public design record.

## Repeated pull interaction

The Material pull gesture receives a fixed `isRefreshing = false`, allowing the visual gesture to reset independently through the spring animation. Network refresh state remains controlled by the ViewModel’s `refreshing` flag, which prevents concurrent refresh requests.

While Garmin is still being read, the user can pull again to reveal the blue-tile atmosphere. This second gesture can travel through its full visual range without starting another network request. Gesture feedback and network progress therefore have independent state while preserving refresh-request exclusivity.

## Validation recorded for this iteration

- All 14 sleep-analysis unit tests passed. The original coverage includes calendar boundaries, exclusion of the compared/future dates, missing and nonfinite values, duplicate dates, date-aligned pairing, minimum pair count, zero variance and ratio denominators. The four added tests cover quartile interpolation, single-record quartiles, offset date pairing with missing dates, and scatter/statistical sample consistency.
- Physical-device checks covered Chinese, English and French in light appearance, and Arabic in dark appearance.
- A second pull while the app was still reading Garmin could fully reveal the photo without starting an additional refresh request.

These checks record the completed coverage of this iteration. Final device QA and release handoff are maintained separately.

Sleep composition also shows deep, REM, light and awake durations in hours (two decimal places), alongside their proportions and available personal references.
