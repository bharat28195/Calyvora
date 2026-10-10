package com.calyvora.document;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A letter as a PDF, laid out the way the screen shows it (PD-64) — for emailing a letter and for
 * downloading one that prints the same everywhere.
 *
 * <p>The body format is the one {@code letter.tsx} renders: paragraphs, {@code -} bullets, {@code 1.}
 * lists, {@code #} and {@code ##} headings, {@code **bold**}, {@code *italic*}, {@code ---} rules,
 * {@code --- page ---} breaks and pipe tables. Kept deliberately in step with it; the two are tested
 * against the same letters.
 *
 * <p>Stationery: an uploaded letterpad is drawn underneath page one and its continuation sheet under
 * every page after it (PD-69), with the text kept inside each sheet's measured writing area, so a
 * letter of any length flows from page to page without touching the artwork. Otherwise the composed
 * heading and footer repeat on every page, as on screen.
 */
public final class LetterPdf {

    private LetterPdf() {
    }

    /**
     * The stationery to print on. {@code background} is the uploaded letterpad image, or null;
     * {@code continuation} the sheet for page two onwards (null repeats the letterpad); {@code area}
     * where to write on each, or null for the defaults.
     */
    public record Stationery(String heading, String addressLines, String footerLines, String brandColor,
                             boolean serif, String logoUrl, byte[] background, byte[] continuation, Area area) {
        public Stationery(String heading, String addressLines, String footerLines, String brandColor,
                          boolean serif, String logoUrl, byte[] background) {
            this(heading, addressLines, footerLines, brandColor, serif, logoUrl, background, null, null);
        }
    }

    /** The writing area in millimetres on A4: page one, the pages after it, and both sides. */
    public record Area(int firstTop, int firstBottom, int laterTop, int laterBottom, int side) {
        static final Area DEFAULT = new Area(40, 32, 22, 32, 22);
    }

    public static byte[] render(String body, Stationery paper) throws IOException {
        String html = page(toHtml(body), paper);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder b = new PdfRendererBuilder();
        b.useFastMode();
        font(b, "fonts/NotoSans-Regular.ttf", "Noto Sans", 400);
        font(b, "fonts/NotoSans-Bold.ttf", "Noto Sans", 700);
        font(b, "fonts/NotoSerif-Regular.ttf", "Noto Serif", 400);
        font(b, "fonts/NotoSerif-Bold.ttf", "Noto Serif", 700);
        b.withHtmlContent(html, null);
        b.toStream(out);
        b.run();
        byte[] pdf = out.toByteArray();
        return paper != null && paper.background() != null
                ? underlay(pdf, paper.background(), paper.continuation()) : pdf;
    }

    private static void font(PdfRendererBuilder b, String resource, String family, int weight) {
        b.useFont(() -> {
            InputStream in = LetterPdf.class.getClassLoader().getResourceAsStream(resource);
            if (in == null) throw new IllegalStateException("Missing font " + resource);
            return in;
        }, family, weight, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
    }

    /** Draws the letterpad under page one and the continuation sheet under the rest, full bleed. */
    private static byte[] underlay(byte[] pdf, byte[] image, byte[] continuation) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDImageXObject first = PDImageXObject.createFromByteArray(doc, image, "letterpad");
            PDImageXObject later = continuation == null ? first
                    : PDImageXObject.createFromByteArray(doc, continuation, "continuation");
            int index = 0;
            for (PDPage page : doc.getPages()) {
                PDImageXObject img = index++ == 0 ? first : later;
                PDRectangle box = page.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                        PDPageContentStream.AppendMode.PREPEND, true, true)) {
                    cs.drawImage(img, 0, 0, box.getWidth(), box.getHeight());
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ---- the page --------------------------------------------------------------------------------

    private static String page(String bodyHtml, Stationery p) {
        boolean printed = p != null && p.background() != null;
        String family = p != null && p.serif() ? "'Noto Serif'" : "'Noto Sans'";
        String accent = p == null || p.brandColor() == null ? "#7c5cff" : p.brandColor();
        StringBuilder head = new StringBuilder();
        StringBuilder foot = new StringBuilder();
        if (p != null && !printed) {
            boolean hasHeading = notBlank(p.heading()) || notBlank(p.addressLines()) || notBlank(p.logoUrl());
            if (hasHeading) {
                head.append("<div id='head'><table style='width:100%'><tr>");
                if (notBlank(p.logoUrl()) && p.logoUrl().startsWith("https://")) {
                    head.append("<td style='width:45%'><img src='").append(esc(p.logoUrl()))
                            .append("' style='max-height:48px;max-width:160px'/></td>");
                }
                head.append("<td style='text-align:right'>");
                if (notBlank(p.heading())) {
                    head.append("<div style='font-size:15pt;font-weight:bold;color:").append(esc(accent)).append("'>")
                            .append(esc(p.heading())).append("</div>");
                }
                for (String line : lines(p.addressLines())) {
                    head.append("<div style='font-size:8.5pt;color:#666'>").append(esc(line)).append("</div>");
                }
                head.append("</td></tr></table><div style='height:2px;background:").append(esc(accent))
                        .append(";margin-top:8px'></div></div>");
            }
            List<String> f = lines(p.footerLines());
            if (!f.isEmpty()) {
                foot.append("<div id='foot' style='border-top:1px solid #ddd;padding-top:4px'>");
                for (String line : f) {
                    foot.append("<div style='text-align:center;font-size:7.5pt;color:#666'>").append(esc(line)).append("</div>");
                }
                foot.append("</div>");
            }
        }
        String margins = (head.length() > 0 ? "42mm" : "22mm") + " 20mm " + (foot.length() > 0 ? "30mm" : "20mm") + " 20mm";
        String firstPage = "";
        if (printed) {
            Area a = p.area() == null ? Area.DEFAULT : p.area();
            margins = a.laterTop() + "mm " + a.side() + "mm " + a.laterBottom() + "mm " + a.side() + "mm";
            firstPage = "@page :first{margin:" + a.firstTop() + "mm " + a.side() + "mm " + a.firstBottom() + "mm " + a.side() + "mm}";
        }
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>"
                + firstPage
                + "@page{size:A4;margin:" + margins + ";"
                + (head.length() > 0 ? "@top-center{content:element(head);vertical-align:bottom;padding-bottom:6mm}" : "")
                + (foot.length() > 0 ? "@bottom-center{content:element(foot);vertical-align:top;padding-top:4mm}" : "")
                + "}"
                + "#head{position:running(head);width:170mm}#foot{position:running(foot);width:170mm}"
                + "body{font-family:" + family + ";font-size:10.5pt;line-height:1.5;color:#222}"
                + "p{margin:0}h2{font-size:14pt;margin:12pt 0 6pt}h3{font-size:11pt;margin:10pt 0 3pt}"
                + "ul,ol{margin:6pt 0 6pt 16pt;padding:0}li{margin:2pt 0}hr{border:0;border-top:1px solid #ccc;margin:12pt 0}"
                + ".gap{height:8pt}.page{page-break-after:always;height:0}"
                + "table.t{width:100%;border-collapse:collapse;margin:8pt 0;font-size:9.5pt}"
                + "table.t th,table.t td{border:1px solid #bbb;padding:4pt 6pt;text-align:left}"
                + "table.t th{background:#f1f1f1}table.t td.n,table.t th.n{text-align:right}"
                + "</style></head><body>" + head + foot + bodyHtml + "</body></html>";
    }

    // ---- the body format ---------------------------------------------------------------------------

    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(^|[^*])\\*([^*]+)\\*");
    private static final Pattern NUMERIC = Pattern.compile("^[*\\s]*[-+]?[\\d,]+(\\.\\d+)?%?[*\\s]*$");

    /** The letter body as XHTML — the same rules as {@code renderLetter} in letter.tsx. */
    static String toHtml(String body) {
        String[] lines = (body == null ? "" : body).replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        List<String> bullets = null, numbers = null;
        List<List<String>> table = null;
        for (String line : lines) {
            if (line.matches("^\\s*\\|.*\\|\\s*$")) {
                if (bullets != null) { out.append(list("ul", bullets)); bullets = null; }
                if (numbers != null) { out.append(list("ol", numbers)); numbers = null; }
                if (table == null) table = new ArrayList<>();
                String inner = line.trim().replaceAll("^\\|", "").replaceAll("\\|$", "");
                List<String> cells = new ArrayList<>();
                for (String c : inner.split("\\|", -1)) cells.add(c.trim());
                table.add(cells);
                continue;
            }
            if (table != null) { out.append(table(table)); table = null; }
            if (line.matches("^\\s*[-*]\\s+.*")) {
                if (numbers != null) { out.append(list("ol", numbers)); numbers = null; }
                if (bullets == null) bullets = new ArrayList<>();
                bullets.add(inline(line.replaceFirst("^\\s*[-*]\\s+", "")));
                continue;
            }
            if (line.matches("^\\s*\\d+[.)]\\s+.*")) {
                if (bullets != null) { out.append(list("ul", bullets)); bullets = null; }
                if (numbers == null) numbers = new ArrayList<>();
                numbers.add(inline(line.replaceFirst("^\\s*\\d+[.)]\\s+", "")));
                continue;
            }
            if (bullets != null) { out.append(list("ul", bullets)); bullets = null; }
            if (numbers != null) { out.append(list("ol", numbers)); numbers = null; }
            if (line.matches("(?i)^\\s*---\\s*page\\s*---\\s*$")) {
                out.append("<div class='page'></div>");
            } else if (line.matches("^\\s*---+\\s*$")) {
                out.append("<hr/>");
            } else if (line.startsWith("## ")) {
                out.append("<h3>").append(inline(line.substring(3))).append("</h3>");
            } else if (line.startsWith("# ")) {
                out.append("<h2>").append(inline(line.substring(2))).append("</h2>");
            } else if (line.isBlank()) {
                out.append("<div class='gap'></div>");
            } else {
                out.append("<p>").append(inline(line)).append("</p>");
            }
        }
        if (table != null) out.append(table(table));
        if (bullets != null) out.append(list("ul", bullets));
        if (numbers != null) out.append(list("ol", numbers));
        return out.toString();
    }

    private static String list(String tag, List<String> items) {
        StringBuilder s = new StringBuilder("<").append(tag).append(">");
        for (String i : items) s.append("<li>").append(i).append("</li>");
        return s.append("</").append(tag).append(">").toString();
    }

    private static String table(List<List<String>> rows) {
        List<List<String>> kept = new ArrayList<>();
        for (List<String> r : rows) {
            if (!r.stream().allMatch(c -> c.matches("^:?-{3,}:?$"))) kept.add(r);
        }
        if (kept.isEmpty()) return "";
        StringBuilder s = new StringBuilder("<table class='t'><thead><tr>");
        for (String c : kept.get(0)) s.append(cell("th", c));
        s.append("</tr></thead><tbody>");
        for (List<String> r : kept.subList(1, kept.size())) {
            s.append("<tr>");
            for (String c : r) s.append(cell("td", c));
            s.append("</tr>");
        }
        return s.append("</tbody></table>").toString();
    }

    private static String cell(String tag, String c) {
        return "<" + tag + (NUMERIC.matcher(c).matches() ? " class='n'" : "") + ">" + inline(c) + "</" + tag + ">";
    }

    private static String inline(String s) {
        String e = esc(s);
        e = BOLD.matcher(e).replaceAll("<b>$1</b>");
        Matcher m = ITALIC.matcher(e);
        e = m.replaceAll("$1<i>$2</i>");
        return e;
    }

    private static String esc(String s) {
        // XHTML: entities beyond the XML five are not defined, so escape to those and leave the rest as text.
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8")
                .replace("&#39;", "&apos;");
    }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String l : s.split("\n")) if (!l.isBlank()) out.add(l.trim());
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
