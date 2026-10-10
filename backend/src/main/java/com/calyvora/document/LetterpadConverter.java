package com.calyvora.document;

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A letterpad handed over as a PDF or a Word file, turned into the page image letters print on
 * (PD-64). Printers deliver stationery as PDF far more often than as an image, and many companies
 * keep theirs as a Word template — asking HR to "export it as PNG first" was a step most could not do.
 *
 * <p>Only the first page is used: a letterpad is one page. Rendered at 150 dpi, which is sharp on
 * paper and keeps an A4 page well under the 2 MB the image is stored in.
 */
final class LetterpadConverter {

    static final int DPI = 150;
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

    private LetterpadConverter() {
    }

    /** The image and its type, ready to store. */
    record Image(byte[] bytes, String contentType) {
    }

    static Image fromPdf(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.getNumberOfPages() == 0) throw new IOException("The PDF has no pages.");
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, DPI, ImageType.RGB);
            return encode(page);
        }
    }

    static Image fromDocx(byte[] docx) throws IOException {
        try (XWPFDocument original = new XWPFDocument(new ByteArrayInputStream(docx))) {
            // Word always writes a styles part; files from other tools sometimes do not, and the
            // converter refuses a document without one. A new part is only filled in when saved, so
            // save once and read it back.
            XWPFDocument doc = original;
            var body = original.getDocument().getBody();
            boolean noPage = !body.isSetSectPr();
            if (noPage) {
                // No page setup at all: A4, which is what an Indian letterpad is.
                var size = body.addNewSectPr().addNewPgSz();
                size.setW(java.math.BigInteger.valueOf(11906));
                size.setH(java.math.BigInteger.valueOf(16838));
            }
            if (original.getStyles() == null || noPage) {
                if (original.getStyles() == null) original.createStyles();
                ByteArrayOutputStream fixed = new ByteArrayOutputStream();
                original.write(fixed);
                doc = new XWPFDocument(new ByteArrayInputStream(fixed.toByteArray()));
            }
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            PdfConverter.getInstance().convert(doc, pdf, PdfOptions.create());
            return fromPdf(pdf.toByteArray());
        }
    }

    /** PNG keeps a letterpad's thin rules crisp; a photographic one that would be too large goes as JPEG. */
    private static Image encode(BufferedImage page) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(page, "png", png);
        if (png.size() <= MAX_IMAGE_BYTES) return new Image(png.toByteArray(), "image/png");

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.88f);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        try (var out = ImageIO.createImageOutputStream(jpg)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(page, null, null), param);
        } finally {
            writer.dispose();
        }
        return new Image(jpg.toByteArray(), "image/jpeg");
    }
}
