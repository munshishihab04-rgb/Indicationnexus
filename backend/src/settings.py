from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "personal-agent-server"
    VERSION: str = "1.0.0"
    DATABASE_URL: str = "sqlite+aiosqlite:///./agent.db"
    SETUP_TOKEN: str = "change-me-in-production"  # Short-lived token for device enrollment
    DEBUG: bool = False

    class Config:
        env_file = ".env"

settings = Settings()
