"""Read-only checks against the observability stack; writes a sanitized JSON summary.

Reads Grafana anonymously (Viewer), Prometheus, Jaeger and both actuators. API keys come from OBS_TRIAGE_KEY and
OBS_ASSISTANT_KEY. Keys, response bodies and span payloads are never written: only counts, names and values.
"""

import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

CUSTOM_METRICS = [
    "triage_patients_classified_total",
    "triage_patients_escalated_total",
    "triage_alerts_acknowledged_total",
    "assistant_answers_total",
    "assistant_llm_latency_seconds_count",
    "assistant_urgency_events_total",
    "triage_outbox_pending",
    "triage_outbox_failing",
    "triage_outbox_requires_review",
    "triage_outbox_oldest_pending_age_seconds",
    "resilience4j_circuitbreaker_state",
]
SERVICES = ["triage-service", "clinical-assistant-service"]
# Text that must never reach spans, metric labels or logs. Canaries come from generate_traffic.py.
FORBIDDEN = [
    "ZZCANARYNAME", "ZZCANARYCOMPLAINT", "ZZCANARYQUESTION", "ZZCANARYANSWER", "ZZCANARYRESPONDER",
    "Crisis hipertensiva", "<pregunta>", "<evidencia", "nifedipino",
]


def http(url, headers=None, body=None, timeout=40):
    request = urllib.request.Request(url, headers=headers or {})
    if body is not None:
        request.add_header("Content-Type", "application/json")
        body = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(request, data=body, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")
    except OSError as error:
        return 0, type(error).__name__


def as_json(text):
    try:
        return json.loads(text)
    except ValueError:
        return None


def json_stream(text):
    """Jaeger v3 search can return several concatenated JSON objects."""
    decoder, index, text = json.JSONDecoder(), 0, text.strip()
    while index < len(text):
        value, index = decoder.raw_decode(text, index)
        yield value
        while index < len(text) and text[index].isspace():
            index += 1


def prometheus_checks(base):
    out = {}
    targets = as_json(http(base + "/api/v1/targets")[1])["data"]["activeTargets"]
    out["targets"] = [
        {"job": t["labels"]["job"], "health": t["health"], "lastError": t["lastError"]} for t in targets
    ]
    out["ruleGroups"] = as_json(http(base + "/api/v1/rules")[1])["data"]["groups"]
    out["alerts"] = [
        {"labels": a["labels"], "state": a["state"]}
        for a in as_json(http(base + "/api/v1/alerts")[1])["data"]["alerts"]
    ]
    out["alertmanagers"] = as_json(http(base + "/api/v1/alertmanagers")[1])["data"]["activeAlertmanagers"]
    out["metrics"] = {}
    for name in CUSTOM_METRICS:
        result = as_json(http(base + "/api/v1/query?query=" + urllib.parse.quote(name))[1])["data"]["result"]
        out["metrics"][name] = [
            {"labels": {k: v for k, v in s["metric"].items() if k in ("level", "outcome", "result", "application", "name", "state")},
             "value": float(s["value"][1])} for s in result
        ]
    names = as_json(http(base + "/api/v1/label/__name__/values")[1])["data"]
    out["metricNamesByPrefix"] = {
        prefix: sorted(n for n in names if n.startswith(prefix))
        for prefix in ("triage_", "assistant_", "r2dbc_pool_", "lettuce_", "kafka_consumer_fetch_manager_records_lag",
                       "resilience4j_circuitbreaker_state", "hikaricp_connections")
    }
    lag = as_json(http(base + "/api/v1/series?match[]=kafka_consumer_fetch_manager_records_lag")[1])["data"]
    out["kafkaLagSeriesLabels"] = [
        {k: s.get(k) for k in ("application", "topic", "partition")} for s in lag
    ]
    return out


def grafana_checks(base):
    out = {}
    out["health"] = as_json(http(base + "/api/health")[1])
    out["datasources"] = []
    for ds in as_json(http(base + "/api/datasources")[1]) or []:
        status, text = http(f"{base}/api/datasources/uid/{ds['uid']}/health")
        body = as_json(text) or {}
        out["datasources"].append({
            "name": ds["name"], "type": ds["type"], "url": ds["url"], "healthHttp": status,
            "status": body.get("status"), "message": body.get("message"),
        })
    status, text = http(base + "/api/datasources/uid/jaeger/resources/services")
    out["jaegerPluginServices"] = {"http": status, "body": text[:200]}
    out["alertRules"] = {
        path: {"http": status, "body": text.strip()[:200]}
        for path in ("/api/v1/provisioning/alert-rules", "/api/prometheus/grafana/api/v1/rules")
        for status, text in [http(base + path)]
    }
    dashboard = as_json(http(base + "/api/dashboards/uid/vidacruz-nfr07")[1])["dashboard"]
    out["panels"] = []
    now = int(time.time() * 1000)
    for number, panel in enumerate(dashboard["panels"], start=1):
        queries = []
        for target in panel["targets"]:
            status, text = http(base + "/api/ds/query", body={
                "from": str(now - 15 * 60 * 1000), "to": str(now),
                "queries": [{
                    "refId": target["refId"], "expr": target["expr"], "range": True,
                    "datasource": {"type": "prometheus", "uid": "prometheus"},
                    "intervalMs": 5000, "maxDataPoints": 300,
                }],
            })
            result = ((as_json(text) or {}).get("results") or {}).get(target["refId"], {})
            series = []
            for frame in result.get("frames", []):
                values = frame["data"]["values"]
                if len(values) < 2:
                    continue
                numbers = [v for v in values[1] if isinstance(v, (int, float))]
                if not numbers:
                    continue
                labels = frame["schema"]["fields"][1].get("labels", {})
                series.append({"labels": labels, "points": len(numbers), "last": round(numbers[-1], 4),
                               "max": round(max(numbers), 4)})
            if status != 200 or result.get("error"):
                state = "ERROR"
            elif not series:
                state = "EMPTY"
            elif all(s["max"] == 0 for s in series):
                state = "ALL_ZERO"
            else:
                state = "DATA"
            queries.append({"refId": target["refId"], "expr": target["expr"], "state": state, "http": status,
                            "error": result.get("error"), "seriesCount": len(series), "series": series[:12]})
        out["panels"].append({"number": number, "title": panel["title"], "type": panel["type"], "queries": queries})
    return out


def attribute_value(value):
    return next(iter(value.values()), None)


def jaeger_checks(base, traffic, minutes):
    out = {"services": as_json(http(base + "/api/v3/services")[1])}
    out["legacyApi"] = {
        path: http(base + path)[0] for path in ("/api/services", "/api/v3/services", "/api/traces?service=triage-service")
    }
    start = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() - minutes * 60))
    end = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    raw_all, spans = [], {}
    for service in SERVICES:
        query = urllib.parse.urlencode({
            "query.service_name": service, "query.start_time_min": start, "query.start_time_max": end,
            "query.search_depth": 1500,
        })
        status, text = http(f"{base}/api/v3/traces?{query}", timeout=120)
        raw_all.append(text)
        if status != 200:
            continue
        for chunk in json_stream(text):
            for resource in chunk.get("result", {}).get("resourceSpans", []):
                name = next(attribute_value(a["value"]) for a in resource["resource"]["attributes"]
                            if a["key"] == "service.name")
                for scope in resource["scopeSpans"]:
                    for span in scope["spans"]:
                        spans[(span["traceId"], span["spanId"])] = (name, span)
    raw = "".join(raw_all)
    keys, names, events = {}, {}, set()
    for service, span in spans.values():
        names.setdefault(service, set()).add(span["name"])
        for attribute in span.get("attributes", []):
            keys.setdefault(attribute["key"], set()).add(str(attribute_value(attribute["value"])))
        for event in span.get("events", []):
            for attribute in event.get("attributes", []):
                events.add(attribute["key"])
    out["scanned"] = {"traces": len({k[0] for k in spans}), "spans": len(spans), "bytes": len(raw)}
    out["spanNames"] = {s: sorted(n) for s, n in names.items()}
    out["spanAttributes"] = {k: {"distinct": len(v), "samples": sorted(v)[:4]} for k, v in sorted(keys.items())}
    out["eventAttributeKeys"] = sorted(events)
    failed = {}
    for service, span in spans.values():
        status = span.get("status") or {}
        if status.get("code") in (2, "STATUS_CODE_ERROR"):
            entry = failed.setdefault(f"{service} :: {span['name']}", {"count": 0, "messages": set()})
            entry["count"] += 1
            entry["messages"].add(status.get("message", ""))
    out["errorSpans"] = {k: {"count": v["count"], "messages": sorted(v["messages"])[:3]} for k, v in failed.items()}
    forbidden = FORBIDDEN + [os.environ["OBS_TRIAGE_KEY"], os.environ["OBS_ASSISTANT_KEY"]]
    labels = FORBIDDEN + ["<triage api key>", "<assistant api key>"]
    out["forbiddenHitsInSpans"] = {label: raw.lower().count(text.lower()) for label, text in zip(labels, forbidden)}
    if traffic:
        for field in ("registrationTrace", "assistantTrace"):
            if field not in traffic:
                continue
            trace_id = traffic[field]["traceId"]
            status, text = http(f"{base}/api/traces/{trace_id}")
            data = ((as_json(text) or {}).get("data") or [None])[0]
            entry = {"traceId": trace_id, "http": status, "spans": []}
            if data:
                processes = {k: v["serviceName"] for k, v in data["processes"].items()}
                first = min(s["startTime"] for s in data["spans"])
                for span in sorted(data["spans"], key=lambda s: s["startTime"]):
                    entry["spans"].append({
                        "service": processes[span["processID"]], "operation": span["operationName"],
                        "spanId": span["spanID"],
                        "parent": next((r["spanID"] for r in span.get("references", [])), None),
                        "offsetMs": round((span["startTime"] - first) / 1000, 1),
                        "durationMs": round(span["duration"] / 1000, 1),
                        "tags": sorted(t["key"] for t in span.get("tags", [])),
                    })
                entry["services"] = sorted({s["service"] for s in entry["spans"]})
            out[field] = entry
    return out


def actuator_checks(triage_port, assistant_port):
    out = {}
    for service, port, variable in (("triage-service", triage_port, "OBS_TRIAGE_KEY"),
                                    ("clinical-assistant-service", assistant_port, "OBS_ASSISTANT_KEY")):
        url = f"http://127.0.0.1:{port}/actuator/prometheus"
        status, text = http(url, headers={"X-API-Key": os.environ[variable]})
        forbidden = FORBIDDEN + [os.environ["OBS_TRIAGE_KEY"], os.environ["OBS_ASSISTANT_KEY"]]
        out[service] = {
            "withoutKeyHttp": http(url)[0], "withKeyHttp": status,
            "forbiddenHits": sum(text.lower().count(f.lower()) for f in forbidden),
        }
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--traffic", help="summary written by generate_traffic.py")
    parser.add_argument("--out", required=True)
    parser.add_argument("--minutes", type=int, default=60)
    # Defaults are the isolated stack of obs.env; override them to read another local stack.
    for name, port in (("prometheus", 29090), ("grafana", 23000), ("jaeger", 26686), ("triage", 28081),
                       ("assistant", 28082)):
        parser.add_argument(f"--{name}-port", type=int, default=port)
    parser.add_argument("--forbid", action="append", default=[], help="extra text that must not appear")
    args = parser.parse_args()
    FORBIDDEN.extend(args.forbid)
    traffic = None
    if args.traffic:
        with open(args.traffic, encoding="utf-8") as handle:
            traffic = json.load(handle)
    report = {
        "collectedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "ports": {k: v for k, v in vars(args).items() if k.endswith("_port")},
        "forbiddenTerms": list(FORBIDDEN),
        "prometheus": prometheus_checks(f"http://127.0.0.1:{args.prometheus_port}"),
        "grafana": grafana_checks(f"http://127.0.0.1:{args.grafana_port}"),
        "jaeger": jaeger_checks(f"http://127.0.0.1:{args.jaeger_port}", traffic, args.minutes),
        "actuators": actuator_checks(args.triage_port, args.assistant_port),
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=1, ensure_ascii=False)
    for panel in report["grafana"]["panels"]:
        print(panel["number"], panel["title"], [q["state"] for q in panel["queries"]])
    print("targets", report["prometheus"]["targets"])
    print("datasources", report["grafana"]["datasources"])
    print("scanned", report["jaeger"]["scanned"])
    print("span hits", report["jaeger"]["forbiddenHitsInSpans"])
    print("actuators", report["actuators"])


if __name__ == "__main__":
    main()
