from collections.abc import AsyncIterator
from datetime import datetime, timezone

from sqlalchemy import (
    JSON, BigInteger, Boolean, DateTime, Index, Integer, String, UniqueConstraint, event, inspect, select, text, update,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from .config import get_settings

# JSONB on PostgreSQL, plain JSON elsewhere (SQLite is used by the desktop server and the tests).
JsonType = JSON().with_variant(JSONB(), "postgresql")
# BIGSERIAL on PostgreSQL; SQLite only auto-increments INTEGER primary keys.
IdType = BigInteger().with_variant(Integer, "sqlite")


class Base(DeclarativeBase):
    pass


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class SyncRecord(Base):
    """Latest copy of a record written by any of the owner's devices (phone app or web app).

    `revision` increases with every accepted write across all records, so a device can ask for
    "everything after revision N" and receive each change exactly once, deletions included.
    """

    __tablename__ = "sync_records"
    __table_args__ = (
        UniqueConstraint("entity", "entity_id", name="uq_sync_entity"),
        Index("ix_sync_entity_updated", "entity", "client_updated_at"),
        Index("ix_sync_revision", "revision"),
    )

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    entity: Mapped[str] = mapped_column(String(64), nullable=False)
    entity_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    payload: Mapped[dict] = mapped_column(JsonType, nullable=False, default=dict)
    deleted: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    client_updated_at: Mapped[int] = mapped_column(BigInteger, nullable=False)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    revision: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    # Device that wrote this version, so a device can skip its own echoes.
    source: Mapped[str] = mapped_column(String(64), nullable=False, default="")


class SyncCounter(Base):
    """Single-row counter that hands out sync revisions."""

    __tablename__ = "sync_counter"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    value: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)


class DeviceToken(Base):
    """Access tokens issued to the owner's own devices through pairing. Only the SHA-256 is stored."""

    __tablename__ = "device_tokens"

    id: Mapped[int] = mapped_column(IdType, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    kind: Mapped[str] = mapped_column(String(16), nullable=False, default="web")
    token_sha256: Mapped[str] = mapped_column(String(64), nullable=False, unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    last_used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    revoked: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)


async def next_revisions(session: AsyncSession, count: int) -> int:
    """Reserves `count` revisions and returns the first one. Runs inside the caller's transaction."""
    row = (await session.execute(select(SyncCounter).where(SyncCounter.id == 1).with_for_update())).scalar_one_or_none()
    if row is None:
        row = SyncCounter(id=1, value=0)
        session.add(row)
        await session.flush()
    first = row.value + 1
    await session.execute(update(SyncCounter).where(SyncCounter.id == 1).values(value=row.value + count))
    row.value += count
    return first


_engine: AsyncEngine | None = None
_sessionmaker: async_sessionmaker[AsyncSession] | None = None


def init_engine(url: str | None = None) -> AsyncEngine:
    global _engine, _sessionmaker
    url = url or get_settings().database_url
    if url.startswith("sqlite"):
        _engine = create_async_engine(url, connect_args={"timeout": 30})

        @event.listens_for(_engine.sync_engine, "connect")
        def _sqlite_pragmas(dbapi_conn, _):
            cur = dbapi_conn.cursor()
            cur.execute("PRAGMA journal_mode=WAL")
            cur.close()
    else:
        _engine = create_async_engine(url, pool_pre_ping=True)
    _sessionmaker = async_sessionmaker(_engine, expire_on_commit=False)
    return _engine


def _upgrade(conn) -> None:
    """Adds columns introduced after 1.0 to an existing sync_records table (create_all only adds tables)."""
    columns = {c["name"] for c in inspect(conn).get_columns("sync_records")}
    if "revision" not in columns:
        conn.execute(text("ALTER TABLE sync_records ADD COLUMN revision BIGINT NOT NULL DEFAULT 0"))
        conn.execute(text("CREATE INDEX IF NOT EXISTS ix_sync_revision ON sync_records (revision)"))
    if "source" not in columns:
        conn.execute(text("ALTER TABLE sync_records ADD COLUMN source VARCHAR(64) NOT NULL DEFAULT ''"))


async def create_schema() -> None:
    assert _engine is not None
    async with _engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await conn.run_sync(_upgrade)


async def dispose_engine() -> None:
    if _engine is not None:
        await _engine.dispose()


def sessionmaker() -> async_sessionmaker[AsyncSession]:
    assert _sessionmaker is not None, "Database engine not initialised"
    return _sessionmaker


async def get_session() -> AsyncIterator[AsyncSession]:
    async with sessionmaker()() as session:
        yield session
