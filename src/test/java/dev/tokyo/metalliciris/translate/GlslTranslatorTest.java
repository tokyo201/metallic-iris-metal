package dev.tokyo.metalliciris.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tokyo.metalliciris.translate.GlslTranslator.Program;
import dev.tokyo.metalliciris.translate.GlslTranslator.Uniform;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlslTranslatorTest {
    private static final String FSH = """
            #version 120
            #extension GL_ARB_shader_texture_lod : enable
            /* DRAWBUFFERS:04 */
            uniform sampler2D colortex0;
            uniform sampler2D depthtex0, noisetex;
            uniform float viewWidth;
            uniform vec3 sunPosition; // uniform vec4 notReal;
            varying vec2 texcoord;
            void main() {
                vec4 c = texture2D(colortex0, texcoord);
                gl_FragData[0] = c * viewWidth;
                gl_FragData[1] = vec4(sunPosition, 1.0);
            }
            """;
    private static final String VSH = """
            #version 120
            uniform float viewWidth;
            varying vec2 texcoord;
            void main() {
                gl_Position = ftransform();
                texcoord = gl_MultiTexCoord0.xy;
            }
            """;

    @Test
    void translatesFragmentAndVertex() {
        Program p = GlslTranslator.translateProgram(VSH, FSH);
        assertEquals(List.of("colortex0", "depthtex0", "noisetex"), p.samplers());
        assertEquals(List.of(new Uniform("float", "viewWidth", 0), new Uniform("vec3", "sunPosition", 0)),
                p.uniforms());
        assertEquals(List.of(0, 4), p.outputs());
        assertTrue(p.fragment().startsWith("#version 330\n"), p.fragment());
        assertTrue(p.fragment().contains("uniform sampler2D colortex0Sampler;"));
        assertTrue(p.fragment().contains("#define colortex0 colortex0Sampler"));
        assertTrue(p.fragment().contains("layout(std140) uniform IrisUniforms"));
        assertTrue(p.fragment().contains("texture(colortex0, texcoord)"));
        assertTrue(p.fragment().contains("iris_FragData1 = vec4"));
        assertTrue(p.fragment().contains("in vec2 texcoord;"));
        assertFalse(p.fragment().contains("notReal"));
        assertFalse(p.fragment().contains("#extension"));
        assertTrue(p.vertex().contains("out vec2 texcoord;"));
        assertTrue(p.vertex().contains("gl_VertexID"));
        assertFalse(p.vertex().contains("ftransform"));
        assertTrue(p.warnings().isEmpty(), p.warnings().toString());
    }

    @Test
    void synthesizesVertexShaderWhenMissing() {
        Program p = GlslTranslator.translateProgram(null, FSH);
        assertTrue(p.vertex().contains("out vec2 texcoord;"), p.vertex());
        assertTrue(p.vertex().contains("texcoord = uv;"), p.vertex());
    }

    @Test
    void handlesGlFragColorAndRendertargets() {
        String f = "/* RENDERTARGETS: 0,3 */\nuniform sampler2D colortex0;\nvarying vec2 uv;\n"
                + "void main(){ gl_FragColor = texture2D(colortex0, uv); }";
        Program p = GlslTranslator.translateProgram(null, f);
        assertEquals(List.of(0, 3), p.outputs());
        assertTrue(p.fragment().contains("layout(location = 0) out vec4 iris_FragData0;"));
    }

    @Test
    void defaultOutputIsTargetZero() {
        Program p = GlslTranslator.translateProgram(null, "varying vec2 t;\nvoid main(){ gl_FragData[0] = vec4(t,0,1); }");
        assertEquals(List.of(0), p.outputs());
    }

    @Test
    void arraysAndDefinedSizes() {
        String f = "#define N 4\nuniform vec4 lights[N];\nuniform mat4 gbufferModelView;\nvarying vec2 t;\n"
                + "void main(){ gl_FragData[0] = lights[1]; }";
        Program p = GlslTranslator.translateProgram(null, f);
        assertEquals(new Uniform("vec4", "lights", 4), p.uniforms().get(0));
        assertTrue(p.fragment().contains("vec4 lights[4];"));
    }

    @Test
    void texCoordBuiltinBecomesVarying() {
        String v = "void main(){ gl_Position = ftransform(); gl_TexCoord[0] = gl_MultiTexCoord0; }";
        String f = "uniform sampler2D tex;\nvoid main(){ gl_FragData[0] = texture2D(tex, gl_TexCoord[0].st); }";
        Program p = GlslTranslator.translateProgram(v, f);
        assertTrue(p.vertex().contains("out vec4 iris_TexCoord0;"), p.vertex());
        assertTrue(p.fragment().contains("in vec4 iris_TexCoord0;"), p.fragment());
    }

    @Test
    void warnsAboutUnsupportedThings() {
        String f = "uniform sampler2DShadow shadowtex;\nvarying vec2 t;\n"
                + "void main(){ gl_FragData[0] = vec4(shadow2D(shadowtex, vec3(t,0)).x); }";
        Program p = GlslTranslator.translateProgram(null, f);
        assertFalse(p.warnings().isEmpty());
    }

    @Test
    void std140Offsets() {
        Std140Layout l = Std140Layout.compute(List.of(
                new Uniform("float", "a", 0), new Uniform("vec3", "b", 0), new Uniform("float", "c", 0),
                new Uniform("vec2", "d", 0), new Uniform("mat4", "m", 0), new Uniform("float", "arr", 3)));
        assertEquals(0, l.find("a").offset());
        assertEquals(16, l.find("b").offset());
        assertEquals(28, l.find("c").offset());
        assertEquals(32, l.find("d").offset());
        assertEquals(48, l.find("m").offset());
        assertEquals(112, l.find("arr").offset());
        assertEquals(160, l.totalSize());
    }
}
