package com.calyvora.document;

import java.awt.image.BufferedImage;

/**
 * Where a letter may be written on an uploaded letterpad (PD-69).
 *
 * <p>Letterpads differ: one has a slim logo strip, another a tall header with an address block, a
 * third a wave of colour across the bottom. Fixed margins put text over somebody's artwork. So the
 * page is measured instead: the tallest run of empty rows between the header and the footer is the
 * writing area, with a little air at each end, and the side margin follows where the letterpad's own
 * printing starts on the left. HR can still adjust every value by hand; this only picks the start.
 *
 * <p>All values are millimetres on A4, because both the PDF and the screen preview lay out on A4 and
 * the image is stretched to it.
 */
final class LetterpadLayout {

    static final double PAGE_MM = 297.0;
    static final double WIDTH_MM = 210.0;

    /** Used when a page cannot be measured — a full-bleed photograph, say. */
    static final int DEFAULT_TOP = 40, DEFAULT_BOTTOM = 32, DEFAULT_SIDE = 22;
    /** Air between the letterpad's printing and the first or last line of the letter. */
    private static final double GAP_TOP = 8, GAP_BOTTOM = 6;

    private LetterpadLayout() {
    }

    /**
     * @param top          writing starts this far from the top edge
     * @param bottom       writing stops this far from the bottom edge
     * @param side         left and right margin
     * @param headerEndPx  the first empty row below the header, in pixels (0 when there is no header)
     * @param measured     false when the defaults were used
     */
    record Area(int top, int bottom, int side, int headerEndPx, boolean measured) {
    }

    static Area measure(BufferedImage img) {
        int h = img.getHeight();
        int w = img.getWidth();
        // Strict first: anything not near-white is printing. A faint watermark across the middle
        // defeats that, so a second pass counts only real ink (text, logos, dark rules).
        int[] run = longestBlankRun(img, 236);
        if (run[1] - run[0] < h * 0.45) {
            int[] lenient = longestBlankRun(img, 170);
            if (lenient[1] - lenient[0] > run[1] - run[0]) run = lenient;
        }
        if (run[1] - run[0] < h * 0.35) {
            return new Area(DEFAULT_TOP, DEFAULT_BOTTOM, DEFAULT_SIDE, 0, false);
        }
        double mmPerPx = PAGE_MM / h;
        int top = clamp((int) Math.round(run[0] * mmPerPx + GAP_TOP), 18, 140);
        int bottom = clamp((int) Math.round((h - run[1]) * mmPerPx + GAP_BOTTOM), 16, 120);
        if (PAGE_MM - top - bottom < 90) {
            return new Area(DEFAULT_TOP, DEFAULT_BOTTOM, DEFAULT_SIDE, 0, false);
        }
        return new Area(top, bottom, side(img, w), run[0], true);
    }

    /**
     * The continuation sheet made from the first page, when none was supplied: the same paper with the
     * header cleared, so page two keeps the footer and the brand without repeating the address block.
     * Painted with the paper's own colour (sampled from the writing area), not assumed white.
     */
    static BufferedImage withoutHeader(BufferedImage first, Area area) {
        BufferedImage copy = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = copy.createGraphics();
        g.drawImage(first, 0, 0, null);
        if (area.measured() && area.headerEndPx() > 0) {
            int sampleY = Math.min(first.getHeight() - 1, area.headerEndPx() + 4);
            g.setColor(new java.awt.Color(first.getRGB(first.getWidth() / 2, sampleY)));
            g.fillRect(0, 0, first.getWidth(), area.headerEndPx());
        }
        g.dispose();
        return copy;
    }

    /** [start, end) of the tallest band of rows with no printing inside the page's middle 92%. */
    private static int[] longestBlankRun(BufferedImage img, int threshold) {
        int h = img.getHeight();
        int w = img.getWidth();
        int x0 = (int) (w * 0.04), x1 = (int) (w * 0.96);
        int bestStart = 0, bestEnd = 0, start = -1;
        int[] row = new int[w];
        for (int y = 0; y <= h; y++) {
            boolean blank = y < h && blankRow(img, y, x0, x1, threshold, row);
            if (blank && start < 0) start = y;
            if (!blank && start >= 0) {
                if (y - start > bestEnd - bestStart) {
                    bestStart = start;
                    bestEnd = y;
                }
                start = -1;
            }
        }
        return new int[]{bestStart, bestEnd};
    }

    private static boolean blankRow(BufferedImage img, int y, int x0, int x1, int threshold, int[] row) {
        img.getRGB(x0, y, x1 - x0, 1, row, 0, x1 - x0);
        for (int i = 0; i < x1 - x0; i++) {
            int p = row[i];
            int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
            if (Math.min(r, Math.min(g, b)) < threshold) return false;
        }
        return true;
    }

    /**
     * The side margin: line the letter up with where the letterpad's own printing starts on the left,
     * which is how a designer would set it. A strip running down the very edge does not count — that
     * would put text against the paper's edge — and an implausible answer falls back to the default.
     */
    private static int side(BufferedImage img, int w) {
        int h = img.getHeight();
        int minX = w;
        for (int y = 0; y < h; y += 2) {
            for (int x = (int) (w * 0.03); x < minX && x < w / 2; x++) {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                if (Math.min(r, Math.min(g, b)) < 200) {
                    minX = x;
                    break;
                }
            }
        }
        double mm = minX * WIDTH_MM / w;
        return mm >= 12 && mm <= 35 ? (int) Math.round(mm) : DEFAULT_SIDE;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
