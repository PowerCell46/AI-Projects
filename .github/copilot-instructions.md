---
name: java-spacing-rule
applyTo:
  - "**/*.java"
description: |
  Enforce a formatting rule for Java source files generated or edited by AI agents: there MUST be exactly two blank lines
  between the last `import` declaration and the `public` type declaration (`class`, `interface`, `enum`, or `record`).
  This instruction applies to all Java files in the workspace.
---

When writing or updating Java source files, follow this hard formatting rule:

- After the final `import ...;` line, insert two blank lines, then the `public` type declaration. Do not insert more or fewer blank lines in this gap.

Examples

Before (incorrect):

import java.util.List;
import java.util.UUID;
public class Task {
    // ...
}

After (correct):

import java.util.List;
import java.util.UUID;


public class Task {
    // ...
}

Enforcement notes for agents and CI:

- Treat this as a deterministic formatting rule for generated Java files. If you modify or generate Java code, apply the spacing automatically before saving.
- Optional check (example regex): use a CI lint step that requires the following pattern to match somewhere in the file:

  (?ms)^(?:import\s.+?;\r?\n)+\r?\n{2}public\s+(class|interface|enum|record)\b

- If you'd like, I can add a small check script or a pre-commit hook that enforces this rule across the repository.

If this should be a soft preference (not enforced everywhere) or should only apply to generated files, tell me which files or directories to target and I will update `applyTo` accordingly.
