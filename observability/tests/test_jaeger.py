"""Checks the pinned Jaeger: Grafana's datasource must be able to use it, in Compose and in the local overlay.

Jaeger 2.21 removed the v1 query endpoints that the Jaeger datasource of Grafana 12.1.1 calls. The first test starts
the pinned image and asks for one of them, so an upgrade that breaks the datasource fails here.

Run from the repository root (needs Docker): python -m unittest discover -s observability/tests -v
"""

import re
import subprocess
import time
import unittest
import urllib.error
import urllib.request

import promtool

COMPOSE = (promtool.ROOT / "docker-compose.yml").read_text(encoding="utf-8")
OVERLAY = (promtool.ROOT / "k8s/local/local-jaeger.yaml").read_text(encoding="utf-8")
IMAGE = re.compile(r"image:\s*(jaegertracing/jaeger:\S+)")


def docker(*args):
    return subprocess.run(["docker", *args], capture_output=True, text=True, check=True).stdout.strip()


class PinnedJaeger(unittest.TestCase):

    def test_serves_the_query_endpoints_grafana_uses(self):
        container = docker("run", "-d", "--rm", "--user", "10001:10001", "--cap-drop", "ALL",
                           "-p", "127.0.0.1::16686", IMAGE.search(COMPOSE).group(1))
        try:
            base = "http://" + docker("port", container, "16686/tcp").splitlines()[0]
            self.assertEqual(200, self.status(base + "/"), "Jaeger UI did not start")
            self.assertEqual(200, self.status(base + "/api/services"))
        finally:
            docker("rm", "-f", "-v", container)

    def test_compose_and_the_local_overlay_run_the_same_image(self):
        self.assertEqual(IMAGE.search(COMPOSE).group(1), IMAGE.search(OVERLAY).group(1))

    def test_runs_without_the_root_group(self):
        # Releases before 2.21 declare only a user, so the group has to be set by whoever runs them.
        self.assertRegex(COMPOSE, r'image: jaegertracing/jaeger:\S+\n\s+user: "10001:10001"')
        self.assertIn("runAsGroup: 10001", OVERLAY)

    @staticmethod
    def status(url):
        for _ in range(60):
            try:
                with urllib.request.urlopen(url, timeout=5) as response:
                    return response.status
            except urllib.error.HTTPError as error:
                return error.code
            except OSError:
                time.sleep(1)
        return None


if __name__ == "__main__":
    unittest.main()
