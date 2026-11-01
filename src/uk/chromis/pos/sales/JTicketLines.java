package uk.chromis.pos.sales;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.forms.AppLocal;
import uk.chromis.pos.forms.ChromisFonts;
import uk.chromis.pos.scripting.ScriptEngine;
import uk.chromis.pos.scripting.ScriptException;
import uk.chromis.pos.scripting.ScriptFactory;
import uk.chromis.pos.ticket.TicketLineInfo;

public class JTicketLines extends JPanel {
   private static final Logger logger = Logger.getLogger("uk.chromis.pos.sales.JTicketLines");
   private static SAXParser m_sp = null;
   private final JTicketLines.TicketTableModel m_jTableModel;
   private final Color bgcolour;
   private final Color selbgcolour;
   private final Color fgcolour;
   private final Color selfgcolour;
   private Color waitingSelected = Color.decode(SystemProperty.WAITINGSELECTEDCOLOUR);
   private Color sentSelected = Color.decode(SystemProperty.SENTSELECTEDCOLOUR);
   private Color waitingColour = Color.decode(SystemProperty.WAITINGBACKGROUNDCOLOUR);
   private Color sentColour = Color.decode(SystemProperty.SENTBACKGROUNDCOLOUR);
   private Color scaleColour = Color.decode("#ff8080");
   private final HashMap<Integer, String> processed;
   private JScrollPane m_jScrollTableTicket;
   private JTable m_jTicketTable;
   /** Under the product, when it was added and when its quantity changed (sales screen only, see LineTimes). */
   private boolean showLineTimes = false;

   public JTicketLines(String ticketline) {
      this.initComponents();
      JTicketLines.ColumnTicket[] acolumns = new JTicketLines.ColumnTicket[0];
      if (ticketline != null) {
         try {
            if (m_sp == null) {
               SAXParserFactory spf = SAXParserFactory.newInstance();
               m_sp = spf.newSAXParser();
            }

            JTicketLines.ColumnsHandler columnshandler = new JTicketLines.ColumnsHandler();
            m_sp.parse(new InputSource(new StringReader(ticketline)), columnshandler);
            acolumns = columnshandler.getColumns();
         } catch (ParserConfigurationException var5) {
            logger.log(Level.WARNING, AppLocal.getIntString("exception.parserconfig"), (Throwable)var5);
         } catch (SAXException var6) {
            logger.log(Level.WARNING, AppLocal.getIntString("exception.xmlfile"), (Throwable)var6);
         } catch (IOException var7) {
            logger.log(Level.WARNING, AppLocal.getIntString("exception.iofile"), (Throwable)var7);
         }
      }

      this.m_jTableModel = new JTicketLines.TicketTableModel(acolumns);
      this.m_jTicketTable.setModel(this.m_jTableModel);
      TableColumnModel jColumns = this.m_jTicketTable.getColumnModel();

      for (int i = 0; i < acolumns.length; i++) {
         jColumns.getColumn(i).setPreferredWidth(acolumns[i].width);
         jColumns.getColumn(i).setResizable(false);
      }

      this.m_jScrollTableTicket.getVerticalScrollBar().setPreferredSize(new Dimension(35, 35));
      this.m_jTicketTable.getTableHeader().setReorderingAllowed(false);
      this.m_jTicketTable.setDefaultRenderer(Object.class, new JTicketLines.TicketCellRenderer(acolumns));
      this.m_jTicketTable.setRowHeight(SystemProperty.LINESIZE);
      this.m_jTicketTable.getSelectionModel().setSelectionMode(0);
      this.m_jTableModel.clear();
      this.processed = new HashMap<>();
      this.bgcolour = this.m_jTicketTable.getBackground();
      this.fgcolour = this.m_jTicketTable.getForeground();
      this.selfgcolour = this.m_jTicketTable.getSelectionForeground();
      this.selbgcolour = this.m_jTicketTable.getSelectionBackground();
   }

   /** With the times under the product a row may need more than the usual height; never less. */
   private void fitRowHeight(int row, String productCell) {
      if (!this.showLineTimes || productCell == null || row < 0 || row >= this.m_jTicketTable.getRowCount()) {
         return;
      }

      JLabel probe = new JLabel(productCell);
      probe.setFont(ChromisFonts.DEFAULTFONT);
      javax.swing.text.View view = (javax.swing.text.View)probe.getClientProperty("html");
      int needed = SystemProperty.LINESIZE;
      if (view != null) {
         int width = Math.max(this.m_jTicketTable.getColumnModel().getColumn(0).getWidth(),
            this.m_jTicketTable.getColumnModel().getColumn(0).getPreferredWidth());
         view.setSize(Math.max(width - 4, 50), 0.0F);
         needed = Math.max(needed, (int)Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + 4);
      }

      if (this.m_jTicketTable.getRowHeight(row) != needed) {
         this.m_jTicketTable.setRowHeight(row, needed);
      }
   }

   public void setShowLineTimes(boolean show) {
      this.showLineTimes = show;
   }

   public void addListSelectionListener(ListSelectionListener l) {
      this.m_jTicketTable.getSelectionModel().addListSelectionListener(l);
   }

   public void removeListSelectionListener(ListSelectionListener l) {
      this.m_jTicketTable.getSelectionModel().removeListSelectionListener(l);
   }

   public void clearTicketLines() {
      this.m_jTableModel.clear();
   }

   public void setTicketLine(int index, TicketLineInfo oLine) {
      this.m_jTableModel.setRow(index, oLine);
   }

   public void addTicketLine(TicketLineInfo oLine) {
      this.m_jTableModel.addRow(oLine);
      this.setSelectedIndex(this.m_jTableModel.getRowCount() - 1);
   }

   public void insertTicketLine(int index, TicketLineInfo oLine) {
      this.m_jTableModel.insertRow(index, oLine);
      this.setSelectedIndex(index);
   }

   public void removeTicketLine(int i) {
      this.m_jTableModel.removeRow(i);
      int rowcount = this.m_jTableModel.getRowCount();
      if (i < rowcount) {
         for (int j = i; j <= rowcount - 1; j++) {
            this.processed.put(j, this.processed.get(j + 1));
         }

         this.processed.remove(rowcount + 1);
      }

      if (i >= this.m_jTableModel.getRowCount()) {
         i = this.m_jTableModel.getRowCount() - 1;
      }

      if (i >= 0 && i < this.m_jTableModel.getRowCount()) {
         this.setSelectedIndex(i);
      }
   }

   public void setSelectedIndex(int i) {
      this.m_jTicketTable.getSelectionModel().setSelectionInterval(i, i);
      Rectangle oRect = this.m_jTicketTable.getCellRect(i, 0, true);
      this.m_jTicketTable.scrollRectToVisible(oRect);
   }

   public int getSelectedIndex() {
      return this.m_jTicketTable.getSelectionModel().getMinSelectionIndex();
   }

   public void selectionDown() {
      int i = this.m_jTicketTable.getSelectionModel().getMaxSelectionIndex();
      if (i < 0) {
         i = 0;
      } else if (++i >= this.m_jTableModel.getRowCount()) {
         i = this.m_jTableModel.getRowCount() - 1;
      }

      if (i >= 0 && i < this.m_jTableModel.getRowCount()) {
         this.setSelectedIndex(i);
      }
   }

   public void selectionUp() {
      int i = this.m_jTicketTable.getSelectionModel().getMinSelectionIndex();
      if (i < 0) {
         i = this.m_jTableModel.getRowCount() - 1;
      } else if (--i < 0) {
         i = 0;
      }

      if (i >= 0 && i < this.m_jTableModel.getRowCount()) {
         this.setSelectedIndex(i);
      }
   }

   private Color processRowColour(int row, boolean isSelected) {
      if (SystemProperty.COLOURTICKETLINES && this.processed.get(row) != null) {
         String var3 = this.processed.get(row).toLowerCase();
         switch (var3) {
            case "ok":
               if (isSelected) {
                  return this.sentSelected;
               }

               return this.sentColour;
            case "waiting":
               if (isSelected) {
                  return this.waitingSelected;
               }

               return this.waitingColour;
            case "scale":
               if (isSelected) {
                  return this.scaleColour;
               }

               return this.bgcolour;
            default:
               return isSelected ? this.selbgcolour : this.bgcolour;
         }
      } else {
         return isSelected ? this.selbgcolour : this.bgcolour;
      }
   }

   public static Color getContrastColor(Color color) {
      double y = (double)((299 * color.getRed() + 587 * color.getGreen() + 114 * color.getBlue()) / 1000);
      return y >= 128.0 ? Color.black : Color.white;
   }

   private void setProperties(Properties attributes, int index) {
      if (attributes.getProperty("sendstatus") == null) {
         attributes.setProperty("sendstatus", "No");
      }

      this.processed.put(index, attributes.getProperty("sendstatus"));
      if ((Boolean.valueOf(attributes.getProperty("product.kitchen")) || Boolean.valueOf(attributes.getProperty("usedisplay")))
         && attributes.getProperty("sendstatus").equals("No")) {
         this.processed.put(index, "waiting");
      }
   }

   private void initComponents() {
      this.m_jScrollTableTicket = new JScrollPane();
      this.m_jTicketTable = new JTable();
      this.setLayout(new BorderLayout());
      this.m_jScrollTableTicket.setHorizontalScrollBarPolicy(31);
      this.m_jScrollTableTicket.setVerticalScrollBarPolicy(22);
      this.m_jScrollTableTicket.setFocusable(false);
      this.m_jScrollTableTicket.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(22.0F));
      this.m_jTicketTable.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.m_jTicketTable.setFocusable(false);
      this.m_jTicketTable.setIntercellSpacing(new Dimension(0, 1));
      this.m_jTicketTable.setRequestFocusEnabled(false);
      this.m_jTicketTable.setShowVerticalLines(false);
      this.m_jScrollTableTicket.setViewportView(this.m_jTicketTable);
      this.add(this.m_jScrollTableTicket, "Center");
   }

   private static class ColumnTicket {
      public String name;
      public int width;
      public int align;
      public String value;
   }

   private static class ColumnsHandler extends DefaultHandler {
      private ArrayList m_columns = null;

      public JTicketLines.ColumnTicket[] getColumns() {
         return (JTicketLines.ColumnTicket[]) this.m_columns.toArray(new JTicketLines.ColumnTicket[this.m_columns.size()]);
      }

      @Override
      public void startDocument() throws SAXException {
         this.m_columns = new ArrayList();
      }

      @Override
      public void endDocument() throws SAXException {
      }

      @Override
      public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
         if ("column".equals(qName)) {
            JTicketLines.ColumnTicket c = new JTicketLines.ColumnTicket();
            c.name = attributes.getValue("name");
            c.width = Integer.parseInt(attributes.getValue("width"));
            String sAlign = attributes.getValue("align");
            switch (sAlign) {
               case "right":
                  c.align = 4;
                  break;
               case "center":
                  c.align = 0;
                  break;
               default:
                  c.align = 2;
            }

            c.value = attributes.getValue("value");
            this.m_columns.add(c);
         }
      }

      @Override
      public void endElement(String uri, String localName, String qName) throws SAXException {
      }

      @Override
      public void characters(char[] ch, int start, int length) throws SAXException {
      }
   }

   private class TicketCellRenderer extends DefaultTableCellRenderer {
      private final JTicketLines.ColumnTicket[] m_acolumns;

      public TicketCellRenderer(JTicketLines.ColumnTicket[] acolumns) {
         this.m_acolumns = acolumns;
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
         JLabel aux = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
         aux.setVerticalAlignment(1);
         aux.setHorizontalAlignment(this.m_acolumns[column].align);
         aux.setFont(ChromisFonts.DEFAULTFONT);
         this.setBackground(JTicketLines.this.processRowColour(row, isSelected));
         this.setForeground(JTicketLines.getContrastColor(JTicketLines.this.processRowColour(row, isSelected)));
         return aux;
      }
   }

   private class TicketTableModel extends AbstractTableModel {
      private final JTicketLines.ColumnTicket[] m_acolumns;
      private final ArrayList m_rows = new ArrayList();

      public TicketTableModel(JTicketLines.ColumnTicket[] acolumns) {
         this.m_acolumns = acolumns;
      }

      @Override
      public int getRowCount() {
         return this.m_rows.size();
      }

      @Override
      public int getColumnCount() {
         return this.m_acolumns.length;
      }

      @Override
      public String getColumnName(int column) {
         return AppLocal.getIntString(this.m_acolumns[column].name);
      }

      @Override
      public Object getValueAt(int row, int column) {
         return ((String[])this.m_rows.get(row))[column];
      }

      @Override
      public boolean isCellEditable(int row, int column) {
         return false;
      }

      public void clear() {
         int old = this.getRowCount();
         if (old > 0) {
            this.m_rows.clear();
            this.fireTableRowsDeleted(0, old - 1);
         }
      }

      public void setRow(int index, TicketLineInfo oLine) {
         String[] row = (String[])this.m_rows.get(index);

         for (int i = 0; i < this.m_acolumns.length; i++) {
            try {
               ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
               script.put("ticketline", oLine);
               row[i] = script.eval(this.m_acolumns[i].value).toString();
            } catch (ScriptException var6) {
               row[i] = null;
            }

            if (i == 0 && JTicketLines.this.showLineTimes) {
               row[i] = LineTimes.withLabel(row[i], oLine);
            }

            JTicketLines.this.setProperties(oLine.getProperties(), index);
            this.fireTableCellUpdated(index, i);
         }

         JTicketLines.this.fitRowHeight(index, row.length > 0 ? row[0] : null);
      }

      public void addRow(TicketLineInfo oLine) {
         this.insertRow(this.m_rows.size(), oLine);
      }

      public void insertRow(int index, TicketLineInfo oLine) {
         String[] row = new String[this.m_acolumns.length];

         for (int i = 0; i < this.m_acolumns.length; i++) {
            try {
               ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
               script.put("ticketline", oLine);
               row[i] = script.eval(this.m_acolumns[i].value).toString();
            } catch (ScriptException var6) {
               row[i] = null;
            }

            if (i == 0 && JTicketLines.this.showLineTimes) {
               row[i] = LineTimes.withLabel(row[i], oLine);
            }
         }

         JTicketLines.this.setProperties(oLine.getProperties(), index);
         this.m_rows.add(index, row);
         this.fireTableRowsInserted(index, index);
         JTicketLines.this.fitRowHeight(index, row[0]);
      }

      public void removeRow(int row) {
         this.m_rows.remove(row);
         this.fireTableRowsDeleted(row, row);
      }
   }
}
