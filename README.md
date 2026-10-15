# Task Manager API

Spring Boot 4, Java 21, PostgreSQL, Liquibase and Redis. No UI or authentication.

## Run

```bash
docker compose up -d --wait
./mvnw spring-boot:run
```

The API is at `http://localhost:8080`; Swagger UI is at
`http://localhost:8080/swagger-ui.html`. The first launch applies Liquibase
migrations and inserts sample users and tasks.

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
{"name":"Finish a book","status":"COMPLETED","category":"EDUCATION"}
```

Allowed statuses: `IN_PROGRESS`, `COMPLETED`, `FAILED`, `CANCELED`.
Allowed categories: `HOME`, `WORK`, `EDUCATION`, `HEALTH`, `HOBBY`, `OTHER`.

## Checking Redis in Postman

Send `GET /api/tasks/by-category/WORK` several times and compare Postman's
response time. The first request reads PostgreSQL; later requests use Redis for
five minutes. The application logs `Loading tasks by category=WORK from database`
only on a cache miss. You can confirm the key with:

```bash
docker compose exec -T redis redis-cli --scan
```

After a task is created, updated or deleted, all task cache entries are removed
so the next GET reads fresh data. Postman timings include network and client
overhead, so run several requests rather than comparing only one pair. For a
baseline without cache, restart the app with `SPRING_CACHE_TYPE=none`.

DTOs keep HTTP payloads separate from JPA entities: request DTOs contain only
fields clients may set, response DTOs include useful task/owner fields but never
expose the user's password. Mappers perform these conversions in one place.
