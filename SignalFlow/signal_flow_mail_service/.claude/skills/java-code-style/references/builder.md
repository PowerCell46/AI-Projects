# Builder over setter chains

When constructing an object with `new X()` followed by two or more chained setter calls, add
`@Builder` to the class and construct it with the builder instead. A single setter call doesn't
warrant a builder — leave `new X(); x.setY(...)` as-is.

On a JPA entity, `@Builder` only covers the class's own declared fields, not inherited ones (e.g.
`CommonEntity`'s `id`/`createdAt`/`updatedAt`), so it's safe to combine with `@NoArgsConstructor` —
Hibernate still gets its no-arg constructor for hydration. `@Builder` needs an all-args constructor
to build from, so pair it with `@AllArgsConstructor` whenever `@NoArgsConstructor` is already present
(without it, Lombok won't generate one for you).

Any field with an inline initializer (`private NewsStatus status = NewsStatus.PENDING;`) needs
`@Builder.Default` too — otherwise the builder silently drops the initializer and leaves the field
null/zero when that field isn't set explicitly.

```java
// flag
InterestTopic interestTopic = new InterestTopic();
interestTopic.setName(name);
interestTopic.setDescription(description);
interestTopic.setPrompt(prompt);
interestTopic.setCategory(category);

// prefer
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interest_topics")
public class InterestTopic extends CommonEntity { ... }

InterestTopic interestTopic = InterestTopic.builder()
        .name(name)
        .description(description)
        .prompt(prompt)
        .category(category)
        .build();
```
