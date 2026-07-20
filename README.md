# QAcraft

**Quantum physics, playable in Minecraft.** QAcraft is an educational Paper plugin
that turns three landmark quantum-information protocols into hands-on, visual
experiments you can run inside a Minecraft world — built for lectures and
workshops at the Czech–Bavarian Quantum Academy.

| Protocol | What you demonstrate |
|---|---|
| **BB84** | Quantum key distribution — encode photons in random bases, measure, sift a shared key |
| **Grover** | Quantum search — find the marked item in √N steps with amplitude amplification |
| **E91** | Entanglement-based QKD — Bell pairs, correlated measurements, eavesdropper detection |

Plus a lobby **plasma "atom"** particle effect and a built-in **guided tutorial**
for workshops.

---

## Requirements

- **Java 25** or newer — [Adoptium / Temurin](https://adoptium.net/)
- **Paper 26.1.2+** server ([papermc.io](https://papermc.io/))

---

## Build

No Maven installation required — the wrapper downloads it automatically on first run.

**Windows:** double-click **`build.bat`**, or:

```bat
mvnw.cmd clean package
```

**Linux / macOS:**

```bash
./mvnw clean package
```

The compiled plugin lands at **`target/QAcraft.jar`**.

## Install

1. Copy `target/QAcraft.jar` into your server's `plugins/` folder.
2. Start (or restart) the server.
3. In game, run `/qacraft help`.

---

## Try it on your own

The quickest way to meet the plugin — build the tutorial hall and take the guided
walkthrough:

1. `/qacraft world build` — builds a decorated tutorial hall (Lobby → BB84 → Grover → E91) next to you.
2. `/qacraft tutorial start` — starts the interactive, gated walkthrough inside it
   (or press the **Start Tutorial** button in the hall lobby).

The tutorial teleports you into the hall and guides you step by step: each protocol
room must be completed hands-on before its door opens. Wall panels, floating
"X here" markers, live "Missing: …" chest hints, and in-room "give tools" buttons
walk a newcomer through every experiment.

---

## Commands

All commands are under `/qacraft` (aliases: `/q`, `/qac`).

### General

| Command | Description |
|---|---|
| `/qacraft help` | Show the in-game help |
| `/qacraft tools <bb84\|grover\|e91\|all>` | Give a protocol's tools (clears your inventory first) |
| `/qacraft item <name>` | Give one named tool **without** clearing your inventory (tab-complete for names) |
| `/qacraft clear` | Remove every QAcraft entity in all worlds |

### BB84 — Quantum Key Distribution

| Command | Description |
|---|---|
| `/qacraft sender place` | Place the transmitter (snaps to block centre; outlines mark the double chest) |
| `/qacraft sender fill` | Fill the double chest with 27 random basis filters |
| `/qacraft sender start` | Transmit — one photon per second (27 total) |
| `/qacraft sender stop` | Stop transmitting |
| `/qacraft gate place` | Place a measurement gate (outline marks its filter chest) |
| `/qacraft gate clear` | Remove all gates |
| `/qacraft wp [place\|clear]` | Route waypoints for photons |
| `/qacraft parking` | Place a parking spot |
| `/qacraft send <rect\|diag> <0\|1>` | Manually send one photon with a given basis and bit |
| `/qacraft photon <clear\|info>` | Clear photons / print their hidden state |

### Grover's Search

Multiple independent grids can run at once — each is identified by an id shown on a
big number floating in its centre. Give the id after each command (omit it when only
one grid exists).

| Command | Description |
|---|---|
| `/qacraft grover setup [id]` | Mark 8 positions in a ring (new grid; auto-assigns the lowest free id) |
| `/qacraft grover placechests [id]` | Auto-place chests on the marked positions |
| `/qacraft grover fillwool [id]` | Put a random wool colour in each chest |
| `/qacraft grover iterate [id]` | Run one Grover iteration (or throw a Wind Charge — iterates the nearest grid) |
| `/qacraft grover reset [id]` | Reset to equal superposition |
| `/qacraft grover clear [id]` | Remove one grid; without an id, remove all |
| `/qacraft grover list` | List the active grids and their iteration counts |

The first iteration runs only while you hold the item you are searching for — it
becomes the grid's target. Hold the **Spyglass** in your main hand and the target in
the offhand to see the probability columns after the first iteration. Searching for a
new item restarts that grid. Drop the **Grover Reset** (TNT) on a grid's centre block
to deactivate it.

### E91 — Entanglement Protocol

| Command | Description |
|---|---|
| `/qacraft e91 source` | Place the entangled-pair (EPR) source |
| `/qacraft e91 alice` / `bob` | Set Alice's / Bob's zone marker |
| `/qacraft e91 generate` | Emit one entangled pair |
| `/qacraft e91 start` / `stop` | Auto-generate pairs |
| `/qacraft e91 key` | Show the sifted key (same-basis measurements) |
| `/qacraft e91 bell` | Show Bell / CHSH correlations |
| `/qacraft e91 eve` | Toggle the eavesdropper simulation |
| `/qacraft e91 clear` | Remove all E91 entities |

Hold a **Compass**, **Recovery Compass**, or **Clock** near an arrived photon to
measure it in the rectilinear, diagonal, or circular basis.

### Lobby & Workshop

| Command | Description |
|---|---|
| `/qacraft plasma` | Get the Plasma tools — right-click **Heart of the Sea** to summon a particle "atom", **Echo Shard** to remove the nearest |
| `/qacraft plasma clear` | Remove all plasmas |
| `/qacraft world build` | Build a decorated tutorial hall (Lobby → BB84 → Grover → E91) next to you |
| `/qacraft world clear` | Remove the hall and restore the ground |
| `/qacraft tutorial start` | Start the interactive, gated walkthrough inside the built hall |
| `/qacraft tutorial stop` | End the walkthrough (clears placed apparatus and chests) |

---

## Tools

| Item | Tool | Protocol |
|---|---|---|
| Compass | Rectilinear filter (+) | BB84 / E91 |
| Recovery Compass | Diagonal filter (×) | BB84 / E91 |
| Clock | Circular filter (○) | E91 |
| Snowball | Quantum source | BB84 |
| Carrot on a Stick | Quantum cable (waypoint) | BB84 |
| Warped Fungus on a Stick | Parking spot | BB84 |
| Nether Star | Quantum sender | BB84 |
| Eye of Ender | Quantum gate | BB84 |
| Fire Charge | Grover setup (new grid) | Grover |
| Conduit | Place chests on the nearest grid | Grover |
| Wind Charge | Grover operator (iterate) | Grover |
| Spyglass | Quantum observer | Grover |
| TNT | Grover reset (drop on grid centre) | Grover |
| Amethyst Shard | EPR source | E91 |
| Diamond Axe | Alice landing pad (R) / zone (L) | E91 |
| Gold Axe | Bob landing pad (R) / zone (L) | E91 |
| Heart of the Sea / Echo Shard | Plasma summon / remove | Lobby |
| Brush | Quantum eraser | all |

Filter colours mirror the compasses: **Rectilinear** red, **Diagonal** cyan, **Circular** yellow.

---

## Persistence

QAcraft entities (photons, gates, pads, plasmas, tutorial signs) and protocol
runtime state survive **server restarts** — they are saved with the world and to
`plugins/QAcraft/state.yml`. Use `/qacraft clear` (or the per-protocol `clear`
subcommands) to remove them deliberately.

---

## Documentation

The step-by-step user guide is [`docs/QAcraft-Guide.docx`](docs/QAcraft-Guide.docx) —
chapter by chapter: tools and items with in-game icons, every command, and each
protocol explained.

---

## Project

Czech–Bavarian Quantum Academy · Interreg Bayern–Tschechien · **BYCZ10-263**
Developed by [NVIAS](https://nvias.org/).
