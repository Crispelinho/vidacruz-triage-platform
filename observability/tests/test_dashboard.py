"""Checks the provisioned dashboard: units, panel coverage and the arithmetic of the queries that were wrong.

Run from the repository root (needs Docker): python -m unittest discover -s observability/tests -v
"""

import json
import os
import pathlib
import tempfile
import unittest

import promtool

DASHBOARD = json.loads((promtool.ROOT / "observability/grafana/dashboards/vidacruz.json").read_text(encoding="utf-8"))
PANELS = {panel["title"]: panel for panel in DASHBOARD["panels"]}
ERRORS_PANEL = "Respuestas HTTP 4xx/5xx por servicio"


def unit(title):
    return PANELS[title]["fieldConfig"]["defaults"]["unit"]


class DashboardUnits(unittest.TestCase):

    def test_event_rates_are_not_labelled_as_requests(self):
        for title in ("Clasificación y escalado por nivel", "Alertas reconocidas por nivel", "Desenlace de consultas",
                      "Eventos de urgencia procesados"):
            with self.subTest(title):
                self.assertEqual("ops", unit(title))

    def test_per_minute_counts_are_plain_numbers(self):
        for title in ("Escalations and acknowledgements (per minute)", ERRORS_PANEL):
            with self.subTest(title):
                self.assertEqual("short", unit(title))


class DashboardColours(unittest.TestCase):

    def test_counters_do_not_turn_red_above_grafanas_default_threshold(self):
        # Without explicit steps Grafana paints any value over 80 red, including a count of registered patients.
        for panel in (p for p in DASHBOARD["panels"] if p["type"] == "stat"):
            with self.subTest(panel["title"]):
                thresholds = panel["fieldConfig"]["defaults"].get("thresholds", {})
                self.assertEqual([None], [step["value"] for step in thresholds.get("steps", [])])


class PoolPanel(unittest.TestCase):

    def test_shows_the_jdbc_pool_beside_the_r2dbc_pool(self):
        expressions = " ".join(t["expr"] for t in PANELS["PostgreSQL pool connections by service"]["targets"])
        for metric in ("r2dbc_pool_acquired_connections", "hikaricp_connections_active", "hikaricp_connections_idle",
                       "hikaricp_connections_pending"):
            with self.subTest(metric):
                self.assertIn(metric, expressions)


class QueryArithmetic(unittest.TestCase):

    def assert_promql(self, tests):
        document = {"evaluation_interval": "15s", "tests": tests}
        with tempfile.TemporaryDirectory() as directory:
            # promtool reads YAML, and JSON is valid YAML.
            queries = pathlib.Path(directory, "queries.test.yml")
            queries.write_text(json.dumps(document), encoding="utf-8")
            # The image runs promtool as an unprivileged user; on Linux the temporary directory is private by default.
            os.chmod(directory, 0o755)
            os.chmod(queries, 0o644)
            result = promtool.run(directory, "test", "rules", "queries.test.yml")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_redis_latency_is_the_real_average_below_one_command_per_second(self):
        expression = PANELS["Redis command latency"]["targets"][0]["expr"]
        # One command every 15 s that takes 2 s: a round latency keeps the floating point comparison exact.
        self.assert_promql([{
            "interval": "15s",
            "input_series": [
                {"series": "lettuce_command_completion_seconds_sum", "values": "0+2x20"},
                {"series": "lettuce_command_completion_seconds_count", "values": "0+1x20"},
            ],
            "promql_expr_test": [{
                "expr": expression, "eval_time": "5m",
                "exp_samples": [{"labels": "{}", "value": 2}],
            }],
        }])

    def test_redis_latency_has_no_sample_while_idle(self):
        expression = PANELS["Redis command latency"]["targets"][0]["expr"]
        self.assert_promql([{
            "interval": "15s",
            "input_series": [
                {"series": "lettuce_command_completion_seconds_sum", "values": "1+0x20"},
                {"series": "lettuce_command_completion_seconds_count", "values": "5+0x20"},
            ],
            "promql_expr_test": [{"expr": expression, "eval_time": "5m", "exp_samples": []}],
        }])

    def test_error_responses_do_not_report_lifetime_totals_after_a_failed_scrape(self):
        expression = PANELS[ERRORS_PANEL]["targets"][0]["expr"]
        # The scrape at 4m fails, which marks the series stale; one minute later it is back with its old total.
        self.assert_promql([{
            "interval": "15s",
            "input_series": [
                {"series": 'up{job="triage"}', "values": "1x15 0 1x4"},
                {"series": 'http_server_requests_seconds_count{application="triage-service",job="triage",status="503"}',
                 "values": "40x15 stale 40x4"},
            ],
            "promql_expr_test": [{"expr": expression, "eval_time": "5m", "exp_samples": []}],
        }])

    def test_error_responses_count_the_first_occurrence_of_a_new_series(self):
        expression = PANELS[ERRORS_PANEL]["targets"][0]["expr"]
        series = 'http_server_requests_seconds_count{{application="triage-service",job="triage",status="{}"}}'
        self.assert_promql([{
            "interval": "15s",
            "input_series": [
                {"series": 'up{job="triage"}', "values": "1x20"},
                # Existing series: two more errors during the last minute.
                {"series": series.format("409"), "values": "5x16 6 7x3"},
                # New series: its first error appears 30 s before the evaluation.
                {"series": series.format("503"), "values": "_x18 1x2"},
                {"series": series.format("200"), "values": "0+3x20"},
            ],
            "promql_expr_test": [{
                "expr": expression, "eval_time": "5m",
                "exp_samples": [
                    {"labels": '{application="triage-service", status="409"}', "value": 2},
                    {"labels": '{application="triage-service", status="503"}', "value": 1},
                ],
            }],
        }])


if __name__ == "__main__":
    unittest.main()
