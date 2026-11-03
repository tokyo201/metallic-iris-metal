# metallic-iris-metal
Clean-room Fabric mod prototype to run Iris shaderpacks on Apple Metal 3

## Status

Clean-room implementation, written from the publicly documented shaderpack format. It does not
use Iris or Sodium code and does not need either mod installed.

| Stage | State |
| --- | --- |
| 1. Shaderpack loader (folder/zip, `shaders.properties`, `#include`, program discovery) | done, unit tested |
| 2. GLSL to MSL (glslang + SPIRV-Cross through Java's FFM API) | planned |
| 3. Metal backend (device, pipelines, render targets via FFM) | planned |
| 4. Minecraft hookup (gbuffers/composite/final passes in the render loop) | planned |

Stages 2-4 need a Mac with Metal to test; CI only covers stage 1.
