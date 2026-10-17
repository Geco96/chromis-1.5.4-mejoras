package uk.chromis.administration.restock;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
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
import uk.chromis.pos.restock.RestockStore.Status;

/** Restock order as PDF, the same layout the branch monitor (Lite) printed. */
public final class RestockPdf {

    private static final String[] HEADERS = {"Referencia", "Producto", "Stock actual", "Mínimo", "Máximo", "Pedir", "Estado"};
    private static final float[] WIDTHS = {90, 300, 70, 60, 60, 60, 70};

    private RestockPdf() {
    }

    public static void write(File file, List<RestockItem> items, String company, String location, String user)
            throws IOException, DocumentException {
        List<RestockItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(i -> i.getName() == null ? "" : i.getName().toLowerCase()));

        Document doc = new Document(PageSize.LETTER.rotate(), 20, 20, 20, 20);
        // Closing the document also closes the file; it has to happen before the stream is closed
        OutputStream out = new FileOutputStream(file);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font info = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9);

            centered(doc, blank(company) ? "PEDIDO DE REPOSICIÓN" : company.toUpperCase(), title);
            String sub = "PEDIDO DE REPOSICIÓN DE STOCK" + (blank(location) ? "" : " - " + location.toUpperCase());
            centered(doc, sub, subtitle);
            centered(doc, "Generado el: " + new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm").format(new Date()), info);
            if (!blank(user)) {
                centered(doc, "Solicitado por: " + user, info);
            }
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(WIDTHS);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : HEADERS) {
                PdfPCell c = new PdfPCell(new Phrase(h, header));
                c.setBackgroundColor(new Color(51, 51, 102));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setPaddingBottom(6);
                table.addCell(c);
            }
            int n = 0;
            for (RestockItem item : sorted) {
                Color bg = background(item.getStatus(), n++);
                table.addCell(cell(item.getReference(), cell, Element.ALIGN_LEFT, bg));
                table.addCell(cell(item.getName(), cell, Element.ALIGN_LEFT, bg));
                table.addCell(cell(RestockItem.format(item.getRow().current), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(RestockItem.format(item.getRow().minimum), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(RestockItem.format(item.getRow().maximum), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(RestockItem.format(item.orderProperty().get()), cell, Element.ALIGN_CENTER, bg));
                table.addCell(cell(item.getStatus().getLabel().toUpperCase(), cell, Element.ALIGN_CENTER, bg));
            }
            doc.add(table);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            centered(doc, "__________________________", info);
            centered(doc, "Firma de autorización", info);
            doc.close();
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
            out.close();
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void centered(Document doc, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private static PdfPCell cell(String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(bg);
        c.setPadding(4);
        return c;
    }

    private static Color background(Status status, int index) {
        switch (status) {
            case AGOTADO:
                return new Color(217, 217, 217);
            case BAJO:
                return new Color(255, 217, 217);
            case RESERVA:
                return new Color(255, 255, 217);
            default:
                return index % 2 == 1 ? new Color(242, 242, 242) : Color.WHITE;
        }
    }
}
