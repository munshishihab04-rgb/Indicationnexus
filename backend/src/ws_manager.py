"""
WebSocket connection manager for real-time device → dashboard push.

Usage:
  await ws_manager.connect(device_id, websocket)
  ws_manager.disconnect(device_id, websocket)
  await ws_manager.broadcast(device_id, {"event": "heartbeat", ...})
  await ws_manager.broadcast_all({"event": "server_status", ...})
"""
import asyncio
import json
import logging
from collections import defaultdict
from fastapi import WebSocket

log = logging.getLogger(__name__)


class WebSocketManager:
    def __init__(self):
        # device_id → set of connected WebSocket clients
        self._connections: dict[str, set[WebSocket]] = defaultdict(set)
        # admin connections (receive all events)
        self._admin: set[WebSocket] = set()

    async def connect(self, websocket: WebSocket, device_id: str | None = None):
        await websocket.accept()
        if device_id:
            self._connections[device_id].add(websocket)
            log.info(f"WS connected: device={device_id} total={self.count(device_id)}")
        else:
            self._admin.add(websocket)
            log.info(f"WS admin connected total={len(self._admin)}")

    def disconnect(self, websocket: WebSocket, device_id: str | None = None):
        if device_id:
            self._connections[device_id].discard(websocket)
        else:
            self._admin.discard(websocket)

    async def broadcast(self, device_id: str, payload: dict):
        """Send event to all dashboards watching this device."""
        msg = json.dumps(payload)
        dead: list[WebSocket] = []
        for ws in list(self._connections.get(device_id, set())):
            try:
                await ws.send_text(msg)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self._connections[device_id].discard(ws)
        # Also push to admin connections
        await self._push_admin(msg)

    async def broadcast_all(self, payload: dict):
        """Send event to all connected dashboards."""
        msg = json.dumps(payload)
        for device_id in list(self._connections.keys()):
            dead: list[WebSocket] = []
            for ws in list(self._connections[device_id]):
                try:
                    await ws.send_text(msg)
                except Exception:
                    dead.append(ws)
            for ws in dead:
                self._connections[device_id].discard(ws)
        await self._push_admin(msg)

    async def _push_admin(self, msg: str):
        dead: list[WebSocket] = []
        for ws in list(self._admin):
            try:
                await ws.send_text(msg)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self._admin.discard(ws)

    def count(self, device_id: str) -> int:
        return len(self._connections.get(device_id, set()))

    @property
    def total_connections(self) -> int:
        return sum(len(v) for v in self._connections.values()) + len(self._admin)


ws_manager = WebSocketManager()
