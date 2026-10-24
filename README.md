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
Death’s Door. A catastrophic fall also bypasses the Door when its final accepted HP damage, after
mitigation and absorption, is at least twice the maximum HP captured immediately before that hit.
This applies both above zero and at the Door; smaller falls retain the ordinary injury rules. A
canceled final death preserves life without fabricating a maim for the catastrophic bypass.
Vanilla blocking, armor, absorption, and hurt immunity still apply; this mod adds no maim cooldown.
Absorption-only hits add trauma but do not maim.

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

Heartbeat sound has a separate 600-tick (30-second) window. Entry and actual accepted HP or
absorption damage at the Door restart it; blocked, canceled and zero-loss hits do not. Healing,
confirmed death and respawn stop it. The server saves the deadline; reconnect/non-death clone
resume only its remaining time. Old bodily saves without a deadline stay silent until another
accepted hit or a new entry. Periodic state synchronization never grants a fresh window.

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
button changes to **Cancel**. The Journal and teammate view share a percentage, progress bar and
brief completion/cancellation/interruption status; unavailable/no-injury states disable starting
care. Feedback is subject-bound and clears when closing or changing the viewed body. The fixed
queue treats arms, head, torso, and legs in that order, oldest
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

## Current-world life records and death presentation

The Overworld owns a versioned `SavedData` ledger per player UUID: one current life,
completed-life bests, reached milestone tiers and the latest frozen recap. It is not an
unlimited history, a lineage transport, or a reward system. New players have a fully
measured life; an existing player first observed without a ledger has a labeled partial
life. Partial lives summarize observed activity but award no bests or milestones. The
next ordinary death-respawn begins a full life. Reconnects, restarts, dimensions and
non-death clones retain the current life; successor worlds do not carry the ledger.

Only alive, ticking Survival activity outside Arena matches accrues counters. Spectator
suspends measurement; Creative or Adventure invalidates that life's awards. Arena Trials
registers typed native membership/collection predicates into Door. Arena-contained death
retains the ordinary life through native return; terminal match activity remains suspended
through that server tick. There is no reverse Door dependency on Arena, kit inference or
persistent-tag guess.

| Metric | Native committed observation | Milestones |
| --- | --- | --- |
| Duration | Eligible server ticks, excluding pauses/offline time | 24,000 / 72,000 / 168,000 ticks |
| Highest level | Maximum native XP level, including pre-clear death capture | 10 / 20 / 30 |
| Mob kills | Credited non-player mob-kill statistic increments | 10 / 50 / 100 |
| Blocks placed | Successful BlockItem transactions after Forge rollback/cancellation; multiblock once | 100 / 500 / 1,000 |
| Blocks mined | Native mined-block statistic increments | 100 / 500 / 1,000 |
| Food replenishment | Positive hunger-point delta inside completed edible-item consumption, not saturation | 50 / 250 / 500 haunches |
| Damage endured | Accepted HP lost, clipped to available HP; internal semantic-zero sentinel is not HP | 50 / 250 / 500 hearts |
| Door checks survived | Post-entry accepted HP-consuming checks ending alive, excluding entry and absorption-only hits | 1 / 5 / 10 |
| Door entries | Actual transitions into the Door | 1 / 5 / 10 |
| Injuries treated | Successfully completed native cures on the subject, not attempted care | 1 / 5 / 10 |

Raw hunger/HP points and ticks are persisted. Each milestone is announced once per
player/world, from an eligible full life, on accepted finalization; only the highest new
tier per metric is displayed. Cancellation does not finalize a life. Frozen counters and
old bests are compared before updating aggregates; repeated finalization returns the same
life-ID snapshot. Corrupt/unsupported record payloads are diagnosed and reset without
altering inventory, body state or inventing past bests.

At most three cards are selected deterministically: beaten established bests by relative
improvement, unrepresented new milestones, matched/near nonzero bests at least 90% complete,
then positive first baselines by first-milestone progress. Fixed metric order breaks ties;
metric badges merge. Partial/ineligible lives and lives without qualifying cards use a
neutral explanation, not fabricated achievements.

The death screen begins with cause, a compact life/body summary and graphical comparison
cards. **Details** exposes the complete damage/body recap and all ten metric totals;
**Summary** returns without replaying the reveal. Door owns the content viewport, scrollbar,
hint reservation and native-control positions. Discovery Guides measures/wraps its hint
into that viewport and does not credit clipped/hidden copy as exposure. Content may scroll;
the original native Respawn/Title handlers and enable delay remain untouched.

A monotonic clock starts on the first rendered frame of each actual DeathScreen. Cause,
backdrop and native actions are immediate. Summary and Details appear at 150 ms; cards
follow at 300/450/600 ms and the hint follows the final card by 150 ms (three cards finish
at 750 ms). A neutral empty state uses one card stage. These are discrete draws, not fades,
slides or per-element sounds. Space is reserved before revealing; resize, duplicate/late
packets and Details navigation do not restart timing. The first immutable selection wins
for the episode. Closing/respawning clears presentation; reduced motion reveals immediately.
The existing sound preference remains independent.

Pure clock/layout tests cover boundaries, late data, reduced motion, Details, episode
reset and narrow scrolling. Native GameTests cover committed counters, partial/bootstrap,
canceled/final/arena deaths, clones, persistence, food, statistics, placements and owner
hooks. These do not establish human pointer acceptance or full-pack visual acceptance.
The separate `injuryVisual` review support includes `LifeVisualReview`/`LifeVisualClient`:
with `-Dlife.review.root=ISOLATED_ROOT`, it prepares bounded one-player ledger scenes,
uses real final-death/respawn packets and records timestamped production framebuffers.
Details is API-prepared by a nonshipping accessor, explicitly not a pointer test. Run
wide/narrow/reduced-motion as separate fixed-startup clients, never synthetic resizing.
Review every claimed acceptance image and retain worlds/evidence across handoffs by default under the
[shared retention policy](../../better-content-modpack/docs/policies/generated-data.md).
Finishing activity deletes nothing; purge is separately gated. The support classes, accessor and fixtures never ship.

## Presentation and console review

Skull marks on the heart bar represent the server’s maim-derived death probability. They remain
subdued above zero to show carried risk; urgent Pressure sound/effects run at Death’s Door. Full
coverage means 100%. Death's Door displays zero filled health hearts while the server preserves
a tiny engine-only survival value. With structurally compatible stable Mantle 1.11.x releases
starting at 1.11.104, an optional client mixin wraps the renderer's health-read operation and
clears its own remembered-health/blink state. It delegates the health read exactly once and only
substitutes zero for the camera player's synchronized Door state; other wrapped results are preserved. Mantle
continues owning its colored containers and layout. Without Mantle, or with its renderer disabled,
the Forge renderer hooks provide the same semantic-zero presentation. Absorption hearts remain
independent: zero ordinary HP does not remove earned absorption or empty/max-health containers.
Missing synchronized injury state never causes a guessed zero-health override. Active maims appear as marks on exposed skin or over armor in
the world and on inventory avatars. The optional Epic Fight 20.14.17 bridge replaces only the
maim layer's native fallback, using posed native skin and armor meshes with region/joint masks;
vanilla rendering retains its own layer. Native outer-clothing UVs are mapped to the existing
base-skin mark islands, and native 64×32 armor UVs are normalized to the 64×64 mark textures.
Shared mesh visibility is restored after every draw. Mark clearance uses geometric normals recovered
from the current position and lighting-normal transforms, not lighting normals directly: native
inventory rendering reflects/scales positions without applying that reflection to its lighting normals.
The offset remains model-scaled, and original lighting normals, textures and native depth testing are
preserved. Pinned mesh triangles and authored PNG alpha coverage test both standard/slim skin and native
armor mappings; transform tests cover position-only GUI reflection. Source gates are not pixel acceptance.
The inventory and teammate views show active injuries; the
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

### Focused maim-surface diagnostic

Run the nonshipping surface diagnostic with:

```sh
INJURY_REVIEW_MANUAL=1 MAIM_REVIEW_MATRIX=1 ./gradlew runInjuryVisual \
  -PinjuryEpicTests=true -PinjuryVisualWidth=1280 -PinjuryVisualHeight=960
```

Set GUI scale 2 in the isolated fixture's options before startup. The diagnostic does not resize
or synthesize pointer input. It captures the real native avatar helper at fifteen explicit
angle pairs for each maim type with clothing enabled/disabled, then native personal-inventory
skin/clothing/armor views and third-person world views. It authors six server injuries and requires
synchronized visual counts before capture. Screenshots remain under `build/injury-visual/screenshots/`.
The multi-avatar diagnostic canvas is not a native menu or human-pointer acceptance result;
vanilla-only reruns omit `-PinjuryEpicTests=true`. Curios variants, equipped-backpack parity and
human interaction still require their separate same-artifact native acceptance.

### Focused native heart compatibility review

The focused opt-in review uses the exact pack Mantle input
`curse.maven:mantle-74924:7563777` (1.11.104, SHA-1
`8a3f948f8c505ee92dae915ec0a1593594eb2667`). `verifyFull` checks this **raw pinned
artifact** with a shared ASM provenance contract: method signature, camera health-read/ceil,
health-to-drawer and history-update flow, private history types, independent absorption and
Forge-overlay cancellation. Local slot numbers, debug tables and harmless no-ops are not contract
boundaries. The optional dependency range is `[1.11.104,1.12)`; an early client-class-free mixin
plugin additionally rejects prereleases and checks both the upstream target and the target
presented for transformation. It changes only Mantle mixin selection. Absent Mantle and dedicated
servers do not request client renderer bytecode. Incompatible installed renderers fail clearly;
there is no silent disabling, configuration rewrite or automatic renderer replacement.

The composable operation hook uses MixinExtras 0.5.4, independently supplied through Forge JarJar
with compatibility range `[0.5.4,0.6.0)`. Minecraft's getter is remapped; Mantle entry points and
history fields are not. Mantle itself remains unbundled. Compatible future stable patch releases
can pass the structural guard without editing a version allowlist, but **structural admission is
not native verification of those releases**. Other mixins and unseen upstream semantics may still
require review; this is update-tolerant compatibility, not a promise of universal compatibility.

```sh
# Repository-local clients, pack Forge version, four independent renderer profiles:
python3 scripts/review_hearts.py --profile all --forge-version 47.4.22
# Or select WITH_MAX, NO_MAX, DISABLE or absent:
python3 scripts/review_hearts.py --profile WITH_MAX --forge-version 47.4.22
# Build the separate reobfuscated support JAR, under the shared Forge lock:
./gradlew stageRuntimeJar stageHeartReviewSupportJar
# Real production namespace: staged reobfuscated JARs in stock Forge, no userdev mapping:
python3 scripts/review_hearts_production.py --profile WITH_MAX
python3 scripts/review_hearts_production.py --profile absent
# With an existing display, hold the shared Forge lock around the whole invocation:
./gradlew runInjuryVisual -Pforge_version=47.4.22 -PinjuryHeartProfile=WITH_MAX
```

The Python runner uses an isolated Xvfb display, software OpenGL, bounded startup/runtime and
shutdown, and the workspace `fcntl.lockf` Forge cache lock. Run under a registered maintenance
task. Profile working directories are `build/heart-review-PROFILE/`; logs are under
`build/heart-review-control/`. Every invocation creates a fresh disposable world. The selected
profile asserts Mantle presence and its exact renderer configuration before review. Review-only
mixins observe the actual transformed Mantle drawer or Forge's native heart arguments; they
are excluded from the production JAR. Survival HUD fading is disabled **only in this review**
so healthy baseline hearts remain visible, without synthesized input.

The matrix covers 20/60 maximum HP, GUI scales 2/3/4, normal/poison/wither variants, and
absorption. Actual server damage leaves a legitimate 1 HP, then crosses into Death's Door;
production healing returns to 1 HP, followed by a second crossing to expose stale history.
The fixture prevents unrelated ongoing poison/wither damage after each intended hit, without
altering renderer inputs. Framebuffers capture first synchronized Door rendering, early hurt
frames, and settled rendering after the healing lock. Runtime assertions correlate the active
renderer, camera UUID, synchronized Door state, effective current/remembered health, positive
engine health, and server liveness. Final death/production respawn and native save/disconnect/
rejoin/healing check lifecycle reset and persistence. Three additional native negative cases
cover missing synchronized state, another UUID's Door state, and a non-player camera. A second
review-only health-operation wrapper composes with the production hook; an engine-getter probe
requires exactly one real getter call per composed operation. Production smokes run the first
matrix combination plus these negatives and lifecycle cases in stock Forge 47.4.22, loading the
separately reobfuscated support mod. Development launches are not production-remapping proof.

Every acceptance image must be opened and reviewed. Numerical contracts and observed native
health arguments are not pixel acceptance. These are isolated repository-local checks, not
full-pack visual acceptance: proving the shipped pack requires an explicitly authorized fresh
candidate and focused full-pack review. This command does not deploy, refresh Packwiz, package,
or run Dist/Debug.

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
Network protocol **9** retains the heartbeat and subject-bound treatment status and adds
bounded, immutable life-recap packet ID **5**; client and server must update together.

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
