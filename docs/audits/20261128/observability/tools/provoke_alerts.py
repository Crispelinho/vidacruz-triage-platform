"""Provokes alerts on the isolated stack with synthetic requests. Stop its Kafka broker first to stall the outbox.

Sends a few provider timeouts (SIMULAR-LENTO), then steady provider failures, and registers synthetic patients so the
outbox has events it cannot publish. Prints alert state changes. Keys come from OBS_TRIAGE_KEY and OBS_ASSISTANT_KEY.
"""

import argparse
import json
import time
import urllib.request

import generate_traffic as traffic

PROMETHEUS = "http://127.0.0.1:29090"


def alert_states():
    with urllib.request.urlopen(PROMETHEUS + "/api/v1/alerts", timeout=20) as response:
        alerts = json.load(response)["data"]["alerts"]
    return sorted(
        f"{a['labels']['alertname']}={a['state']}"
        + "".join(f" {k}={a['labels'][k]}" for k in ("name", "application", "job") if k in a["labels"])
        for a in alerts
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minutes", type=float, default=5)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    summary = {"startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "timeline": []}

    trace_id, headers = traffic.new_trace()
    status, _ = traffic.ask(traffic.QUESTION + " SIMULAR-LENTO", headers, "ask timeout")
    summary["timeoutTrace"] = {"traceId": trace_id, "status": status}
    for _ in range(2):
        traffic.ask(traffic.QUESTION + " SIMULAR-LENTO", label="ask timeout")

    deadline = time.time() + args.minutes * 60
    previous = None
    while time.time() < deadline:
        traffic.register("E4")
        for _ in range(3):
            traffic.ask(traffic.QUESTION + " SIMULAR-FALLO", label="ask provider failure")
        states = alert_states()
        if states != previous:
            summary["timeline"].append({"at": time.strftime("%H:%M:%SZ", time.gmtime()), "alerts": states})
            print(summary["timeline"][-1], flush=True)
            previous = states
        summary["statuses"] = dict(sorted(traffic.statuses.items()))
        with open(args.out, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=2)
        time.sleep(10)


if __name__ == "__main__":
    # Importing generate_traffic from the script directory requires running without -I.
    main()
