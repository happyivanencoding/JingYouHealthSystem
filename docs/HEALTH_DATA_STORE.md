# JingYou Health data store

## User isolation

Each account has its own physical data directory and SQLite database.

For the current owner profile:

`data/users/<user_id>/health.db`

Garmin authentication is stored separately under that same user root:

`data/users/<user_id>/garmin/`

Each additional account must use a different user id, database, Garmin token home, activity directory and import directory. The Android/backend layer must resolve the current user from the authenticated session; clients do not choose a `user_id` query parameter.

## Storage model

The database deliberately has two layers:

1. `raw_records`: complete Garmin JSON responses by endpoint/date/activity. This is the fidelity layer and should be retained even when only a few fields are currently normalized.
2. Query tables: `daily_metrics`, `hrv_daily`, `sleep_sessions`, `time_series_samples`, `activities`, `activity_laps`, plus preserved WHOOP history. These exist for fast application and agent queries.

Original Garmin activity files are stored outside SQLite under the user's `activities/fit/` directory and referenced by the `activities.fit_path` column.

## Legacy Google Drive import

`C:\GoogleDrive\笔记\60_Data\Health` is **not a runtime dependency**.

The 2026-09-05 import followed this sequence:

1. Copy non-auth data files into `data/users/<user>/imports/legacy_health_20260905/`.
2. Do not copy or read `Private_Runtime/tokens/garmin_tokens.json`.
3. Import the copied SQLite/JSON/JS data into `health.db`.
4. From that point on, the application and agents use only the project-local user data space.

The copied legacy Garmin summaries that predate the data currently exposed by Garmin Connect are labeled `legacy_health`, rather than being presented as newly fetched Garmin records. WHOOP tables retain their own provider identity.

## Garmin historical sync

`scripts/sync_garmin_history.py` is resumable because a full history sync is long enough to encounter Garmin rate limits in normal supported use. Successful endpoint/date/activity reads are persisted in `raw_records` and are skipped on a restart unless `--refresh` is explicitly requested.

Actual historical start dates are account-specific and are stored only in the private local data/handoff. Public documentation intentionally does not include personal health timeline metadata.

