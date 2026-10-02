import math
import os
import hmac
from datetime import datetime, timezone
from typing import Any
import httpx
import numpy as np
import shap
from chromadb import HttpClient
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from sklearn.ensemble import IsolationForest

app = FastAPI(title="CogniVault ML and RAG service", version="0.1.0")
CHROMA_URL = os.getenv("CHROMA_URL", "")
OLLAMA_URL = os.getenv("OLLAMA_URL", "")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "")
COGNIVAULT_ML_API_KEY = os.getenv("COGNIVAULT_ML_API_KEY", "")

def now():
    return datetime.now(timezone.utc)

def service_status(status: str, details: str):
    return {"status": status, "details": details, "checkedAt": now().isoformat()}

async def check_http(url: str):
    if not url:
        return service_status("NOT_CONFIGURED", "Service URL is not configured.")
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(url)
        if response.status_code < 300:
            return service_status("READY", "Readiness endpoint responded.")
        return service_status("UNAVAILABLE", f"HTTP {response.status_code}")
    except Exception:
        return service_status("UNAVAILABLE", "Service is unreachable.")

async def chroma_status():
    if not CHROMA_URL:
        return service_status("NOT_CONFIGURED", "CHROMA_URL is not configured.")
    return await check_http(CHROMA_URL.rstrip("/") + "/api/v1/heartbeat")

async def ollama_status():
    if not OLLAMA_URL or not OLLAMA_MODEL:
        return service_status("NOT_CONFIGURED", "OLLAMA_URL or OLLAMA_MODEL is not configured.")
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            response = await client.get(OLLAMA_URL.rstrip("/") + "/api/tags")
        response.raise_for_status()
        names = {m.get("name") for m in response.json().get("models", [])}
        if OLLAMA_MODEL not in names:
            return service_status("NOT_CONFIGURED", f"Configured model {OLLAMA_MODEL} is not available.")
        return service_status("READY", "Ollama responded and the configured model is available.")
    except Exception:
        return service_status("UNAVAILABLE", "Ollama is unreachable.")

@app.get("/health/readiness")
async def readiness():
    chroma = await chroma_status()
    ollama = await ollama_status()
    rag_auth = service_status("READY", "Internal RAG API authentication is configured.") if COGNIVAULT_ML_API_KEY else service_status("NOT_CONFIGURED", "COGNIVAULT_ML_API_KEY is not configured.")
    dependencies = {"ChromaDB": chroma, "Ollama": ollama, "RAG API Authentication": rag_auth}
    overall = "READY" if chroma["status"] == "READY" and ollama["status"] == "READY" and rag_auth["status"] == "READY" else "DEGRADED"
    return {"status": overall, "checkedAt": now().isoformat(), "dependencies": dependencies}

def require_ml_service_key(api_key: str | None = Header(default=None, alias="X-CogniVault-Service-Key")):
    if not COGNIVAULT_ML_API_KEY:
        raise HTTPException(status_code=503, detail="ML service authentication is not configured.")
    if api_key is None or not hmac.compare_digest(api_key, COGNIVAULT_ML_API_KEY):
        raise HTTPException(status_code=401, detail="ML service authentication required.")

class FeatureVector(BaseModel):
    values: dict[str, float] = Field(min_length=1)

class AnomalyRequest(BaseModel):
    baseline: list[FeatureVector] = Field(min_length=8)
    instance: FeatureVector
    contamination: float = Field(default=0.1, gt=0.0, lt=0.5)

class FeatureExplanation(BaseModel):
    feature: str
    contribution: float
    direction: str
    explanation: str

def matrix(request: AnomalyRequest):
    names = sorted({key for row in request.baseline for key in row.values} | set(request.instance.values))
    baseline = np.array([[row.values.get(name, 0.0) for name in names] for row in request.baseline], dtype=float)
    instance = np.array([[request.instance.values.get(name, 0.0) for name in names]], dtype=float)
    return names, baseline, instance

def fit(request: AnomalyRequest):
    names, baseline, instance = matrix(request)
    model = IsolationForest(n_estimators=200, contamination=request.contamination, random_state=42)
    model.fit(baseline)
    return names, baseline, instance, model

@app.post("/v1/anomaly/score", dependencies=[Depends(require_ml_service_key)])
def anomaly_score(request: AnomalyRequest):
    names, _, instance, model = fit(request)
    decision = float(model.decision_function(instance)[0])
    flagged = bool(decision < 0.0)
    normalized = float(1.0 / (1.0 + math.exp(10.0 * decision)))
    return {"score": normalized, "flagged": flagged, "model": "Isolation Forest", "features": names, "generatedAt": now().isoformat()}

@app.post("/v1/anomaly/explain", dependencies=[Depends(require_ml_service_key)])
def anomaly_explain(request: AnomalyRequest):
    names, _, instance, model = fit(request)
    try:
        explainer = shap.TreeExplainer(model)
        values = explainer.shap_values(instance)
        if isinstance(values, list):
            values = values[0]
        contributions = np.asarray(values)[0]
        explanations = []
        for name, contribution in zip(names, contributions):
            numeric = float(contribution)
            direction = "DECREASES_RISK" if numeric > 0 else "INCREASES_RISK" if numeric < 0 else "NEUTRAL"
            explanations.append(FeatureExplanation(feature=name, contribution=numeric, direction=direction, explanation="Contribution calculated by SHAP TreeExplainer for the fitted Isolation Forest model."))
        return {"model": "Isolation Forest", "explanations": explanations, "generatedAt": now().isoformat()}
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"SHAP explanation unavailable: {exc.__class__.__name__}") from exc

class IndexDocument(BaseModel):
    id: str
    text: str = Field(min_length=1)
    metadata: dict[str, Any] = Field(default_factory=dict)

class IndexRequest(BaseModel):
    documents: list[IndexDocument] = Field(min_length=1)

class RagRequest(BaseModel):
    query: str = Field(min_length=1)
    allowed_document_ids: list[str] = Field(min_length=1)
    top_k: int = Field(default=5, ge=1, le=20)

def chroma_client():
    if not CHROMA_URL:
        raise HTTPException(status_code=503, detail="ChromaDB is not configured.")
    parsed = httpx.URL(CHROMA_URL)
    return HttpClient(host=parsed.host, port=parsed.port or 8000)

@app.post("/v1/rag/index", dependencies=[Depends(require_ml_service_key)])
def index_documents(request: IndexRequest):
    try:
        collection = chroma_client().get_or_create_collection("cognivault_audit")
        collection.upsert(ids=[d.id for d in request.documents], documents=[d.text for d in request.documents], metadatas=[{**d.metadata, "document_id": d.id} for d in request.documents])
        return {"indexed": len(request.documents), "collection": "cognivault_audit"}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"ChromaDB indexing unavailable: {exc.__class__.__name__}") from exc

@app.post("/v1/rag/query", dependencies=[Depends(require_ml_service_key)])
async def rag_query(request: RagRequest):
    try:
        collection = chroma_client().get_or_create_collection("cognivault_audit")

        result = collection.query(
            query_texts=[request.query],
            n_results=request.top_k,
            where={"document_id": {"$in": request.allowed_document_ids}},
        )

        documents = result.get("documents", [[]])[0]
        ids = result.get("ids", [[]])[0]

        evidence = [
            {"id": doc_id, "text": text}
            for doc_id, text in zip(ids, documents)
        ]

        if not evidence:
            return {
                "answer": None,
                "evidence": [],
                "status": "EMPTY",
                "limitations": [
                    "No authorized evidence matched the query."
                ],
            }

        ollama = await ollama_status()

        if ollama["status"] != "READY":
            return {
                "answer": None,
                "evidence": evidence,
                "status": "DEGRADED",
                "limitations": [
                    "Retrieval succeeded, but Ollama generation is unavailable."
                ],
            }

        context = "\n\n".join(
            f"Source event ID: {item['id']}\n"
            f"Evidence: {item['text']}"
            for item in evidence
        )

        prompt = f"""
You are the CogniVault audit investigation assistant.

Answer the user's question using ONLY the authorized evidence provided below.

Strict rules:
1. Do not invent facts, events, actors, timestamps, IDs, or actions.
2. Treat each "Source event ID" as one audit event.
3. Fields inside an event, including values in before/after snapshots, are NOT separate audit events.
4. Do not interpret a field such as auditEventId=null as another audit event.
5. If the evidence does not contain enough information to answer, explicitly say that the evidence is insufficient.
6. Mention the relevant source event ID when answering.
7. Keep the answer concise: at most 3 short sentences.

User question:
{request.query}

Authorized evidence:
{context}
"""

        try:
            async with httpx.AsyncClient(timeout=120) as client:
                response = await client.post(
                    OLLAMA_URL.rstrip("/") + "/api/generate",
                    json={
                        "model": OLLAMA_MODEL,
                        "prompt": prompt,
                        "stream": False,
                        "options": {
                            "num_predict": 64
                        },
                    },
                )

            response.raise_for_status()

            answer = response.json().get("response")

            if not answer:
                return {
                    "answer": None,
                    "evidence": evidence,
                    "status": "DEGRADED",
                    "limitations": [
                        "Retrieval succeeded, but Ollama returned no answer."
                    ],
                }

            return {
                "answer": answer.strip(),
                "evidence": evidence,
                "status": "READY",
                "limitations": [
                    "Answer is generated only from authorized retrieved context."
                ],
            }

        except (httpx.TimeoutException, httpx.HTTPError):
            return {
                "answer": None,
                "evidence": evidence,
                "status": "DEGRADED",
                "limitations": [
                    "Authorized evidence was retrieved, but "
                    "LLM answer generation timed out or failed."
                ],
            }

    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=503,
            detail=f"RAG service unavailable: {exc.__class__.__name__}",
        ) from exc




\n