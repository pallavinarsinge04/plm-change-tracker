# Security (story 7)

## Who can do what
| Action                                   | Not logged in | viewer | engineer |
|------------------------------------------|---------------|--------|----------|
| GET /api/change-requests, GET /{id}      | 401           | yes    | yes      |
| POST/PUT /api/change-requests, jira-sync | 401           | 403    | yes      |
| POST /api/webhooks/jira                  | needs `?token=` secret | n/a | n/a |
| GET /actuator/health                     | yes           | yes    | yes      |
| anything else                            | denied        | denied | denied   |

401 = we do not know who you are. 403 = we know you, but you may not do this.

## How it works
- HTTP Basic over HTTPS, stateless (no session, no cookies, so no CSRF risk).
- Two accounts, `engineer` and `viewer`. Passwords come from environment variables
  `TRACKER_ENGINEER_PASSWORD` and `TRACKER_VIEWER_PASSWORD`, minimum 12 characters,
  stored only as bcrypt hashes in memory. A blank password means that account is not created.
  A too-short password stops the app at startup.
- The author of a change request (`createdBy`) is the logged-in user. Whatever the
  request body says is ignored, so nobody can raise a request in another person's name.
- The Jira webhook cannot log in, so it is open at the HTTP layer and protected by the
  secret token in its URL (constant-time comparison, fails closed if no secret is set).

## Trying it in Postman
Authorization tab > Type: Basic Auth > username `engineer`, password = your
`TRACKER_ENGINEER_PASSWORD`. Without it you get 401.

## Known limits (honest list)
- Accounts are fixed in configuration, not a user database. Fine for a small tool;
  a real deployment would use a user table or the Mini PLM login (JWT).
- The webhook secret is in the URL, so it can appear in access logs. A signed header
  (HMAC) is the stronger option when Jira supports it for your setup.
- Basic auth must only be used over HTTPS outside local development (story 9).
