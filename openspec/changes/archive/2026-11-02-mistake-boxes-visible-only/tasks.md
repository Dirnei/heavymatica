# Tasks

## 1. Boxes only where seen

- [x] 1.1 In `placement/MistakeBoxes.java`, drop the pass that draws boxes behind blocks and the `HIDDEN_ALPHA` constant, so `draw` makes one depth-tested pass at full alpha; verify `gradlew build` passes
- [x] 1.2 Remove `MISTAKE_BOX_HIDDEN` and its registration from `placement/GhostPipeline.java`; verify the build passes and that `grep -rn "MISTAKE_BOX_HIDDEN\|mistake_box_hidden" src/mod` finds nothing
- [x] 1.4 In `placement/GhostRenderer.java`, draw a placement's boxes before its ghosts, and stop boxes writing depth in `GhostPipeline.MISTAKE_BOX`, so ghosts never hide a box and a ghost inside its own box stays visible; verify the build passes
- [x] 1.5 Make boxes follow the found block's outline: `MistakeBoxes` takes the outline of the block at each boxed cell from `WorldComparison` (full cube when it fills the cell), merges shared faces only between full boxes, and `WorldComparison` rebuilds a cell's box when another block takes its place in the same state; verify the build passes
- [x] 1.3 Verify in `runClient` **(in-game check)** with a locked placement: a red box behind a solid wall doesn't show, a cube of wrong blocks shows only its outer surface with nothing glowing through, boxes in plain sight keep their colours, a box around leaf litter is flat, boxes stay visible while standing among ghosts, and the mistake pointer still points to a hidden mistake

## Workflow follow-up

- Commit the implementation once all tasks are done (Conventional Commits, for example `fix(comparison): show mistake boxes only where seen`).
- Archive the change with `/opsx:archive` when asked.
