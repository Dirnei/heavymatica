# Tasks

## 1. Hint rules

- [x] 1.1 Add `KeyHintRules` with `Situation`, `Hint`, `hints(Situation)`, `header(Situation)`, `shows(...)`, `bottom(...)` and `keyHintsAfterMigration(...)`. Verify with `KeyHintRulesTest`, covering: hints per state (no selection, following, anchored, locked per layer mode, capture per corner count, capture busy); pending remove and unlock; bound versus unbound wheel keys; without the remote, only the layer hints of a locked placement and nothing otherwise; visibility with F1, a screen, hints off and pinned; the corner bottom without and with subtitles and on a narrow screen; and the migration.

## 2. Corner hint list and status-only panel

- [x] 2.1 Add the `KeyHints` GUI layer above the hotbar layer. It draws the header and hints from `KeyHintRules`, right-aligned at `KeyHintRules.bottom`, and registers in `HeavymaticaClient`. Remove every hint line from `PlacementOverlay`, keeping its status lines and its development text. Add the header lang keys. Verify with `./gradlew :heavymatica:test` and a check that the new lang keys exist.
- [x] 2.2 Replace compact with `overlay.keyHints`: config value, migration on client setup, settings table (compact is file-only), and lang name and hint. Verify with `SettingsTableTest` and the migration test from 1.1.
- [x] 2.3 Update the README overlay paragraph. Verify by reading it against the specs.

## 3. Integration

- [x] 3.1 Run `./gradlew :heavymatica:test`, all green. In-game checks (done by hand): the corner list with the remote in each state; the from-to and follow headers; pinned without the remote; subtitles on; GUI scale 4 on a small window; and key hints off.
