package net.beady.parabola.trajectory;

public enum ProjectileType {
    //                           speed  grav   airDrag waterDrag  rgb
    BOW(              3.0f,  0.05f, 0.99f,  0.6f,  0xFFFF00),
    CROSSBOW_ARROW(   3.15f, 0.05f, 0.99f,  0.6f,  0xFF8800),
    CROSSBOW_FIREWORK(1.6f,  0.0f,  1.0f,   0.6f,  0xFF3300),
    TRIDENT(          2.5f,  0.05f, 0.99f,  -1f,   0x00FFFF),  // -1 = water-exempt
    ENDER_PEARL(      1.5f,  0.03f, 0.99f,  0.8f,  0xAA00FF),
    SNOWBALL(         1.5f,  0.03f, 0.99f,  0.8f,  0xFFFFFF),
    EGG(              1.5f,  0.03f, 0.99f,  0.8f,  0xFFFFFF),
    WIND_CHARGE(      1.5f,  0.01f, 0.99f,  0.8f,  0x88DDFF);

    public final float baseSpeed;
    public final float gravity;
    public final float drag;
    /**
     * Drag applied each tick while the projectile is in water (no gravity applied).
     * -1 means this type is exempt from water slowdown (trident).
     */
    public final float waterDrag;
    public final int defaultRgb;

    ProjectileType(float baseSpeed, float gravity, float drag, float waterDrag, int defaultRgb) {
        this.baseSpeed  = baseSpeed;
        this.gravity    = gravity;
        this.drag       = drag;
        this.waterDrag  = waterDrag;
        this.defaultRgb = defaultRgb;
    }

    public boolean isAffectedByWater() { return waterDrag > 0f; }

    public float r(int rgb) { return ((rgb >> 16) & 0xFF) / 255f; }
    public float g(int rgb) { return ((rgb >>  8) & 0xFF) / 255f; }
    public float b(int rgb) { return  (rgb        & 0xFF) / 255f; }
}
