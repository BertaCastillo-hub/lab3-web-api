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

[Files and behaviour. Not a restatement of the starter README.]

## Technical decisions

[Choices you own: which MockK block you used for each method, what you rejected, and why a repeated call does or does not change state.]

## How I verified

[Commands (`./gradlew check`), what failed first, what you fixed. You remain accountable for correctness.]

## AI disclosure

Fill **either** the list **or** the no-AI line.

- **Tools / skills:** …
- **Purpose:** …
- **Representative prompts:** … (or appendix)
- **Affected files/sections:** …
- **Validation steps:** …
- **Citations:** … (external snippets you adapted)
- **Human-reviewed:** what you checked, changed, or rejected

Or: **No AI assistance** was used for this lab.
