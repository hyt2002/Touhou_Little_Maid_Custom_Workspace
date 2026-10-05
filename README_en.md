# Touhou Little Maid Custom Workspace

![Smart Compass cuboid areas, labels and waypoint graph](exp.png)

[简体中文](README.md)

Adds **Kappa's Smart Compass** to [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid). Give each maid her own cuboid work, rest and sleep areas, guide travel with a bidirectional waypoint graph, and rotate work destinations using game-tick schedules.

## Development Stage

This is the project's first public version, **1.3.3**. It has passed the build, 49 unit tests, 28 game integration tests, and client input, UI and texture verification. Reports from complex terrain and third-party tasks are welcome.

Please include mod versions, reproduction steps, area/waypoint screenshots and relevant logs in [Issues](https://github.com/hyt2002/Touhou_Little_Maid_Custom_Workspace/issues). Mention whether Home is enabled, whether the maid entered the area, and whether the waypoint segments are traversable.

## Installation

- Minecraft **1.21.1**, **NeoForge**; developed and tested with NeoForge **21.1.219**.
- Requires **Touhou Little Maid 1.5.3 for NeoForge / Minecraft 1.21.1**.
- Install the addon JAR in `mods` on **both client and server**, using matching addon versions.
- Create and Catnip are not required.

## Usage

### Smart Compass and Tools

Find the Smart Compass in the TLM creative tab, or craft it shapelessly using one **Kappa Compass**, one **Redstone Dust** and one **Copper Ingot**.

Hold it in your main hand. Hold the tool-menu key (default **Left Alt**), scroll to select a tool, then release to confirm:

| Tool | Action |
| --- | --- |
| Cuboid selection | Use two block corners to add an area, initially marked as work |
| Route nodes | Choose a starting node, then create or connect mandatory waypoints |
| Mark areas | Use a cuboid to cycle work → rest → sleep |
| Rename areas | Use a cuboid to open its name editor |
| Schedule options | Use to edit work destinations and delay conditions |
| Apply to maid | Use your own maid to copy the plan; enable **Home** |

“Use”, “Attack” and “Sneak” follow Minecraft's configured bindings, including swapped mouse buttons. In-game help displays the actual keys. The menu and clear keys are also rebindable.

### Areas and Waypoints

Create cuboids, mark their types, optionally name them, then apply the plan to a maid. Work is **red**, rest **green**, and sleep **blue**. Bounds include both endpoint block coordinates and must contain the maid's actual feet block; selecting only a solid floor does not count as arrival. Sleep areas should also include the bed's head block.

In the route tool, use an existing area or waypoint to choose a start. Walk to the desired waypoint and use a position that is not an existing node, either a block or air. The node records **your current feet block**, not the clicked block, and connects to the previous node. Continue adding points, or use another existing node to connect it. Edges are bidirectional; there is no “finish editing” step.

Maids follow the shortest graph route through each mandatory node, advancing only after actually entering its box. With no valid graph route, they navigate directly to the destination. Outside all nodes, they enter the nearest intermediate waypoint in the destination's connected component. Multiple rest or sleep areas are chosen by total graph distance. Distance uses game coordinates, not precomputed terrain path lengths; ensure each segment is traversable. See [route details](docs/ROUTE-GRAPH.md).

### Work Schedules

The editor uses destination cards and separate condition editors, inspired by Create train schedules. The current condition is **delay x game ticks**, counted while working after arrival. Travel, rest, sleep and inability to work pause progress; wall-clock time is not used.

Choose destinations and delays, reorder, duplicate or remove entries, and run cyclically or once. A finished one-shot schedule stays in the last work area and continues the original task. Without a custom schedule, work areas rotate in added order after **2400 working game ticks** each. Confirm the editor to save the compass draft, then reapply it to the maid.

### Removing Data

- Attack a cuboid in the cuboid tool to remove it. Sneak + Use cancels an unfinished selection.
- Attack a waypoint in the route tool to delete it and its edges; attacking an area there only removes its connections.
- Sneak + Clear (default **Shift+V**) removes all cuboid types in the cuboid tool, or only intermediate waypoints in the route tool. The other node category is preserved; incident edges are removed.
- Sneak + Use your maid in the apply tool to clear her custom configuration and restore original area behavior.

## Compatibility

- **Both compasses coexist:** for each schedule type, custom cuboids take priority when present; otherwise TLM's original compass positions and behavior apply.
- **Applied data belongs to the maid:** editing, clearing or destroying the compass does not affect her. Reapplying replaces her custom plan. Each maid has an independent snapshot.
- Navigation still uses TLM's pathfinding. Waypoints guide difficult detours; arrival uses strict cuboid bounds.
- The addon changes area membership and candidate searches. Individual tasks retain their own operations and area effects.
- Plans are dimension-specific. Searches do not actively load or generate chunks.
- Compass plans and maid states carry independent schema versions. Unversioned legacy data loads automatically and gains a version on its next save. See [migration notes](docs/DATA-VERSIONS.md).

## Configuration

`config/touhou_little_maid_custom_workspace-common.toml`; server configuration controls behavior.

| Setting | Default | Purpose |
| --- | ---: | --- |
| `workTicksPerArea` | 2400 | Default rotation budget and initial delay for new entries |
| `travelTimeoutTicks` | 0 | Travel timeout in active game ticks; 0 disables skipping |
| `maxAreasPerMaid` | 16 | Combined work, rest and sleep cuboid limit |
| `maxAreaEdge` | 64 | Maximum length of each cuboid axis |
| `maxAreaVolume` | 131072 | Maximum cuboid volume |
| `candidateBudget` | 2048 | Candidates checked per task search; scanning resumes later |

Custom schedules retain their explicit delays. Upgrading does not overwrite existing timeout settings.

## Asset License and Credits

**Code is licensed under [MIT](LICENSE).** The Smart Compass artwork and associated resources are adapted from **Touhou Little Maid assets**, originally by **TartaricAcid / tartaric_acid and TLM's contributors and artists**. They are not wholly original artwork. The adaptations by hyt2002 retain **[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)**: attribution, non-commercial use and share-alike.

When sharing or adapting these assets, retain the original attribution, source and license, indicate changes, and follow those terms. The code's MIT license does not relicense this artwork. See [copyright and third-party notices](NOTICE.md) and the [full asset license](LICENSE-CC), also included in the published JAR. TLM and Minecraft artwork visible in `exp.png` remains the property of the respective rightsholders.

Thanks to TLM's authors and contributors, and NeoForge's template maintainers. Selection, tool-menu and schedule interactions refer to [Create](https://github.com/Creators-of-Create/Create); the addon implements its own code and rendering and does not redistribute Create code or artwork. README organization was inspired by [Maid Storage Manager](https://github.com/zxy19/maid_storage_manager).

## Development

Use **Java 21**. The Gradle Wrapper resolves TLM and matching sources from Modrinth Maven; do not copy TLM JARs from a game installation.

```shell
./gradlew build
./gradlew runGameTestServer
```

On Windows, use `gradlew.bat`. The distributable is in `build/libs/`; development test classes and structures are excluded. GitHub Actions runs the build and game integration tests.

[Full guide](docs/FIRST-VERSION.md) · [Routes](docs/ROUTE-GRAPH.md) · [Data migration](docs/DATA-VERSIONS.md) · [Changelog](docs/CHANGELOG.md)
