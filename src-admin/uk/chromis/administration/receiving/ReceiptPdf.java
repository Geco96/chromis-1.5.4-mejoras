package uk.chromis.administration.receiving;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
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
import java.util.Date;
import java.util.List;
import uk.chromis.administration.receiving.PurchaseOrderStore.Line;
import uk.chromis.administration.receiving.PurchaseOrderStore.Order;
import uk.chromis.administration.restock.RestockPdf;

/**
 * Contrarecibo: what was ordered against what arrived, line by line, to give
 * back to the supplier or keep. Shortages are listed as carried to the next
 * order and cost changes as waiting for authorization.
 */
public final class ReceiptPdf {

    private static final String[] HEADERS = {"Referencia", "Producto", "Pedido", "Recibido", "Diferencia",
        "Costo pedido", "Costo factura", "Importe recibido", "Observación"};
    private static final float[] WIDTHS = {80, 230, 55, 60, 60, 70, 70, 80, 140};
    private static final Color SHORT = new Color(255, 217, 217);
    private static final Color EXTRA = new Color(217, 236, 255);
    private static final Color PRICE = new Color(255, 245, 200);

    private ReceiptPdf() {
    }

    public static void write(File file, Order order, String company, String receivedBy) throws IOException, DocumentException {
        Document doc = new Document(PageSize.LETTER.rotate(), 20, 20, 20, 20);
        OutputStream out = new FileOutputStream(file);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font info = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9);
            SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            List<Paragraph> head = new ArrayList<>();
            head.add(RestockPdf.centered(RestockPdf.blank(company) ? "CONTRARECIBO" : company.toUpperCase(), title));
            head.add(RestockPdf.centered("CONTRARECIBO DE MERCANCÍA", subtitle));
            head.add(RestockPdf.centered("Proveedor: " + (order.supplierName == null ? "" : order.supplierName), info));
            head.add(RestockPdf.centered("Pedido del " + (order.orderDate == null ? "" : date.format(order.orderDate))
                    + "  ·  Recibido el " + date.format(order.receiptDate == null ? new Date() : order.receiptDate), info));
            if (!RestockPdf.blank(order.invoiceNo)) {
                head.add(RestockPdf.centered("Factura / remisión: " + order.invoiceNo, info));
            }
            if (!RestockPdf.blank(receivedBy)) {
                head.add(RestockPdf.centered("Recibió: " + receivedBy, info));
            }
            RestockPdf.addHeader(doc, writer, head, order.ponumber);
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(WIDTHS);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : HEADERS) {
                PdfPCell c = new PdfPCell(new Phrase(h, header));
                c.setBackgroundColor(new Color(51, 51, 102));
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                c.setPaddingBottom(5);
                table.addCell(c);
            }
            int shortLines = 0;
            int priceLines = 0;
            double shortUnits = 0;
            for (Line l : order.lines) {
                double d = l.difference();
                Color bg = d < 0 ? SHORT : l.isExtra() || d > 0 ? EXTRA : l.costChanged() ? PRICE : Color.WHITE;
                if (d < 0) {
                    shortLines++;
                    shortUnits -= d;
                }
                if (l.costChanged()) {
                    priceLines++;
                }
                table.addCell(RestockPdf.cell(l.reference, cell, Element.ALIGN_LEFT, bg));
                table.addCell(RestockPdf.cell(l.name, cell, Element.ALIGN_LEFT, bg));
                table.addCell(RestockPdf.cell(l.isExtra() ? "-" : PurchaseOrderStore.format(l.ordered), cell, Element.ALIGN_CENTER, bg));
                table.addCell(RestockPdf.cell(PurchaseOrderStore.format(l.received), cell, Element.ALIGN_CENTER, bg));
                table.addCell(RestockPdf.cell(d == 0 ? "" : (d > 0 ? "+" : "") + PurchaseOrderStore.format(d), cell,
                        Element.ALIGN_CENTER, bg));
                table.addCell(RestockPdf.cell(PurchaseOrderStore.money(l.orderCost), cell, Element.ALIGN_RIGHT, bg));
                table.addCell(RestockPdf.cell(PurchaseOrderStore.money(l.receivedCost), cell, Element.ALIGN_RIGHT, bg));
                table.addCell(RestockPdf.cell(PurchaseOrderStore.money(l.received * l.receivedCost), cell, Element.ALIGN_RIGHT, bg));
                table.addCell(RestockPdf.cell(l.observation(), cell, Element.ALIGN_LEFT, bg));
            }
            doc.add(table);

            PdfPTable totals = new PdfPTable(new float[]{3, 1});
            totals.setWidthPercentage(40);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(8);
            total(totals, "Total del pedido", PurchaseOrderStore.total(order.lines, false), info);
            total(totals, "Total recibido", PurchaseOrderStore.total(order.lines, true), bold);
            doc.add(totals);

            doc.add(new Paragraph(" "));
            if (shortLines == 0 && priceLines == 0 && order.lines.stream().noneMatch(l -> l.difference() > 0)) {
                doc.add(new Paragraph("Se recibió exactamente lo solicitado en el pedido.", bold));
            }
            if (shortLines > 0) {
                doc.add(new Paragraph("Faltantes: " + shortLines + " producto(s), " + PurchaseOrderStore.format(shortUnits)
                        + " unidad(es). Se agregarán al siguiente pedido.", bold));
            }
            if (priceLines > 0) {
                doc.add(new Paragraph("Cambios de costo: " + priceLines + " producto(s). El costo en Chromis no cambia "
                        + "hasta que se autorice en Admin, menú Autorizar costos.", bold));
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            PdfPTable signs = new PdfPTable(2);
            signs.setWidthPercentage(80);
            sign(signs, "Entregó (proveedor)", info);
            sign(signs, "Recibió", info);
            doc.add(signs);
            doc.close();
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
            out.close();
        }
    }

    private static void total(PdfPTable t, String label, double value, Font font) {
        PdfPCell a = new PdfPCell(new Phrase(label, font));
        a.setBorder(Rectangle.NO_BORDER);
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        PdfPCell b = new PdfPCell(new Phrase("$ " + PurchaseOrderStore.money(value), font));
        b.setBorder(Rectangle.NO_BORDER);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(b);
    }

    private static void sign(PdfPTable t, String label, Font font) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.addElement(RestockPdf.centered("______________________________", font));
        c.addElement(RestockPdf.centered(label, font));
        t.addCell(c);
    }
}
