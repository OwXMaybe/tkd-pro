from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..db import SyncRecord, get_session, next_revisions, utcnow
from ..schemas import SYNC_ENTITIES, SyncChange, SyncChangesResponse, SyncPushRequest, SyncPushResponse
from ..security import Owner, require_owner

router = APIRouter(prefix="/api/v1/sync", tags=["sync"])


@router.post("/push", response_model=SyncPushResponse)
async def push(
    body: SyncPushRequest, owner: Owner = Depends(require_owner), session: AsyncSession = Depends(get_session)
) -> SyncPushResponse:
    """Upserts pushed records. Older versions never overwrite newer ones (last-writer-wins by client time)."""
    unknown = {i.entity for i in body.items} - SYNC_ENTITIES
    if unknown:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, f"Unknown entities: {sorted(unknown)}")
    accepted: list[SyncRecord] = []
    count = 0
    pending: dict[tuple[str, int], SyncRecord] = {}
    for item in body.items:
        key = (item.entity, item.entity_id)
        existing = pending.get(key)
        if existing is None:
            existing = (
                await session.execute(
                    select(SyncRecord).where(SyncRecord.entity == item.entity, SyncRecord.entity_id == item.entity_id)
                )
            ).scalar_one_or_none()
        if existing is not None and existing.client_updated_at > item.client_updated_at:
            continue
        if existing is None:
            existing = SyncRecord(entity=item.entity, entity_id=item.entity_id)
            session.add(existing)
        pending[key] = existing
        existing.deleted = item.op == "DELETE"
        existing.payload = {} if existing.deleted else item.payload
        existing.client_updated_at = item.client_updated_at
        existing.received_at = utcnow()
        existing.source = owner.source
        count += 1
        if existing not in accepted:
            accepted.append(existing)
    if accepted:
        first = await next_revisions(session, len(accepted))
        for offset, record in enumerate(accepted):
            record.revision = first + offset
    await session.commit()
    return SyncPushResponse(accepted=count)


@router.get("/changes", response_model=SyncChangesResponse, dependencies=[Depends(require_owner)])
async def changes(
    after: int = Query(0, ge=0),
    limit: int = Query(1000, ge=1, le=5000),
    session: AsyncSession = Depends(get_session),
) -> SyncChangesResponse:
    """Every write after revision `after`, oldest first, deletions included. Devices keep `next_after` as their cursor."""
    rows = (
        await session.execute(
            select(SyncRecord).where(SyncRecord.revision > after).order_by(SyncRecord.revision).limit(limit + 1)
        )
    ).scalars().all()
    has_more = len(rows) > limit
    rows = rows[:limit]
    return SyncChangesResponse(
        items=[
            SyncChange(
                entity=r.entity, entity_id=r.entity_id, op="DELETE" if r.deleted else "UPSERT",
                payload=r.payload, client_updated_at=r.client_updated_at, revision=r.revision,
            )
            for r in rows
        ],
        next_after=rows[-1].revision if rows else after,
        has_more=has_more,
    )


@router.get("/records/{entity}", dependencies=[Depends(require_owner)])
async def list_records(entity: str, session: AsyncSession = Depends(get_session)) -> list[dict]:
    """Current copy of one entity (used for restore and inspection)."""
    if entity not in SYNC_ENTITIES:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Unknown entity")
    rows = (
        await session.execute(
            select(SyncRecord).where(SyncRecord.entity == entity, SyncRecord.deleted.is_(False)).order_by(SyncRecord.entity_id)
        )
    ).scalars().all()
    return [{"entity_id": r.entity_id, "payload": r.payload, "client_updated_at": r.client_updated_at} for r in rows]
