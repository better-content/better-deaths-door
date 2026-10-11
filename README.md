# Death’s Door

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


Death’s Door is Better Content’s server-authoritative bodily injury system for Forge 1.20.1.
The runtime identity remains `better_deaths_door`.

A hit that exhausts positive HP enters Death’s Door and causes one maim. Positive HP protects
against that hit’s death roll, even with many existing injuries. At semantic zero, every further
accepted HP-damaging hit either kills or causes exactly one new maim. Players keep ordinary
movement, combat, inventory, and item controls. There is no bleed-out timer or multiplayer-only
revival rule.

Every zero crossing prevents all HP healing for 40 server ticks. Subsequent hits do not restart
the lock. Healing afterward restores the ordinary HP buffer; injuries remain until treated.
Totems do not prevent player death. Explicit special-kill sources, command kill, and void bypass
Death’s Door. Vanilla blocking, armor, absorption, and hurt immunity still apply; this mod adds no
maim cooldown. Absorption-only hits add trauma but do not maim.

## Injuries and trauma

Exactly six regions are modeled: head, torso, left/right arm, and left/right leg. Region determines
impairment; injury type determines cure. Damage direction biases region selection, with falls
strongly favoring the legs. Configured source overrides and damage/weapon tags classify injuries;
unrecognized sources deliberately produce Cracked injuries.

Each active maim contributes 5 percentage points of death probability. Each head maim adds another
10 percentage points. The server rolls against the active injuries that existed before the hit.
The total caps at 100%; zero maims means zero maim-derived death probability.

Every accepted hit adds one trauma stack for 1,200 server ticks, including damage absorbed by golden
hearts. Stacks expire independently. Trauma scales functional injury effects from 50% at rest to
100% with five stacks. Trauma does not change death probability.

Default full-strength penalties:

- Torso: maximum HP reduction `0.5 × n/(n+3)`.
- Each leg: movement reduction `0.5 × n/(n+3)`; both legs add.
- Arms: combined arm count × 10%, capped at 100%, for melee damage and attack/use reach.
- Treatment: `2 + (8/3 × arm count × functional multiplier)` seconds, using the healer’s arms.

Treatment remains possible at every finite injury count. Maximum-health changes never grant HP.
All coefficients are centralized in the common configuration and server snapshots carry the
configured values used for presentation.

## Treatment

The inventory has a **Mend** button with active injuries listed beneath it. Right-click another
player within reach and line of sight to open a compact Mend view with their avatar and injuries.
Opening either view only inspects; pressing **Mend** starts server-owned automatic care and the
button then cancels it. The fixed queue treats arms, head, torso, and legs in that order, oldest
injury first within each region. Treating arms first reduces later self-care time. No item is
required or consumed. Damage, closing the view, losing reach or sight, and disconnecting interrupt
treatment. Each completed step remains in treatment history.

Burnt includes heat, freezing, and tagged corrosive damage. Opened denotes a major wound. Injury
type describes the harm; its region determines the functional penalty. Old Balm and Soocher stacks
remain registered for saved-world compatibility but have no treatment role or recipes.

Body state and treatment history survive reconnects and dimension changes within the current life.
They clear on confirmed final death. A separate recap retains the final active/treated regional
counts and per-life accepted-hit totals while the player is dead, then clears on respawn. Incoming
damage is measured before armor and resistance after shield and hurt-immunity admission. The recap
separates observed mitigation, absorption, applied damage and actual HP loss; hook changes or other
unattributed remainder is labeled unknown. There is no lineage integration.

## Presentation and console review

Skull marks on the heart bar represent the server’s maim-derived death probability. They remain
subdued above zero to show carried risk; urgent Pressure sound/effects run at Death’s Door. Full
coverage means 100%. Death's Door displays zero filled health hearts while the server preserves
a tiny engine-only survival value. Active maims appear as marks on exposed skin or over armor in
the world and on inventory avatars. The inventory and teammate views show active injuries; the
final-death recap retains active and treated totals. A confirmed failed death roll plays its own
sound; surviving zero crossings do not show a final recap.

Operator-level commands target explicit player names and work from the server console. Use isolated
review worlds for state-changing scenarios:

```text
betterdeathsdoor debug gui PLAYER inventory
betterdeathsdoor debug gui PLAYER own-body
betterdeathsdoor debug gui VIEWER mend SUBJECT
betterdeathsdoor debug gui PLAYER death-recap
betterdeathsdoor debug gui PLAYER close
betterdeathsdoor debug scenario PLAYER mixed
betterdeathsdoor debug maim PLAYER LEFT_ARM BURNT
betterdeathsdoor debug treatment start HEALER SUBJECT
betterdeathsdoor debug treatment cancel HEALER
betterdeathsdoor debug capture PLAYER review-label 12
betterdeathsdoor debug pressure PLAYER 11 true 60
betterdeathsdoor debug presentation PLAYER true false
```

Fixture names include `healthy`, `mixed`, `severe`, `long_history`,
`healing_lock`, `trauma_expiry`, and `final_death`. The final-death fixture runs real death.
Commands invoke production screens and handlers; they do not simulate player input.
`gui PLAYER own-body` opens the inventory with the same Mend panel as ordinary entry.

`pressure PLAYER MAIMS AT_DOOR MAX_HP` creates real server-owned leg injuries and health,
then reports the actual configured death probability. `presentation PLAYER REDUCED_MOTION SOUND`
controls the viewing client's presentation settings. Completed steps retain historical care records
for the current life and recap. The death-recap command requires an actual completed death.

`./gradlew runInjuryVisual` launches the isolated real Minecraft review client under
`build/injury-visual/`, creates a disposable flat world, invokes console commands, and captures its
actual framebuffer. The review source set is excluded from runtime JARs. Its standard matrix covers
1280×720, 1280×960, and 1920×1080 at requested GUI scales 2, 3, and 4; Minecraft clamps scales that
would violate its minimum GUI dimensions. It includes inventory maim lists, armored avatars,
treatment states, probability/health/absorption combinations, HUD fading, reduced motion, and
native final death.

For two actual connected clients, start the primary and wait for `INJURY_REVIEW_WAITING_HELPER`:

```sh
INJURY_REVIEW_MULTIPLAYER=1 ./gradlew runInjuryVisual
```

Then run in a second terminal:

```sh
./gradlew runInjuryHelper
```

The primary exposes its isolated development world on port 56694 without account authentication;
`InjuryHelper` joins through Minecraft's production connection flow. Console operations open the
helper's production Mend view, apply treatment, and capture progress, success, and damage
interruption on both clients. The harness shuts down both clients on completion. Keep the review
port confined to the local development environment.

Screenshots are saved in `build/injury-visual/screenshots/` and
`build/injury-helper/screenshots/`. For a persistent console-driven review, set
`INJURY_REVIEW_MANUAL=1` on the primary; write one production server command per line to
`build/injury-visual/review.commands`. The harness consumes that file on the server thread.
A harness-only line `viewport 1920 1080 3` changes the actual window and GUI scale.
Only active injury types appear in the scrollable Mend list. The inventory panel and teammate view
use the same list and server-owned treatment control.
A line containing `stop` ends the primary. No mouse, key, or player-control events are synthesized.

On lanes without a display, an isolated user-namespace Xvfb avoids system temporary files:

```sh
unshare -Ur Xvfb :94 -screen 0 1920x1080x24 -nolisten unix -nolock -listen tcp -ac -noreset
```

Run clients with `DISPLAY=localhost:94 TMPDIR=/home/dev/.tmp`. Stop that exact Xvfb process after
review. Native toasts pause during Death’s Door and while the teammate Mend view occupies their space, then resume
with their presentation lifetime preserved. Trauma duration text comes from server tuning.

Every acceptance screenshot must actually be opened and reviewed; image generation or layout tests
alone do not establish GUI quality. Render inspection does not prove pointer usability or natural
combat pacing. The lane's OpenAL device was unavailable during automated rendering, so these
screenshots are not evidence of audible sound quality or audio balance.

## Integration and verification

`InjuryApi` exposes read-only server injury counts, semantic HP, Death’s Door state, and snapshots.
`InjuryEvent` publishes committed entry, healing exit, maim, treatment, and final-death changes.
Client packets contain bounded active and treated counts, not authoritative mutation decisions.

RPG Stats and Configurable Death retain final-death ownership. Surviving an episode does not run
their final-death consequences. Depth Director scales reinforcement cadence by active injuries;
Pillager Campaigns keeps fighting through Death’s Door; Player Traces records until actual death.
Legacy Revival API/event consumers require updates before deploying this provider. Teaching-surface
changes are intentionally separate from this implementation.

Dynamic Survival HUD is an optional client presentation provider. The bridge uses its typed API
behind a mod-presence guard; gameplay state remains in Revival. Compilation needs the canonical
`better-survival-hud-1.0.0.jar`, resolved from `BC_CUSTOM_MOD_JAR_DIR` when supplied, otherwise
`../better-survival-hud/build/libs/`. A blank override or missing JAR fails configuration.
Release ordering stages the HUD provider first. CI builds the exact HUD source revision pinned in
its workflow because the currently bundled pack provider predates the injury presentation API.
The visual lane includes the provider by default; `-PinjuryVisualHud=false` checks standalone
client behavior without putting the provider on the runtime classpath.

```sh
./gradlew verifyFull stageRuntimeJar
```

The deployable JAR is the reobfuscated `build/libs/better-deaths-door-<version>.jar`.
Local verification and staging do not deploy into the modpack or authorize pack suites/distributions.
