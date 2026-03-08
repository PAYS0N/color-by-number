Compose a prompt for a new session task to do the indicated work item. 
Include all the context someone would need, both practically . 
The prompt should likely include an instruction to read project_management/manifest.md. 
The prompt should indicate the following workflow item in addition to the task definition: 

- Run this checklist after the user has declared the task done (make it clear to run this after completion is externally confirmed, not when it thinks it's done.):

    1. **status.md** — remove the item from Open; add any newly discovered open items.
    2. **manifest.md** — add a row for every new file created; remove rows for deleted files.
    3. **context docs** — Read cdoc.md. Update only the context file affected by the change, using the table below. Do not rewrite unaffected files.

    | Change type | File to update |
    |---|---|
    | New or changed UI feature / screen | ui.md |
    | New or changed data field or entity | data.md |
    | New or changed pipeline step | pipeline.md |
    | New or changed setting | settings.md |
    | New or changed architecture / data flow | architecture.md |
    | App purpose or tech stack change | app.md |
    | New file only (no behavior change) | manifest.md only |
    4. **Response to user** - remind the user to make a git commit and update the github project.

Indicate the Claude model best suited for the task, not as part of the prompt. 
The created prompt should be output to the user, not a markdown doc.