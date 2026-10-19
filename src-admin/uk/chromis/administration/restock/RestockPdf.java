package uk.chromis.administration.restock;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import uk.chromis.administration.receiving.PurchaseOrderStore;
import uk.chromis.pos.restock.RestockStore.Status;

/**
 * Restock order as PDF, the same layout the branch monitor (Lite) printed,
 * plus the order number as a Code 128 barcode: scanning it in Recepción de
 * mercancía loads the whole order from the database.
 */
public final class RestockPdf {

    /** One product of the order; current / minimum / maximum may be null when unknown. */
    public static final class Line {

        public String reference;
        public String name;
        public Double current;
        public Double minimum;
        public Double maximum;
        public double qty;
        public double cost;
        public Status status;
        public String note;

        public static Line of(RestockItem item, String note) {
            Line l = new Line();
            l.reference = item.getReference();
            l.name = item.getName();
            l.current = item.getRow().current;
            l.minimum = item.getRow().minimum;
            l.maximum = item.getRow().maximum;
            l.qty = item.orderProperty().get();
            l.status = item.getStatus();
            l.note = note;
            return l;
        }
    }

    private RestockPdf() {
    }

    public static void write(File file, List<Line> lines, String company, String location, String user,
            String orderNumber, String supplier) throws IOException, DocumentException {
        List<Line> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(i -> i.name == null ? "" : i.name.toLowerCase()));
        boolean notes = sorted.stream().anyMatch(l -> l.note != null && !l.note.isEmpty());
        boolean costs = sorted.stream().anyMatch(l -> l.cost > 0);

        List<String> headers = new ArrayList<>(List.of("Referencia", "Producto", "Stock actual", "Mínimo", "Máximo", "Pedir"));
        List<Float> widths = new ArrayList<>(List.of(90f, 300f, 70f, 60f, 60f, 60f));
        if (costs) {
            headers.add("Costo unit.");
            widths.add(70f);
        }
        headers.add("Estado");
        widths.add(70f);
        if (notes) {
            headers.add("Nota");
            widths.add(150f);
        }
        float[] w = new float[widths.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = widths.get(i);
        }

        Document doc = new Document(PageSize.LETTER.rotate(), 20, 20, 20, 20);
        // Closing the document also closes the file; it has to happen before the stream is closed
        OutputStream out = new FileOutputStream(file);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font info = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9);

            List<Paragraph> head = new ArrayList<>();
            head.add(centered(blank(company) ? "PEDIDO DE REPOSICIÓN" : company.toUpperCase(), title));
            head.add(centered("PEDIDO DE REPOSICIÓN DE STOCK" + (blank(location) ? "" : " - " + location.toUpperCase()), subtitle));
            if (!blank(supplier)) {
                head.add(centered("Proveedor: " + supplier, info));
            }
            head.add(centered("Generado el: " + new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm").format(new Date()), info));
            if (!blank(user)) {
                head.add(centered("Solicitado por: " + user, info));
            }
            addHeader(doc, writer, head, orderNumber);
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(w);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, header));
                c.setBackgroundColor(new Color(51, 51, 102));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setPaddingBottom(6);
                table.addCell(c);
            }
            int n = 0;
            for (Line l : sorted) {
                Color bg = background(l.status, n++);
                table.addCell(cell(l.reference, cell, Element.ALIGN_LEFT, bg));
                table.addCell(cell(l.name, cell, Element.ALIGN_LEFT, bg));
                table.addCell(cell(number(l.current), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(number(l.minimum), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(number(l.maximum), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(PurchaseOrderStore.format(l.qty), cell, Element.ALIGN_CENTER, bg));
                if (costs) {
                    table.addCell(cell(l.cost > 0 ? PurchaseOrderStore.money(l.cost) : "", cell, Element.ALIGN_RIGHT, bg));
                }
                table.addCell(cell(l.status == null ? "" : l.status.getLabel().toUpperCase(), cell, Element.ALIGN_CENTER, bg));
                if (notes) {
                    table.addCell(cell(l.note, cell, Element.ALIGN_LEFT, bg));
                }
            }
            doc.add(table);

            doc.add(new Paragraph(" "));
            if (!blank(orderNumber)) {
                doc.add(centered("Al recibir la mercancía, escanee el código del pedido en Chromis Admin, menú Recepción de mercancía.", info));
            }
            doc.add(new Paragraph(" "));
            doc.add(centered("__________________________", info));
            doc.add(centered("Firma de autorización", info));
            doc.close();
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
            out.close();
        }
    }

    /** Titles on the left and, when there is an order number, its barcode on the right. */
    public static void addHeader(Document doc, PdfWriter writer, List<Paragraph> titles, String orderNumber)
            throws DocumentException {
        if (blank(orderNumber)) {
            for (Paragraph p : titles) {
                doc.add(p);
            }
            return;
        }
        PdfPTable t = new PdfPTable(new float[]{3f, 1.2f});
        t.setWidthPercentage(100);
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        for (Paragraph p : titles) {
            left.addElement(p);
        }
        t.addCell(left);
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph label = centered("PEDIDO " + orderNumber, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        label.setSpacingAfter(4);
        right.addElement(label);
        Paragraph code = new Paragraph();
        code.setAlignment(Element.ALIGN_CENTER);
        code.add(new com.lowagie.text.Chunk(barcode(writer.getDirectContent(), orderNumber), 0, 0, true));
        right.addElement(code);
        t.addCell(right);
        doc.add(t);
    }

    public static Image barcode(PdfContentByte cb, String text) {
        Barcode128 bc = new Barcode128();
        bc.setCode(text);
        bc.setBarHeight(38f);
        bc.setX(1.1f);
        return bc.createImageWithBarcode(cb, null, null);
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String number(Double v) {
        return v == null ? "-" : PurchaseOrderStore.format(v);
    }

    public static Paragraph centered(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    public static PdfPCell cell(String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(bg);
        c.setPadding(4);
        return c;
    }

    private static Color background(Status status, int index) {
        if (status != null) {
            switch (status) {
                case AGOTADO:
                    return new Color(217, 217, 217);
                case BAJO:
                    return new Color(255, 217, 217);
                case RESERVA:
                    return new Color(255, 255, 217);
                default:
                    break;
            }
        }
        return index % 2 == 1 ? new Color(242, 242, 242) : Color.WHITE;
    }
}
