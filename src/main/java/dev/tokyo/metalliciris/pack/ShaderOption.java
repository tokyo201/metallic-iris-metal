package dev.tokyo.metalliciris.pack;

import java.util.List;

/**
 * A user-adjustable option declared by a shaderpack through a {@code #define}.
 *
 * <p>A {@code BOOLEAN} option is a bare define that is on ({@code #define X}) or commented out
 * ({@code //#define X}). A {@code VALUE} option carries a value ({@code #define X 1.0}) and
 * usually lists the allowed values in a trailing comment such as {@code // [0.5 1.0 2.0]}.
 */
public record ShaderOption(
        String name,
        Type type,
        String defaultValue,
        List<String> allowedValues,
        String file) {

    public enum Type { BOOLEAN, VALUE }

    /** Whether {@code value} is acceptable for this option. */
    public boolean accepts(String value) {
        if (value == null) {
            return false;
        }
        if (type == Type.BOOLEAN) {
            return value.equals("true") || value.equals("false");
        }
        return allowedValues.isEmpty() || allowedValues.contains(value);
    }
}
