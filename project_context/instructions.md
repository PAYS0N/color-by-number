## Project Rules

- Every context compaction, re-read this file.

- All non-code files should be markdown unless specifically mentioned otherwise.

- The source files in this project are the single source of truth. Always read them directly rather than relying on conversation memory.

- When asked to create a context document, read cdoc.md.

- When asked to create project management files, create them in the project_context directory in root.

- When asked to create a prompt, read prompting.md.

- If beginning work on an open issue, start by moving it to Active.

## After Completing Any Task

Run this checklist after the user has declared a task done:

1. **status.md** — move the item from Open/Active to Closed; clear Active Work; add any newly discovered open items.
2. **manifest.md** — add a row for every new file created; remove rows for deleted files.
3. **context.md** — Read cdoc.md. update only the sections affected by the change, using the table below. Do not rewrite unaffected sections.

| Change type | Sections to update in context.md |
|---|---|
| New or changed UI feature / screen | UI Screens, Open Work |
| New or changed data field or entity | Data Model |
| New or changed pipeline step | Puzzle generation pipeline |
| New or changed setting | Settings |
| New or changed architecture / data flow | Architecture |
| New file only (no behavior change) | manifest.md only |
| Open item closed | Open Work |
4. **Response to user** - remind the user to make a git commit and update the github project.