package net.beady.parabola.trajectory;

public enum ProjectileType {
    BOW(3.0f, 0.05f, 0.99f, 0xFFFF00),
    CROSSBOW_ARROW(3.15f, 0.05f, 0.99f, 0xFF8800),
    CROSSBOW_FIREWORK(1.6f, 0.0f, 1.0f, 0xFF3300),
    TRIDENT(2.5f, 0.05f, 0.99f, 0x00FFFF),
    ENDER_PEARL(1.5f, 0.03f, 0.99f, 0xAA00FF),
    SNOWBALL(1.5f, 0.03f, 0.99f, 0xFFFFFF),
    EGG(1.5f, 0.03f, 0.99f, 0xFFFFFF),
    WIND_CHARGE(1.5f, 0.01f, 0.99f, 0x88DDFF);

    public final float baseSpeed;
    public final float gravity;
    public final float drag;
    /** Default 24-bit RGB color (no alpha). Config can override. */
    public final int defaultRgb;

    ProjectileType(float baseSpeed, float gravity, float drag, int defaultRgb) {
        this.baseSpeed = baseSpeed;
        this.gravity = gravity;
        this.drag = drag;
        this.defaultRgb = defaultRgb;
    }

    public float r(int rgb) { return ((rgb >> 16) & 0xFF) / 255f; }
    public float g(int rgb) { return ((rgb >>  8) & 0xFF) / 255f; }
    public float b(int rgb) { return  (rgb        & 0xFF) / 255f; }
}
