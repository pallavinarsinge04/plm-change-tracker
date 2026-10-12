# Jira webhook setup (story 5)

When a ticket moves on the Jira board, Jira calls this service and the change
request status follows:

| Jira status category | Typical board column | Change request status |
|----------------------|----------------------|-----------------------|
| new                  | To Do                | OPEN                  |
| indeterminate        | In Progress          | IN_REVIEW             |
| done                 | Done                 | CLOSED                |

The mapping uses the status *category*, so renaming a column does not break it.

## 1. Choose a secret
Make a long random string (30+ characters). Put it in `JIRA_WEBHOOK_SECRET`
(IntelliJ environment variables, or `.env` on the server). Never commit it.

## 2. Test without Jira first
Create a change request (`POST /api/change-requests`) and note its `jiraKey`
(for example KAN-12). Then send this to simulate Jira:

    POST http://localhost:8081/api/webhooks/jira?token=<your secret>
    Content-Type: application/json

    {"webhookEvent":"jira:issue_updated",
     "issue":{"key":"KAN-12","fields":{"status":{"name":"In Progress",
       "statusCategory":{"key":"indeterminate"}}}}}

Expected: `{"result":"UPDATED"}`, and `GET /api/change-requests/{id}` shows
`IN_REVIEW`. A wrong or missing token returns 401.

## 3. Let Jira reach your app
Jira Cloud cannot call `localhost`. For local testing use a tunnel, for example:

    cloudflared tunnel --url http://localhost:8081

(install with `winget install Cloudflare.cloudflared`). It prints an
`https://....trycloudflare.com` address. On the deployed server (story 9)
you use the server address instead.

## 4. Create the webhook in Jira
Jira (gear icon) > System > WebHooks > Create a WebHook (needs Jira admin):

- Name: PLM Change Tracker
- Status: Enabled
- URL: `https://<tunnel-or-server>/api/webhooks/jira?token=<your secret>`
- Issue related events: tick **Issue: updated**
- JQL filter (optional): `project = KAN`

Move a ticket on the board and watch the status change.

## Notes
- The endpoint always answers 200 for a valid token, even for tickets it
  does not know, so Jira does not keep retrying.
- Receiving the same event twice is harmless.
- Planned hardening in story 7: HMAC signature check and rate limiting.
