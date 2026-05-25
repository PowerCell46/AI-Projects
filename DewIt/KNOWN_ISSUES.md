# Known Issues & Deferred Work

This file documents design limitations and pending improvements that require
a deliberate architectural decision before implementation. They are tracked here
rather than in the codebase to avoid clutter and to keep the context visible.

---

## 1. PATCH semantics — clearing nullable fields

**Affected files:** `TaskService.java:applyPartialUpdate`, `TaskUpdateRequest.java`, `EditTaskModal.tsx`

**Problem:**  
The PATCH endpoint treats JSON `null` as "field not provided" (unchanged) rather than
"explicitly set to null". This means `dueDate` and `description` can never be *cleared*
once set: the client sends `dueDate: null`, the backend sees null and skips the update.

**Options:**
- **Boolean flags** — add `clearDueDate: boolean` / `clearDescription: boolean` to
  `TaskUpdateRequest`. Simple but noisy on the wire.
- **Optional wrapper** — change the record fields to `Optional<LocalDateTime> dueDate`
  and `Optional<String> description`. `Optional.empty()` = not provided;
  `Optional.of(null)` is not representable, so this needs a custom Jackson deserializer
  (`OptionalDeserializer`) that maps the JSON key being *absent* to `empty()` and the
  key being present-with-null to `Optional.ofNullable(null)`.
- **JSON Merge Patch** (`application/merge-patch+json`, RFC 7396) — a present `null`
  means "delete the field"; an absent key means "leave unchanged". Spring has
  `@JsonMergePatch` support. The client switches to this media type.

**Recommended approach:** JSON Merge Patch is the most standards-aligned and requires
no special DTO changes, but it needs the client to send the correct `Content-Type`.
The boolean-flag approach is the least risky migration.

---

## 5. Completion-rate denominator mismatch

**Affected file:** `computeStats.ts:completionRate`

**Problem:**  
`completionRate` divides "tasks completed in the timeframe" by "tasks *created* in the
timeframe". A task created last year and completed today adds to the numerator but not
the denominator, so rates above 100% are possible and the number is misleading.

**Options:**
- **Snapshot approach** — for a given window, count how many tasks were *created* in
  that window and what fraction of those are now `COMPLETED`. This is semantically clean
  but the denominator shrinks for recent windows (most tasks created "last week" aren't
  done yet).
- **Relabel** — keep the current calculation but label it "Tasks completed / created
  in window" instead of "Completion rate", and accept that it measures throughput, not
  a true rate.
- **Cohort approach** — for each task completed in the window, compute the time from
  creation to completion and surface that as "avg. time to complete" instead.

**Recommended approach:** Relabelling is the lowest-risk, lowest-effort change and
avoids misleading users. A cohort/throughput view is the most informative.

---

## 6. Time-zone handling

**Affected files:** `NewTaskView.tsx`, `EditTaskModal.tsx`, `TaskService.java:count`

**Problem:**  
- The FE serialises dates as `"yyyy-MM-dd'T'HH:mm:ss"` (no timezone offset) using the
  browser's local time.
- The BE stores them as `LocalDateTime` (no timezone).
- `TaskService.count` computes `startOfToday` using `LocalDate.now()` in the JVM's
  default timezone (typically UTC in cloud deployments).

If the user is in GMT+2 and the server is in UTC, "today" boundaries diverge by 2 hours:
tasks the user sees as "today" appear as "upcoming" or "overdue" in the count endpoint.

**Recommended approach:**
1. Change `Task.dueDate` to `OffsetDateTime` (or `Instant`) and migrate the column.
2. Have the client send ISO-8601 timestamps with an offset (`"2025-05-25T09:00:00+02:00"`).
3. For the `/count` endpoint, accept an `X-Timezone` header (IANA zone name, e.g.
   `"Europe/Sofia"`) and compute bucket boundaries in that zone:
   ```java
   ZoneId zone = ZoneId.of(request.getHeader("X-Timezone"), ZoneId.SHORT_IDS);
   LocalDate today = LocalDate.now(zone);
   ```
4. Update FE `tasks.ts` to send the header from `Intl.DateTimeFormat().resolvedOptions().timeZone`.

---

## 11. Pagination — hardcoded caps

**Affected files:** `useTasksQuery.ts` (size=200), `useCategoriesQuery.ts` (size=100)

**Problem:**  
Both hooks fetch a single page of up to 200 / 100 items. There is no "load more" for
tasks on the Dashboard or Statistics pages. Users with more items than the cap will see
silently truncated data.

**Options:**
- **Raise the cap** — quick but doesn't scale; just pushes the problem further.
- **Infinite scroll** — use `useInfiniteQuery` with `queryKey: ['tasks', { filter }]`
  and concatenate pages. Requires refactoring all consumers of `useTasksQuery`.
- **Cursor / keyset pagination** — similar to above but cheaper on the DB.
- **Acknowledge the ceiling** — add a visible counter ("Showing 200 of 312 tasks") and
  a link to a full-list view. Lowest effort if total task counts stay modest.

**Note:** Fixing pagination properly also requires fixing the shared query key (see §32
in the codebase note in `useTasksQuery.ts`).

---

## 23. Sort-field whitelist on paginated endpoints

**Affected files:** `TaskController.java`, `CategoryController.java`

**Problem:**  
Both `@PageableDefault` endpoints accept arbitrary `sort=...` query parameters. A client
can sort by internal columns like `id` or `lastModifiedAt`, exposing more of the schema
than intended. This is a minor surface-area issue rather than a security vulnerability.

**Recommended approach:**
Add a `@SortDefault`-and-whitelist handler using Spring Data's `PageableHandlerMethodArgumentResolverCustomizer`, or validate the sort fields manually in the controllers:

```java
private static final Set<String> ALLOWED_SORT_FIELDS =
    Set.of("createdAt", "dueDate", "priority", "title");

// In the controller method:
for (Sort.Order order : pageable.getSort()) {
    if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Sort field not allowed: " + order.getProperty());
    }
}
```

Alternatively, wrap `Pageable` in a custom `ValidatedPageable` value object.
