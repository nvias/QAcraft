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

- **Java 21** or newer — [Adoptium / Temurin](https://adoptium.net/)
- **Paper 1.21.4+** server ([papermc.io](https://papermc.io/))

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

## Commands

All commands are under `/qacraft` (aliases: `/q`, `/qac`).

### General

| Command | Description |
|---|---|
| `/qacraft help` | Show the in-game help |
| `/qacraft tools <bb84\|grover\|e91\|all>` | Give a protocol's tools (Eraser always lands in slot 9) |
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

| Command | Description |
|---|---|
| `/qacraft grover setup` | Mark 8 positions in a ring around you |
| `/qacraft grover placechests` | Auto-place chests on the marked positions |
| `/qacraft grover fillwool` | Put a random wool colour in each chest |
| `/qacraft grover iterate` | Run one Grover iteration (or throw a Wind Charge) |
| `/qacraft grover reset` | Reset to equal superposition |
| `/qacraft grover clear` | Remove all Grover entities |

Hold the **Spyglass** in your main hand and your target block in the offhand to see
the probability columns after the first iteration.

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
| `/qacraft plasma <summon\|despawn\|clear>` | Floating particle "atom" with orbiting rings and electrons |
| `/qacraft tutorial <start\|next\|back\|goto\|stop>` | Guided step-by-step walkthrough |
| `/qacraft tutorial <spawn\|clear\|reload>` | Manage the floating station signs (admin) |

Tutorial steps live in `plugins/QAcraft/tutorial.yml` — edit the coordinates to fit
your own world, then `/qacraft tutorial reload`.

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
| Breeze Rod | Quantum gate | BB84 |
| Wind Charge | Grover operator | Grover |
| Spyglass | Quantum observer | Grover |
| Amethyst Shard | EPR source | E91 |
| Diamond Axe | Alice landing pad (R) / zone (L) | E91 |
| Gold Axe | Bob landing pad (R) / zone (L) | E91 |
| Brush (slot 9) | Quantum eraser | all |

---

## Persistence

QAcraft entities (photons, gates, pads, plasmas, tutorial signs) and protocol
runtime state survive **server restarts** — they are saved with the world and to
`plugins/QAcraft/state.yml`. Use `/qacraft clear` (or the per-protocol `clear`
subcommands) to remove them deliberately.

---

## Documentation

Step-by-step user guides are in [`docs/`](docs/):

- **`QAcraft-Guide.docx`** — English
- **`QAcraft-Navod.docx`** — Czech

---

## Project

Czech–Bavarian Quantum Academy · Interreg Bayern–Tschechien · **BYCZ10-263**
Developed by [NVIAS](https://nvias.org/).
