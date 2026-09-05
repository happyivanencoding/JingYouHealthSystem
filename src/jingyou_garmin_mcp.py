"""JingYou Garmin MCP server.

Composes garmin-py's convenient normalized tools with raw Garmin Connect
read-only tools so JingYou can preserve fine-grained health payloads instead of
being limited to UI-oriented summaries.
"""
from __future__ import annotations

from typing import Any, Callable

from mcp_types import ToolAnnotations

from garmin_cli import backend
from garmin_cli.config import CliConfig, load_config
from garmin_cli.mcp_server import create_mcp_server
from garmin_cli.mcp_tools._shared import _authenticated, _parse_date, _parse_date_range


def _client_call(config: CliConfig, fn: Callable[[Any], Any]) -> Any:
    """Authenticate through garmin-py, then call the underlying Garmin client."""
    return _authenticated(config, lambda: fn(backend._require_backend()))


def _single_day(config: CliConfig, date_value: str, fn: Callable[[Any, str], Any]) -> dict[str, Any]:
    day = _parse_date(date_value, "date").isoformat()
    raw = _client_call(config, lambda client: fn(client, day))
    return {"date": day, "raw": raw}


def create_server():
    config = load_config()
    mcp = create_mcp_server(config)
    read_only = ToolAnnotations(read_only_hint=True)

    @mcp.tool(annotations=read_only)
    def health_hrv_raw(date: str) -> dict[str, Any]:
        """Raw Garmin HRV payload for one day, including baseline and intranight hrvReadings when Garmin provides them."""
        return _single_day(config, date, lambda client, day: client.get_hrv_data(day))

    @mcp.tool(annotations=read_only)
    def health_sleep_raw(date: str) -> dict[str, Any]:
        """Raw detailed Garmin sleep payload for one day, preserving sleep levels/scores and all fields returned by Garmin Connect."""
        return _single_day(config, date, lambda client, day: client.get_sleep_data(day))

    @mcp.tool(annotations=read_only)
    def health_heart_rate_raw(date: str) -> dict[str, Any]:
        """Raw Garmin daily heart-rate payload, preserving intraday heart-rate samples and summary fields."""
        return _single_day(config, date, lambda client, day: client.get_heart_rates(day))

    @mcp.tool(annotations=read_only)
    def health_stress_raw(date: str) -> dict[str, Any]:
        """Raw Garmin daily stress payload, including intraday stressValuesArray when available."""
        return _single_day(config, date, lambda client, day: client.get_stress_data(day))

    @mcp.tool(annotations=read_only)
    def health_spo2_raw(date: str) -> dict[str, Any]:
        """Raw Garmin Pulse Ox / SpO2 payload for one day."""
        return _single_day(config, date, lambda client, day: client.get_spo2_data(day))

    @mcp.tool(annotations=read_only)
    def health_respiration_raw(date: str) -> dict[str, Any]:
        """Raw Garmin respiration payload for one day."""
        return _single_day(config, date, lambda client, day: client.get_respiration_data(day))

    @mcp.tool(annotations=read_only)
    def health_training_readiness_raw(date: str) -> dict[str, Any]:
        """Raw Garmin training-readiness snapshots for one day, preserving component scores and timestamps."""
        return _single_day(config, date, lambda client, day: client.get_training_readiness(day))

    @mcp.tool(annotations=read_only)
    def health_training_status_raw(date: str) -> dict[str, Any]:
        """Raw Garmin training-status/load payload for one day."""
        return _single_day(config, date, lambda client, day: client.get_training_status(day))

    @mcp.tool(annotations=read_only)
    def health_daily_raw(date: str) -> dict[str, Any]:
        """Raw daily health bundle: Garmin stats, user summary, resting-HR source, intensity minutes, and max metrics."""
        day = _parse_date(date, "date").isoformat()

        def fetch(client: Any) -> dict[str, Any]:
            return {
                "stats": client.get_stats(day),
                "user_summary": client.get_user_summary(day),
                "resting_heart_rate": client.get_rhr_day(day),
                "intensity_minutes": client.get_intensity_minutes_data(day),
                "max_metrics": client.get_max_metrics(day),
            }

        return {"date": day, "raw": _client_call(config, fetch)}

    @mcp.tool(annotations=read_only)
    def health_body_battery_raw(start_date: str, end_date: str) -> dict[str, Any]:
        """Raw Garmin Body Battery daily reports for a range, preserving timestamp/level arrays."""
        start, end = _parse_date_range(start_date, end_date)
        raw = _client_call(
            config,
            lambda client: client.get_body_battery(start.isoformat(), end.isoformat()),
        )
        return {"start_date": start.isoformat(), "end_date": end.isoformat(), "raw": raw}

    @mcp.tool(annotations=read_only)
    def health_body_battery_events_raw(date: str) -> dict[str, Any]:
        """Raw Garmin Body Battery event payload for one day."""
        return _single_day(config, date, lambda client, day: client.get_body_battery_events(day))

    @mcp.tool(annotations=read_only)
    def activity_details_raw(activity_id: int) -> dict[str, Any]:
        """Raw Garmin activity detail payload. For large per-sample streams prefer FIT download or activity_detail_metrics."""
        if activity_id <= 0:
            raise ValueError("activity_id must be positive")
        raw = _client_call(config, lambda client: client.get_activity_details(str(activity_id)))
        return {"activity_id": activity_id, "raw": raw}

    return mcp


if __name__ == "__main__":
    create_server().run(transport="stdio")
