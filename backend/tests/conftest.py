import pytest
import os

# Override DB before any import of app
os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite:///:memory:")
os.environ.setdefault("SETUP_TOKEN", "ci-test-token")

pytest_plugins = ('anyio',)
