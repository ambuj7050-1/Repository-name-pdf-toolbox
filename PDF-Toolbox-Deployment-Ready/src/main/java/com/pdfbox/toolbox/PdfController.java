package com.pdfbox.toolbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(origins = "*")
public class PdfController {

    private ResponseEntity<ByteArrayResource> download(byte[] data, String filename, String contentType) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(contentType));
        h.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        h.setContentLength(data.length);
        return ResponseEntity.ok().headers(h).body(new ByteArrayResource(data));
    }

    @PostMapping("/merge")
    public ResponseEntity<ByteArrayResource> merge(@RequestParam("files") MultipartFile[] files) throws Exception {
        if (files.length < 2) throw new IllegalArgumentException("Select at least two PDF files.");
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);

        for (MultipartFile f : files) {
            if (!isPdf(f)) throw new IllegalArgumentException("Only PDF files are allowed.");
            merger.addSource(new RandomAccessReadBuffer(f.getBytes()));
        }
        merger.mergeDocuments(null);
        return download(out.toByteArray(), "merged.pdf", "application/pdf");
    }

    @PostMapping("/split")
    public ResponseEntity<ByteArrayResource> split(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "1") int from,
            @RequestParam(required = false) Integer to) throws Exception {

        byte[] input = file.getBytes();
        try (PDDocument source = Loader.loadPDF(input)) {
            int total = source.getNumberOfPages();
            if (from < 1 || from > total) throw new IllegalArgumentException("Invalid start page.");
            int end = to == null ? from : to;
            if (end < from || end > total) throw new IllegalArgumentException("Invalid end page.");

            try (PDDocument out = new PDDocument()) {
                for (int i = from - 1; i < end; i++) {
                    out.addPage(source.getPage(i));
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                out.save(bos);
                return download(bos.toByteArray(), "split.pdf", "application/pdf");
            }
        }
    }

    @PostMapping("/rotate")
    public ResponseEntity<ByteArrayResource> rotate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "90") int degrees) throws Exception {

        int d = ((degrees % 360) + 360) % 360;
        if (d != 0 && d != 90 && d != 180 && d != 270)
            throw new IllegalArgumentException("Rotation must be 0, 90, 180 or 270.");

        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            for (PDPage page : doc.getPages()) {
                page.setRotation((page.getRotation() + d) % 360);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return download(out.toByteArray(), "rotated.pdf", "application/pdf");
        }
    }

    @PostMapping("/delete-pages")
    public ResponseEntity<ByteArrayResource> deletePages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pages") String pages) throws Exception {

        Set<Integer> delete = parsePages(pages);
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            List<PDPage> remove = new ArrayList<>();
            for (int p : delete) {
                if (p >= 1 && p <= doc.getNumberOfPages()) remove.add(doc.getPage(p - 1));
            }
            remove.forEach(doc::removePage);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return download(out.toByteArray(), "pages-removed.pdf", "application/pdf");
        }
    }

    @PostMapping("/extract-pages")
    public ResponseEntity<ByteArrayResource> extractPages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pages") String pages) throws Exception {

        List<Integer> wanted = new ArrayList<>(parsePages(pages));
        Collections.sort(wanted);

        try (PDDocument source = Loader.loadPDF(file.getBytes());
             PDDocument out = new PDDocument()) {
            for (int p : wanted) {
                if (p >= 1 && p <= source.getNumberOfPages())
                    out.addPage(source.getPage(p - 1));
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out.save(bos);
            return download(bos.toByteArray(), "extracted-pages.pdf", "application/pdf");
        }
    }

    @PostMapping("/compress")
    public ResponseEntity<ByteArrayResource> compress(@RequestParam("file") MultipartFile file) throws Exception {
        /*
         * PDFBox does not provide a one-click "maximum compression" operation.
         * This implementation removes some redundant document objects by saving
         * the document again. Image recompression is intentionally conservative.
         */
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return download(out.toByteArray(), "compressed.pdf", "application/pdf");
        }
    }

    @PostMapping("/pdf-to-jpg")
    public ResponseEntity<ByteArrayResource> pdfToJpg(@RequestParam("file") MultipartFile file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();

            try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, 120, ImageType.RGB);
                    ByteArrayOutputStream jpg = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", jpg);

                    zip.putNextEntry(new ZipEntry("page-" + (i + 1) + ".jpg"));
                    zip.write(jpg.toByteArray());
                    zip.closeEntry();
                }
            }
            return download(zipBytes.toByteArray(), "pdf-pages.zip", "application/zip");
        }
    }

    @PostMapping("/jpg-to-pdf")
    public ResponseEntity<ByteArrayResource> jpgToPdf(@RequestParam("files") MultipartFile[] files) throws Exception {
        if (files.length == 0) throw new IllegalArgumentException("Select at least one image.");

        try (PDDocument doc = new PDDocument()) {
            for (MultipartFile f : files) {
                BufferedImage image = ImageIO.read(f.getInputStream());
                if (image == null) throw new IllegalArgumentException("Invalid image: " + f.getOriginalFilename());

                PDPage page = new PDPage();
                doc.addPage(page);

                try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    var pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, image);
                    float pageW = page.getMediaBox().getWidth();
                    float pageH = page.getMediaBox().getHeight();
                    float scale = Math.min(pageW / image.getWidth(), pageH / image.getHeight());
                    float w = image.getWidth() * scale;
                    float h = image.getHeight() * scale;
                    content.drawImage(pdImage, (pageW - w) / 2, (pageH - h) / 2, w, h);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return download(out.toByteArray(), "images.pdf", "application/pdf");
        }
    }

    private boolean isPdf(MultipartFile f) {
        String n = Optional.ofNullable(f.getOriginalFilename()).orElse("").toLowerCase();
        return n.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(f.getContentType());
    }

    private Set<Integer> parsePages(String raw) {
        Set<Integer> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            if (token.contains("-")) {
                String[] p = token.split("-", 2);
                int a = Integer.parseInt(p[0].trim());
                int b = Integer.parseInt(p[1].trim());
                if (a > b) { int t = a; a = b; b = t; }
                for (int i = a; i <= b; i++) result.add(i);
            } else {
                result.add(Integer.parseInt(token));
            }
        }
        return result;
    }
}
