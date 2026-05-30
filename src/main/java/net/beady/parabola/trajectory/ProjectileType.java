package net.beady.parabola.trajectory;

public enum ProjectileType {
    BOW(3.0f, 0.05f, 0.99f, 0xFFFFFF00),           // yellow
    CROSSBOW_ARROW(3.15f, 0.05f, 0.99f, 0xFFFF8800), // orange
    CROSSBOW_FIREWORK(1.6f, 0.0f, 1.0f, 0xFFFF3300), // red — special tick physics
    TRIDENT(2.5f, 0.05f, 0.99f, 0xFF00FFFF),          // cyan
    ENDER_PEARL(1.5f, 0.03f, 0.99f, 0xFFAA00FF),      // purple
    SNOWBALL(1.5f, 0.03f, 0.99f, 0xFFFFFFFF),          // white
    EGG(1.5f, 0.03f, 0.99f, 0xFFFFFFFF);              // white

    public final float baseSpeed;
    public final float gravity;
    public final float drag;
    public final int argb;

    ProjectileType(float baseSpeed, float gravity, float drag, int argb) {
        this.baseSpeed = baseSpeed;
        this.gravity = gravity;
        this.drag = drag;
        this.argb = argb;
    }

    public float r() { return ((argb >> 16) & 0xFF) / 255f; }
    public float g() { return ((argb >>  8) & 0xFF) / 255f; }
    public float b() { return  (argb        & 0xFF) / 255f; }
    public float a() { return ((argb >> 24) & 0xFF) / 255f; }
}
