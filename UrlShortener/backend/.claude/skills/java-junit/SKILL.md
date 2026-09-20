---
name: java-junit
description: JUnit 5 + Mockito conventions for this codebase — test structure, snake_case naming styles, @Nested grouping, mocking, assertions and data-driven (parameterized) tests. Use before writing, editing or reviewing any test class.
---

# JUnit 5+ Best Practices

Your goal is to help me write effective unit tests with JUnit 5, covering both standard and data-driven testing approaches.

## What to aim for

- **Meaningful coverage of real behaviour** — not a line-coverage percentage target. Prefer tests that would catch regressions in business logic, error handling, and cross-class contracts.
- **Unit tests are the default.** Plain JUnit 5 + Mockito, no Spring context unless you genuinely need it (e.g. a Spring Boot integration point like a filter or listener). `@SpringBootTest` is reserved for smoke tests only.
- Test sources live in `src/test/java`; run them with `mvn test`.

## Test Structure

- Test classes should have a `Test` suffix, e.g., `CalculatorTest` for a `Calculator` class.
- Use `@Test` for test methods.
- Follow the Arrange-Act-Assert (AAA) pattern.
- Name tests in snake_case — pick the style that matches the class under test:
  - `should_<behaviour>_when_<condition>` — **the default**; services, controllers, filters, orchestration
  - `<methodName>_<scenario>` — mappers, fixture-driven service tests
  - `<input>_<expectedOutcome>` — pure utilities with many input permutations
  - `<feature>_<behaviour>` — round-trip / serializer tests
- New tests in an existing class follow that class's established style.
- Use `@BeforeEach` and `@AfterEach` for per-test setup and teardown.
- Use `@BeforeAll` and `@AfterAll` for per-class setup and teardown (must be static methods).
- Use `@DisplayName` to provide a human-readable name for test classes and methods.

## Standard Tests

- Keep tests focused on a single behavior.
- Avoid testing multiple conditions in one test method.
- Make tests independent and idempotent (can run in any order).
- Avoid test interdependencies.

## Data-Driven (Parameterized) Tests

- Use `@ParameterizedTest` to mark a method as a parameterized test.
- Use `@ValueSource` for simple literal values (strings, ints, etc.).
- Use `@MethodSource` to refer to a factory method that provides test arguments as a `Stream`, `Collection`, etc.
- Use `@CsvSource` for inline comma-separated values.
- Use `@CsvFileSource` to use a CSV file from the classpath.
- Use `@EnumSource` to use enum constants.

## Assertions

- Use the static methods from `org.junit.jupiter.api.Assertions` (e.g., `assertEquals`, `assertTrue`, `assertNotNull`).
- For more fluent and readable assertions, consider using a library like AssertJ (`assertThat(...).is...`).
- Use `assertThrows` or `assertDoesNotThrow` to test for exceptions.
- Group related assertions with `assertAll` to ensure all assertions are checked before the test fails.
- Use descriptive messages in assertions to provide clarity on failure.

## Mocking and Isolation

- Use a mocking framework like Mockito to create mock objects for dependencies.
- Use `@Mock` and `@InjectMocks` annotations from Mockito to simplify mock creation and injection.
- Use interfaces to facilitate mocking.

## Test Organization

- Group tests by feature or component using packages.
- Use `@Tag` to categorize tests (e.g., `@Tag("fast")`, `@Tag("integration")`).
- Use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` and `@Order` to control test execution order when strictly necessary.
- Use `@Disabled` to temporarily skip a test method or class, providing a reason.
- Use `@Nested` to group related cases in an inner class **named after the method under test**.
- One `@BeforeEach` per nested group — not one global setup that does too much.
