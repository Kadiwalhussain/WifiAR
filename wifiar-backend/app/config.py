from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    database_url: str = "postgresql+psycopg2://wifiar:wifiar@localhost:5432/wifiar"
    jwt_secret: str = "wifiar-dev-secret-change-in-production-32chars"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 60 * 24 * 7  # 7 days
    cors_origins: str = "*"


@lru_cache
def get_settings() -> Settings:
    return Settings()
