"""Synthetic traffic for the isolated observability stack. Synthetic data only.

Every free-text value carries a ZZCANARY marker so that spans, metrics and logs can be searched for leaks.
API keys are read from OBS_TRIAGE_KEY and OBS_ASSISTANT_KEY and are never printed.
"""

import argparse
import collections
import json
import os
import secrets
import time
import urllib.error
import urllib.request

TRIAGE = "http://127.0.0.1:28081"
ASSISTANT = "http://127.0.0.1:28082"
KEYS = {TRIAGE: os.environ["OBS_TRIAGE_KEY"], ASSISTANT: os.environ["OBS_ASSISTANT_KEY"]}

# heart rate, systolic, diastolic, spo2, temperature, pain, gcs
CASES = {
    "E1": (130, 180, 110, 97, 36.8, 8, 15),
    "E2": (95, 150, 95, 91, 37.0, 4, 15),
    "E3": (88, 130, 85, 98, 38.2, 6, 15),
    "E4": (72, 120, 80, 99, 36.5, 2, 15),
    "E5": (70, 115, 75, 99, 36.4, 0, 15),
    "E6": (35, 80, 50, 85, 35.9, 0, 6),
}
QUESTION = "¿Cuáles son las medidas iniciales ante una crisis hipertensiva? ZZCANARYQUESTION"

statuses = collections.Counter()


def call(base, method, path, body=None, headers=None, auth=True, label=None):
    request = urllib.request.Request(base + path, method=method)
    request.add_header("Content-Type", "application/json")
    if auth:
        request.add_header("X-API-Key", KEYS[base])
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(request, data=data, timeout=40) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    except OSError as error:
        status, raw = type(error).__name__, b""
    statuses[f"{label or method + ' ' + path} -> {status}"] += 1
    try:
        return status, json.loads(raw or b"null")
    except ValueError:
        return status, None


def register(case, headers=None):
    hr, sys_bp, dia_bp, spo2, temp, pain, gcs = CASES[case]
    return call(TRIAGE, "POST", "/triage/pacientes", {
        "nombre_completo": f"ZZCANARYNAME Sintetico {case}", "edad": 50,
        "frecuencia_cardiaca": hr, "presion_sistolica": sys_bp, "presion_diastolica": dia_bp,
        "spo2": spo2, "temperatura": temp, "dolor": pain, "gcs": gcs,
        "queja_principal": f"ZZCANARYCOMPLAINT dolor sintetico {case}",
    }, headers=headers, label="register")


def new_trace():
    trace_id = secrets.token_hex(16)
    return trace_id, {"traceparent": f"00-{trace_id}-{secrets.token_hex(8)}-01"}


def patient_id(body):
    body = body or {}
    return body.get("id") or body.get("pacienteId") or body.get("paciente_id")


def ask(question, headers=None, label="ask"):
    return call(ASSISTANT, "POST", "/asistente/consulta", {"pregunta": question}, headers=headers, label=label)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minutes", type=float, default=8)
    parser.add_argument("--out", required=True)
    parser.add_argument("--assistant-only", action="store_true", help="send no triage requests")
    args = parser.parse_args()
    summary = {"startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}

    if not args.assistant_only:
        trace_id, headers = new_trace()
        _, body = register("E1", headers)
        summary["registrationTrace"] = {"traceId": trace_id, "patientId": patient_id(body)}
    trace_id, headers = new_trace()
    summary["assistantTrace"] = {"traceId": trace_id, "status": ask(QUESTION, headers, "ask traced")[0]}

    deadline = time.time() + args.minutes * 60
    cycle = 0
    while time.time() < deadline:
        cycle += 1
        if not args.assistant_only:
            ids = {case: patient_id(register(case)[1]) for case in CASES}
            moved = ids["E4"]
            if moved:
                path = f"/triage/pacientes/{moved}/estado"
                call(TRIAGE, "PATCH", path, {"estado": "EN_CONSULTA"}, label="to EN_CONSULTA")
                call(TRIAGE, "PATCH", path, {"estado": "ALTA"}, label="to ALTA")
                call(TRIAGE, "PATCH", path, {"estado": "REGISTRADO"}, label="invalid transition")
                call(TRIAGE, "GET", f"/triage/pacientes/{moved}", label="get patient")
            call(TRIAGE, "GET", "/triage/pacientes/00000000-0000-0000-0000-000000000000", label="unknown patient")
            call(TRIAGE, "GET", "/triage/alertas", auth=False, label="alerts without key")
            call(TRIAGE, "POST", "/triage/pacientes", {"edad": -1}, label="invalid registration")

            _, alerts = call(TRIAGE, "GET", "/triage/alertas", label="list alerts")
            pending = [a for a in alerts or [] if isinstance(a, dict) and a.get("estado") == "PENDIENTE"]
            # Acknowledge only part of the queue so "escalated but not acknowledged" stays above zero.
            for alert in pending[:2]:
                path = f"/triage/alertas/{alert['id']}/reconocimiento"
                call(TRIAGE, "POST", path, {"responsable": "ZZCANARYRESPONDER"}, label="acknowledge")
                call(TRIAGE, "POST", path, {"responsable": "ZZCANARYRESPONDER"}, label="acknowledge twice")

        ask(QUESTION)
        ask("Cual es la capital de Francia ZZCANARYQUESTION", label="ask out of corpus")
        if cycle % 3 == 0:
            ask(QUESTION + " SIMULAR-FALLO", label="ask provider failure")
        if cycle % 4 == 0:
            ask(QUESTION + " SIMULAR-CITA", label="ask invalid citation")
        call(ASSISTANT, "GET", "/asistente/estado-urgencias", label="urgency state")

        summary.update({"cycles": cycle, "statuses": dict(sorted(statuses.items()))})
        with open(args.out, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=2)
        time.sleep(20)
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
