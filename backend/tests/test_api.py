from app.ai.prompts import BASE_RULES, coach_system_prompt, language_instruction
from app.security import hash_token, new_token, verify_token


async def test_health_requires_owner_token(client):
    r = await client.get("/api/v1/health")
    assert r.status_code == 401
    r = await client.get("/api/v1/health", headers={"Authorization": "Bearer wrong"})
    assert r.status_code == 401


async def test_health_reports_capabilities(client, auth):
    r = await client.get("/api/v1/health", headers=auth)
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["ai_configured"] is False
    assert body["motion_available"] is False


async def test_ai_unavailable_without_key(client, auth):
    r = await client.post(
        "/api/v1/coach/chat",
        headers=auth,
        json={"messages": [{"role": "user", "content": "hi"}], "context": {}, "locale": "en"},
    )
    assert r.status_code == 503


async def test_chat_rejects_non_user_last_message(client, auth):
    r = await client.post(
        "/api/v1/coach/chat",
        headers=auth,
        json={"messages": [{"role": "assistant", "content": "hi"}], "context": {}, "locale": "en"},
    )
    assert r.status_code == 422


async def test_motion_interpretation_rejects_failed_quality(client, auth):
    r = await client.post(
        "/api/v1/ai/motion-interpretation",
        headers=auth,
        json={"technique": "Dollyo Chagi", "result": {"quality": {"passed": False}}, "locale": "tr"},
    )
    assert r.status_code == 422


async def test_sync_upsert_is_idempotent_and_last_writer_wins(client, auth):
    item = {"entity": "training_sessions", "entity_id": 7, "op": "UPSERT", "payload": {"name": "A"}, "client_updated_at": 100}
    r = await client.post("/api/v1/sync/push", headers=auth, json={"items": [item]})
    assert r.json()["accepted"] == 1
    # Older version is ignored.
    older = {**item, "payload": {"name": "old"}, "client_updated_at": 50}
    r = await client.post("/api/v1/sync/push", headers=auth, json={"items": [older]})
    assert r.json()["accepted"] == 0
    newer = {**item, "payload": {"name": "B"}, "client_updated_at": 200}
    await client.post("/api/v1/sync/push", headers=auth, json={"items": [newer]})
    rows = (await client.get("/api/v1/sync/records/training_sessions", headers=auth)).json()
    assert rows == [{"entity_id": 7, "payload": {"name": "B"}, "client_updated_at": 200}]


async def test_sync_delete_hides_record(client, auth):
    up = {"entity": "matches", "entity_id": 1, "op": "UPSERT", "payload": {"x": 1}, "client_updated_at": 1}
    de = {"entity": "matches", "entity_id": 1, "op": "DELETE", "payload": {}, "client_updated_at": 2}
    await client.post("/api/v1/sync/push", headers=auth, json={"items": [up, de]})
    rows = (await client.get("/api/v1/sync/records/matches", headers=auth)).json()
    assert rows == []


async def test_sync_rejects_unknown_entity(client, auth):
    item = {"entity": "users", "entity_id": 1, "op": "UPSERT", "payload": {}, "client_updated_at": 1}
    r = await client.post("/api/v1/sync/push", headers=auth, json={"items": [item]})
    assert r.status_code == 422


async def test_motion_endpoint_reports_unavailable(client, auth):
    files = {"video": ("clip.mp4", b"not a real video", "video/mp4")}
    r = await client.post("/api/v1/motion/analyze", headers=auth, files=files, data={"technique": "Ap Chagi", "side": ""})
    assert r.status_code in (503, 422)


def test_token_helpers():
    token, digest = new_token()
    assert len(token) >= 40
    assert digest == hash_token(token)
    assert verify_token(token, digest)
    assert not verify_token(token + "x", digest)
    assert not verify_token("", digest)


def test_prompt_contains_safety_rules_and_language():
    prompt = coach_system_prompt({"training_sessions": []}, "tr-TR")
    assert BASE_RULES in prompt
    assert "Turkish" in prompt
    assert "Never invent" in prompt
    assert language_instruction("en") == "Answer in English."
