import json
import uuid
from datetime import datetime
from typing import Optional, Any

from fastapi import FastAPI, Depends, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlmodel.ext.asyncio.session import AsyncSession
from sqlmodel import select
from contextlib import asynccontextmanager

from src.database import init_db, get_session, Device, HeartbeatLog, ConfigRecord, CommandRecord, LogRecord
from src.settings import settings


# ─── Lifespan ─────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield

app = FastAPI(
    title="Personal Agent Server",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── Auth helper ──────────────────────────────────────────────────────────────

async def get_device(
    x_device_id: str = Header(alias="X-Device-Id"),
    authorization: str = Header(default=""),
    session: AsyncSession = Depends(get_session)
) -> Device:
    device = await session.get(Device, x_device_id)
    if not device:
        raise HTTPException(status_code=401, detail="Device not enrolled")
    api_key = authorization.removeprefix("Bearer ").strip()
    if api_key != device.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")
    return device


def ok(data: Any = None) -> dict:
    return {
        "ok": True,
        "data": data,
        "meta": {"requestId": str(uuid.uuid4()), "serverTime": int(datetime.utcnow().timestamp() * 1000)}
    }


def err(code: str, message: str) -> dict:
    return {
        "ok": False,
        "error": {"code": code, "message": message},
        "meta": {"requestId": str(uuid.uuid4()), "serverTime": int(datetime.utcnow().timestamp() * 1000)}
    }


# ─── Health ───────────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"ok": True}


# ─── Device Registration ──────────────────────────────────────────────────────

class DevicePayload(BaseModel):
    deviceId: str
    installId: str
    appVersion: str = ""
    appVersionCode: int = 0
    manufacturer: str = ""
    model: str = ""
    androidVersion: str = ""
    sdk: int = 0
    capabilities: list[str] = []
    createdAt: int = 0


class RegisterRequest(BaseModel):
    setupToken: Optional[str] = None
    device: DevicePayload


@app.post("/v1/device/register")
async def register_device(req: RegisterRequest, session: AsyncSession = Depends(get_session)):
    # Validate setup token
    if req.setupToken != settings.SETUP_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid setup token")

    d = req.device
    existing = await session.get(Device, d.deviceId)
    if existing:
        existing.last_seen = datetime.utcnow()
        session.add(existing)
        await session.commit()
        return ok({"apiKey": existing.api_key, "deviceId": existing.device_id})

    device = Device(
        device_id=d.deviceId,
        install_id=d.installId,
        app_version=d.appVersion,
        model=d.model,
        manufacturer=d.manufacturer,
        android_version=d.androidVersion,
        sdk=d.sdk,
        capabilities=json.dumps(d.capabilities),
    )
    session.add(device)
    await session.commit()
    return ok({"apiKey": device.api_key, "deviceId": device.device_id})


# ─── Heartbeat ────────────────────────────────────────────────────────────────

@app.post("/v1/heartbeat")
async def heartbeat(
    body: dict,
    device: Device = Depends(get_device),
    session: AsyncSession = Depends(get_session)
):
    device.last_seen = datetime.utcnow()
    session.add(device)

    log = HeartbeatLog(device_id=device.device_id, payload_json=json.dumps(body))
    session.add(log)
    await session.commit()
    return ok({"received": True})


# ─── Config ───────────────────────────────────────────────────────────────────

DEFAULT_CONFIG = {
    "version": 1,
    "modules": {
        "device_manager":       {"enabled": True},
        "permission_manager":   {"enabled": True},
        "notification_engine":  {"enabled": False},
        "accessibility_engine": {"enabled": False},
        "vision_engine":        {"enabled": False},
        "ocr_engine":           {"enabled": False},
        "automation_engine":    {"enabled": False},
    },
    "intervals": {
        "heartbeatSeconds": 30,
        "configSyncMinutes": 5,
        "logUploadMinutes": 5,
    },
    "limits": {
        "actionsPerMinute": 30,
        "maxWorkflowSteps": 50,
        "maxQueueSize": 10000,
    },
    "privacy": {
        "storeScreenshots": False,
        "uploadScreenshots": False,
        "redactPatterns": [],
    },
    "apps": {
        "allowlist": [],
        "denylist": [],
    },
}


@app.get("/v1/config")
async def get_config(
    deviceId: str,
    currentVersion: int = 0,
    device: Device = Depends(get_device),
    session: AsyncSession = Depends(get_session)
):
    record = await session.get(ConfigRecord, deviceId)
    if record is None:
        record = ConfigRecord(device_id=deviceId, version=1, config_json=json.dumps(DEFAULT_CONFIG))
        session.add(record)
        await session.commit()

    if currentVersion >= record.version:
        return ok({"version": record.version, "config": None})  # No update needed

    return ok({
        "version": record.version,
        "config": json.loads(record.config_json)
    })


# ─── Commands ─────────────────────────────────────────────────────────────────

@app.get("/v1/commands")
async def get_commands(
    limit: int = 20,
    device: Device = Depends(get_device),
    session: AsyncSession = Depends(get_session)
):
    stmt = select(CommandRecord).where(
        CommandRecord.device_id == device.device_id,
        CommandRecord.status == "pending"
    ).limit(limit)
    result = await session.exec(stmt)
    commands = result.all()
    return ok({
        "commands": [
            {
                "id": c.id, "type": c.type,
                "payload": json.loads(c.payload_json),
                "createdAt": int(c.created_at.timestamp() * 1000)
            }
            for c in commands
        ]
    })


class AckRequest(BaseModel):
    commandId: str
    status: str
    result: Optional[dict] = None
    timestamp: int


@app.post("/v1/ack")
async def ack_command(
    body: AckRequest,
    device: Device = Depends(get_device),
    session: AsyncSession = Depends(get_session)
):
    cmd = await session.get(CommandRecord, body.commandId)
    if not cmd or cmd.device_id != device.device_id:
        raise HTTPException(status_code=404, detail="Command not found")
    cmd.status = body.status
    cmd.acked_at = datetime.utcnow()
    cmd.result_json = json.dumps(body.result) if body.result else None
    session.add(cmd)
    await session.commit()
    return ok({"acked": True})


# ─── Logs ─────────────────────────────────────────────────────────────────────

class LogPayload(BaseModel):
    id: str
    level: str
    module: str
    event: str
    message: Optional[str] = None
    data: Optional[dict] = None
    createdAt: int


class LogUploadRequest(BaseModel):
    deviceId: str
    logs: list[LogPayload]


@app.post("/v1/logs")
async def upload_logs(
    body: LogUploadRequest,
    device: Device = Depends(get_device),
    session: AsyncSession = Depends(get_session)
):
    for entry in body.logs:
        record = LogRecord(
            id=entry.id,
            device_id=device.device_id,
            level=entry.level,
            module=entry.module,
            event=entry.event,
            message=entry.message,
            data_json=json.dumps(entry.data) if entry.data else None,
            created_at=datetime.fromtimestamp(entry.createdAt / 1000),
        )
        session.add(record)
    await session.commit()
    return ok({"received": len(body.logs)})


# ─── Create command (from dashboard/admin) ────────────────────────────────────

class CreateCommandRequest(BaseModel):
    deviceId: str
    type: str
    payload: dict = {}


@app.post("/v1/commands")
async def create_command(
    body: CreateCommandRequest,
    session: AsyncSession = Depends(get_session)
):
    """Create a command for a device to execute. No device auth — admin endpoint."""
    device = await session.get(Device, body.deviceId)
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    cmd = CommandRecord(
        device_id=device.device_id,
        type=body.type,
        payload_json=json.dumps(body.payload),
    )
    session.add(cmd)
    await session.commit()
    return ok({"commandId": cmd.id, "deviceId": cmd.device_id, "type": cmd.type})


# ─── Status (for dashboard/debug) ─────────────────────────────────────────────

@app.get("/v1/status")
async def server_status(session: AsyncSession = Depends(get_session)):
    devices = (await session.exec(select(Device))).all()
    return ok({
        "devices": [
            {
                "device_id": d.device_id,
                "model": d.model,
                "app_version": d.app_version,
                "enrolled_at": d.enrolled_at.isoformat() if d.enrolled_at else None,
                "last_seen": d.last_seen.isoformat() if d.last_seen else None,
            }
            for d in devices
        ]
    })
