# Tasks

## 1. Steps and config

- [x] 1.1 Add game-free `OpacitySteps` with `DEFAULT`, `sanitize`, `matches` and `percent` (D1); verify unit tests for the default, dropping out-of-range and non-finite values, removing duplicates, sorting, falling back with fewer than 2 or more than 8 steps, integer values, the epsilon match and the percentage
- [ ] 1.2 In `HeavymaticaConfig`, change the `ghost.opacity` default to 0.4, add `ghost.opacitySteps` with `ghostOpacitySteps()`, and add `setGhostOpacity` that sets and saves the value (D2); verify the build passes, and in `runClient` that the config file gains `opacitySteps` and that a config with `[0.9, 0.3, 1.5, 0.3]` offers 30% and 90% on the wheel (build passes; runClient check pending)

## 2. Wheel

- [x] 2.1 Add `Opacity`, `Entry.OPACITY`, `Entry.OPACITY_STEP`, `Page.OPACITY`, the segment value, `Command.SET_OPACITY` and the run value to `RadialMenu` (D3); verify `RadialMenuTest` for Opacity in every state before Library, the sub-wheel's steps and marking (including no mark), HOLD release staying open, click picking with the value, and the centre and Esc closing
- [ ] 2.2 Draw the Opacity and step segments in `RadialMenuScreen` with their icons and percentage names, pass runs to `RemoteInput`, which saves the picked opacity, and add the language keys (D3, D4); verify in `runClient` the "Pick a step", "Release on Opacity", "Leave from the centre", "Current step", "No matching step" and "Nothing selected" scenarios, that the in-hand filter's dimmed blocks follow, and the "Restart" scenario (runClient check pending)
- [ ] 2.3 Extend `DevScreens` with `wheelPoint=<entry>@<n>` and `logOpacity` (D5); verify the build passes, and in `runClient` that `wheelPoint=OPACITY_STEP@3` then `wheelClick` sets 80% and `logOpacity` logs it (build passes; runClient check pending)

## Workflow follow-up

- Archive `material-swaps` before this change, since its `client/radial-menu` delta builds on that change's segment lists.
- Commit the implementation once all tasks are done (Conventional Commits, for example `feat(remote): add ghost opacity wheel`).
