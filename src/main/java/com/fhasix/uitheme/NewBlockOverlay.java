package com.fhasix.uitheme;

import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block Overlay for 26.3.
 *
 * Minecraft 26.3 removed the old 3D world-render events, so this version does not draw in the 3D world at all.
 * It works the same way as Hitbox: the corners of the block you are looking at are projected onto the screen
 * (NewProjection) and the edges and faces are drawn with the normal HUD calls, which work on every renderer
 * (OpenGL and Vulkan). Only the edges and faces that point towards you are drawn, like a real outline.
 */
final class NewBlockOverlay {

    static final String ID = "block_overlay";
    private static final NewProjection PROJ = new NewProjection();
    private static final double NEAR = 0.12;

    private NewBlockOverlay() {
    }

    static void init() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("fhasixuitheme", "block_overlay_hud"),
                NewSafe.hud("block_overlay", (context, tickCounter) -> render(context, tickCounter.getGameTimeDeltaPartialTick(false)))
        );
    }

    private static void render(GuiGraphicsExtractor g, float tickProgress) {
        Minecraft c = Minecraft.getInstance();
        if (c.level == null || c.player == null || c.gui.screen() != null || !NewModSettings.isEnabled(c, ID)) {
            return;
        }
        if (!(c.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!PROJ.begin(c, g, tickProgress)) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockState state = c.level.getBlockState(pos);
        List<AABB> boxes = boxesOf(c, state, pos);
        if (boxes.isEmpty()) {
            return;
        }
        int mode = NewModSettings.opt(c, ID, "mode"); // 0 border, 1 whole block, 2 both
        int border = 0xFF000000 | NewModOptions.paletteColor(NewModSettings.opt(c, ID, "bcolor"));
        int fill = 0xFF000000 | NewModOptions.paletteColor(NewModSettings.opt(c, ID, "fcolor"));
        int bAlpha = Math.max(0, Math.min(100, NewModSettings.opt(c, ID, "balpha"))) * 255 / 100;
        int fAlpha = Math.max(0, Math.min(100, NewModSettings.opt(c, ID, "falpha"))) * 255 / 100;
        int borderArgb = (bAlpha << 24) | (border & 0xFFFFFF);
        int fillArgb = (fAlpha << 24) | (fill & 0xFFFFFF);
        int thick = Math.max(1, Math.min(4, NewModSettings.opt(c, ID, "thick")));

        final double e = 0.002; // a hair outside the block so the lines do not sink into it
        for (AABB b : boxes) {
            double[] lo = {pos.getX() + b.minX - e, pos.getY() + b.minY - e, pos.getZ() + b.minZ - e};
            double[] hi = {pos.getX() + b.maxX + e, pos.getY() + b.maxY + e, pos.getZ() + b.maxZ + e};
            drawBox(g, lo, hi, mode != 1, mode != 0, borderArgb, fillArgb, thick);
        }
    }

    private static Method shapeMethod;
    private static Method boxesMethod;
    private static boolean shapeFailed = false;

    /**
     * The outline boxes of the block. The shape methods are looked up by name once, so a different signature in a newer
     * Minecraft cannot break the build; if they cannot be found the block is outlined as a plain cube.
     */
    @SuppressWarnings("unchecked")
    private static List<AABB> boxesOf(Minecraft c, BlockState state, BlockPos pos) {
        try {
            if (shapeMethod == null) {
                for (Method m : state.getClass().getMethods()) {
                    if (m.getName().equals("getShape") && m.getParameterCount() == 2) {
                        shapeMethod = m;
                        break;
                    }
                }
                boxesMethod = VoxelShape.class.getMethod("toAabbs");
            }
            Object shape = shapeMethod.invoke(state, c.level, pos);
            return (List<AABB>) boxesMethod.invoke(shape);
        } catch (Exception | LinkageError e) {
            if (!shapeFailed) {
                shapeFailed = true;
                NewSafe.failed("block_overlay", e);
            }
            return List.of(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        }
    }

    /** One axis-aligned box: visible faces (fill) and the edges that border a visible face (border). */
    private static void drawBox(GuiGraphicsExtractor g, double[] lo, double[] hi, boolean border, boolean fill,
                                int borderArgb, int fillArgb, int thick) {
        double ex = PROJ.eyeX();
        double ey = PROJ.eyeY();
        double ez = PROJ.eyeZ();
        // visible[axis*2 + side]: side 0 = the low face of that axis, 1 = the high face
        boolean[] visible = new boolean[6];
        visible[0] = ex < lo[0];
        visible[1] = ex > hi[0];
        visible[2] = ey < lo[1];
        visible[3] = ey > hi[1];
        visible[4] = ez < lo[2];
        visible[5] = ez > hi[2];

        if (fill) {
            for (int axis = 0; axis < 3; axis++) {
                for (int side = 0; side < 2; side++) {
                    if (visible[axis * 2 + side]) {
                        fillFace(g, lo, hi, axis, side, fillArgb);
                    }
                }
            }
        }
        if (border) {
            // 12 edges: 4 along each axis. An edge is drawn when at least one of its two faces is visible.
            for (int axis = 0; axis < 3; axis++) {
                int a1 = (axis + 1) % 3;
                int a2 = (axis + 2) % 3;
                for (int s1 = 0; s1 < 2; s1++) {
                    for (int s2 = 0; s2 < 2; s2++) {
                        if (!visible[a1 * 2 + s1] && !visible[a2 * 2 + s2]) {
                            continue;
                        }
                        double[] p0 = new double[3];
                        double[] p1 = new double[3];
                        p0[axis] = lo[axis];
                        p1[axis] = hi[axis];
                        p0[a1] = p1[a1] = s1 == 0 ? lo[a1] : hi[a1];
                        p0[a2] = p1[a2] = s2 == 0 ? lo[a2] : hi[a2];
                        line3d(g, p0, p1, borderArgb, thick);
                    }
                }
            }
        }
    }

    private static void fillFace(GuiGraphicsExtractor g, double[] lo, double[] hi, int axis, int side, int argb) {
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        double[][] corner = new double[4][3];
        int[][] order = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            corner[i][axis] = side == 0 ? lo[axis] : hi[axis];
            corner[i][a1] = order[i][0] == 0 ? lo[a1] : hi[a1];
            corner[i][a2] = order[i][1] == 0 ? lo[a2] : hi[a2];
        }
        // clip the quad against the near plane (Sutherland-Hodgman, one plane)
        double[][] poly = new double[8][];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            double[] cur = corner[i];
            double[] nxt = corner[(i + 1) % 4];
            double dc = PROJ.depth(cur[0], cur[1], cur[2]) - NEAR;
            double dn = PROJ.depth(nxt[0], nxt[1], nxt[2]) - NEAR;
            if (dc >= 0) {
                poly[n++] = cur;
            }
            if ((dc >= 0) != (dn >= 0)) {
                double t = dc / (dc - dn);
                poly[n++] = new double[]{cur[0] + (nxt[0] - cur[0]) * t, cur[1] + (nxt[1] - cur[1]) * t, cur[2] + (nxt[2] - cur[2]) * t};
            }
        }
        if (n < 3) {
            return;
        }
        int[] xs = new int[n];
        int[] ys = new int[n];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int[] pt = PROJ.projectLoose(poly[i][0], poly[i][1], poly[i][2]);
            if (pt == null) {
                return;
            }
            xs[i] = pt[0];
            ys[i] = pt[1];
            minY = Math.min(minY, pt[1]);
            maxY = Math.max(maxY, pt[1]);
        }
        int sh = g.guiHeight();
        int sw = g.guiWidth();
        minY = Math.max(minY, 0);
        maxY = Math.min(maxY, sh - 1);
        for (int y = minY; y <= maxY; y++) {
            double left = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            double yc = y + 0.5;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double y0 = ys[i];
                double y1 = ys[j];
                if ((yc >= y0 && yc < y1) || (yc >= y1 && yc < y0)) {
                    double x = xs[i] + (xs[j] - xs[i]) * (yc - y0) / (y1 - y0);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (left <= right) {
                int x0 = Math.max(0, (int) Math.floor(left));
                int x1 = Math.min(sw, (int) Math.ceil(right));
                if (x1 > x0) {
                    g.fill(x0, y, x1, y + 1, argb);
                }
            }
        }
    }

    /** A 3D line segment, clipped against the near plane, drawn as a thick 2D line. */
    private static void line3d(GuiGraphicsExtractor g, double[] a, double[] b, int argb, int thick) {
        double da = PROJ.depth(a[0], a[1], a[2]) - NEAR;
        double db = PROJ.depth(b[0], b[1], b[2]) - NEAR;
        if (da < 0 && db < 0) {
            return;
        }
        double[] p = a;
        double[] q = b;
        if (da < 0) {
            double t = da / (da - db);
            p = new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        } else if (db < 0) {
            double t = da / (da - db);
            q = new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        }
        int[] s = PROJ.projectLoose(p[0], p[1], p[2]);
        int[] t2 = PROJ.projectLoose(q[0], q[1], q[2]);
        if (s == null || t2 == null) {
            return;
        }
        line2d(g, s[0], s[1], t2[0], t2[1], argb, thick);
    }

    private static void line2d(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int argb, int thick) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        if (dx > 4000 || dy > 4000) {
            return;
        }
        int off = thick / 2;
        if (dx >= dy) {
            if (x0 > x1) {
                int tx = x0; x0 = x1; x1 = tx;
                int ty = y0; y0 = y1; y1 = ty;
            }
            int span = Math.max(1, x1 - x0);
            int runStart = x0;
            int runY = y0;
            for (int x = x0; x <= x1; x++) {
                int y = y0 + Math.round((float) (y1 - y0) * (x - x0) / span);
                if (y != runY) {
                    g.fill(runStart, runY - off, x, runY - off + thick, argb);
                    runStart = x;
                    runY = y;
                }
            }
            g.fill(runStart, runY - off, x1 + 1, runY - off + thick, argb);
        } else {
            if (y0 > y1) {
                int tx = x0; x0 = x1; x1 = tx;
                int ty = y0; y0 = y1; y1 = ty;
            }
            int span = Math.max(1, y1 - y0);
            int runStart = y0;
            int runX = x0;
            for (int y = y0; y <= y1; y++) {
                int x = x0 + Math.round((float) (x1 - x0) * (y - y0) / span);
                if (x != runX) {
                    g.fill(runX - off, runStart, runX - off + thick, y, argb);
                    runStart = y;
                    runX = x;
                }
            }
            g.fill(runX - off, runStart, runX - off + thick, y1 + 1, argb);
        }
    }
}
