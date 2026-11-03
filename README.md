# metallic-iris-metal
Clean-room Fabric mod prototype to run Iris shaderpacks on Apple Metal 3

## Status

Clean-room implementation, written from the publicly documented shaderpack format. It does not
use Iris or Sodium code and does not need either mod installed.

| Stage | State |
| --- | --- |
| 1. Shaderpack loader (folder/zip, `shaders.properties`, `#include`, program discovery) | done, unit tested |
| 1b. Settings: master on/off, per-pack enable/disable, per-pack option values, saved to disk | done, unit tested (no in-game screen yet) |
| 2. GLSL to MSL (glslang + SPIRV-Cross through Java's FFM API) | planned |
| 3. Metal backend (device, pipelines, render targets via FFM) | planned |
| 4. Minecraft hookup (gbuffers/composite/final passes in the render loop) | planned |

Stages 2-4 need a Mac with Metal to test; CI only covers stage 1.

## Settings

Settings are stored in `config/metallic_iris_metal.properties`; packs are read from the game's
`shaderpacks/` folder (directories with a `shaders/` folder, or `.zip` files).

```properties
enabled=true                              # master switch
activePack=MyPack.zip                     # selected pack
pack.OtherPack.enabled=false              # hide/disable an installed pack
option.MyPack.zip.QUALITY=3               # per-pack option value
```

Options come from the pack's own `#define` lines: `#define X` / `//#define X` are on/off
toggles, and `#define X 2 // [1 2 3]` is a value with the listed choices. Changes are validated
against what the pack declares and applied to the shader source when the pack is loaded.
`ShaderManager` is the entry point (`MetallicIrisMetal.manager()`); an in-game settings screen
still needs to be built on top of it.
