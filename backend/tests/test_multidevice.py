from app.security import COOKIE_NAME, login_limiter, pairing


def _item(entity: str, entity_id: int, t: int, op: str = "UPSERT", **payload):
    return {"entity": entity, "entity_id": entity_id, "op": op, "payload": payload, "client_updated_at": t}


async def test_changes_feed_returns_each_write_once_in_order(client, auth):
    await client.post("/api/v1/sync/push", headers=auth, json={"items": [_item("training_sessions", 1, 10, name="A")]})
    await client.post("/api/v1/sync/push", headers=auth, json={"items": [_item("recovery_entries", 2, 11, energy=4)]})

    first = (await client.get("/api/v1/sync/changes?after=0", headers=auth)).json()
    assert [i["entity"] for i in first["items"]] == ["training_sessions", "recovery_entries"]
    assert first["has_more"] is False
    cursor = first["next_after"]

    # Nothing new: same cursor, no items.
    again = (await client.get(f"/api/v1/sync/changes?after={cursor}", headers=auth)).json()
    assert again == {"items": [], "next_after": cursor, "has_more": False}

    # An update and a delete appear after the cursor, the delete without payload.
    await client.post("/api/v1/sync/push", headers=auth, json={"items": [
        _item("training_sessions", 1, 20, name="B"),
        _item("recovery_entries", 2, 21, op="DELETE"),
    ]})
    later = (await client.get(f"/api/v1/sync/changes?after={cursor}", headers=auth)).json()
    assert [(i["entity_id"], i["op"]) for i in later["items"]] == [(1, "UPSERT"), (2, "DELETE")]
    assert later["items"][0]["payload"] == {"name": "B"}
    assert later["items"][1]["payload"] == {}


async def test_changes_feed_paginates(client, auth):
    items = [_item("performance_metrics", i, i) for i in range(1, 6)]
    await client.post("/api/v1/sync/push", headers=auth, json={"items": items})
    page = (await client.get("/api/v1/sync/changes?after=0&limit=2", headers=auth)).json()
    assert len(page["items"]) == 2 and page["has_more"] is True
    seen = [i["entity_id"] for i in page["items"]]
    while page["has_more"]:
        page = (await client.get(f"/api/v1/sync/changes?after={page['next_after']}&limit=2", headers=auth)).json()
        seen += [i["entity_id"] for i in page["items"]]
    assert seen == [1, 2, 3, 4, 5]


async def test_ignored_old_write_does_not_appear_in_feed(client, auth):
    await client.post("/api/v1/sync/push", headers=auth, json={"items": [_item("matches", 1, 100, x=1)]})
    cursor = (await client.get("/api/v1/sync/changes", headers=auth)).json()["next_after"]
    r = await client.post("/api/v1/sync/push", headers=auth, json={"items": [_item("matches", 1, 50, x=0)]})
    assert r.json()["accepted"] == 0
    assert (await client.get(f"/api/v1/sync/changes?after={cursor}", headers=auth)).json()["items"] == []


async def test_web_login_sets_cookie_and_logout_revokes_it(client):
    login_limiter.reset()
    r = await client.post("/api/v1/auth/login", json={"token": "wrong"})
    assert r.status_code == 401
    r = await client.post("/api/v1/auth/login", json={"token": "test-owner-token"})
    assert r.status_code == 204
    cookie = r.cookies.get(COOKIE_NAME)
    assert cookie and cookie != "test-owner-token"  # the browser gets its own device token
    assert "httponly" in r.headers["set-cookie"].lower()
    assert "samesite=strict" in r.headers["set-cookie"].lower()

    client.cookies.set(COOKIE_NAME, cookie)
    assert (await client.get("/api/v1/auth/session")).status_code == 200
    assert (await client.post("/api/v1/auth/logout")).status_code == 204
    client.cookies.set(COOKIE_NAME, cookie)
    assert (await client.get("/api/v1/auth/session")).status_code == 401


async def test_pairing_code_issues_phone_token_once(client, auth):
    login_limiter.reset()
    code = (await client.post("/api/v1/auth/pairing-code", headers=auth)).json()["code"]
    r = await client.post("/api/v1/auth/pair", json={"code": code, "device_name": "Pixel", "kind": "phone"})
    assert r.status_code == 200
    phone_token = r.json()["token"]
    assert (await client.get("/api/v1/health", headers={"Authorization": f"Bearer {phone_token}"})).status_code == 200
    # One-time: the same code cannot be used again.
    again = await client.post("/api/v1/auth/pair", json={"code": code, "kind": "phone"})
    assert again.status_code == 401

    devices = (await client.get("/api/v1/devices", headers=auth)).json()
    pixel = next(d for d in devices if d["name"] == "Pixel")
    assert pixel["kind"] == "phone"
    assert (await client.delete(f"/api/v1/devices/{pixel['id']}", headers=auth)).status_code == 204
    assert (await client.get("/api/v1/health", headers={"Authorization": f"Bearer {phone_token}"})).status_code == 401


async def test_pairing_code_is_invalidated_after_wrong_guesses(client, auth):
    login_limiter.reset()
    code = (await client.post("/api/v1/auth/pairing-code", headers=auth)).json()["code"]
    wrong = "000000" if code != "000000" else "111111"
    for _ in range(pairing.max_attempts):
        await client.post("/api/v1/auth/pair", json={"code": wrong})
    login_limiter.reset()
    assert (await client.post("/api/v1/auth/pair", json={"code": code})).status_code == 401


async def test_login_is_rate_limited(client):
    login_limiter.reset()
    for _ in range(login_limiter.max_failures):
        await client.post("/api/v1/auth/login", json={"token": "nope"})
    r = await client.post("/api/v1/auth/login", json={"token": "test-owner-token"})
    assert r.status_code == 429
    login_limiter.reset()


async def test_local_login_works_once_and_only_from_this_computer(client):
    code = pairing.new_local_code()
    r = await client.get(f"/auth/local?code={code}", follow_redirects=False)  # test client is 127.0.0.1
    assert r.status_code == 303 and r.headers["location"] == "/"
    assert r.cookies.get(COOKIE_NAME)
    again = await client.get(f"/auth/local?code={code}", follow_redirects=False)
    assert again.headers["location"] == "/#/login"

    client._transport.client = ("192.168.1.50", 50000)  # a phone on the Wi-Fi
    remote = await client.get(f"/auth/local?code={pairing.new_local_code()}", follow_redirects=False)
    assert remote.headers["location"] == "/#/login"
    assert COOKIE_NAME not in remote.cookies


async def test_sync_accepts_parent_keyed_entities(client, auth):
    route = _item("run_routes", 5, 1, points=[[0, 41.0, 29.0, None, 0]], splits=[])
    r = await client.post("/api/v1/sync/push", headers=auth, json={"items": [route]})
    assert r.json()["accepted"] == 1
