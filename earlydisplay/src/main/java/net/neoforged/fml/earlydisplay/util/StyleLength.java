package net.neoforged.fml.earlydisplay.util;

/**
 * A style value that can be used as a position, which can be:
 * <ul>
 * <li>undefined</li>
 * <li>a fixed value</li>
 * <li>a percentage relative to some base value</li>
 * </ul>
 */
public final class StyleLength {
    private static final StyleLength UNDEFINED = new StyleLength(Unit.UNDEFINED, Float.NaN);
    private final Unit unit;
    private final float value;

    private StyleLength(Unit unit, float value) {
        this.unit = unit;
        this.value = value;
    }

    public static StyleLength ofUndefined() {
        return UNDEFINED;
    }

    public static StyleLength ofPoints(float points) {
        if (Float.isNaN(points)) {
            return ofUndefined();
        }
        return new StyleLength(Unit.POINT, points);
    }

    public static StyleLength ofREM(float rem) {
        if (Float.isNaN(rem)) {
            return ofUndefined();
        }
        return new StyleLength(Unit.REM, rem);
    }

    public static StyleLength ofPercent(float percent) {
        if (Float.isNaN(percent)) {
            return ofUndefined();
        }
        return new StyleLength(Unit.PERCENT, percent);
    }

    public Unit unit() {
        return unit;
    }

    public float value() {
        return value;
    }

    @Override
    public String toString() {
        return switch (unit) {
            case UNDEFINED -> "undefined";
            case POINT -> String.valueOf(value);
            case REM -> value + "rem";
            case PERCENT -> value + "%";
        };
    }

    public enum Unit {
        UNDEFINED,
        POINT,
        REM,
        PERCENT
    }
}
