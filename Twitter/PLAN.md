# Twitter — plans

All phases across every service are built and merged to `main`. See
`DEPLOYMENT-READINESS.md` for what is still left to do before and after the
first deployment.

## What was built

| Service | Phases |
|---|---|
| `twitter_api_gateway` | 1–5: auth + email confirmation → profile (MinIO) → follows → tweet routing → people to follow |
| `twitter_tweet_service` | 1–3: tweets (create/read/edit/delete + images) → replies API → replies UI; outbox for `tweet.created` / `tweet.deleted` |
| `twitter_timeline_service` | 1–5: fan-out feed (7-day retention) → saved tweets → views → likes endpoints + counter → likes UI + `/liked` |
| `twitter_mail_service` | 1–2: confirmation email (`user.confirmation-requested`) → follow email (`user.followed`) |
| `frontend` | 1–5: auth flow → feed + compose → tab row + People → profile view |
| e2e | Playwright suite (79 specs) covering auth, feed, follows, profile and follow email |

README for e2e (how to start the tests fast and slow - look at SignalFlow)

ports:
    - "127.0.0.1:5432:5432"
is this ok for deploy?

in readme "Compose refuses to start while a required value is empty. " -> just refer to .env.example no need to repeat the keys

README 'Put the certificate in certs/fullchain.pem and certs/privkey.pem' -> ask it about that

api gateway
    PLAN.md -> move to a root level file (about the accepted gaps)
    same for SECURITY AUDITS
    run an agent on the folder structure, but some dirs like config don't seem like they follow the +5 files rule
    no .env.example
    seed images

tweet service
    PLAN.md -> move to a root level file (about the accepted gaps)
    same for SECURITY AUDITS

mail service
    PLAN.md -> move to a root level file (about the accepted gaps)
    same for SECURITY AUDITS
    run an agent on the folder structure, but some dirs like config don't seem like they follow the +5 files rule
    
timeline service
    PLAN.md -> move to a root level file (about the accepted gaps)
    same for SECURITY AUDITS
    run an agent on the folder structure, but some dirs like config don't seem like they follow the +5 files rule
