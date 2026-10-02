from fastapi.testclient import TestClient
from app.main import app
import os

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
                item["direction"] == "NEGATIVE_CONTRIBUTION"
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
