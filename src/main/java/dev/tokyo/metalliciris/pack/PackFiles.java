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

    /**
     * Finds the folder that holds {@code shaders/}: the pack root itself, or a single level below
     * it (packs are often unzipped into a folder that contains another folder).
     */
    static Path findPackRoot(Path dir) {
        if (Files.isDirectory(dir.resolve("shaders"))) {
            return dir;
        }
        try (Stream<Path> kids = Files.list(dir)) {
            for (Path k : (Iterable<Path>) kids.sorted()::iterator) {
                if (Files.isDirectory(k) && Files.isDirectory(k.resolve("shaders"))) {
                    return k;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Whether a folder or {@code .zip} looks like a shaderpack (a shaders/ folder at most one level down). */
    public static boolean looksLikePack(Path path) {
        if (Files.isDirectory(path)) {
            return findPackRoot(path) != null;
        }
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try (ZipFile zf = new ZipFile(path.toFile())) {
            return zipPrefix(zf) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** Loads a pack from an unzipped directory that contains a {@code shaders/} folder. */
    public static PackFiles fromDirectory(Path packRoot) {
        Path root = findPackRoot(packRoot);
        if (root == null) {
            throw new ShaderPackException("No shaders/ folder in " + packRoot);
        }
        Path shaders = root.resolve("shaders");
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

    /**
     * The path inside the zip that precedes {@code shaders/}: empty for a root-level folder, or
     * e.g. {@code MyPack/} when the pack is wrapped in one folder. Null if there is none.
     */
    private static String zipPrefix(ZipFile zf) {
        String best = null;
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName().replace('\\', '/');
            int i = name.indexOf("shaders/");
            if (i == 0 || (i > 0 && name.charAt(i - 1) == '/')) {
                String prefix = name.substring(0, i);
                if (prefix.chars().filter(c -> c == '/').count() <= 1
                        && (best == null || prefix.length() < best.length())) {
                    best = prefix;
                }
            }
        }
        return best;
    }

    /** Loads a pack from a {@code .zip}; {@code shaders/} may be at the root or inside one wrapper folder. */
    public static PackFiles fromZip(Path zip) {
        Map<String, String> out = new TreeMap<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            String prefix = zipPrefix(zf);
            if (prefix == null) {
                throw new ShaderPackException("No shaders/ folder in " + zip);
            }
            String base = prefix + "shaders/";
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().replace('\\', '/');
                if (!name.startsWith(base)) {
                    continue;
                }
                String rel = name.substring(base.length());
                if (e.isDirectory() || rel.isEmpty() || !isText(rel)) {
                    continue;
                }
                try (InputStream in = zf.getInputStream(e)) {
                    out.put(rel, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
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
