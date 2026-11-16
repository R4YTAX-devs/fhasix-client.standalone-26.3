package com.fhasix.uitheme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

/**
 * The FHASIX points currency: starts at 0 and grows with the daily check-in streak (15, 20, 30, 40, 50, 60, 75,
 * then the 7-day cycle starts over). Missing a day resets the streak. Owned store cosmetics can be sold back
 * (SELL_LIMIT per week, for the price minus SELL_PENALTY points). Stored client-side in config/fhasix_points.json.
 */
public final class FhasixPoints {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("fhasix_points.json");

    /** Reward for day 1..7 of the streak cycle. */
    public static final int[] REWARDS = {15, 20, 30, 40, 50, 60, 75};

    /** The redeem code and what it unlocks. */
    public static final String CODE = "987624861846";
    public static final long CODE_POINTS = 99_999_999_999L;

    /** Selling a cosmetic back: at most SELL_LIMIT per calendar week, and it pays SELL_PENALTY points less than the price. */
    public static final int SELL_LIMIT = 2;
    public static final int SELL_PENALTY = 500;

    public static final int CODE_WRONG = 0;
    public static final int CODE_OK = 1;
    public static final int CODE_USED = 2;

    private static boolean loaded;
    private static long points;
    private static int streak;
    private static int best;
    private static String lastClaim = "";
    private static boolean codeUsed;
    private static String salesWeek = "";
    private static int salesCount;

    private FhasixPoints() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                JsonObject o = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                points = o.has("points") ? Math.max(0L, o.get("points").getAsLong()) : 0L;
                streak = o.has("streak") ? Math.max(0, o.get("streak").getAsInt()) : 0;
                best = o.has("best") ? Math.max(0, o.get("best").getAsInt()) : 0;
                lastClaim = o.has("last_claim") ? o.get("last_claim").getAsString() : "";
                codeUsed = o.has("code_used") && o.get("code_used").getAsBoolean();
                salesWeek = o.has("sales_week") ? o.get("sales_week").getAsString() : "";
                salesCount = o.has("sales_count") ? Math.max(0, o.get("sales_count").getAsInt()) : 0;
            }
        } catch (Exception ignored) {
            points = 0L;
            streak = 0;
            best = 0;
            lastClaim = "";
            codeUsed = false;
            salesWeek = "";
            salesCount = 0;
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("points", points);
        o.addProperty("streak", streak);
        o.addProperty("best", best);
        o.addProperty("last_claim", lastClaim);
        o.addProperty("code_used", codeUsed);
        o.addProperty("sales_week", salesWeek);
        o.addProperty("sales_count", salesCount);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, o.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // points are a client-side convenience; keep running if saving fails
        }
    }

    public static long balance() {
        ensureLoaded();
        return points;
    }

    public static int bestStreak() {
        ensureLoaded();
        return best;
    }

    /** True when today's reward has not been taken yet. */
    public static boolean canClaim() {
        ensureLoaded();
        return !LocalDate.now().toString().equals(lastClaim);
    }

    /** Streak that is still alive: claimed today or yesterday, otherwise 0 (the streak was broken). */
    public static int liveStreak() {
        ensureLoaded();
        if (lastClaim.isEmpty()) return 0;
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        return (lastClaim.equals(today) || lastClaim.equals(yesterday)) ? streak : 0;
    }

    /** Reward of the next claim (today if it is still open, otherwise tomorrow). */
    public static int nextReward() {
        return REWARDS[liveStreak() % REWARDS.length];
    }

    /** How many of the 7 day cards are already collected in the current cycle. */
    public static int collectedInCycle() {
        int s = liveStreak();
        if (canClaim()) return s % REWARDS.length;
        return s == 0 ? 0 : ((s - 1) % REWARDS.length) + 1;
    }

    /** Takes today's reward; returns the points gained (0 when already claimed today). */
    public static int claim() {
        ensureLoaded();
        if (!canClaim()) return 0;
        int live = liveStreak();
        int reward = REWARDS[live % REWARDS.length];
        points += reward;
        streak = live + 1;
        best = Math.max(best, streak);
        lastClaim = LocalDate.now().toString();
        save();
        return reward;
    }

    /** Spends points; false (and nothing changes) when there are not enough. */
    public static boolean spend(long cost) {
        ensureLoaded();
        if (cost < 0 || points < cost) return false;
        points -= cost;
        save();
        return true;
    }

    private static String weekKey() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Sales still allowed this calendar week (the counter starts over every Monday). */
    public static int salesLeft() {
        ensureLoaded();
        return weekKey().equals(salesWeek) ? Math.max(0, SELL_LIMIT - salesCount) : SELL_LIMIT;
    }

    /** What a cosmetic with this store price pays when sold back. */
    public static long sellValue(long price) {
        return Math.max(0L, price - SELL_PENALTY);
    }

    /** Records one sale and pays it out; returns the points gained, or -1 when the weekly limit is used up. */
    public static long sell(long price) {
        ensureLoaded();
        if (salesLeft() <= 0) return -1L;
        long gain = sellValue(price);
        String week = weekKey();
        if (!week.equals(salesWeek)) {
            salesWeek = week;
            salesCount = 0;
        }
        salesCount++;
        points = Math.min(Long.MAX_VALUE / 2, points + gain);
        save();
        return gain;
    }

    public static int redeemCode(String entered) {
        ensureLoaded();
        if (entered == null || !CODE.equals(entered.trim())) return CODE_WRONG;
        if (codeUsed) return CODE_USED;
        codeUsed = true;
        points = Math.min(Long.MAX_VALUE / 2, points + CODE_POINTS);
        save();
        return CODE_OK;
    }

    /** 8095 -> "8,095". */
    public static String format(long v) {
        return String.format(java.util.Locale.US, "%,d", v);
    }

    /** Short form for tight spaces: 8,095 stays, 1,250,000 -> "1.25M", 99,999,999,999 -> "100B". */
    public static String compact(long v) {
        if (v < 1_000_000L) return format(v);
        String[] units = {"M", "B", "T"};
        double d = v / 1_000_000.0;
        int u = 0;
        while (d >= 1000.0 && u < units.length - 1) {
            d /= 1000.0;
            u++;
        }
        String s = String.format(java.util.Locale.US, "%.2f", d);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + units[u];
    }
}
