package android.graphics;

/**
 * JVM-only geometry bridge for PDFBox. Android's return-default-values test jar
 * leaves constructor coordinates at zero, invalidating real appearance matrices.
 * This implements the value arithmetic of PointF; it does not emulate rendering.
 */
public class PointF {
    public float x;
    public float y;
    public PointF() { this(0, 0); }
    public PointF(float x, float y) { this.x = x; this.y = y; }
    public void set(float x, float y) { this.x = x; this.y = y; }
    public void set(PointF p) { set(p.x, p.y); }
    public void negate() { x = -x; y = -y; }
    public void offset(float dx, float dy) { x += dx; y += dy; }
    public float length() { return length(x, y); }
    public static float length(float x, float y) { return (float) Math.hypot(x, y); }
    public boolean equals(float x, float y) { return this.x == x && this.y == y; }
    @Override public boolean equals(Object other) {
        return other instanceof PointF && equals(((PointF) other).x, ((PointF) other).y);
    }
    @Override public int hashCode() { return 31 * Float.floatToIntBits(x) + Float.floatToIntBits(y); }
    @Override public String toString() { return "PointF(" + x + ", " + y + ")"; }
}
