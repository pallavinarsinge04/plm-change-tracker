# PLM Change Tracker: Requirements

## 1. Problem
In product engineering, a released part must not be changed casually. Engineers raise a **change request** that explains what must change and why. Teams track that work in Jira, while the part data lives in a PLM system. Today the two are not connected, so people copy information by hand and the status in one system goes out of date.

## 2. Goal
A small service that stores change requests against part numbers, creates the matching Jira issue automatically, and keeps both statuses in sync.

## 3. Users
| User | Need |
|---|---|
| Design engineer | Raise a change request for a part and see its status |
| Reviewer / manager | Work the request in Jira as usual |
| Admin | See all requests, filter by part or status |

## 4. Scope (version 1)
In scope:
- Create, view, list (with filters) and edit change requests
- Create a Jira issue for every new change request
- Receive Jira webhooks and update the request status
- Check that the part number exists in Mini PLM
- Authentication on the API

Out of scope for v1: attachments, email notifications, several Jira projects, a full UI.

## 5. Functional requirements
| ID | Requirement |
|---|---|
| FR-1 | A user can create a change request with part number, title, reason, priority and requester |
| FR-2 | Part numbers are stored in upper case |
| FR-3 | New requests start as `OPEN` with Jira sync state `PENDING` |
| FR-4 | Requests can be listed and filtered by status and part number |
| FR-5 | Open and in-review requests can be edited. Closed requests cannot |
| FR-6 | Creating a request creates a Jira issue and stores its key and URL |
| FR-7 | If Jira is unavailable, the request is still saved and retried later |
| FR-8 | A Jira status change updates the request: To Do = OPEN, In Progress = IN_REVIEW, Done = CLOSED |
| FR-9 | An unknown part number is rejected with a clear error |

## 6. Non-functional requirements
| ID | Requirement |
|---|---|
| NFR-1 | No secret (Jira token, passwords) is stored in code or Git. Environment variables only |
| NFR-2 | Every error returns the same JSON shape with a useful message |
| NFR-3 | Business rules are covered by automated unit tests |
| NFR-4 | Runs with one `docker compose up` command |
| NFR-5 | The webhook endpoint verifies a shared secret |
| NFR-6 | Calls to Jira have timeouts and are logged without the token |

## 7. Status mapping
| Jira column | Request status |
|---|---|
| To Do | OPEN |
| In Progress | IN_REVIEW |
| Done | CLOSED |
