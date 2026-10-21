# Heavymatica

## Commits

- After implementing an OpenSpec change (`/opsx:apply`), commit the work once all its tasks are done.
- Everything else is committed only when the user asks: fixes, proposals (`/opsx:propose`), archives
  (`/opsx:archive`) and any other edits.
- Use Conventional Commits: `type(scope): subject`, for example `feat(placement): add remote overlay` or
  `fix(library): keep selection after refresh`. Types: feat, fix, refactor, test, docs, build, chore.

## Code comments

- Comment only when the code can't say it itself: a non-obvious constraint, a workaround, or an API quirk.
- Never reference OpenSpec in code: no change names, spec or capability paths, requirement names or decision
  IDs like `(D5)`.
- No reasoning or history in comments ("we chose X because...", "previously..."). That belongs in the design
  docs, not the code.
