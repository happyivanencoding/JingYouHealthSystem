# JingYou Rhythm v1

`jingyou-rhythm-v1` connects recovery, recorded training load and the next training direction. It is a transparent personal planning method. Its labels are not diagnoses or injury probabilities, and its numeric boundaries are initial engineering choices.

## Four parts on Home

- **Recovery:** the existing `personal-v1` reference from sleep, HRV, resting heart rate and recent load. Its 40/30/20/10 weights, strict prior-42-day baseline and missing-data behavior are unchanged. [Recovery formula](RECOVERY_AND_TRAINING.md)
- **Stimulus:** recorded AU summed over `[D-6,D]`, the latest seven calendar days.
- **Accumulation:** recorded AU summed over `[D-27,D]`, the latest 28 calendar days. This is training history, not a measured fitness gain.
- **Direction:** recovery and today's optional feeling take priority; load changes, recent intensity, category exposure and the user's chosen goal guide the next session.

The default goal is balanced aerobic and strength fitness. Every user can independently choose balanced, endurance or strength. An optional daily `fresh/normal/tired` check-in changes the action recommendation; it is not blended into the recovery score. Fresh never cancels low recovery or short sleep.

## Load and its personal reference

Each session uses `minutes × effective RPE` in AU. Reported RPE is preferred. Missing RPE uses labeled category estimates: easy aerobic 3, hard aerobic 6, anaerobic 8, strength 6. These estimates are not the user's actual subjective effort and are never added to Garmin's proprietary load scale. Estimated/reported proportions refer to the latest 28 days.

The 7-day reference window is `[D-34,D-7]`; the latest week does not appear again in its own comparison. The 28-day reference uses the separate preceding 28 days `[D-55,D-28]`, without overlap with the current 28-day window:

```text
referenceWeeklyAU = recordedPrior28AU / coveredPrior28Days × 7
relativeLoad = recordedRecent7AU / referenceWeeklyAU
reference28AU = recordedEarlier28AU / coveredEarlier28Days × 28
relative28Load = recordedRecent28AU / reference28AU
```

The current comparison window must have complete activity-sync coverage (7/7 or 28/28); each earlier reference requires at least 24 of its 28 days. Coverage comes from successful activity-history sync ranges and confirmed recent-list refreshes. Neither wellness dates nor the presence of an activity row proves a whole day's activity coverage. A capped recent list excludes its oldest returned day because that day could be partial. Reference scaling uses only loads on verified covered dates and identifies when it was scaled from 24–27 covered days. Partial-day activities still contribute to recorded totals. Unknown dates are not filled as rest days.

Recorded totals remain visible when coverage is insufficient; a comparative trend is withheld. A zero reference is labeled **building**, with no ratio division. Above 1.25 is **rising**, below 0.75 is **lighter**, otherwise **usual**. These describe recorded habits and are not a physiological safe zone.

Home renders separate 7-day and 28-day reference bands, while Activities provides their historical curves. The 7-day marker is compared with the non-overlapping weekly-equivalent reference; the 28-day marker is compared with the separate `[D-55,D-28]` 28-day reference. A band or comparative curve is omitted when its current window or historical reference lacks the required coverage; the recorded total can still be shown with its coverage label.

## Direction rules

1. A tired check-in, recovery score below 60, or latest sleep duration below 85% of the current sleep target favors recovery and easy activity.
2. Rising 7-day or 28-day load, or high-intensity activity on at least two distinct days in the latest three days, favors consolidation. Those are separate explanations; dense intensity does not imply that total load rose.
3. Missing recovery, missing HRV/RHR contribution, or insufficient activity coverage keeps the direction conservative instead of using a category gap to recommend extra work.
4. Otherwise, goal and recent category exposure guide the next session. Balanced fitness initially checks for two distinct strength days and two aerobic days per week; a recent strength day also affects the choice. These are planning references, not a complete exercise guideline or proof of undertraining. No anaerobic or hard-aerobic quota is imposed.
5. Predominantly estimated effort keeps advice conservative but does not erase a useful goal direction. Low recorded load does not automatically mean more training is appropriate.

Same-day easy/hard aerobic sessions count as one aerobic day. Same-day hard/anaerobic sessions count as one high-intensity day. Strength and aerobic exposure are considered separately; their AU shares are not interpreted as shares of physiological adaptation.

## Interaction and Coach

Activities switches between daily AU bars, rolling 7-day totals and rolling 28-day totals on a shared horizontally draggable four-week viewport. Rolling history is computed by backend prefix sums, uses the same definition as Home and leaves incomplete windows as gaps; recorded partial sums remain available separately. Each mode can be filtered by the four activity categories. The chart, date range, four-week totals and activity list use the same visible dates. Its scale stays fixed across the loaded history for a given category filter. The activity calendar uses a diary-style month view: dates show actual activity category icons and counts, and selecting a day reveals its activity names, durations, AU and estimate labels. Confirming a date jumps to the four-week window ending on that day; the selected end date moves the same 28-day viewport and both reference lines. No upper arrow navigation is required.

Home gives each load its reference band, current-value marker and below/close/above-reference label. The pale band is the initial 75–125% descriptive comparison band, not a safety or optimal-training zone. The Home method sheet exposes formulas, coverage, goals, daily feeling and research links. The same backend result is available to Coach, so an explanation uses the same numbers and dated planning rules without forcing a full recap into every response.

Research, assumptions and limits are recorded in [the research notes](JINGYOU_METHOD_RESEARCH.md). The rationale includes [session-RPE](https://pubmed.ncbi.nlm.nih.gov/11708692/), [subjective monitoring](https://pubmed.ncbi.nlm.nih.gov/26423706/), [workload-ratio limitations](https://pubmed.ncbi.nlm.nih.gov/32502973/) and [the ECSS/ACSM overtraining consensus](https://pubmed.ncbi.nlm.nih.gov/23247672/).
