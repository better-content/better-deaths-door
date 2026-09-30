# Changelog

## Unreleased

- Replace binary downed/revive episodes with active combat at semantic zero HP, regional maiming, and server-authoritative next-hit death rolls. Preserve ordinary final-death integrations and disable player totem rescue.
- Add six body regions, three simple cures, interruptible self/teammate treatment, unbounded current-life treatment history, and a frozen final-death recap.
- Add independent expiring trauma, tunable regional penalties, a two-second healing lock, causal damage classification, and optional Epic Fight reach/collider integration.
- Add an inventory Body screen, honest death-pressure feedback, Dynamic Survival HUD integration, and operator console controls for real-client visual review.
- Replace the obsolete revival API/events with read-only injury state and committed injury events. Existing consumers must update together before deployment.
- Keep the runtime ID `better_deaths_door` and artifact `better-deaths-door`; display the mechanic as Death's Door. Convert an existing same-ID downed episode to active Death's Door and release its forced pose.
- Add deterministic model tests, real-player GameTests, optional Epic Fight runtime tests, and a development-only command-driven visual review client.
