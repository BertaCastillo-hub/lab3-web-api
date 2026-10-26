# Lab 3 Web API -- Project Report

## What I specified

Before completing the tests, I specified the safety and idempotency of each method of the employee API, following RFC 9110 (§9.2.1 Safe Methods, §9.2.2 Idempotent Methods):

| Method | Endpoint | Safe | Idempotent |
| --- | --- | --- | --- |
| `POST` | `/employees` | No | No |
| `GET` | `/employees/{id}` | Yes | Yes |
| `GET` | `/employees` | Yes | Yes |
| `PUT` | `/employees/{id}` | No | Yes |
| `DELETE` | `/employees/{id}` | No | Yes |

Since the repository is a mock, the server state is represented by what the mock answers and by the calls the controller makes to it. So I translated each property into something a test can check:

- **Safe** means that the request does not ask for a change of state. In the test, the writing methods of the repository (`save` and `deleteById`) must be called **0 times**. Also, each `GET` must only use the read method it needs: the item `GET` must not call `findAll`, and the collection `GET` must not call `findById`.
- **Idempotent** means that repeating the same request has the same intended effect on the server as sending it once. In the test, the second identical request must not allocate a new id or leave a different state. The status code is allowed to change (for example, `201` and then `200` for `PUT`).
- **Not idempotent** (`POST`) means that two identical requests create two employees with two different ids.

I would know that the tests pass, and that they really prove these properties, when:

- the five tests in `ControllerTests` run without `TODO` and finish successfully with `./gradlew check`,
- every test verifies both which repository methods were called (and how many times) and which ones were not called, not only the HTTP response.

## What I changed

- **ControllerTests.kt** (`src/test/kotlin/es/unizar/webeng/lab3/`):
    - Added the imports required by the blocks: `io.mockk.every`, `io.mockk.justRun`, `io.mockk.verify` and `java.util.Optional`.
    - Replaced the ten `TODO` calls (one setup and one verification per test) with the blocks given in `docs/GUIDE.md`, without modifying them.
    - Added a short comment above each block explaining what state it represents and which property (safe / idempotent) the verification proves.
- **REPORT.md**: this report.

Controller.kt was not changed. The behaviour of the API is exactly the one of the starter.

## Technical decisions

I used only the blocks provided in the guide. For each test, the setup reproduces the "Initial state / Runtime state" comment, and the verification proves the property of the method.

- **`POST` — not safe, not idempotent**
    - Setup: *save returns a new id on each call* (id 1 and then id 2), because each create must produce a new employee.
    - Verify: *both saves discard the client id* (`save` called twice with `id = null`, since the controller sets the id to `null`) and *no read or delete*.
    - Two calls to `save` and two different ids show that the request is neither safe nor idempotent.
- **`GET /employees/{id}` — safe, idempotent**
    - Setup: *findById returns an employee for id 1* and *findById returns empty for id 2*. I used two fixed answers instead of a changing one, because a read must not change the state between calls.
    - Verify: *findById(1) was called twice*, *findById(2) was called twice* and *no modifying methods were called*.
    - A repeated `404` is still idempotent: the resource stays absent and nothing is written.
- **`PUT /employees/{id}` — not safe, idempotent**
    - Setup: *findById is empty, then returns the employee* (the first `PUT` creates the employee and returns `201`, the second one finds it and replaces it with `200`) and *save returns the employee at id 1*.
    - Verify: *both saves store the same employee* (Tom, Manager, id 1, twice), *findById(1) was called twice* (the controller looks up the target before choosing create or replace) and *no delete or list*.
    - The status changes from `201` to `200`, but the stored state is the same after one or two requests, which is what idempotency requires.
- **`DELETE /employees/{id}` — not safe, idempotent**
    - Setup: *deleteById may be called*, using `justRun` because `deleteById` returns `Unit`.
    - Verify: *deleteById was called twice, and nothing else*. The controller does not check whether the employee exists, so the second `DELETE` also returns `204` and the resource stays absent (RFC 9110 §9.3.5, §15.3.5).
- **`GET /employees` — safe, idempotent**
    - Setup: *findAll returns one employee*.
    - Verify: *findAll was called twice* and *the list does not write or read one id*.

**What I rejected:**

- Using *save returns a new id on each call* for `PUT`: it would allocate a new id on the second request, which is the behaviour of `POST`, not of an idempotent `PUT`.
- Using *findById is empty, then returns the employee* for the item `GET`: it would simulate a state change between two reads, which contradicts a safe method.
- Using `every { ... }` for `deleteById`: `justRun` is the MockK way to stub a method that returns nothing.
- Verifying only the HTTP responses: the responses alone do not prove that the repository was not written, so every test also checks the methods that must not be called (`exactly = 0`).
- Changing the controller to make a test pass, which the guide does not allow.

## How I verified

I ran the following commands:

```bash
./gradlew test
./gradlew check
```

The five tests passed (5 tests, 0 failures, 0 errors, 0 skipped). The checks finished with `BUILD SUCCESSFUL`, so the tests and Ktlint (`ktlintMainSourceSetCheck`, `ktlintTestSourceSetCheck`) pass. A single test can be run with a filter, for example:

```bash
./gradlew test --tests "*PUT*"
```

To check that the `verify` blocks really detect a wrong behaviour, I temporarily changed `verify(exactly = 2)` to `verify(exactly = 1)` for the `save` call in the `POST` test and ran `./gradlew test --tests "*POST*"`. The test failed (`BUILD FAILED`), because the mock had received two `save` calls instead of one. I then restored the original value and the test passed again. This confirms that the call counts are not decorative: if the controller called the repository a different number of times, the tests would fail.

I also tested the real API (with the H2 database instead of the mock). The server is started with:

```bash
./gradlew bootRun
```

The requests were sent from Git Bash (they also work in WSL, Linux or macOS):

```bash
curl -i -X POST http://localhost:8080/employees -H "Content-Type: application/json" -d '{"name":"Mary","role":"Manager"}'
curl -i -X PUT http://localhost:8080/employees/1 -H "Content-Type: application/json" -d '{"name":"Tom","role":"Manager"}'
curl -i -X DELETE http://localhost:8080/employees/10
curl -i http://localhost:8080/employees/10
curl -i http://localhost:8080/employees
```

In Windows PowerShell 5.1 these exact commands do not work: `curl` is an alias of `Invoke-WebRequest`, and even with `curl.exe` PowerShell removes the inner double quotes of the JSON body, so the server receives invalid JSON and answers `400 Bad Request`. In PowerShell the quotes must be escaped:

```powershell
curl.exe -i -X POST http://localhost:8080/employees -H "Content-Type: application/json" -d '{\"name\":\"Mary\",\"role\":\"Manager\"}'
```

Results (each request sent twice):

- `POST /employees`: `201` with `Location: .../employees/1`, then `201` with `Location: .../employees/2`. Two employees were created, so `POST` is not idempotent.
- `PUT /employees/1` (existing employee): `200` both times, with the same body. The list afterwards still has two employees.
- `DELETE /employees/10`: `204` both times. `GET /employees/10` returns `404` before and after.
- `GET /employees`: same list on repeated calls.

**Observation:** `PUT` on an id that does **not** exist in H2 (for example `/employees/10`) returned `500` instead of `201`. The log shows a `StaleObjectStateException` ("Row was already updated or deleted by another transaction"). The `Employee` id uses `@GeneratedValue`, so when the controller sets the id manually and calls `save`, Hibernate treats the entity as an existing one and the update fails. The `PUT` test does not detect this because the repository is a mock that always answers what the setup says. I did not change the controller, since the guide does not allow it, but this shows the limits of a mock-based test: only a test with the real database (H2) would detect it.

## AI disclosure

- **Tools / skills:** Claude Code (Claude Opus 5.5 and Claude Sonnet 5.5) in VS Code.

- **Purpose:**

    - Help to summarise `docs/GUIDE.md` and to outline the test changes before implementation.
    - Assistance for creating the commands to manually test the lab.
    - Draft this report following the style of a previous report of mine.

- **Representative prompts:**

    - "Create a plan to follow to complete the tests."
    - "Apply the same format and style of this other report to REPORT.md."

- **Affected files/sections:** `src/test/kotlin/es/unizar/webeng/lab3/ControllerTests.kt` and `REPORT.md`.

- **Validation steps:** I checked that every setup and verification is one of the blocks of the guide, unmodified, and that each one matches the "Initial state / Runtime state" comment of its test. I ran `./gradlew test` (5 tests passed) and `./gradlew check` (`BUILD SUCCESSFUL`, Ktlint included). I ran the `bootRun` and `curl` requests listed above, and reviewed the results, including the `500` on `PUT` for a missing id.

- **Citations:** The MockK blocks come from `docs/GUIDE.md`. The definitions of safe and idempotent methods come from RFC 9110: https://www.rfc-editor.org/rfc/rfc9110 (§9.2.1, §9.2.2, §9.3.5, §15.3.5).

- **Human-reviewed:** I reviewed the tests and the explanatory comments. I chose to use only the blocks from the guide and asked to confirm that no block was modified. I reviewed and edited this report before submitting it.
