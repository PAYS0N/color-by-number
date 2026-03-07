Compose a prompt for a new session task to [work item]. 
Include all the context someone would need. 
The prompt should include an instruction to read project_context/instructions.md and project_context/manifest.md. 
The prompt should indicate the following two workflow items in addition to the task definition: 

- Start by moving it to Active.

- Run this checklist after the user has declared the task done:

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
    4. **Response to user** - remind the user to make a git commit and update the github project.

Indicate the Claude model best suited for the task, not as part of the prompt. 
The created prompt should be output to the user, not a markdown doc.