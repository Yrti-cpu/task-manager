# Task Manager API

Spring Boot 4, Java 21, PostgreSQL, Liquibase and optional Redis. No UI or authentication.

## Run

```bash
docker compose up -d --wait
./mvnw spring-boot:run
```

The API is at `http://localhost:8080`; Swagger UI is at
`http://localhost:8080/swagger-ui.html`. The first launch applies Liquibase migrations. Sample data is opt-in:
set `SPRING_LIQUIBASE_CONTEXTS=demo` on a fresh database to insert sample users
and tasks. Passwords are not accepted or stored; this API has no authentication.

Redis caching is enabled by default. To run locally without Redis, set
`APP_CACHE_REDIS_ENABLED=false`; the application then uses an in-memory cache
with the same cache names and service-level cache annotations.

Task changes are persisted together with a transactional outbox event. Kafka
and the retrying outbox dispatcher are enabled by default for local development.
`docker compose up -d --wait` starts PostgreSQL, Redis and a single-node Kafka
3.9 broker/controller in KRaft mode (no ZooKeeper), with persistent storage and
a broker healthcheck. The application itself is run separately with Maven.

Applications on the host connect to `localhost:9092`; services in the Compose
network must use `kafka:29092` (`KAFKA_BOOTSTRAP_SERVERS=kafka:29092`). These
PLAINTEXT listeners are for local development only. Topic auto-creation is
enabled, including the default `task-events` topic on first publication.

Disable dispatch explicitly with `OUTBOX_DISPATCHER_ENABLED=false` when Kafka
is not needed. Optionally tune `OUTBOX_DISPATCHER_FIXED_DELAY_MS` and
`OUTBOX_DISPATCHER_BATCH_SIZE`.

There is no application container in this Compose file. The notification/SSE
service lives in a separate directory and is managed independently; start it
with the same topic and the appropriate Kafka listener to complete the flow.
A future application container should depend on healthy `postgres`, `redis`
and `kafka` services.

The dispatcher atomically claims events with PostgreSQL `SKIP LOCKED` and an
expiring lease/token. Events for a task are sent in outbox sequence order;
a failed predecessor blocks later events for that task. Delivery is at-least-once:
consumers must deduplicate `eventId`, including after a successful Kafka send
followed by a database failure.

Additional `app.outbox.dispatcher` settings (also configurable with Spring's
standard environment variable binding): `max-attempts` (10), `lease-duration`
(2m), `send-timeout` (10s), `initial-backoff` (30s), `max-backoff` (1h), and
`retention` (7d). Durations and batch size are validated on startup. Permanent
payload errors and exhausted retries become `FAILED`; transient errors use
exponential backoff. Published rows are cleaned up after retention.

Monitor `FAILED` rows: after investigating and correcting the cause, an operator
may explicitly reset a failed row to `PENDING` with `attempts=0` and
`next_attempt_at=now()`. Do not discard or replay events blindly: failed events
intentionally block subsequent events for their aggregate.

## Postman checks

Set `baseUrl` to `http://localhost:8080` and use JSON bodies.

| Method | URL | Expected result |
| --- | --- | --- |
| GET | `{{baseUrl}}/api/tasks` | `200`, task list |
| GET | `{{baseUrl}}/api/tasks/by-category/WORK` | `200`, only work tasks |
| GET | `{{baseUrl}}/api/tasks/by-status/IN_PROGRESS` | `200`, only in-progress tasks |
| GET | `{{baseUrl}}/api/tasks/by-owner/1` | `200`, tasks for owner 1 |
| GET | `{{baseUrl}}/api/tasks/999999` | `404`, Problem Detail JSON |
| POST | `{{baseUrl}}/api/users` | `201` for a new username, `409` for an existing one |
| POST | `{{baseUrl}}/api/tasks` | `201`; use the returned `id` for PUT and DELETE |
| PUT | `{{baseUrl}}/api/tasks/{id}` | `200`, updated task |
| DELETE | `{{baseUrl}}/api/tasks/{id}` | `204` |

Example task creation body:

```json
{"name":"Read a book","status":"IN_PROGRESS","category":"EDUCATION","ownerId":1}
```

Example update body:

```json
{"name":"Finish a book","status":"COMPLETED","category":"EDUCATION","version":0}
```

Allowed statuses: `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELED`.
Allowed categories: `HOME`, `WORK`, `EDUCATION`, `HEALTH`, `HOBBY`, `OTHER`.

## Pagination and concurrency

All task/user collection endpoints return a page envelope, not a bare JSON array.
Use `?page=0&size=20`; task search accepts `status`, `from`, `to` and an allowed
`sort` field. Sorting includes `id` as a tie-breaker. Invalid date ranges are
rejected with `400`.

Task responses contain `version`. Send the last version read in every update;
a stale version returns `409` rather than overwriting another client's changes.
A new deadline must not be in the past; updating a task may preserve its existing
overdue deadline or clear it with `null`.

Create a user with `{"username":"alice"}`. Names are limited to 255 characters.
Audit timestamps use UTC instants. The timestamp migration treats legacy
zone-less timestamps as UTC; back up an existing database before upgrading.

## Caching

Only `GET /api/tasks/{id}` is cached. Lists are paginated database queries and
fetch owner data without per-row lazy queries. Updating/deleting a task evicts
only its cache key after the database transaction commits.

Redis entries expire after five minutes. Local mode uses Caffeine with the same
TTL and a maximum of 10,000 entries. Local caches are process-local: use Redis
when running multiple application instances. Cache names are defined solely in
`CacheNames`; manually configured managers do not use `spring.cache.cache-names`.

Inspect Redis keys with:

```bash
docker compose exec -T redis redis-cli --scan
```

## Tests

```bash
./mvnw test
```

Docker is required for integration tests. Testcontainers starts isolated
PostgreSQL containers on random ports; tests do not use the development database.
The suite demonstrates Mockito unit tests, parameterized Jakarta Validation,
MVC slices with `@MockitoBean`, and mandatory outbox transactions verified with
`TransactionTemplate` against PostgreSQL. The sort-validation slice mocks the
task service and checks that `InvalidTaskRequestException` maps to HTTP 400.
JSON uses Spring Boot's auto-configured Jackson 3 `JsonMapper`; no deprecated
Jackson 2 builder/configuration is needed.
