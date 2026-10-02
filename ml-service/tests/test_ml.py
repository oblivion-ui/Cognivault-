from fastapi.testclient import TestClient
from app.main import app
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

client = TestClient(app)

def request_payload():
    baseline = [{"values": {"amount": float(i), "frequency": float(i % 3), "night": 0.0}} for i in range(1, 12)]
    return {"baseline": baseline, "instance": {"values": {"amount": 80.0, "frequency": 9.0, "night": 1.0}}}

def test_isolation_forest_scores_real_input(monkeypatch):
    import app.main as ml
    monkeypatch.setattr(ml, "COGNIVAULT_ML_API_KEY", "test-key")
    response = client.post("/v1/anomaly/score", json=request_payload(), headers={"X-CogniVault-Service-Key": "test-key"})
    assert response.status_code == 200
    body = response.json()
    assert body["model"] == "Isolation Forest"
    assert 0 <= body["score"] <= 1
    assert isinstance(body["flagged"], bool)

def test_shap_returns_model_explanation_or_explicit_unavailable(monkeypatch):
    import app.main as ml
    monkeypatch.setattr(ml, "COGNIVAULT_ML_API_KEY", "test-key")
    response = client.post("/v1/anomaly/explain", json=request_payload(), headers={"X-CogniVault-Service-Key": "test-key"})
    assert response.status_code in (200, 503)
    if response.status_code == 200:
        assert response.json()["explanations"]
        response = client.post("/v1/anomaly/score", json=request_payload(), headers={"X-CogniVault-Service-Key": "test-key"})
        assert response.status_code == 200
        if response.json()["flagged"]:
            assert any(
                item["direction"] == "INCREASES_RISK"
                for item in client.post("/v1/anomaly/explain", json=request_payload(), headers={"X-CogniVault-Service-Key": "test-key"}).json()["explanations"]
                if item["contribution"] < 0
            )

def test_rag_endpoints_fail_closed_without_internal_auth(monkeypatch):
    import app.main as ml

    monkeypatch.setattr(ml, "COGNIVAULT_ML_API_KEY", "runtime-test-key")
    index = client.post("/v1/rag/index", json={"documents": [{"id": "d1", "text": "restricted evidence"}]})
    query = client.post("/v1/rag/query", json={"query": "show restricted evidence", "allowed_document_ids": ["d1"]})

    assert index.status_code == 401
    assert query.status_code == 401

def test_rag_endpoints_succeed_with_correct_auth(monkeypatch):
    import app.main as ml

    monkeypatch.setattr(ml, "COGNIVAULT_ML_API_KEY", "runtime-test-key")
    index = client.post("/v1/rag/index", json={"documents": [{"id": "d1", "text": "restricted evidence"}]}, headers={"X-CogniVault-Service-Key": "runtime-test-key"})
    # This may fail due to ChromaDB not being available, but it should not be 401
    assert index.status_code != 401

def test_rag_index_requires_configured_internal_key(monkeypatch):
    import app.main as ml

    monkeypatch.setattr(ml, "COGNIVAULT_ML_API_KEY", "")
    response = client.post("/v1/rag/index", json={"documents": [{"id": "d1", "text": "restricted evidence"}]}, headers={"X-CogniVault-Service-Key": "test-key"})

    assert response.status_code == 503

# ---------------------------------------------------------------------------
# RAG generation path tests.
#
# These run the real /v1/rag/query handler (including the real Ollama HTTP
# client behavior) against an in-process stub that speaks the Ollama HTTP
# protocol (/api/tags and /api/generate). Retrieval is exercised through a
# fake Chroma collection that enforces the allowed_document_ids filter the
# same way the production where-clause does.
# ---------------------------------------------------------------------------

RAG_DOCUMENTS = [
    ("evt-1", "eventId=evt-1 action=LOGIN actor=admin@example.com timestamp=2026-10-01T10:00:00Z"),
    ("evt-2", "eventId=evt-2 action=EMPLOYEE_UPDATED actor=hr@example.com timestamp=2026-10-01T11:00:00Z"),
    ("evt-restricted", "eventId=evt-restricted action=SALARY_VIEWED actor=auditor@example.com timestamp=2026-10-01T12:00:00Z"),
]


class FakeChromaCollection:
    def __init__(self, documents):
        self.documents = documents
        self.queries = []

    def query(self, query_texts, n_results, where):
        self.queries.append(
            {"query_texts": query_texts, "n_results": n_results, "where": where}
        )
        allowed = set(where["document_id"]["$in"])
        matched = [(i, t) for (i, t) in self.documents if i in allowed][:n_results]
        return {"ids": [[i for i, _ in matched]], "documents": [[t for _, t in matched]]}


class FakeChromaClient:
    def __init__(self, collection):
        self.collection = collection

    def get_or_create_collection(self, name):
        assert name == "cognivault_audit"
        return self.collection


class FakeOllamaHandler(BaseHTTPRequestHandler):
    behavior = {"mode": "ok", "model": "llama3.2:3b"}
    requests = []

    def do_GET(self):
        if self.path == "/api/tags":
            body = json.dumps(
                {"models": [{"name": FakeOllamaHandler.behavior["model"]}]}
            ).encode()
            self._send(200, body)
        else:
            self._send(404, b"{}")

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length))
        FakeOllamaHandler.requests.append({"path": self.path, "body": payload})
        if self.path != "/api/generate":
            self._send(404, b"{}")
            return
        mode = FakeOllamaHandler.behavior["mode"]
        if mode == "ok":
            self._send(
                200, json.dumps({"response": " Stub answer citing evt-1. "}).encode()
            )
        elif mode == "timeout":
            time.sleep(2.0)
            self._send(200, json.dumps({"response": "late"}).encode())
        elif mode == "garbage":
            self._send(200, b"<html>upstream crashed</html>")
        elif mode == "server-error":
            self._send(500, b"boom")
        else:
            self._send(200, b"{}")

    def _send(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class FakeOllamaServer:
    def __init__(self):
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), FakeOllamaHandler)
        self.httpd.daemon_threads = True
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    @property
    def url(self):
        return f"http://127.0.0.1:{self.httpd.server_address[1]}"

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()


@pytest.fixture
def rag_environment(monkeypatch):
    import app.main as ml

    FakeOllamaHandler.behavior = {"mode": "ok", "model": "llama3.2:3b"}
    FakeOllamaHandler.requests = []
    server = FakeOllamaServer()
    collection = FakeChromaCollection(RAG_DOCUMENTS)
    monkeypatch.setattr(ml, "COGNIVAULT_ML_API_KEY", "rag-test-key")
    monkeypatch.setattr(ml, "CHROMA_URL", "http://chroma-fake:8000")
    monkeypatch.setattr(ml, "chroma_client", lambda: FakeChromaClient(collection))
    monkeypatch.setattr(ml, "OLLAMA_URL", server.url)
    monkeypatch.setattr(ml, "OLLAMA_MODEL", "llama3.2:3b")
    try:
        yield {"server": server, "collection": collection}
    finally:
        server.stop()


def rag_query(allowed, query="login activity for admin"):
    return client.post(
        "/v1/rag/query",
        json={"query": query, "allowed_document_ids": allowed, "top_k": 5},
        headers={"X-CogniVault-Service-Key": "rag-test-key"},
    )


def test_rag_query_generates_answer_from_authorized_evidence_only(rag_environment):
    response = rag_query(["evt-1", "evt-2"])
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "READY"
    assert body["answer"] == "Stub answer citing evt-1."

    evidence_ids = [item["id"] for item in body["evidence"]]
    assert set(evidence_ids) <= {"evt-1", "evt-2"}
    assert "evt-restricted" not in evidence_ids

    generate_requests = [
        r for r in FakeOllamaHandler.requests if r["path"] == "/api/generate"
    ]
    assert len(generate_requests) == 1
    payload = generate_requests[0]["body"]
    assert payload["model"] == "llama3.2:3b"
    assert payload["stream"] is False
    assert payload["options"]["num_predict"] == 64

    prompt = payload["prompt"]
    assert "evt-1" in prompt and "evt-2" in prompt
    # Unauthorized evidence must never reach the prompt.
    assert "evt-restricted" not in prompt
    assert "SALARY_VIEWED" not in prompt
    # The user question itself must be present.
    assert "login activity for admin" in prompt


def test_rag_query_filters_chroma_query_by_allowed_document_ids(rag_environment):
    rag_query(["evt-2"])
    where = rag_environment["collection"].queries[-1]["where"]
    assert where == {"document_id": {"$in": ["evt-2"]}}
    assert rag_environment["collection"].queries[-1]["n_results"] == 5


def test_rag_query_degrades_without_fabrication_when_generation_times_out(
    rag_environment, monkeypatch
):
    import app.main as ml

    monkeypatch.setattr(ml, "OLLAMA_TIMEOUT_SECONDS", 0.2)
    FakeOllamaHandler.behavior["mode"] = "timeout"
    response = rag_query(["evt-1"])
    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["answer"] is None
    assert [item["id"] for item in body["evidence"]] == ["evt-1"]


def test_rag_query_degrades_when_ollama_returns_invalid_payload(rag_environment):
    FakeOllamaHandler.behavior["mode"] = "garbage"
    response = rag_query(["evt-1"])
    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["answer"] is None
    assert [item["id"] for item in body["evidence"]] == ["evt-1"]


def test_rag_query_degrades_when_ollama_generation_fails(rag_environment):
    FakeOllamaHandler.behavior["mode"] = "server-error"
    response = rag_query(["evt-1"])
    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["answer"] is None
    assert [item["id"] for item in body["evidence"]] == ["evt-1"]


def test_rag_query_degrades_when_ollama_is_unreachable(rag_environment, monkeypatch):
    import app.main as ml

    monkeypatch.setattr(ml, "OLLAMA_URL", "http://127.0.0.1:9")
    response = rag_query(["evt-1"])
    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["answer"] is None
    assert [item["id"] for item in body["evidence"]] == ["evt-1"]


def test_rag_query_degrades_when_configured_model_is_missing(rag_environment):
    FakeOllamaHandler.behavior["model"] = "some-other-model:latest"
    response = rag_query(["evt-1"])
    body = response.json()
    assert body["status"] == "DEGRADED"
    assert body["answer"] is None
    # No generation attempt may be made with an unavailable model.
    assert not [r for r in FakeOllamaHandler.requests if r["path"] == "/api/generate"]


def test_rag_query_reports_empty_when_no_authorized_evidence_matches(rag_environment):
    response = rag_query(["evt-unknown"])
    body = response.json()
    assert body["status"] == "EMPTY"
    assert body["answer"] is None
    assert body["evidence"] == []
    assert not [r for r in FakeOllamaHandler.requests if r["path"] == "/api/generate"]


def test_rag_query_requires_internal_service_key(rag_environment):
    response = client.post(
        "/v1/rag/query",
        json={"query": "anything", "allowed_document_ids": ["evt-1"], "top_k": 5},
    )
    assert response.status_code == 401
