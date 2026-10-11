package com.calyvora.document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A signature or seal as it should sit on a letter: cropped to the ink, the paper made transparent,
 * and scaled to a sensible size (PD-71).
 *
 * <p>People photograph a signature on white paper or scan a stamp on a grey desk. Printed as-is, that
 * is a white (or grey) rectangle over the letterpad. Making the near-white background transparent and
 * trimming the margins is what turns it into ink on the page.
 */
final class SignatureImages {

    /** Uploads larger than this are refused before decoding. */
    static final long MAX_UPLOAD_BYTES = 3L * 1024 * 1024;
    private static final int MAX_WIDTH = 900;

    private SignatureImages() {
    }

    /** PNG bytes of the cleaned image. Throws IOException when the bytes are not a readable image. */
    static byte[] clean(byte[] bytes) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) throw new IOException("Not a readable image");
        int w = src.getWidth(), h = src.getHeight();

        // Paper becomes transparent: light, low-saturation pixels fade out smoothly, ink stays.
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = (p >>> 24) & 0xff;
                int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                if (!src.getColorModel().hasAlpha()) a = 255;
                int lightness = (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 2;
                int chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                // Above 215 lightness with little colour is paper; fade between 170 and 215.
                int keep = chroma > 60 ? 255 : lightness >= 215 ? 0 : lightness <= 170 ? 255
                        : (215 - lightness) * 255 / 45;
                int alpha = a * keep / 255;
                if (alpha < 16) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) throw new IOException("The image is blank");

        // Crop to the ink with a little margin.
        int pad = Math.max(4, (maxX - minX) / 40);
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);
        BufferedImage cropped = out.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);

        // Large phone photos are scaled down; nothing on a letter needs more than this.
        BufferedImage finalImage = cropped;
        if (cropped.getWidth() > MAX_WIDTH) {
            int nh = Math.max(1, cropped.getHeight() * MAX_WIDTH / cropped.getWidth());
            finalImage = new BufferedImage(MAX_WIDTH, nh, BufferedImage.TYPE_INT_ARGB);
            var g = finalImage.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(cropped, 0, 0, MAX_WIDTH, nh, null);
            g.dispose();
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(finalImage, "png", png);
        return png.toByteArray();
    }
}
