"""
Backend test suite — Phase 9 hardening.
Synchronous style using asyncio.run() to avoid pytest-asyncio scope issues.
"""
import os
os.environ["SETUP_TOKEN"] = "change-me-in-production"

import asyncio
import json
import uuid
import pytest
from httpx import AsyncClient, ASGITransport
from sqlmodel import SQLModel
from sqlmodel.ext.asyncio.session import AsyncSession as SQLModelAsyncSession
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker

from src.main import app
from src.database import get_session

SETUP_TOKEN = "change-me-in-production"

# ─── Test DB setup ────────────────────────────────────────────────────────────

_engine = create_async_engine("sqlite+aiosqlite:///:memory:", echo=False)
_SessionFactory = async_sessionmaker(_engine, class_=SQLModelAsyncSession, expire_on_commit=False)


async def _init():
    async with _engine.begin() as conn:
        await conn.run_sync(SQLModel.metadata.create_all)

asyncio.run(_init())


async def _override_session():
    async with _SessionFactory() as s:
        yield s


app.dependency_overrides[get_session] = _override_session


# ─── Helpers ──────────────────────────────────────────────────────────────────

def run(coro):
    return asyncio.run(coro)


def auth(device_id, api_key):
    return {"X-Device-Id": device_id, "Authorization": f"Bearer {api_key}"}


async def _client():
    transport = ASGITransport(app=app)
    return AsyncClient(transport=transport, base_url="http://test")


async def enroll(client):
    did = f"test-{uuid.uuid4().hex[:8]}"
    r = await client.post("/v1/device/register", json={
        "setupToken": SETUP_TOKEN,
        "device": {
            "deviceId": did, "installId": uuid.uuid4().hex,
            "appVersion": "1.0.0", "appVersionCode": 1,
            "manufacturer": "pytest", "model": "test",
            "androidVersion": "14", "sdk": 34,
            "capabilities": [], "createdAt": 0,
        }
    })
    assert r.status_code == 200, r.text
    return did, r.json()["data"]["apiKey"]


# ─── Tests ────────────────────────────────────────────────────────────────────

def test_health():
    async def _():
        async with await _client() as c:
            r = await c.get("/health")
            assert r.status_code == 200
            assert r.json()["ok"] is True
    run(_())


def test_register_success():
    async def _():
        async with await _client() as c:
            did = f"test-{uuid.uuid4().hex[:8]}"
            r = await c.post("/v1/device/register", json={
                "setupToken": SETUP_TOKEN,
                "device": {
                    "deviceId": did, "installId": uuid.uuid4().hex,
                    "appVersion": "1", "appVersionCode": 1,
                    "manufacturer": "t", "model": "t",
                    "androidVersion": "14", "sdk": 34,
                    "capabilities": [], "createdAt": 0,
                }
            })
            assert r.status_code == 200
            assert r.json()["data"]["deviceId"] == did
            assert "apiKey" in r.json()["data"]
    run(_())


def test_register_bad_token():
    async def _():
        async with await _client() as c:
            r = await c.post("/v1/device/register", json={
                "setupToken": "wrong",
                "device": {
                    "deviceId": "x", "installId": "y",
                    "appVersion": "1", "appVersionCode": 1,
                    "manufacturer": "t", "model": "t",
                    "androidVersion": "14", "sdk": 34,
                    "capabilities": [], "createdAt": 0,
                }
            })
            assert r.status_code == 403
    run(_())


def test_register_missing_fields():
    async def _():
        async with await _client() as c:
            r = await c.post("/v1/device/register", json={"setupToken": SETUP_TOKEN})
            assert r.status_code == 422
    run(_())


def test_heartbeat_ok():
    async def _():
        async with await _client() as c:
            did, key = await enroll(c)
            r = await c.post("/v1/heartbeat",
                headers=auth(did, key),
                json={
                    "deviceId": did, "timestamp": 1_700_000_000_000,
                    "battery": {"percent": 75, "charging": False, "temperatureC": 28.0, "powerSaveMode": False},
                    "network": {"transport": "wifi", "metered": False, "connected": True},
                    "storage": {"totalBytes": 64_000_000_000, "freeBytes": 20_000_000_000},
                    "memory":  {"totalBytes": 8_000_000_000, "availableBytes": 3_000_000_000, "lowMemory": False},
                    "modules": [{"id": "device_manager", "status": "HEALTHY", "queueDepth": 0}],
                    "queueDepth": 0, "configVersion": 1,
                })
            assert r.status_code == 200
            assert r.json()["ok"] is True
    run(_())


def test_heartbeat_unauthenticated():
    async def _():
        async with await _client() as c:
            r = await c.post("/v1/heartbeat",
                headers={"X-Device-Id": "ghost", "Authorization": "Bearer bad"},
                json={"deviceId": "ghost"})
            assert r.status_code == 401
    run(_())


def test_config_returns_default():
    async def _():
        async with await _client() as c:
            did, key = await enroll(c)
            r = await c.get(f"/v1/config?deviceId={did}&currentVersion=0",
                headers=auth(did, key))
            assert r.status_code == 200
            assert r.json()["data"]["version"] >= 1
    run(_())


def test_commands_empty():
    async def _():
        async with await _client() as c:
            did, key = await enroll(c)
            r = await c.get("/v1/commands", headers=auth(did, key))
            assert r.status_code == 200
            assert r.json()["data"]["commands"] == []
    run(_())


def test_create_and_fetch_command():
    async def _():
        async with await _client() as c:
            did, key = await enroll(c)
            r = await c.post("/v1/commands",
                json={"deviceId": did, "type": "get_stats", "payload": {}})
            assert r.status_code == 200
            cmd_id = r.json()["data"]["commandId"]

            r2 = await c.get("/v1/commands", headers=auth(did, key))
            assert any(c2["id"] == cmd_id for c2 in r2.json()["data"]["commands"])
    run(_())


def test_ack_command():
    async def _():
        async with await _client() as c:
            did, key = await enroll(c)
            r = await c.post("/v1/commands",
                json={"deviceId": did, "type": "heartbeat", "payload": {}})
            cmd_id = r.json()["data"]["commandId"]
            r2 = await c.post("/v1/ack",
                headers=auth(did, key),
                json={"commandId": cmd_id, "status": "acked",
                      "result": {}, "timestamp": 1_700_000_000_000})
            assert r2.status_code == 200
    run(_())


def test_upload_logs():
    async def _():
        async with await _client() as c:
            did, key = await enroll(c)
            r = await c.post("/v1/logs",
                headers=auth(did, key),
                json={"deviceId": did, "logs": [{
                    "id": uuid.uuid4().hex, "level": "INFO",
                    "module": "dm", "event": "ok",
                    "message": "test", "data": None,
                    "createdAt": 1_700_000_000_000,
                }]})
            assert r.status_code == 200
            assert r.json()["data"]["received"] == 1
    run(_())


def test_status():
    async def _():
        async with await _client() as c:
            r = await c.get("/v1/status")
            assert r.status_code == 200
            assert "devices" in r.json()["data"]
    run(_())


def test_command_missing_device():
    async def _():
        async with await _client() as c:
            r = await c.post("/v1/commands",
                json={"deviceId": "no-such-device", "type": "ping", "payload": {}})
            assert r.status_code == 404
    run(_())


def test_accessibility_missing_device():
    async def _():
        async with await _client() as c:
            r = await c.post("/v1/accessibility/action",
                json={"deviceId": "ghost", "actionType": "CLICK"})
            assert r.status_code == 404
    run(_())
