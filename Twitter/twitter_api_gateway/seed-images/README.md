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

Still to add or fix (as of 2026-10-08):

- Missing: `hristo`, `bobkata`, `atanas`, `stanimir`, `bogdan`, `kaloyan`.
- Over the 5 MB limit, shrink and replace: `stiliyan.jpg` (7.4 MB), `gosho.jpg` (5.5 MB).
