package dev.tokyo.metalliciris.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The text files of a shaderpack's {@code shaders/} folder, held in memory.
 *
 * <p>Paths are relative to the {@code shaders/} folder and always use {@code /} as the
 * separator, e.g. {@code lib/common.glsl} or {@code world-1/composite.fsh}. Only text
 * files are loaded; textures and other binary assets are not needed to parse a pack.
 */
public final class PackFiles {
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("vsh", "fsh", "gsh", "csh", "glsl", "inc", "h", "properties", "txt");

    private final Map<String, String> text;

    private PackFiles(Map<String, String> text) {
        this.text = Collections.unmodifiableMap(new TreeMap<>(text));
    }

    /** Builds a pack from an in-memory map of path to file contents. */
    public static PackFiles of(Map<String, String> files) {
        return new PackFiles(files);
    }

    /** Loads a pack from an unzipped directory that contains a {@code shaders/} folder. */
    public static PackFiles fromDirectory(Path packRoot) {
        Path shaders = packRoot.resolve("shaders");
        if (!Files.isDirectory(shaders)) {
            throw new ShaderPackException("No shaders/ folder in " + packRoot);
        }
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(shaders)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String rel = shaders.relativize(p).toString().replace('\\', '/');
                if (isText(rel)) {
                    out.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new ShaderPackException("Failed to read shaderpack " + packRoot, e);
        }
        return new PackFiles(out);
    }

    /** Loads a pack from a {@code .zip} file that has a {@code shaders/} folder at its root. */
    public static PackFiles fromZip(Path zip) {
        Map<String, String> out = new TreeMap<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            boolean sawShadersFolder = false;
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().replace('\\', '/');
                if (!name.startsWith("shaders/")) {
                    continue;
                }
                sawShadersFolder = true;
                String rel = name.substring("shaders/".length());
                if (e.isDirectory() || rel.isEmpty() || !isText(rel)) {
                    continue;
                }
                try (InputStream in = zf.getInputStream(e)) {
                    out.put(rel, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            if (!sawShadersFolder) {
                throw new ShaderPackException("No shaders/ folder at the root of " + zip);
            }
        } catch (IOException e) {
            throw new ShaderPackException("Failed to read shaderpack " + zip, e);
        }
        return new PackFiles(out);
    }

    public boolean has(String path) {
        return text.containsKey(path);
    }

    /** Returns the file contents, or {@code null} if the file does not exist. */
    public String get(String path) {
        return text.get(path);
    }

    public Set<String> paths() {
        return text.keySet();
    }

    private static boolean isText(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }
}
