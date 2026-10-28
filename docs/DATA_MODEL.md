# Data model (Room, version 1)

All timestamps are epoch milliseconds; recovery entries are keyed by epoch day. Enums are stored by name.
The exported schema JSON is in `app/schemas/com.tkdpro.core.database.TkdDatabase/1.json`.

| Entity | Table | Key relations / indexes |
|---|---|---|
| AthleteProfile | `athlete_profile` | single row (id = 1) |
| BeltRecord | `belt_records` | index `achievedOn` |
| PersonalGoal | `personal_goals` | index `status` |
| Competition | `competitions` | index `date` |
| TrainingSession | `training_sessions` | indexes `startTime`, `category`, `status`; duplicate guard (name + start + category) |
| TrainingTechnique | `techniques` | unique `builtInKey` (built-in library) |
| TechniqueRecord | `technique_records` | FK session (cascade), FK technique (cascade) |
| Combination / CombinationStep / CombinationPractice | `combinations`, `combination_steps`, `combination_practices` | steps ordered by `position`, FK cascade |
| VideoAsset | `video_assets` | path in app-private storage |
| MotionAnalysis | `motion_analyses` | FK video (cascade); `resultJson` = serialized `MotionResult`, `posePath` = pose frames file |
| Match / MatchRound / MatchEvent | `matches`, `match_rounds`, `match_events` | unique (matchId, round number); FK cascade |
| ScoringPreset | `scoring_presets` | user-maintained point values |
| RunningSession | `running_sessions` | indexes `startTime`, `status`; best 1/5/10 km efforts |
| GpsPoint | `gps_points` | unique (runId, timeMs) → duplicate points ignored; `segment` splits pauses |
| RunningSplit | `running_splits` | unique (runId, idx) |
| ReactionTest / ReactionAttempt | `reaction_tests`, `reaction_attempts` | device model and test conditions stored per test |
| PerformanceMetric | `performance_metrics` | test type, value, unit, side, method |
| RecoveryEntry | `recovery_entries` | unique `epochDay` (one check-in per day) |
| AiReport / ChatMessage | `ai_reports`, `chat_messages` | data sources JSON per report/answer |
| SyncQueueItem | `sync_queue` | unique (entity, entityId) — repeated edits collapse |

Backend (PostgreSQL): `sync_records(entity, entity_id, payload JSONB, deleted, client_updated_at, received_at)`,
unique (entity, entity_id) — see `backend/schema.sql`.
