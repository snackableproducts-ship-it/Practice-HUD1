package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small helper that stacks translucent info panels down the left side of the screen. */
public final class Hud {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int GREEN = 0xFF55FF55;
    public static final int YELLOW = 0xFFFFFF55;
    public static final int RED = 0xFFFF5555;
    public static final int AQUA = 0xFF55FFFF;
    public static final int GOLD = 0xFFFFAA00;

    /** One line in a panel; frac >= 0 draws a progress bar under the text. */
    public record Row(String text, int color, float frac) {
        public static Row of(String text, int color) {
            return new Row(text, color, -1f);
        }

        public static Row bar(String text, float frac, int color) {
            return new Row(text, color, Math.max(0f, Math.min(1f, frac)));
        }
    }

    private static int cursorY = 4;

    private Hud() {
    }

    public static void begin() {
        cursorY = 4;
    }

    public static void panel(DrawContext ctx, String title, int accent, Row... rows) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = tr.getWidth(title);
        int h = 15;
        boolean anyBar = false;
        for (Row r : rows) {
            w = Math.max(w, tr.getWidth(r.text()));
            h += r.frac() >= 0 ? 14 : 10;
            anyBar |= r.frac() >= 0;
        }
        w += 12;
        if (anyBar) w = Math.max(w, 110);

        int x = 4;
        int y = cursorY;
        ctx.fill(x, y, x + w, y + h, 0x90000000);
        ctx.fill(x, y, x + 2, y + h, accent);
        ctx.drawTextWithShadow(tr, title, x + 6, y + 3, accent);

        int ry = y + 15;
        for (Row r : rows) {
            ctx.drawTextWithShadow(tr, r.text(), x + 6, ry, r.color());
            if (r.frac() >= 0) {
                int bw = w - 12;
                ctx.fill(x + 6, ry + 10, x + 6 + bw, ry + 12, 0x60FFFFFF);
                ctx.fill(x + 6, ry + 10, x + 6 + Math.round(bw * r.frac()), ry + 12, r.color());
                ry += 14;
            } else {
                ry += 10;
            }
        }
        cursorY += h + 3;
    }

    /** Draws text centred on the given x. */
    public static void centered(DrawContext ctx, String text, int cx, int y, int color) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawTextWithShadow(tr, text, cx - tr.getWidth(text) / 2, y, color);
    }

    public static String time(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
