"""Checks the Prometheus configuration and alert rules of the local stack, with promtool.

Run from the repository root (needs Docker): python -m unittest discover -s observability/tests -v
"""

import unittest

import promtool

PROMETHEUS = promtool.ROOT / "observability/prometheus"


class AlertRules(unittest.TestCase):

    def assert_promtool(self, *args):
        result = promtool.run(PROMETHEUS, *args)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_rules_are_valid(self):
        self.assert_promtool("check", "rules", "alerts.yml")

    def test_configuration_loads_the_rules(self):
        self.assert_promtool("check", "config", "prometheus.yml")
        self.assertIn("- alerts.yml", (PROMETHEUS / "prometheus.yml").read_text(encoding="utf-8"))

    def test_compose_mounts_the_rules_beside_the_configuration(self):
        compose = (promtool.ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        self.assertIn("./observability/prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro", compose)

    def test_each_rule_goes_pending_or_firing_and_stays_quiet_when_it_should(self):
        self.assert_promtool("test", "rules", "alerts.test.yml")

    def test_no_notification_channel_is_configured(self):
        self.assertNotIn("alerting:", (PROMETHEUS / "prometheus.yml").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
