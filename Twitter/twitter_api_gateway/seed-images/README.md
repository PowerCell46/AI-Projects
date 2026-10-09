Profile pictures for the seeded demo users.

Drop an image here named after a username (png, jpg, jpeg or webp; the name is case-insensitive). On the first
start with an empty database the seeder uploads each one as that user's profile picture. A user without an image
keeps the default avatar. **Every image must be under 5 MB** (`MAX_UPLOAD_FILE_BYTES`); a larger one is skipped
with a warning in the gateway log.

All the names the seeder looks for:

| Username  | File name, any of                          |
| --------- | ------------------------------------------ |
| Stiliyan  | `stiliyan.jpg` (also .jpeg, .png, .webp)   |
| Kristian  | `kristian.jpg`                             |
| Kristyian | `kristyian.jpg`                            |
| Gosho     | `gosho.jpg`                                |
| Gabi      | `gabi.jpg`                                 |
| Gabi2     | `gabi2.jpg`                                |
| Hristo    | `hristo.jpg`                               |
| Bobkata   | `bobkata.jpg`                              |
| Atanas    | `atanas.jpg`                               |
| Stanimir  | `stanimir.jpg`                             |
| Bogdan    | `bogdan.jpg`                               |
| Kaloyan   | `kaloyan.jpg`                              |

## How the seeder works

`DataSeedRunner` runs after startup and, when the `users` table is empty, seeds:

- 12 confirmed users (`stiliyan@seed.local`, ...) with one shared password (`SEED_PASSWORD`, default `Password123`)
  and their profile pictures.
- About a third of all possible follows between them (fixed random seed, through `FollowService`, so counts, outbox
  events and timeline back-fill are real).
- 3 tweets each (POST to the tweet service with `X-User-Id`), 0 to 3 replies per tweet from other users, and likes
  (PUT to the timeline service; each user likes about 30% of the others' tweets).

It runs on a background thread after `MinioBucketInitializer`. The timeline service only starts once the gateway is
healthy, so the likes retry for about three minutes. `SEED_ENABLED=false` turns it off (tests and the e2e stack do).
The full compose stack mounts this folder read-only (`SEED_PICTURES_DIR`). Follow emails go to the `@seed.local`
addresses, so run the mail service against Mailpit locally.

Still to add or fix (as of 2026-10-08):

- Missing: `hristo`, `bobkata`, `atanas`, `stanimir`, `bogdan`, `kaloyan`.
- Over the 5 MB limit, shrink and replace: `stiliyan.jpg` (7.4 MB), `gosho.jpg` (5.5 MB).
