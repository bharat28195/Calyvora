package com.calyvora.email;

/**
 * The HTML shell every transactional email is poured into.
 *
 * <p>Written the way email has to be written rather than the way a web page is: tables for layout,
 * every style inline, and no external stylesheet — Outlook ignores {@code <style>} blocks and most
 * clients strip anything they cannot inline, so a class-based design silently renders as unstyled
 * text. Nothing here depends on CSS the recipient's client might not support.
 *
 * <p>The logo is a remote image and is therefore assumed to be blocked: many clients refuse remote
 * images until the reader asks for them. It sits beside a text wordmark rather than replacing one, so
 * a blocked image costs a picture and not the sender's identity.
 *
 * <p>Every value interpolated into HTML goes through {@link #escape} — company and contact names
 * reach us from a public form, so an unescaped one would let a stranger put markup into an email we
 * send under our own domain.
 */
final class EmailLayout {

    private EmailLayout() {}

    private static final String INK = "#14141c";
    private static final String MUTED = "#6c6c7d";
    private static final String VIOLET = "#7c5cff";
    private static final String BORDER = "#e7e7ef";
    private static final String CANVAS = "#f4f4f8";
    private static final String LOGO = "https://calyvora.in/apple-touch-icon.png";

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    /**
     * @param preview the line clients show next to the subject in an inbox list. Worth setting: left
     *                empty, clients fill it with whatever text comes first, which is usually the logo
     *                alt text or a greeting rather than the point of the message.
     */
    static String page(String preview, String content) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                </head>
                <body style="margin:0;padding:0;background:%s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:%s;padding:32px 16px;">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;">
                      <tr><td style="padding-bottom:20px;">
                        <img src="%s" width="28" height="28" alt="" style="vertical-align:middle;border-radius:6px;border:0;">
                        <span style="font-family:%s;font-size:17px;font-weight:600;color:%s;letter-spacing:-0.2px;vertical-align:middle;padding-left:9px;">Orbit</span>
                      </td></tr>
                      <tr><td style="background:#ffffff;border:1px solid %s;border-radius:14px;padding:32px;">
                        %s
                      </td></tr>
                      <tr><td style="padding-top:20px;font-family:%s;font-size:12px;line-height:19px;color:%s;">
                        Orbit is HR software by Calyvora.<br>
                        This message was sent automatically — replies to it reach us, but a person may take a little longer than usual.
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(CANVAS, escape(preview), CANVAS, LOGO, FONT, INK, BORDER, content, FONT, MUTED);
    }

    static String heading(String text) {
        return ("<h1 style=\"margin:0 0 14px;font-family:%s;font-size:20px;line-height:28px;"
                + "font-weight:600;color:%s;\">%s</h1>").formatted(FONT, INK, escape(text));
    }

    /** A paragraph. {@code html} is inserted as-is, so callers must escape anything untrusted. */
    static String paragraph(String html) {
        return ("<p style=\"margin:0 0 14px;font-family:%s;font-size:15px;line-height:23px;"
                + "color:%s;\">%s</p>").formatted(FONT, INK, html);
    }

    static String muted(String html) {
        return ("<p style=\"margin:14px 0 0;font-family:%s;font-size:13px;line-height:20px;"
                + "color:%s;\">%s</p>").formatted(FONT, MUTED, html);
    }

    /**
     * The one-time code: large, bold and spaced, because it is read off the screen and typed by hand
     * into another window. Letter-spacing is what stops 6 digits being misread as a single number.
     */
    static String code(String value) {
        return ("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"margin:18px 0;\"><tr><td style=\"background:%s;border:1px solid %s;"
                + "border-radius:10px;padding:14px 26px;font-family:%s;font-size:30px;line-height:36px;"
                + "font-weight:700;letter-spacing:7px;color:%s;\">%s</td></tr></table>")
                .formatted(CANVAS, BORDER, FONT, INK, escape(value));
    }

    static String button(String label, String url) {
        return ("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"margin:20px 0 6px;\"><tr><td style=\"background:%s;border-radius:9px;\">"
                + "<a href=\"%s\" style=\"display:inline-block;padding:12px 22px;font-family:%s;"
                + "font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;\">%s</a>"
                + "</td></tr></table>").formatted(VIOLET, escape(url), FONT, escape(label));
    }

    /**
     * The same link as plain text under a button.
     *
     * <p>Not redundant: a button is an anchor, and a client that strips or fails to render it leaves
     * the reader with no way to proceed at all.
     */
    static String fallbackLink(String url) {
        return ("<p style=\"margin:6px 0 0;font-family:%s;font-size:12px;line-height:19px;color:%s;"
                + "word-break:break-all;\">Or paste this into your browser:<br>%s</p>")
                .formatted(FONT, MUTED, escape(url));
    }

    /** A label/value line, for the detail block in a trial enquiry. */
    static String field(String label, String value) {
        return ("<tr><td style=\"padding:3px 14px 3px 0;font-family:%s;font-size:13px;color:%s;"
                + "white-space:nowrap;vertical-align:top;\">%s</td>"
                + "<td style=\"padding:3px 0;font-family:%s;font-size:14px;color:%s;\">%s</td></tr>")
                .formatted(FONT, MUTED, escape(label), FONT, INK, escape(value));
    }

    static String fields(String rows) {
        return ("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"margin:4px 0 14px;\">%s</table>").formatted(rows);
    }

    /** Minimal HTML escaping. Applied to every interpolated value, trusted or not. */
    static String escape(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
