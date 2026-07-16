"""
Global error handlers and structured logging for the FastAPI backend.
Import and call register_handlers(app) in main.py.
"""
import logging
import time
import uuid
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

# ─── Structured logging setup ─────────────────────────────────────────────────

def setup_logging():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%dT%H:%M:%S",
    )
    # Silence noisy libraries
    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("sqlalchemy.engine").setLevel(logging.WARNING)


# ─── Error handlers ───────────────────────────────────────────────────────────

def register_handlers(app: FastAPI):
    setup_logging()

    @app.middleware("http")
    async def request_id_middleware(request: Request, call_next):
        request_id = request.headers.get("X-Request-Id", str(uuid.uuid4())[:8])
        start = time.monotonic()
        response = await call_next(request)
        duration = int((time.monotonic() - start) * 1000)
        response.headers["X-Request-Id"] = request_id
        response.headers["X-Response-Time"] = f"{duration}ms"
        return response

    @app.exception_handler(StarletteHTTPException)
    async def http_exception_handler(request: Request, exc: StarletteHTTPException):
        return JSONResponse(
            status_code=exc.status_code,
            content={"ok": False, "error": {"code": str(exc.status_code), "message": str(exc.detail)}},
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError):
        errors = [
            {"field": ".".join(str(l) for l in e["loc"]), "msg": e["msg"]}
            for e in exc.errors()
        ]
        return JSONResponse(
            status_code=422,
            content={"ok": False, "error": {"code": "validation_error", "errors": errors}},
        )

    @app.exception_handler(Exception)
    async def generic_exception_handler(request: Request, exc: Exception):
        logging.getLogger("agent.api").error(
            f"Unhandled exception on {request.method} {request.url.path}: {exc}",
            exc_info=True
        )
        return JSONResponse(
            status_code=500,
            content={"ok": False, "error": {"code": "internal_error", "message": "An internal error occurred"}},
        )
