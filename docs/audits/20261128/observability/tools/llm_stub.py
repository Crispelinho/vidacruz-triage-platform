"""Deterministic stand-in for the Ollama chat API, bound to loopback. It is NOT a real model.

It echoes the first evidence block of the grounded prompt so the assistant's answered, invalid-citation and
unavailable paths can be exercised without a provider. Markers inside the question select the failure paths.
"""

import html
import json
import random
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 21434
EVIDENCE = re.compile(
    r'<evidencia id="1" protocolo="(?P<protocol>[^"]*)" seccion="(?P<section>[^"]*)">(?P<content>.*?)</evidencia>',
    re.S,
)
ANSWER_CANARY = "ZZCANARYANSWER"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # request bodies are never logged
        print(self.command, self.path, args[1] if len(args) > 1 else "", flush=True)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path != "/api/chat":
            self.send_error(404)
            return
        prompt = "\n".join(m.get("content", "") for m in body.get("messages", []) if m.get("role") == "user")
        # SIMULAR-LENTO answers after the assistant's 15 s node timeout.
        time.sleep(17 if "SIMULAR-LENTO" in prompt else random.uniform(0.2, 2.5))
        match = EVIDENCE.search(prompt)
        if "SIMULAR-FALLO" in prompt or match is None:
            self.send_error(500, "simulated provider failure")
            return
        protocol, section, content = (html.unescape(match[key]) for key in ("protocol", "section", "content"))
        if "SIMULAR-CITA" in prompt:
            protocol = "PROTO-999"
        sentence = content.split(". ")
        answer = {
            "respuesta": f"{'. '.join(sentence[:3]).rstrip('.')} {ANSWER_CANARY} [1].",
            "fuentes": [{"protocolo": protocol, "seccion": section}],
        }
        base = {"model": body.get("model", "stub"), "created_at": "2026-01-01T00:00:00Z"}
        chunks = [
            {**base, "message": {"role": "assistant", "content": json.dumps(answer, ensure_ascii=False)}, "done": False},
            {**base, "message": {"role": "assistant", "content": ""}, "done": True, "done_reason": "stop",
             "total_duration": 1, "load_duration": 1, "prompt_eval_count": 1, "prompt_eval_duration": 1,
             "eval_count": 1, "eval_duration": 1},
        ]
        payload = "".join(json.dumps(c, ensure_ascii=False) + "\n" for c in chunks).encode()
        if not body.get("stream", True):
            payload = json.dumps({**chunks[1], "message": chunks[0]["message"]}, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/x-ndjson")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    print(f"LLM stub listening on 127.0.0.1:{PORT}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
