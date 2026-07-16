from sqlmodel import SQLModel, Field, create_engine
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlmodel.ext.asyncio.session import AsyncSession
from typing import Optional
from datetime import datetime
import uuid

from src.settings import settings


# ─── Engine ───────────────────────────────────────────────────────────────────

connect_args = {"check_same_thread": False} if "sqlite" in settings.DATABASE_URL else {}
engine = create_async_engine(settings.DATABASE_URL, echo=settings.DEBUG, connect_args=connect_args)
async_session = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(SQLModel.metadata.create_all)


async def get_session():
    async with async_session() as session:
        yield session


# ─── Models ───────────────────────────────────────────────────────────────────

def gen_uuid() -> str:
    return str(uuid.uuid4())


class Device(SQLModel, table=True):
    __tablename__ = "devices"
    device_id: str = Field(primary_key=True)
    install_id: str
    api_key: str = Field(default_factory=gen_uuid)
    app_version: str = ""
    model: str = ""
    manufacturer: str = ""
    android_version: str = ""
    sdk: int = 0
    capabilities: str = "[]"   # JSON array
    enrolled_at: datetime = Field(default_factory=datetime.utcnow)
    last_seen: Optional[datetime] = None


class HeartbeatLog(SQLModel, table=True):
    __tablename__ = "heartbeats"
    id: str = Field(default_factory=gen_uuid, primary_key=True)
    device_id: str
    payload_json: str
    received_at: datetime = Field(default_factory=datetime.utcnow)


class ConfigRecord(SQLModel, table=True):
    __tablename__ = "configs"
    device_id: str = Field(primary_key=True)
    version: int = 1
    config_json: str = "{}"
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class CommandRecord(SQLModel, table=True):
    __tablename__ = "commands"
    id: str = Field(default_factory=gen_uuid, primary_key=True)
    device_id: str
    type: str
    payload_json: str = "{}"
    status: str = "pending"     # pending / acked / failed
    created_at: datetime = Field(default_factory=datetime.utcnow)
    acked_at: Optional[datetime] = None
    result_json: Optional[str] = None


class LogRecord(SQLModel, table=True):
    __tablename__ = "logs"
    id: str = Field(primary_key=True)
    device_id: str
    level: str
    module: str
    event: str
    message: Optional[str] = None
    data_json: Optional[str] = None
    created_at: datetime
    received_at: datetime = Field(default_factory=datetime.utcnow)
