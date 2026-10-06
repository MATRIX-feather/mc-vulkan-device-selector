package dev.vkselect.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Text helpers for the overlay.
 *
 * <p>GPU names are long ("AMD Ryzen 7 9700X 8-Core Processor (RADV RAPHAEL_MENDOCINO)"), so plain
 * {@code graphics.text}/{@code centeredText} would spill outside the panel. {@link #drawFitted}
 * centres short text and turns longer text into a clipped, smoothly easing marquee that moves back
 * and forth inside its box.
 *
 * <p>Minecraft 26.3 has its own marquee ({@code ActiveTextCollector#acceptScrolling}), but it is
 * tuned for button labels at 0.5 s per scrolled pixel, which needs roughly 35 seconds to reveal the
 * end of a long GPU name - and the part that distinguishes two AMD/Intel adapters is usually at the
 * end. This implementation keeps the same "centre while it fits, otherwise scroll" behaviour with a
 * speed that reaches the end of a typical name in a few seconds.
 */
public final class GuiText {
    /** Horizontal marquee speed. Tune this if the scrolling feels too fast or too slow. */
    private static final double SCROLL_PIXELS_PER_SECOND = 40.0;

    /** Shortest full out-and-back cycle, so very small overflows still look calm. */
    private static final double MIN_SCROLL_PERIOD_SECONDS = 2.0;

    private GuiText() {
    }

    /** Draws a component inside a box, centring it or scrolling it horizontally. */
    public static void drawFitted(GuiGraphicsExtractor graphics, Component message, int argbColor,
                                  int left, int right, int top, int bottom) {
        drawFitted(graphics, message.getString(), argbColor, left, right, top, bottom);
    }

    /** Draws a plain string inside a box, centring it or scrolling it horizontally. */
    public static void drawFitted(GuiGraphicsExtractor graphics, String text, int argbColor,
                                  int left, int right, int top, int bottom) {
        Font font = Minecraft.getInstance().font;
        int boxWidth = right - left;
        int textWidth = font.width(text);
        int textY = (top + bottom - font.lineHeight) / 2 + 1;

        if (textWidth <= boxWidth) {
            int textX = left + (boxWidth - textWidth) / 2;
            graphics.text(font, text, textX, textY, argbColor);
            return;
        }

        int maxOffset = textWidth - boxWidth;
        double period = Math.max(maxOffset / SCROLL_PIXELS_PER_SECOND * 2.0, MIN_SCROLL_PERIOD_SECONDS);
        double time = Util.getMillis() / 1000.0;
        // Smooth out-and-back easing, the same shape vanilla uses for scrolling labels.
        double progress = Math.sin((Math.PI / 2.0) * Math.cos((Math.PI * 2.0) * time / period)) / 2.0 + 0.5;
        int offset = (int) Mth.lerp(progress, 0.0, maxOffset);

        graphics.enableScissor(left, top, right, bottom);
        graphics.text(font, text, left - offset, textY, argbColor);
        graphics.disableScissor();
    }
}
