# Sleep review and metric exploration

The sleep experience starts with a question: “Why didn’t I sleep well?” It helps the user inspect the available observations, compare them with their own history, and prepare a more specific question for Coach. It does not infer a cause from a single score or correlation.

## User paths

- **Today → sleep review:** open the latest sleep record, see its date, duration and Garmin score, then inspect personal comparisons, sleep-stage totals and recurring associations.
- **Sleep review → metric exploration:** open a contributor with its record date preserved. Explore sleep duration, HRV, resting heart rate or stress over 7, 30 or 90 calendar days. The date cursor and chart use the same calendar positions; missing days remain visible gaps.
- **Sleep review → Coach:** choose a suggested question or the main Coach action. The app prepares an editable draft containing the sleep record date and the requested context. The user sends the draft explicitly.
- **Trends → exploration:** select a metric and a calendar window, inspect individual dates and the available-record count, and open the methods disclosure or prepare a Coach question.

The methods disclosure is available alongside sleep review, metric exploration and Sleep Insights. The main view prioritizes visual comparisons; paired sample counts, Pearson r and the RF variable-importance method are revealed in the expanded explanation.

## Visual reading

- **Current value against personal history:** Today and sleep contributors use a current-value dot over a band representing the middle 50% of preceding observations. Plain-language labels describe whether the reading falls below, within or above that range. Visible delta notation has been removed.
- **Recent sleep at a glance:** the sleep entry combines a Garmin score arc with seven calendar nights of duration bars. A dashed personal-median reference appears when sufficient baseline data exists. Missing nights remain gaps.
- **Sleep composition:** the center shows overall sleep duration in hours and minutes. Deep, REM and light sleep show recorded duration plus their share of time asleep; awake duration shows its own duration and proportion using sleep plus awake as the denominator. Deep and REM can also be opened as duration or percentage targets in Sleep Insights. This is an aggregate composition, not an overnight stage timeline.
- **One relationship at a time:** the association panel lets the user select HRV, resting heart rate or previous-day stress. Its scatterplot shows one point per valid date-aligned pair, with sleep duration on the horizontal axis. The plain-language relationship comes first; the coefficient and pair count are available on expansion.
- **Trends with context:** the trend line sits over a personal-range band and median reference for the inspected date. A date cursor moves across real calendar positions, and each inspected date uses its own preceding baseline period. Missing values remain gaps rather than being connected across missing days.

- **Sleep Insights:** the main insight path uses one Random Forest result and held-out variable importance. It does not expose a manual regression picker or a candidate list. The target choices are overall sleep duration, Deep duration/percentage and REM duration/percentage; the result is a predictive clue and a review of later records, not a causal explanation.

- **Main navigation:** `MainShell` exposes five root tabs: Today, Sleep, Coach, Activities and Body. Metric and sleep-lab screens open as detail surfaces from those tabs rather than adding another root tab.

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

The **personal baseline** is the median of valid observations in the 42 calendar days preceding the compared record. The compared day is excluded, and at least 7 valid records are required before displaying a comparison. The visual **usual range** spans the 25th to 75th percentiles, the middle 50% of this history. Percentiles use linear interpolation at position `(n − 1) × p` in the sorted valid values. Each inspected calendar date receives its own rolling baseline. This baseline and range are separate from Garmin’s HRV baseline and are not medical thresholds.

## Associations and ratios

The association panel calculates descriptive **Pearson r** between sleep duration and the selected aligned signal in the chosen calendar window. Its scatterplot and coefficient consume the same date-aligned pair list, so the visible observations and statistical sample count agree. The expanded explanation displays r only when there are at least 14 valid pairs and both series have nonzero variance. Invalid dates and nonfinite values are excluded, and records after the window’s end are excluded. The coefficient remains unavailable for insufficient pairs or zero variance.

The formula is `cov(x, y) / (σx × σy)`, with a range of −1 to 1. The interface uses `|r| < 0.3` only as a descriptive boundary for a weak linear relationship. It is not a significance test, confidence level or causal threshold. Device-derived signals may share measurement inputs, so an observed association does not identify why a person slept poorly.

- Deep-sleep and REM proportions use recorded time asleep as the denominator.
- Awake proportion uses recorded time asleep plus recorded awake duration.
- A missing duration or an invalid denominator leaves the proportion unavailable; it is not converted to zero.
- Awake proportion is not labelled as sleep efficiency or an awakening count.

## Current API contract and boundaries

The frontend uses the existing dashboard and trends endpoints. No new backend analysis endpoint is required for the calculations above.

| Available data | Boundary |
| --- | --- |
| Latest sleep duration, score, deep/REM/light/awake totals | No overnight stage timeline or awakening-count endpoint |
| Sleep duration, score, deep/REM/light/awake trends | The trends endpoint returns recorded totals and explicit Local clock fields; invalid or offset-changing clocks remain unavailable |
| HRV, resting heart rate and average stress trends | Daily summaries cannot establish the timing or cause of an overnight event |
| Daily Body Battery charged/drained totals | These represent calendar-day totals, not overnight recovery |
| Latest Body Battery reading and timestamp | A current reading does not measure how much was recovered during sleep |
| JingYou readiness snapshot and readiness history | The recovery index is an engineering composite, not a clinical or causal measure |
| Component freshness dates/timestamps | Different component dates must remain visible and distinct |

`GET /api/trends?days=N` currently returns each series’ latest **N records**, rather than guaranteeing N consecutive calendar days. The frontend requests 90 records by default and filters its 7/30/90-day views by calendar date. Coverage counts and gaps reflect the observations actually returned.

Sleep start/end fields use backend-provided `sleep_start_local` and `sleep_end_local` ISO naive values. The frontend keeps these recorded local wall clocks and never converts them to the phone's current timezone. A missing endpoint, invalid span or start/end offset change leaves the local timing feature unavailable; the app does not invent a clock value.

Sleep timing and trend dates are based on strict calendar positions. The preceding 42 days are used for rolling personal references, and missing days remain gaps. Display durations use hours plus minutes; short values can retain seconds when that is more precise than one minute.

## Travel atmosphere and app symbol

The optional blue-tile atmosphere is supplied locally at `.local/private-assets/travel/azulejo.jpg`. This private asset is excluded from the public repository. Public builds without the photo retain the ambient gradient and glass treatment as a usable fallback. The atmosphere preference is stored on the device.

The new `jingyou_symbol_v2.png` app symbol uses an opening and two ripples. It contains no portrait. Image-generation prompts remain in private local documentation and are not part of this public design record.

## Repeated pull interaction

The Material pull gesture receives a fixed `isRefreshing = false`, allowing the visual gesture to reset independently through the spring animation. Network refresh state remains controlled by the ViewModel’s `refreshing` flag, which prevents concurrent refresh requests.

While Garmin is still being read, the user can pull again to reveal the blue-tile atmosphere. This second gesture can travel through its full visual range without starting another network request. Gesture feedback and network progress therefore have independent state while preserving refresh-request exclusivity.

## Validation recorded for this iteration

- Sleep-analysis unit coverage includes calendar boundaries, exclusion of compared/future dates, missing and nonfinite values, duplicate dates, date-aligned pairing, minimum pair count, zero variance, ratio denominators, strict Local clocks, stage proportions, rolling 42-day references and RF importance boundaries.
- Physical-device checks covered Chinese, English and French in light appearance, and Arabic in dark appearance.
- A second pull while the app was still reading Garmin could fully reveal the photo without starting an additional refresh request.

These checks record the completed coverage of this iteration. Final device QA and release handoff are maintained separately.


## Language behavior

Chinese, English, French, and Arabic apply to navigation, method labels, feature names, time units, error messages, default conversation titles, and editable Coach handoffs. Arabic prose follows RTL; plots remain in chronological LTR order and numeric ranges use bidi isolation. User-written messages and original activity names retain their content.

The activity locale is synchronized with the in-app language preference so Material dialogs, calendar headings, and accessibility descriptions resolve in the same language. Android 13+ uses `LocaleManager.applicationLocales`; earlier versions use a localized activity context and recreation. Configuration recreation does not re-consume an authentication deep link. See [Android per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages).

Static translation coverage and real English/French/Arabic sleep-insight screens were checked; seconds and Random forest labels no longer fall back to Chinese.
