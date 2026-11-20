package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.Holder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-game side of the Mod Menu:
 *  - registers the Right Shift key (opens NewLauncherScreen while in a world),
 *  - draws the HUD mods: FPS Display, CPS Display, Armor Status, Armor Bar, Block Indicator,
 *  - counts left clicks for CPS,
 *  - starts the Block Overlay (world rendering lives in NewBlockOverlay).
 *
 * Every HUD mod has the same "Shadow" options (a rounded dark box behind it: on/off, roundness,
 * opacity) plus its own; see NewModOptions. HUD text has NO text shadow unless the player turns
 * "Text shadow" on. Positions are dragged in the launcher and saved by NewModSettings.
 */
public final class NewHud {

    public static final String FPS = "fps_display";
    public static final String CPS = "cps_display";
    public static final String ARMOR_STATUS = "armor_status";
    public static final String ARMOR_BAR = "armor_bar";
    public static final String BLOCK_INDICATOR = "block_indicator";
    public static final String ATTACK_INDICATOR = "attack_indicator";
    public static final String DIRECTION = "direction";
    public static final String PING = "ping";
    public static final String SERVER_ADDRESS = "server_address";
    public static final String PLAYTIME = "playtime";
    public static final String COMBO = "combo_display";
    public static final String REACH = "reach_display";
    public static final String PACK = "pack_display";

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int GREEN = 0xFF3FBF4D;
    private static final int YELLOW = 0xFFFFD43B;
    private static final int RED = 0xFFE73C40;

    private static KeyMapping openKey;
    /** Shared by every Fhasix key binding (Right Shift launcher, Freelook). */
    static KeyMapping.Category CATEGORY;
    private static final ArrayDeque<Long> CLICKS = new ArrayDeque<>();
    private static boolean leftWasDown = false;

    private NewHud() {
    }

    // ------------------------------------------------------------------ elements

    /** One HUD mod: knows its size and how to draw itself at a position. */
    abstract static class El {
        final String id;
        final String safeKey;
        final float defX;
        final float defY;

        El(String id, float defX, float defY) {
            this.id = id;
            this.safeKey = "hud_" + id;
            this.defX = defX;
            this.defY = defY;
        }

        /** {width, height}, or null when there is nothing to draw right now. */
        abstract int[] size(Minecraft c, boolean preview);

        abstract void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview);
    }

    /** One drawn HUD element: its rectangle (scaled GUI pixels) and the element that draws it. */
    public static final class AABB {
        public final String id;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        final El el;

        AABB(El el, int x, int y, int w, int h) {
            this.id = el.id;
            this.el = el;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // --- shared style helpers

    private static void shadowBox(GuiGraphicsExtractor g, Minecraft c, String id, int x, int y, int w, int h) {
        if (!NewModSettings.optBool(c, id, "bg")) {
            return;
        }
        int a = Math.round(255f * NewModSettings.opt(c, id, "bgalpha") / 100f) & 255;
        NewUi.rrect(g, x, y, w, h, NewModSettings.opt(c, id, "round"),
                (a << 24) | 0x0C0D11, (a << 24) | 0x08090C);
    }

    private static int textColor(Minecraft c, String id) {
        return NewModOptions.paletteColor(NewModSettings.opt(c, id, "color"));
    }

    private static boolean textShadow(Minecraft c, String id) {
        return NewModSettings.optBool(c, id, "tshadow");
    }

    private static int durabilityColor(float frac) {
        frac = Math.max(0f, Math.min(1f, frac));
        return frac > 0.5f ? NewUi.lerp(YELLOW, GREEN, (frac - 0.5f) * 2f) : NewUi.lerp(RED, YELLOW, frac * 2f);
    }

    private static float durabilityFraction(ItemStack s) {
        int max = s.getMaxDamage();
        return max <= 0 ? 1f : (max - s.getDamageValue()) / (float) max;
    }

    private static List<ItemStack> sampleArmor;

    /** Worn armor pieces (in slot order); the sample set in preview (launcher open) when none are worn. */
    private static List<ItemStack> armorPieces(Minecraft c, boolean preview, boolean keepEmpty) {
        List<ItemStack> out = new ArrayList<>(4);
        boolean any = false;
        if (c.player != null) {
            for (EquipmentSlot s : ARMOR) {
                ItemStack st = c.player.getItemBySlot(s);
                if (!st.isEmpty()) {
                    any = true;
                }
                if (keepEmpty || !st.isEmpty()) {
                    out.add(st);
                }
            }
        }
        if (!any && preview) {
            if (sampleArmor == null) {
                sampleArmor = new ArrayList<>(4);
                Item[] items = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
                int[] damage = {60, 140, 90, 20};
                for (int i = 0; i < items.length; i++) {
                    ItemStack st = new ItemStack(items[i]);
                    st.setDamageValue(damage[i]);
                    sampleArmor.add(st);
                }
            }
            return sampleArmor;
        }
        return any ? out : new ArrayList<>();
    }

    // --- FPS / CPS

    private static final class Counter extends El {
        private final boolean fps;

        Counter(String id, boolean fps, float defX, float defY) {
            super(id, defX, defY);
            this.fps = fps;
        }

        private String text(Minecraft c) {
            String n = fps ? String.valueOf(c.getFps()) : String.valueOf(cps());
            if (NewModSettings.optBool(c, id, "label")) {
                return n + (fps ? " FPS" : " CPS");
            }
            return n;
        }

        @Override
        int[] size(Minecraft c, boolean preview) {
            return new int[]{c.font.width(text(c)) + 10, c.font.lineHeight + 6};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            g.text(c.font, text(c), x + 5, y + 3, textColor(c, id), textShadow(c, id));
        }
    }

    // --- Armor Status: worn pieces (+ held weapon/tool) with durability, percentage bar, enchantments

    private static ItemStack sampleSword;

    private static final class ArmorStatus extends El {
        ArmorStatus(float defX, float defY) {
            super(ARMOR_STATUS, defX, defY);
        }

        private boolean wearingArmor(Minecraft c) {
            if (c.player == null) {
                return false;
            }
            for (EquipmentSlot s : ARMOR) {
                if (!c.player.getItemBySlot(s).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private List<ItemStack> stacks(Minecraft c, boolean preview) {
            List<ItemStack> l = new ArrayList<>(armorPieces(c, preview, false));
            if (NewModSettings.optBool(c, id, "held")) {
                ItemStack held = c.player != null ? c.player.getMainHandItem() : ItemStack.EMPTY;
                if (!held.isEmpty() && held.getMaxDamage() > 0) {
                    l.add(held);
                } else if (preview && !wearingArmor(c)) {
                    if (sampleSword == null) {
                        sampleSword = new ItemStack(Items.DIAMOND_SWORD);
                        sampleSword.setDamageValue(100);
                    }
                    l.add(sampleSword);
                }
            }
            return l;
        }

        /** Short enchantment list like "Prot IV Unbr III" (up to 3). */
        private String enchantments(ItemStack s) {
            ItemEnchantments comp = s.getEnchantments();
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Holder<Enchantment> e : comp.keySet()) {
                if (n++ >= 3) {
                    break;
                }
                String[] words = Enchantment.getFullname(e, comp.getLevel(e)).getString().split(" ");
                String name = words[0].length() > 4 ? words[0].substring(0, 4) : words[0];
                String level = words.length > 1 ? " " + words[words.length - 1] : "";
                sb.append(sb.length() == 0 ? "" : "  ").append(name).append(level);
            }
            return sb.toString();
        }

        private String label(Minecraft c, ItemStack s) {
            int max = s.getMaxDamage();
            String dur;
            if (max <= 0) {
                dur = "-";
            } else {
                int left = max - s.getDamageValue();
                dur = NewModSettings.opt(c, id, "mode") == 1 ? Math.round(100f * left / max) + "%" : String.valueOf(left);
            }
            if (NewModSettings.optBool(c, id, "ench")) {
                String e = enchantments(s);
                if (!e.isEmpty()) {
                    return dur + "  " + e;
                }
            }
            return dur;
        }

        private int rowH(Minecraft c) {
            return NewModSettings.optBool(c, id, "pbar") ? 21 : 18;
        }

        @Override
        int[] size(Minecraft c, boolean preview) {
            List<ItemStack> l = stacks(c, preview);
            if (l.isEmpty()) {
                return null;
            }
            boolean icons = NewModSettings.optBool(c, id, "icons");
            int maxW = 0;
            for (ItemStack s : l) {
                maxW = Math.max(maxW, c.font.width(label(c, s)));
            }
            return new int[]{8 + (icons ? 20 : 0) + maxW, 8 + l.size() * rowH(c) - 2};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            boolean icons = NewModSettings.optBool(c, id, "icons");
            boolean byDur = NewModSettings.optBool(c, id, "colordur");
            boolean pbar = NewModSettings.optBool(c, id, "pbar");
            int base = textColor(c, id);
            boolean ts = textShadow(c, id);
            int rowH = rowH(c);
            int yy = y + 4;
            for (ItemStack s : stacks(c, preview)) {
                if (icons) {
                    g.item(s, x + 4, yy);
                }
                float frac = durabilityFraction(s);
                int col = byDur && s.getMaxDamage() > 0 ? durabilityColor(frac) : base;
                g.text(c.font, label(c, s), x + 4 + (icons ? 20 : 0), yy + 4, col, ts);
                if (pbar && s.getMaxDamage() > 0) {
                    g.fill(x + 4, yy + 17, x + w - 4, yy + 19, 0x66000000);
                    g.fill(x + 4, yy + 17, x + 4 + Math.round((w - 8) * frac), yy + 19, durabilityColor(frac));
                }
                yy += rowH;
            }
        }
    }

    // --- Armor Bar: compact row/column of the four slots with a thin durability bar

    private static final class ArmorBar extends El {
        ArmorBar(float defX, float defY) {
            super(ARMOR_BAR, defX, defY);
        }

        @Override
        int[] size(Minecraft c, boolean preview) {
            List<ItemStack> l = armorPieces(c, preview, true);
            boolean any = false;
            for (ItemStack s : l) {
                any |= !s.isEmpty();
            }
            if (!any) {
                return null;
            }
            int slotH = NewModSettings.optBool(c, id, "bar") ? 24 : 20;
            if (NewModSettings.opt(c, id, "layout") == 1) {
                return new int[]{8 + 20, 8 + 4 * slotH + 3 * 2};
            }
            return new int[]{8 + 4 * 20 + 3 * 2, 8 + slotH};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            boolean bar = NewModSettings.optBool(c, id, "bar");
            boolean vertical = NewModSettings.opt(c, id, "layout") == 1;
            int slotH = bar ? 24 : 20;
            List<ItemStack> l = armorPieces(c, preview, true);
            for (int i = 0; i < l.size(); i++) {
                ItemStack s = l.get(i);
                int sx = vertical ? x + 4 : x + 4 + i * 22;
                int sy = vertical ? y + 4 + i * (slotH + 2) : y + 4;
                if (s.isEmpty()) {
                    g.fill(sx + 2, sy + 2, sx + 18, sy + 18, 0x14FFFFFF);
                    continue;
                }
                g.item(s, sx + 2, sy + 2);
                if (bar && s.getMaxDamage() > 0) {
                    g.fill(sx + 2, sy + 19, sx + 18, sy + 21, 0x66000000);
                    int fw = Math.round(16f * durabilityFraction(s));
                    g.fill(sx + 2, sy + 19, sx + 2 + fw, sy + 21, durabilityColor(durabilityFraction(s)));
                }
            }
        }
    }

    // --- Block Indicator: name (and icon) of the block you are looking at

    private static final class BlockIndicator extends El {
        private Block lastBlock;
        private ItemStack lastStack = ItemStack.EMPTY;
        private String lastName = "";
        private ItemStack dirt;

        BlockIndicator(float defX, float defY) {
            super(BLOCK_INDICATOR, defX, defY);
        }

        /** Fills lastName / lastStack. False when there is nothing to show. */
        private boolean resolve(Minecraft c, boolean preview) {
            if (c.level != null && c.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK) {
                Block b = c.level.getBlockState(bhr.getBlockPos()).getBlock();
                if (b != lastBlock) {
                    lastBlock = b;
                    lastName = b.getName().getString();
                    lastStack = new ItemStack(b.asItem());
                }
                return true;
            }
            if (preview) {
                if (dirt == null) {
                    dirt = new ItemStack(Items.DIRT);
                }
                lastBlock = null;
                lastName = "Dirt Block";
                lastStack = dirt;
                return true;
            }
            return false;
        }

        @Override
        int[] size(Minecraft c, boolean preview) {
            if (!resolve(c, preview)) {
                return null;
            }
            boolean icon = NewModSettings.optBool(c, id, "icon") && !lastStack.isEmpty();
            return new int[]{8 + (icon ? 20 : 0) + c.font.width(lastName), icon ? 24 : c.font.lineHeight + 6};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            if (!resolve(c, preview)) {
                return;
            }
            shadowBox(g, c, id, x, y, w, h);
            boolean icon = NewModSettings.optBool(c, id, "icon") && !lastStack.isEmpty();
            if (icon) {
                g.item(lastStack, x + 4, y + (h - 16) / 2);
            }
            g.text(c.font, lastName, x + 4 + (icon ? 20 : 0), y + (h - c.font.lineHeight) / 2 + 1,
                    textColor(c, id), textShadow(c, id));
        }
    }

    // --- Attack Indicator: by default the NORMAL vanilla crosshair indicator is used (this element
    // draws nothing). Choosing "Fhasix meter" in the settings shows this movable bar instead and
    // hides the vanilla indicator (and restores it when you switch back / disable the mod).

    private static final class AttackIndicatorEl extends El {
        AttackIndicatorEl(float defX, float defY) {
            super(ATTACK_INDICATOR, defX, defY);
        }

        /** Charge of the item being used (bow, crossbow, trident, eating...) or the weapon cooldown. */
        private float progress(Minecraft c) {
            if (c.player == null) {
                return 1f;
            }
            if (NewModSettings.optBool(c, id, "charge") && c.player.isUsingItem()) {
                ItemStack s = c.player.getUseItem();
                int t = c.player.getTicksUsingItem();
                if (s.is(Items.BOW)) {
                    return BowItem.getPowerForTime(t);
                }
                if (s.is(Items.CROSSBOW)) {
                    return Math.min(1f, t / (float) Math.max(1, CrossbowItem.getChargeDuration(s, c.player)));
                }
                if (s.is(Items.TRIDENT)) {
                    return Math.min(1f, t / 10f);
                }
                int max = s.getUseDuration(c.player);
                if (max > 0 && max < 72000) {
                    return Math.min(1f, t / (float) max);
                }
            }
            return Math.max(0f, Math.min(1f, c.player.getAttackStrengthScale(0f)));
        }

        @Override
        int[] size(Minecraft c, boolean preview) {
            if (NewModSettings.opt(c, id, "style") != 1) {
                return null; // vanilla crosshair indicator is in use
            }
            boolean bar = NewModSettings.optBool(c, id, "bar");
            boolean pct = NewModSettings.optBool(c, id, "percent");
            if (!bar && !pct) {
                bar = true;
            }
            int bw = bar ? NewModSettings.opt(c, id, "width") : 0;
            int tw = pct ? c.font.width("100%") : 0;
            return new int[]{10 + bw + (bar && pct ? 6 : 0) + tw, c.font.lineHeight + 6};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            boolean bar = NewModSettings.optBool(c, id, "bar");
            boolean pct = NewModSettings.optBool(c, id, "percent");
            if (!bar && !pct) {
                bar = true;
            }
            float p = progress(c);
            int cx = x + 5;
            if (bar) {
                int bw = NewModSettings.opt(c, id, "width");
                int by = y + (h - 6) / 2;
                int col = NewModOptions.paletteColor(NewModSettings.opt(c, id, p >= 0.999f ? "fullcolor" : "color"));
                g.fill(cx, by, cx + bw, by + 6, 0x66000000);
                g.fill(cx, by, cx + Math.round(bw * p), by + 6, col);
                cx += bw + 6;
            }
            if (pct) {
                g.text(c.font, Math.round(p * 100f) + "%", cx, y + 3, 0xFFFFFFFF, false);
            }
        }
    }

    /**
     * Charge of a bow / crossbow / trident being drawn (0..1), or -1 when none is. Used for the small
     * bar under the crosshair, which works like the vanilla sword cooldown indicator.
     */
    private static float weaponCharge(Minecraft c) {
        if (c.player == null || !c.player.isUsingItem()) {
            return -1f;
        }
        ItemStack s = c.player.getUseItem();
        int t = c.player.getTicksUsingItem();
        if (s.is(Items.BOW)) {
            return BowItem.getPowerForTime(t);
        }
        if (s.is(Items.CROSSBOW)) {
            return Math.min(1f, t / (float) Math.max(1, CrossbowItem.getChargeDuration(s, c.player)));
        }
        if (s.is(Items.TRIDENT)) {
            return Math.min(1f, t / 10f);
        }
        return -1f;
    }

    /** Attack Indicator, vanilla-crosshair style: bow / crossbow / trident charge bar under the crosshair. */
    private static void drawCrosshairCharge(GuiGraphicsExtractor g, Minecraft c) {
        if (c.player == null || c.gui.screen() != null || !c.options.getCameraType().isFirstPerson()
                || !NewModSettings.isEnabled(c, ATTACK_INDICATOR)
                || NewModSettings.opt(c, ATTACK_INDICATOR, "style") != 0
                || !NewModSettings.optBool(c, ATTACK_INDICATOR, "charge")) {
            return;
        }
        float p = weaponCharge(c);
        if (p < 0f) {
            return;
        }
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int col = NewModOptions.paletteColor(NewModSettings.opt(c, ATTACK_INDICATOR, p >= 0.999f ? "fullcolor" : "color"));
        g.fill(cx - 9, cy + 8, cx + 9, cy + 13, 0xAA000000);
        g.fill(cx - 8, cy + 9, cx + 8, cy + 12, 0xFF3A3A3A);
        g.fill(cx - 8, cy + 9, cx - 8 + Math.round(16f * Math.max(0f, Math.min(1f, p))), cy + 12, col);
    }

    /**
     * Keeps the vanilla crosshair attack indicator OFF while the Fhasix meter is the chosen style, and
     * puts the player's original setting back afterwards. The original value is remembered in the mod
     * settings (key "_prev", ordinal + 1) so it survives a restart.
     */
    private static void syncVanillaIndicator(Minecraft c) {
        if (c.options == null) {
            return;
        }
        boolean meter = NewModSettings.isEnabled(c, ATTACK_INDICATOR) && NewModSettings.opt(c, ATTACK_INDICATOR, "style") == 1;
        int prev = NewModSettings.opt(c, ATTACK_INDICATOR, "_prev");
        if (meter && prev == 0) {
            AttackIndicatorStatus current = c.options.attackIndicator().get();
            if (current != AttackIndicatorStatus.OFF) {
                NewModSettings.setOpt(c, ATTACK_INDICATOR, "_prev", current.ordinal() + 1);
                NewModSettings.save(c);
                c.options.attackIndicator().set(AttackIndicatorStatus.OFF);
                c.options.save();
            }
        } else if (!meter && prev > 0) {
            AttackIndicatorStatus[] all = AttackIndicatorStatus.values();
            c.options.attackIndicator().set(all[Math.min(all.length - 1, prev - 1)]);
            c.options.save();
            NewModSettings.setOpt(c, ATTACK_INDICATOR, "_prev", 0);
            NewModSettings.save(c);
        }
    }

    // --- Direction: compass direction you are facing (N, NE, E ...) and optionally the bearing

    private static final class Direction extends El {
        private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

        Direction(float defX, float defY) {
            super(DIRECTION, defX, defY);
        }

        private String text(Minecraft c) {
            float yaw = c.player == null ? 0f : c.player.getYRot();
            float bearing = ((yaw + 180f) % 360f + 360f) % 360f; // Minecraft yaw 0 = south, so +180 makes 0 = north
            String s = NAMES[Math.round(bearing / 45f) % 8];
            if (NewModSettings.optBool(c, id, "degrees")) {
                s += " " + Math.round(bearing) + "\u00B0";
            }
            return s;
        }

        @Override
        int[] size(Minecraft c, boolean preview) {
            return new int[]{c.font.width(text(c)) + 10, c.font.lineHeight + 6};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            g.text(c.font, text(c), x + 5, y + 3, textColor(c, id), textShadow(c, id));
        }
    }

    // --- Ping / Server Address / Playtime / Combo / Reach / Pack Display: one line of text in a box

    private static final long SESSION_START = System.currentTimeMillis();
    private static int combo;
    private static long lastHitMs;
    private static double lastReach = -1.0;
    private static int lastHurtTime;

    /** A hit on something living: grows the combo and remembers how far away the target was. */
    static void onAttack(net.minecraft.world.entity.player.Player p, net.minecraft.world.entity.Entity e) {
        if (!(e instanceof net.minecraft.world.entity.LivingEntity)) {
            return;
        }
        combo++;
        lastHitMs = System.currentTimeMillis();
        net.minecraft.world.phys.AABB b = e.getBoundingBox();
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        double dx = Math.max(Math.max(b.minX - eye.x, 0.0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0.0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0.0), eye.z - b.maxZ);
        lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The combo resets when you get hurt or stop hitting for 3 seconds. */
    private static void tickStats(Minecraft c) {
        if (c.player == null) {
            combo = 0;
            lastHurtTime = 0;
            return;
        }
        int hurt = c.player.hurtTime;
        if (hurt > lastHurtTime) {
            combo = 0;
        }
        lastHurtTime = hurt;
        if (combo > 0 && System.currentTimeMillis() - lastHitMs > 3000L) {
            combo = 0;
        }
    }

    private abstract static class TextEl extends El {
        TextEl(String id, float defX, float defY) {
            super(id, defX, defY);
        }

        /** The text to show, or null for nothing. */
        abstract String text(Minecraft c, boolean preview);

        @Override
        int[] size(Minecraft c, boolean preview) {
            String t = text(c, preview);
            if (t == null) {
                return null;
            }
            return new int[]{c.font.width(t) + 10, c.font.lineHeight + 6};
        }

        @Override
        void draw(GuiGraphicsExtractor g, Minecraft c, int x, int y, int w, int h, boolean preview) {
            String t = text(c, preview);
            if (t == null) {
                return;
            }
            shadowBox(g, c, id, x, y, w, h);
            g.text(c.font, t, x + 5, y + 3, textColor(c, id), textShadow(c, id));
        }
    }

    private static final class PingEl extends TextEl {
        PingEl(float x, float y) {
            super(PING, x, y);
        }

        @Override
        String text(Minecraft c, boolean preview) {
            int ms = 0;
            net.minecraft.client.multiplayer.ClientPacketListener h = c.getConnection();
            if (h != null && c.player != null) {
                net.minecraft.client.multiplayer.PlayerInfo e = h.getPlayerInfo(c.player.getUUID());
                if (e != null) {
                    ms = e.getLatency();
                }
            } else if (!preview) {
                return null;
            } else {
                ms = 42;
            }
            return NewModSettings.optBool(c, id, "label") ? ms + " ms" : String.valueOf(ms);
        }
    }

    private static final class ServerAddressEl extends TextEl {
        ServerAddressEl(float x, float y) {
            super(SERVER_ADDRESS, x, y);
        }

        @Override
        String text(Minecraft c, boolean preview) {
            net.minecraft.client.multiplayer.ServerData si = c.getCurrentServer();
            String a = si != null ? si.ip : (c.isLocalServer() ? "Singleplayer" : (preview ? "play.fhasix.net" : null));
            if (a == null) {
                return null;
            }
            return NewModSettings.optBool(c, id, "label") ? "Server: " + a : a;
        }
    }

    private static final class PlaytimeEl extends TextEl {
        PlaytimeEl(float x, float y) {
            super(PLAYTIME, x, y);
        }

        @Override
        String text(Minecraft c, boolean preview) {
            long s = (System.currentTimeMillis() - SESSION_START) / 1000L;
            if (NewModSettings.optBool(c, id, "seconds")) {
                return String.format(java.util.Locale.ROOT, "%02dh %02dm %02ds", s / 3600, (s / 60) % 60, s % 60);
            }
            return String.format(java.util.Locale.ROOT, "%02dh %02dm", s / 3600, (s / 60) % 60);
        }
    }

    private static final class ComboEl extends TextEl {
        ComboEl(float x, float y) {
            super(COMBO, x, y);
        }

        @Override
        String text(Minecraft c, boolean preview) {
            int n = preview && combo == 0 ? 7 : combo;
            return NewModSettings.optBool(c, id, "label") ? n + " Combo" : String.valueOf(n);
        }
    }

    private static final class ReachEl extends TextEl {
        ReachEl(float x, float y) {
            super(REACH, x, y);
        }

        @Override
        String text(Minecraft c, boolean preview) {
            double r = preview && lastReach < 0 ? 3.12 : Math.max(0.0, lastReach);
            String v = String.format(java.util.Locale.ROOT, "%.2f", r);
            return NewModSettings.optBool(c, id, "label") ? v + " blocks" : v;
        }
    }

    private static final class PackEl extends TextEl {
        PackEl(float x, float y) {
            super(PACK, x, y);
        }

        @Override
        String text(Minecraft c, boolean preview) {
            String name = null;
            try {
                for (net.minecraft.server.packs.repository.Pack p : c.getResourcePackRepository().getSelectedPacks()) {
                    String pid = p.getId();
                    if (pid.equals("vanilla") || pid.startsWith("fabric") || pid.startsWith("mod_resources")
                            || pid.startsWith("mod/") || pid.contains("fhasix")) {
                        continue;
                    }
                    name = p.getTitle().getString(); // the last one is the top-priority pack
                }
            } catch (Exception ignored) {
                name = null;
            }
            if (name == null) {
                name = preview ? "Faithful 64x" : "Default";
            }
            if (name.length() > 24) {
                name = name.substring(0, 23) + "..";
            }
            return NewModSettings.optBool(c, id, "label") ? "Pack: " + name : name;
        }
    }

    private static final El[] ELEMENTS = {
            new Counter(FPS, true, 0.012f, 0.02f),
            new Counter(CPS, false, 0.012f, 0.085f),
            new ArmorStatus(0.012f, 0.17f),
            new ArmorBar(0.30f, 0.80f),
            new BlockIndicator(0.44f, 0.06f),
            new AttackIndicatorEl(0.44f, 0.56f),
            new Direction(0.46f, 0.012f),
            new NewHudExtras.Coordinates(0.012f, 0.78f),
            new NewHudExtras.PotionEffects(0.80f, 0.30f),
            new NewHudExtras.Keystrokes(0.86f, 0.62f),
            new PingEl(0.86f, 0.02f),
            new ServerAddressEl(0.66f, 0.02f),
            new PlaytimeEl(0.86f, 0.085f),
            new ComboEl(0.44f, 0.62f),
            new ReachEl(0.44f, 0.69f),
            new PackEl(0.66f, 0.085f)
    };

    // ------------------------------------------------------------------ registration

    /** Call once from the client entrypoint. */
    public static void init() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fhasixuitheme", "modmenu"));
        CATEGORY = category;
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fhasixuitheme.modmenu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("hud_tick", minecraft -> {
            boolean fire = false;
            while (openKey.consumeClick()) {
                fire = true;
            }
            if (fire && minecraft.player != null && minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new NewLauncherScreen());
            }
            syncVanillaIndicator(minecraft);
            tickStats(minecraft);
        }));

        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) {
                onAttack(player, entity);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // If HudElementRegistry/VanillaHudElements don't resolve on this Fabric API build, the
        // older equivalent is: HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("fhasixuitheme", "mod_hud"),
                NewSafe.hud("hud_elements", (context, tickCounter) -> {
                    NewCamera.frameAll(Minecraft.getInstance());
                    render(context);
                })
        );

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("fhasixuitheme", "charge_indicator"),
                NewSafe.hud("charge_indicator", (context, tickCounter) -> drawCrosshairCharge(context, Minecraft.getInstance()))
        );

        NewBlockOverlay.init();
        NewCamera.init(category);
        NewZoom.init(category);
        NewExtras.init(category);
        NewAutohide.init();
        NewOreHighlight.init();
        NewNoBossBar.init();
        NewCrosshair.init();
        NewDamageIndicator.init();
        NewHitbox.init();
        NewShulkerTooltips.init();
        NewScreenLogo.init();
        NewPerf.init();
        NewNoScoreboard.init();
        NewFullBright.init();
        NewFovChanger.init();
        NewTimeChanger.init();
        NewWeatherChanger.init();
        NewTntTimer.init();
        NewDarkMode.init();
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft c = Minecraft.getInstance();
        pollClicks(c);
        if (c.player == null) {
            return;
        }
        for (AABB b : boxes(c, context.guiWidth(), context.guiHeight())) {
            drawBox(context, c, b);
        }
    }

    public static void drawBox(GuiGraphicsExtractor context, Minecraft c, AABB b) {
        if (NewSafe.disabled(b.el.safeKey)) {
            return;
        }
        try {
            b.el.draw(context, c, b.x, b.y, b.w, b.h, c.gui.screen() instanceof NewLauncherScreen);
        } catch (Exception | LinkageError t) {
            NewSafe.failed(b.el.safeKey, t);
        }
    }

    /** The currently ENABLED HUD elements that have something to show, with their rectangles. */
    public static List<AABB> boxes(Minecraft c, int sw, int sh) {
        List<AABB> out = new ArrayList<>(ELEMENTS.length);
        boolean preview = c.gui.screen() instanceof NewLauncherScreen;
        for (El el : ELEMENTS) {
            if (!NewModSettings.isEnabled(c, el.id)) {
                continue;
            }
            int[] size = null;
            if (!NewSafe.disabled(el.safeKey)) {
                try {
                    size = el.size(c, preview);
                } catch (Exception | LinkageError t) {
                    NewSafe.failed(el.safeKey, t);
                }
            }
            if (size == null) {
                continue;
            }
            float[] p = NewModSettings.position(c, el.id, el.defX, el.defY);
            int x = Math.max(0, Math.min(Math.max(0, sw - size[0]), Math.round(p[0] * sw)));
            int y = Math.max(0, Math.min(Math.max(0, sh - size[1]), Math.round(p[1] * sh)));
            out.add(new AABB(el, x, y, size[0], size[1]));
        }
        return out;
    }

    // ------------------------------------------------------------------ CPS

    private static void pollClicks(Minecraft c) {
        long now = System.currentTimeMillis();
        boolean down = c.mouseHandler.isLeftPressed();
        if (down && !leftWasDown && c.gui.screen() == null && c.player != null) {
            CLICKS.addLast(now);
        }
        leftWasDown = down;
        prune(now);
    }

    private static void prune(long now) {
        long cutoff = now - 1000L;
        while (!CLICKS.isEmpty() && CLICKS.peekFirst() < cutoff) {
            CLICKS.pollFirst();
        }
    }

    /** Left clicks in the last second. */
    public static int cps() {
        prune(System.currentTimeMillis());
        return CLICKS.size();
    }
}
