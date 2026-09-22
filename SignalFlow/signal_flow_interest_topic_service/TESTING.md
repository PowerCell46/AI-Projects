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
- Name over 100 characters returns 400 (`should_return_400_for_a_name_over_100_characters`)
- Body over the size cap returns 413 (`should_return_413_for_a_body_over_the_size_cap`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)

## `GET /api/v1/categories`

`CategoryControllerIntegrationTest.ListCategories`

- Returns all categories sorted by name, unpaged (`should_return_all_categories_sorted_by_name`)
- No categories returns an empty list, not 404 (`should_return_an_empty_list_when_no_categories_exist`)

## `PATCH /api/v1/categories/{id}`

`CategoryControllerIntegrationTest.RenameCategory`

- Renames the category and returns it (`should_rename_the_category_and_return_it`)
- The renamed name is stored lowercased (`should_store_the_renamed_name_lowercased`)
- Unknown id returns 404 (`should_return_404_when_the_category_does_not_exist`)
- Renaming to a name already in use returns 409 (`should_return_409_when_renaming_to_a_name_already_in_use`)
- Blank name returns 400 (`should_return_400_for_a_blank_name`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `DELETE /api/v1/categories/{id}`

`CategoryControllerIntegrationTest.DeleteCategory`

- Returns 204 and removes the category (`should_return_204_and_remove_the_category`)
- Unknown id returns 404 (`should_return_404_when_the_category_does_not_exist`)
- A category referenced by an interest topic returns 409 and is not deleted (`should_return_409_when_referenced_by_an_interest_topic`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `POST /api/v1/interest-topics`

`InterestTopicControllerIntegrationTest.CreateInterestTopic`

- Creates an interest topic and returns it with `categoryId`/`categoryName` embedded (`should_create_an_interest_topic_and_return_it`)
- Name is stored lowercased (`should_store_the_name_lowercased`)
- Unknown `categoryId` returns 404 (`should_return_404_for_an_unknown_category_id`)
- Duplicate name returns 409 (`should_return_409_for_a_duplicate_name`)
- Duplicate name differing only in case returns 409 (`should_return_409_for_a_duplicate_name_differing_only_in_case`)
- Blank name returns 400 (`should_return_400_for_a_blank_name`)
- Blank prompt returns 400 (`should_return_400_for_a_blank_prompt`)
- Missing `categoryId` returns 400 (`should_return_400_for_a_missing_category_id`)
- Name over 100 characters returns 400 (`should_return_400_for_a_name_over_100_characters`)
- Description over 1000 characters returns 400 (`should_return_400_for_a_description_over_1000_characters`)
- Prompt over 4000 characters returns 400 (`should_return_400_for_a_prompt_over_4000_characters`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)

## `GET /api/v1/interest-topics`

`InterestTopicControllerIntegrationTest.ListInterestTopics`

- Returns the first page sorted by name, default size 20 (`should_return_the_first_page_sorted_by_name_by_default`) —
  asserts on the Spring Data `Page` JSON envelope (`content`, `page.size`/`page.number`/`page.totalElements`)
- `?page=`/`?size=` are respected (`should_respect_page_and_size_params`)
- Size is capped at 100 even when a larger value is requested (`should_cap_the_page_size_at_100`)
- `?categoryId=` filters to that category's topics (`should_filter_by_category_id_when_provided`)
- No topics returns an empty page, not 404 (`should_return_an_empty_page_when_no_topics_exist`)

## `PATCH /api/v1/interest-topics/{id}`

`InterestTopicControllerIntegrationTest.UpdateInterestTopic`

- Updates only the fields provided; omitted (null) fields are unchanged (`should_update_only_the_provided_fields`)
- Unknown topic id returns 404 (`should_return_404_when_the_topic_does_not_exist`)
- Unknown `categoryId` returns 404 (`should_return_404_for_an_unknown_category_id`)
- Renaming to a name already in use returns 409 (`should_return_409_when_renaming_to_a_name_already_in_use`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## `DELETE /api/v1/interest-topics/{id}`

`InterestTopicControllerIntegrationTest.DeleteInterestTopic`

- Returns 204 and cascades to the topic's `TopicNews` (`should_return_204_and_cascade_delete_its_news`)
- Unknown id returns 404 (`should_return_404_when_the_topic_does_not_exist`)
- Malformed id returns 400 (`should_return_400_for_a_malformed_id`)

## Known gaps

See `PLAN.md`'s "Known gaps" section — accepted risks for the whole phase, not specific to these scenarios.
