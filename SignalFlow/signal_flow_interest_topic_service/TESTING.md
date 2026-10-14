# E2E test catalog

Scope: HTTP-layer tests only — full stack through `DispatcherServlet` via `RestTestClient` against
Testcontainers Postgres. `CategoryRepositoryIntegrationTest`, `InterestTopicRepositoryIntegrationTest` and
`TopicNewsRepositoryIntegrationTest` exercise real Postgres too but skip the HTTP layer, so they're not
listed here.

Hand-maintained — see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

Every scenario below is written and enabled — category scenarios since `CategoryController` landed in
step 4, interest topic scenarios since `InterestTopicController` landed in step 5.

## `POST /api/v1/categories`

`CategoryControllerIntegrationTest.CreateCategory`

- Creates a category and returns it (`should_create_a_category_and_return_it`)
- Name is stored lowercased (`should_store_the_name_lowercased`)
- Duplicate name returns 409 (`should_return_409_for_a_duplicate_name`)
- Duplicate name differing only in case returns 409 (`should_return_409_for_a_duplicate_name_differing_only_in_case`)
- Blank name returns 400 (`should_return_400_for_a_blank_name`)
- Name over 40 characters returns 400 (`should_return_400_for_a_name_over_40_characters`)
- A 40-character name is accepted (`should_create_a_category_with_a_40_character_name`)
- Body over the topic-route size cap (32 KB) returns 413 (`should_return_413_for_a_body_over_the_size_cap`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)
- Category count at the limit (`app.category.max-count`) returns 409 (`should_return_409_when_the_category_count_is_at_the_limit`)

## `GET /api/v1/categories`

`CategoryControllerIntegrationTest.ListCategories`

- Returns the first page sorted by name, default size 20 (`should_return_the_first_page_sorted_by_name_by_default`) —
  asserts on the Spring Data `Page` JSON envelope (`content`, `page.size`)
- No categories returns an empty page, not 404 (`should_return_an_empty_page_when_no_categories_exist`)

## `PATCH /api/v1/categories/{id}`

`CategoryControllerIntegrationTest.RenameCategory`

- Renames the category and returns it (`should_rename_the_category_and_return_it`)
- The renamed name is stored lowercased (`should_store_the_renamed_name_lowercased`)
- Unknown id returns 404 (`should_return_404_when_the_category_does_not_exist`)
- Renaming to a name already in use returns 409 (`should_return_409_when_renaming_to_a_name_already_in_use`)
- Blank name returns 400 (`should_return_400_for_a_blank_name`)
- Name over 40 characters returns 400 and the name is unchanged (`should_return_400_for_a_name_over_40_characters`)
- A 40-character name is accepted (`should_rename_to_a_40_character_name`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `DELETE /api/v1/categories/{id}`

`CategoryControllerIntegrationTest.DeleteCategory`

- Returns 204 and removes the category (`should_return_204_and_remove_the_category`)
- Unknown id returns 404 (`should_return_404_when_the_category_does_not_exist`)
- A category referenced by an interest topic returns 409 and is not deleted (`should_return_409_when_referenced_by_an_interest_topic`)
- A category that is one of a topic's three categories returns 409; after the topic is deleted it returns 204, proving the join rows go with the topic (`should_return_409_when_one_of_several_categories_of_a_topic_and_204_after_the_topic_is_deleted`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `POST /api/v1/interest-topics`

`InterestTopicControllerIntegrationTest.CreateInterestTopic`

- Creates an interest topic and returns it with `categories` (`id`, `name`) embedded (`should_create_an_interest_topic_and_return_it`)
- Three categories → 201, `categories` sorted by name (`should_create_with_three_categories_sorted_by_name`)
- The response never carries `prompt` (`should_not_expose_the_prompt_in_the_response`)
- Name is stored lowercased (`should_store_the_name_lowercased`)
- Unknown category id returns 404 (`should_return_404_for_an_unknown_category_id`)
- One unknown id among known ones returns 404 and creates no topic (`should_return_404_and_create_no_topic_when_one_category_id_is_unknown`)
- Duplicate category ids return 400 and create no topic (`should_return_400_for_duplicate_category_ids`)
- Empty `categoryIds` returns 400 (`should_return_400_for_an_empty_category_id_list`)
- More than 3 `categoryIds` returns 400 and creates no topic (`should_return_400_for_more_than_three_category_ids`)
- A null element in `categoryIds` returns 400 (`should_return_400_for_a_null_category_id_element`)
- Duplicate name returns 409 (`should_return_409_for_a_duplicate_name`)
- Duplicate name differing only in case returns 409 (`should_return_409_for_a_duplicate_name_differing_only_in_case`)
- Blank name returns 400 (`should_return_400_for_a_blank_name`)
- Blank prompt returns 400 (`should_return_400_for_a_blank_prompt`)
- Missing `categoryIds` returns 400 (`should_return_400_for_missing_category_ids`)
- Name over 100 characters returns 400 (`should_return_400_for_a_name_over_100_characters`)
- Description over 1000 characters returns 400 (`should_return_400_for_a_description_over_1000_characters`)
- Prompt over 4000 characters returns 400 (`should_return_400_for_a_prompt_over_4000_characters`)
- A 4000-character prompt and 1000-character description in a 3-byte UTF-8 script fit the 32 KB topic-route body cap (`should_accept_a_4000_character_prompt_in_a_multi_byte_script`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)
- Topic count at the limit (`app.interest-topic.max-count`) returns 409 (`should_return_409_when_the_topic_count_is_at_the_limit`)

## `GET /api/v1/interest-topics`

`InterestTopicControllerIntegrationTest.ListInterestTopics`

- Returns the first page sorted by name, default size 20 (`should_return_the_first_page_sorted_by_name_by_default`) —
  asserts on the Spring Data `Page` JSON envelope (`content`, `page.size`/`page.number`/`page.totalElements`)
- `?page=`/`?size=` are respected (`should_respect_page_and_size_params`)
- Size is capped at 100 even when a larger value is requested (`should_cap_the_page_size_at_100`)
- `?categoryId=` filters to that category's topics (`should_filter_by_category_id_when_provided`)
- `?categoryId=` matches a topic whose second category it is, and `totalElements` counts each topic once (`should_return_a_topic_whose_second_category_is_the_filter_and_count_it_once`)
- No listed topic carries `prompt` (`should_not_expose_the_prompt_of_any_listed_topic`)
- No topics returns an empty page, not 404 (`should_return_an_empty_page_when_no_topics_exist`)

## `PATCH /api/v1/interest-topics/{id}`

`InterestTopicControllerIntegrationTest.UpdateInterestTopic`

- Updates only the fields provided; omitted (null) fields are unchanged, checked for `prompt` in the database since responses no longer carry it (`should_update_only_the_provided_fields`)
- `categoryIds` replaces the whole category set (`should_replace_the_whole_category_set_when_category_ids_are_provided`)
- Omitting `categoryIds` leaves the categories unchanged (`should_leave_the_categories_unchanged_when_category_ids_are_absent`)
- Empty `categoryIds` returns 400 and keeps the categories (`should_return_400_for_an_empty_category_id_list_and_keep_the_categories`)
- Unknown topic id returns 404 (`should_return_404_when_the_topic_does_not_exist`)
- Unknown category id returns 404 (`should_return_404_for_an_unknown_category_id`)
- Renaming to a name already in use returns 409 (`should_return_409_when_renaming_to_a_name_already_in_use`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `DELETE /api/v1/interest-topics/{id}`

`InterestTopicControllerIntegrationTest.DeleteInterestTopic`

- Returns 204 and cascades to the topic's `TopicNews` (`should_return_204_and_cascade_delete_its_news`)
- Unknown id returns 404 (`should_return_404_when_the_topic_does_not_exist`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `POST /internal/v1/interest-topics/existing`

`InternalInterestTopicControllerIntegrationTest.FindExistingInterestTopics` — the gateway's subscription
reconciliation lookup; outside `/api/v1`, so no gateway route forwards a client to it.

- Returns only the ids that exist (`should_return_only_the_ids_that_exist`)
- None existing returns an empty list, not 404 (`should_return_an_empty_list_when_none_exist`)
- Missing `ids` returns 400 (`should_return_400_when_ids_is_missing`)
- A null id returns 400 (`should_return_400_when_an_id_is_null`)
- A malformed id returns 400 (`should_return_400_when_an_id_is_malformed`)
- Body over the default size cap (8 KB) returns 413 (`should_return_413_for_a_body_over_the_size_cap`)

## `POST /internal/v1/interest-topics/feed`

`InternalInterestTopicControllerIntegrationTest.FindInterestTopicFeed`. Backs the gateway's `GET /api/v1/feed`.
It's outside `/api/v1`, so no gateway route forwards a client to it.

- First page comes in name order with `nextCursor` set to the last name (`should_return_the_first_page_in_name_order_with_a_cursor_to_the_next`)
- Continues after the cursor; the last page's `nextCursor` is null (`should_continue_after_the_cursor_and_return_no_cursor_on_the_last_page`)
- INCLUDE returns only the given ids (`should_return_only_the_given_ids_when_including`)
- EXCLUDE leaves the given ids out (`should_leave_out_the_given_ids_when_excluding`)
- `total` counts every topic; `matching` counts only the given ids that still exist (`should_count_every_topic_and_only_the_given_ids_that_still_exist`)
- Items embed the categories and carry no `prompt` (`should_embed_the_categories_and_not_expose_the_prompt`)
- 3 topics × 3 categories with size 2 return exactly 2 distinct topics, each with all 3 categories sorted, in ALL, INCLUDE and EXCLUDE modes (`should_return_each_topic_once_with_all_three_categories_sorted_when_paging_all`, `…_when_including`, `…_when_excluding`)
- Missing `mode` returns 400 (`should_return_400_when_the_mode_is_missing`)
- Missing `ids` returns 400 (`should_return_400_when_ids_is_missing`)
- `size` outside 1–100 returns 400 (`should_return_400_when_the_size_is_out_of_range`)
- Unknown `mode` returns 400 (`should_return_400_for_an_unknown_mode`)

## Known gaps

See `PLAN.md`'s "Known gaps" section — accepted risks for the whole phase, not specific to these scenarios.
