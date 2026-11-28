"""Runs promtool from the same Prometheus image that docker-compose.yml uses, so tests match the running version."""

import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]


def prometheus_image():
    compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
    return re.search(r"image:\s*(prom/prometheus:\S+)", compose).group(1)


def run(directory, *args):
    """Runs promtool with `directory` mounted read-only as the working directory."""
    return subprocess.run(
        ["docker", "run", "--rm", "--network", "none", "-v", f"{pathlib.Path(directory).resolve()}:/work:ro",
         "-w", "/work", "--entrypoint", "promtool", prometheus_image(), *args],
        capture_output=True, text=True, encoding="utf-8", errors="replace", check=False,
    )
