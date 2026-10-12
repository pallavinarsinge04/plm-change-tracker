# Mini PLM part validation (story 6)

A change request must point at a part that really exists in Mini PLM
(project 1). On `POST /api/change-requests` the tracker:

1. Normalises the part number (trim, upper case).
2. Asks Mini PLM: `GET /api/parts?q=<part number>` (logged in with a service account).
3. Keeps only the **exact** match, because Mini PLM search is "contains".
4. Saves the request and creates the Jira ticket only if the part exists.

| Situation                         | Result                                   |
|-----------------------------------|------------------------------------------|
| Part exists                       | 201 Created, Jira ticket created         |
| Part does not exist               | 422 Unprocessable Entity, nothing saved  |
| Mini PLM down / slow / wrong login| 503 Service Unavailable, nothing saved   |
| `MINIPLM_BASE_URL` empty          | validation skipped (local development)   |

Design choice: when Mini PLM cannot be reached the request is refused (503),
not silently accepted, because a change request against an unverified part
number would break the traceability this tool exists for.

## 1. Create a service account in Mini PLM
Log in to Mini PLM as admin and create a user for the tracker. Role DESIGNER is
enough, it only reads parts. Via the API:

    POST <miniplm>/api/users          (Authorization: Bearer <admin token>)
    {"name":"Change Tracker","email":"tracker@plm.com","password":"<strong password>","role":"DESIGNER"}

## 2. Environment variables (IntelliJ run configuration, or .env on the server)

    MINIPLM_BASE_URL=http://13.211.190.13
    MINIPLM_EMAIL=tracker@plm.com
    MINIPLM_PASSWORD=<the password you chose>

Never commit the password.
For the deployed version (story 9) both services run on the same server, so use
Mini PLM's internal address instead and avoid sending the login over the internet.

## 3. Try it
- `POST /api/change-requests` with a part number that exists in Mini PLM: 201.
- Same request with `P-DOES-NOT-EXIST`: 422 with a clear message.
- Wrong `MINIPLM_PASSWORD`: 503 and a log line "Mini PLM login failed".
