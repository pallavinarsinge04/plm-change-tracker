# Jira backlog (paste into your KAN board)

Create one **Epic** and the **Stories** below. Put the acceptance criteria in each story's description.

## Epic: Change Tracker v1
Connect change requests to Jira and keep status in sync.

---

### Story 1: Project skeleton and health endpoint
**Acceptance criteria**
- Spring Boot app starts on port 8081 with MySQL
- `GET /actuator/health` returns `UP`
- No secret is committed; `.env.example` exists
- README explains how to run it

### Story 2: ChangeRequest entity and CRUD API
**Acceptance criteria**
- Given valid input, `POST /api/change-requests` returns 201, a Location header, status `OPEN`, sync state `PENDING`
- Part numbers are stored in upper case
- Missing title, reason, partNumber or createdBy returns 400 with field errors
- `GET` supports the filters `status` and `partNumber`
- Unknown id returns 404
- Editing a CLOSED request returns 409
- Unit tests cover create, get, list, update

### Story 3: JiraClient creates an issue
**Acceptance criteria**
- `JiraClient.createIssue(...)` posts to Jira REST API v3 using Basic Auth (email + API token from environment)
- The description is sent in Atlassian Document Format
- Returns the issue key and browse URL
- Timeouts are configured; the token is never logged
- Tests use a fake Jira server (WireMock / MockWebServer): success, 401, 400, 500, timeout

### Story 4: New change request creates a Jira issue
**Acceptance criteria**
- Creating a request creates a Jira issue and stores `jiraKey`, `jiraUrl`, sync state `SYNCED`
- If Jira fails, the request is still saved with sync state `FAILED` and a clear log message
- A scheduled job retries `PENDING` / `FAILED` requests
- Tests cover success and failure paths

### Story 5: Webhook updates status from Jira
**Acceptance criteria**
- `POST /api/jira/webhook` rejects calls without the correct shared secret (401)
- A status change in Jira updates the request: To Do = OPEN, In Progress = IN_REVIEW, Done = CLOSED
- Unknown issue keys are ignored safely (200, logged)
- Each change is stored in a status history table
- Tests cover each mapping

### Story 6: Validate the part number against Mini PLM
**Acceptance criteria**
- Creating a request for a part that does not exist in Mini PLM returns 400 with a clear message
- If Mini PLM is unreachable, behaviour is defined and documented (reject or allow with warning)
- Mini PLM URL comes from configuration

### Story 7: Secure the API
**Acceptance criteria**
- All `/api/change-requests` endpoints require authentication
- `/actuator/health` stays public
- Passwords or keys are not stored in plain text

### Story 8: Tests, Docker and CI
**Acceptance criteria**
- `./mvnw test` passes locally
- Dockerfile and docker-compose start the app and database
- GitHub Actions builds and tests every pull request

### Story 9: Deploy to AWS
**Acceptance criteria**
- Runs on the existing EC2 server next to Mini PLM
- Reachable over HTTP(S), with the Jira webhook delivering to it
- Deploy steps are documented

### Story 10: README, screenshots and demo
**Acceptance criteria**
- README has overview, architecture diagram, setup, API table, screenshots
- A 2-minute demo shows: create request -> Jira issue appears -> move card to Done -> status updates

---

## Definition of Done (every story)
- Work done on a branch named `feature/KAN-<n>-short-name`
- Pull request opened and CI green
- Tests added or updated
- Docs updated
- No secrets in the diff
