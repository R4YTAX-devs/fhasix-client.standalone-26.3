package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.c3;
import com.fhasix.uitheme.d4;
import com.fhasix.uitheme.CosmeticsScreen;
import com.fhasix.uitheme.DiscoverScreen;
import com.fhasix.uitheme.StoreScreen;
import com.fhasix.uitheme.b2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import com.fhasix.uitheme.NewFont;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebuilds the whole title screen:
 *  - Singleplayer / Multiplayer / Discover / Cosmetics / Store, stacked,
 *    positioned as fractions of the screen's scaled width/height (correct
 *    at GUI Scale 2 and every other scale).
 *  - a top-right row of 6 small square icon buttons (grid/chat/gear/gem/
 *    archive/list), same navy theme, sized+spaced off the reference image,
 *    rendering pre-made anti-aliased icon glyphs (see c3) at 70%
 *    opacity - button size unchanged, only the icon glyph inside is smaller.
 *  - an Account pill directly left of the icon row showing just the
 *    player's username (no skin head), same navy theme, also 70% opacity.
 *  - a translucent red Quit Game button below Store, with a soft animated
 *    red fade-in-on-hover (see d4) - the one button that still animates.
 *  - a custom background image instead of the vanilla panorama.
 *  - no Minecraft logo, no splash text (see fhasix$renderCustomScreen).
 */
@Mixin(TitleScreen.class)
public abstract class f6 extends Screen {

    // Main buttons (Singleplayer ... Store): everything is a fraction of the screen HEIGHT, measured from the
    // reference (251x44 px buttons, 55 px step, first top at y=311 on a 1365x767 screen). Sizing from the height
    // - not the width - keeps the buttons the same shape on every aspect ratio (wide phone screens included).
    /**
     * Main buttons: width is a fraction of the screen WIDTH (the reference button is 18.4% of its screen's width),
     * height/gap/top are fractions of the screen HEIGHT. Tune these four numbers to resize everything:
     * a bigger MAIN_HEIGHT_FRAC makes the buttons taller (fatter), a bigger MAIN_WIDTH_FRAC makes them wider.
     */
    private static final Identifier ICON_PERSON = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/icon_person.png");
    private static final Identifier ICON_PEOPLE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/icon_people.png");
    private static final Identifier ICON_STAR = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/icon_star.png");
    private static final Identifier ICON_SHIRT = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/icon_shirt.png");
    private static final Identifier ICON_STORE = Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/icon_store.png");
    private static final float MAIN_WIDTH_FRAC = 0.19f;
    private static final float MAIN_HEIGHT_FRAC = 0.0685f;
    private static final float MAIN_GAP_FRAC = 0.0155f;
    private static final float MAIN_TOP_FRAC = 0.34f;
    /** Logo width as a fraction of the main button width (was 0.74 = too big; original 0.55), and its gap above the buttons. */
    private static final float LOGO_WIDTH_OF_BUTTON = 0.62f;
    private static final float LOGO_GAP_FRAC = 0.03f;

    // Old metrics: still used by the top bar (icon row + Account pill), the Quit button and the logo, so those
    // stay exactly where and how big they were.
    private static final float BUTTON_WIDTH_FRAC = 0.19f;
    private static final float BUTTON_HEIGHT_FRAC = 0.06f;
    private static final float BUTTON_GAP_FRAC = 0.016f;
    private static final float FIRST_BUTTON_TOP_FRAC = 0.383f;
    /** Flat trim requested on top of the width fraction - buttons were "a bit wide." */
    private static final int BUTTON_WIDTH_TRIM_PX = 4;

    private static final float ICON_TOP_FRAC = 0.064f;
    private static final float ICON_RIGHT_MARGIN_FRAC = 0.063f;
    /** "100% opacity to 70%" on the top-bar widgets (icon row + Account pill). */
    private static final int TOPBAR_ALPHA = 179;
    /** Horizontal padding inside the Account pill, as a fraction of its height. */
    private static final float ACCOUNT_PADDING_FRACTION = 0.6f;
    private static final c3.Icon[] ICON_ROW = {
            c3.Icon.GRID, c3.Icon.CHAT, c3.Icon.GEAR,
            c3.Icon.GEM, c3.Icon.ARCHIVE, c3.Icon.LIST
    };

    /** Gap between Store and Quit is much bigger than the inter-button gap - measured off the reference. */
    private static final float QUIT_TOP_GAP_FRAC = 0.12f;
    /** Quit is narrower than the main buttons - measured off the reference (~79% of main width). */
    private static final float QUIT_WIDTH_SCALE = 0.79f;

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/background.png");
    private static final int BACKGROUND_TEX_W = 1584;
    private static final int BACKGROUND_TEX_H = 672;
    private static final Identifier FHASIX_LOGO =
            Identifier.fromNamespaceAndPath("fhasixuitheme", "textures/gui/title/fhasix_logo.png");
    private static final int LOGO_TEX_W = 1251;
    private static final int LOGO_TEX_H = 323;

    private static boolean backgroundFilterApplied = false;
    /** Runtime UI mode. False = FHASIX, true = vanilla Minecraft title screen. */
    private static boolean normalMinecraftUI = false;

    protected f6(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fhasix$rebuildTitleScreen(CallbackInfo ci) {
        if (com.fhasix.uitheme.NewSafe.disabled("title_init")) {
            return;
        }
        try {
            fhasix$rebuildTitleScreenImpl(ci);
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("title_init", t); // the vanilla title screen stays as it is
        }
    }

    private void fhasix$rebuildTitleScreenImpl(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;

        // Normal Minecraft mode: leave every vanilla title-screen element untouched
        // and add only the small FHASIX switch-back button at the top.
        if (normalMinecraftUI) {
            int switchWidth = Math.max(120, Math.round(this.width * 0.16f));
            int switchHeight = Math.max(20, Math.round(this.height * 0.04f));
            int switchX = (this.width - switchWidth) / 2;
            int switchY = Math.max(6, Math.round(this.height * 0.025f));
            this.addRenderableWidget(Button.builder(Component.literal("Switch to FHASIX"),
                    press -> fhasix$switchToFhasix())
                    .bounds(switchX, switchY, switchWidth, switchHeight)
                    .build());
            return;
        }

        // Hide (and disable) every vanilla button init() just added.
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                button.visible = false;
                button.active = false;
            }
        }

        // Old metrics (top bar, Quit button) - unchanged.
        int width = Math.round(this.width * BUTTON_WIDTH_FRAC) - BUTTON_WIDTH_TRIM_PX;
        int height = Math.round(this.height * BUTTON_HEIGHT_FRAC);
        int gap = Math.round(this.height * BUTTON_GAP_FRAC);
        int oldStartY = Math.round(this.height * FIRST_BUTTON_TOP_FRAC);
        int oldStep = height + gap;

        // New metrics for the five main buttons.
        int mainW = Math.round(this.width * MAIN_WIDTH_FRAC);
        int mainH = Math.round(this.height * MAIN_HEIGHT_FRAC);
        int mainGap = Math.round(this.height * MAIN_GAP_FRAC);
        int x = this.width / 2 - mainW / 2;
        int startY = Math.round(this.height * MAIN_TOP_FRAC);
        int step = mainH + mainGap;

        this.addRenderableWidget(new b2(
                x, startY, mainW, mainH, Component.translatable("menu.singleplayer"),
                press -> this.minecraft.gui.setScreen(new SelectWorldScreen(self)),
                0, ICON_PERSON, "Singleplayer", false
        ));
        this.addRenderableWidget(new b2(
                x, startY + step, mainW, mainH, Component.translatable("menu.multiplayer"),
                press -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(self)),
                0, ICON_PEOPLE, "Multiplayer", false
        ));
        this.addRenderableWidget(new b2(
                x, startY + step * 2, mainW, mainH, Component.literal("Partnered Servers"),
                press -> this.minecraft.gui.setScreen(new DiscoverScreen(self)),
                0, ICON_STAR, "Partnered Servers", false
        ));
        this.addRenderableWidget(new b2(
                x, startY + step * 3, mainW, mainH, Component.literal("Cosmetics"),
                press -> this.minecraft.gui.setScreen(new CosmeticsScreen(self)),
                0, ICON_SHIRT, "Cosmetics", false
        ));
        this.addRenderableWidget(new b2(
                x, startY + step * 4, mainW, mainH, Component.literal("Store"),
                press -> this.minecraft.gui.setScreen(new StoreScreen(self)),
                1, ICON_STORE, "Store", true
        ));

        // Quit: same height as the main buttons, 79% of their width, and two button-heights below Store
        // (measured from the reference).
        int quitWidth = Math.round(mainW * QUIT_WIDTH_SCALE);
        int quitHeight = mainH;
        int quitX = this.width / 2 - quitWidth / 2;
        int quitY = startY + step * 4 + mainH + mainH * 2;
        this.addRenderableWidget(new d4(
                quitX, quitY, quitWidth, quitHeight, Component.literal("Quit Game"),
                press -> this.minecraft.stop()
        ));

        // Top-right icon row - same theme, square, sized/spaced off the reference.
        // Button size matches the main buttons' height (unchanged from v4) - only the
        // icon *glyph* drawn inside is smaller now (see c3's padding), and the
        // whole widget is drawn at 70% opacity per the aesthetic correction.
        int iconSize = height;
        int iconGap = gap;
        int rowWidth = ICON_ROW.length * iconSize + (ICON_ROW.length - 1) * iconGap;
        int iconStartX = Math.round(this.width - this.width * ICON_RIGHT_MARGIN_FRAC) - rowWidth;
        int iconY = Math.round(this.height * ICON_TOP_FRAC);

        // Account pill - username only, no skin head - sits directly left of the icon row.
        String username = this.minecraft.getUser().getName();
        int accountPadding = Math.round(iconSize * ACCOUNT_PADDING_FRACTION);
        int accountTextWidth = NewFont.width(this.minecraft.font, username);
        int accountWidth = accountTextWidth + accountPadding * 2;
        int accountX = iconStartX - iconGap - accountWidth;
        this.addRenderableWidget(new b2(
                accountX, iconY, accountWidth, iconSize, Component.literal(username),
                press -> { /* not wired up yet */ },
                b2.NAVY_NORMAL_TOP_LEFT, b2.NAVY_NORMAL_BOTTOM_RIGHT,
                b2.NAVY_HOVER_TOP_LEFT, b2.NAVY_HOVER_BOTTOM_RIGHT,
                TOPBAR_ALPHA
        ));

        for (int i = 0; i < ICON_ROW.length; i++) {
            int iconX = iconStartX + i * (iconSize + iconGap);
            final c3.Icon icon = ICON_ROW[i];
            this.addRenderableWidget(new c3(
                    iconX, iconY, iconSize, icon,
                    press -> {
                        if (icon == c3.Icon.LIST) {
                            fhasix$switchToMinecraft();
                        } else if (icon == c3.Icon.GEAR) {
                            minecraft.gui.setScreen(new OptionsScreen(self, minecraft.options));
                        } else if (icon == c3.Icon.CHAT) {
                            minecraft.gui.setScreen(new com.fhasix.uitheme.ScreenshotsScreen(self));
                        } else if (icon == c3.Icon.GEM) {
                            minecraft.gui.setScreen(new com.fhasix.uitheme.SkinPopupScreen(self, iconX, iconY, iconSize));
                        } else if (icon == c3.Icon.GRID) {
                            // v30: opens the Mod Menu grid (the launcher itself is in-world only)
                            minecraft.gui.setScreen(new com.fhasix.uitheme.NewModMenuScreen(self));
                        } else if (icon == c3.Icon.ARCHIVE) {
                            minecraft.gui.setScreen(new com.fhasix.uitheme.ResourcePacksScreen(self));
                        } else {
                            minecraft.gui.setScreen(new com.fhasix.uitheme.WorldOnlyBannerScreen(self));
                        }
                    }
            ));
        }
    }

    private void fhasix$switchToMinecraft() {
        normalMinecraftUI = true;
        com.fhasix.uitheme.NewGuiScale.titleFhasix = false;
        minecraft.gui.setScreen(new TitleScreen());
    }

    private void fhasix$switchToFhasix() {
        normalMinecraftUI = false;
        com.fhasix.uitheme.NewGuiScale.titleFhasix = true;
        minecraft.gui.setScreen(new TitleScreen());
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void fhasix$renderCustomScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (com.fhasix.uitheme.NewSafe.disabled("title_render")) {
            return; // vanilla draws the title screen instead
        }
        try {
            fhasix$renderCustomScreenImpl(context, mouseX, mouseY, delta, ci);
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("title_render", t);
        }
    }

    private void fhasix$renderCustomScreenImpl(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (normalMinecraftUI) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!backgroundFilterApplied) {
            backgroundFilterApplied = true;
        }

        // UPDATE 42: the moving panorama instead of the single still picture (BACKGROUND_TEXTURE stays unused)
        com.fhasix.uitheme.FhasixPanorama.draw(context, this.width, this.height);

        int buttonWidth = Math.round(this.width * BUTTON_WIDTH_FRAC) - BUTTON_WIDTH_TRIM_PX;
        int buttonHeight = Math.round(this.height * BUTTON_HEIGHT_FRAC);
        int logoWidth = Math.round(Math.round(this.width * MAIN_WIDTH_FRAC) * LOGO_WIDTH_OF_BUTTON);
        int logoHeight = Math.round(logoWidth * (float) LOGO_TEX_H / LOGO_TEX_W);
        int logoX = (this.width - logoWidth) / 2;
        int logoY = Math.round(this.height * MAIN_TOP_FRAC) - logoHeight - Math.round(this.height * LOGO_GAP_FRAC) - 1; // 1 GUI unit = 2 screen pixels up
        context.blit(RenderPipelines.GUI_TEXTURED, FHASIX_LOGO, logoX, logoY, 0, 0,
                logoWidth, logoHeight, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable drawable) {
                drawable.extractRenderState(context, mouseX, mouseY, delta);
            }
        }

        ci.cancel();
    }


}
