# Changelog

## 2.0.0 beta

### What's Changed

- Add “Read from Maid” to copy a maid's configuration to the Smart Compass.
- Support nodes, connections and route planning across dimensions.
- Preserve the link starting node when switching items, tools or dimensions, or reloading.
- Wait in place for an external transfer when the next node is in another dimension.
- Allow maids to travel between dimensions through portals and other mods.
- Reduce maid portal cooldown from 300 game ticks to the player's 10 game ticks.
- Add a master switch for maid dimension compatibility, enabled by default.
- Support configurable distance multipliers between dimensions.
- Upgrade compass and maid data with stable IDs for areas, waypoints and schedule entries.
- Automatically migrate older data while preserving configuration and progress.
- Fix graph entry selection ignoring areas when the maid is outside the route graph.
- Fix maids bypassing the route graph after schedule changes while their chunks are unloaded.
- Fix area navigation not resuming after relocation with a Smart Slab and re-enabling Home.
- Fix maids still waiting for a node in the previous dimension after a transfer.
