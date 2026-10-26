package uk.chromis.pos.panels;

import java.sql.SQLException;
import uk.chromis.pos.attendance.AutoAttendance;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Box.Filler;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import uk.chromis.basic.BasicException;
import uk.chromis.commons.dialogs.JAlertPane;
import uk.chromis.commons.utils.TerminalInfo;
import uk.chromis.data.gui.MessageInf;
import uk.chromis.data.gui.TableRendererBasic;
import uk.chromis.format.Formats;
import uk.chromis.globals.CompanyInfo;
import uk.chromis.globals.IconFactory;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.datalogic.DataLogicSystem;
import uk.chromis.pos.forms.AppLocal;
import uk.chromis.pos.forms.AppView;
import uk.chromis.pos.forms.BeanFactoryApp;
import uk.chromis.pos.forms.BeanFactoryException;
import uk.chromis.pos.forms.ChromisFonts;
import uk.chromis.pos.forms.JPanelView;
import uk.chromis.pos.printer.IncludeFile;
import uk.chromis.pos.printer.TicketParser;
import uk.chromis.pos.printer.TicketPrinterException;
import uk.chromis.pos.scripting.ScriptEngine;
import uk.chromis.pos.scripting.ScriptException;
import uk.chromis.pos.scripting.ScriptFactory;

public class JPanelCloseMoney extends JPanel implements JPanelView, BeanFactoryApp {
   private AppView m_App;
   private DataLogicSystem dbQuery;
   private Integer result;
   private List<HourlySalesInfo> hSales;
   private List<UserSales> userSales;
   private PaymentsModel m_PaymentsToClose = null;
   private TicketParser m_TTP;
   private Boolean reload = false;
   private JButton btnReloadDay;
   private JButton btnResetDay;
   private Filler filler1;
   private JLabel jLabel1;
   private JLabel jLabel11;
   private JLabel jLabel12;
   private JLabel jLabel2;
   private JLabel jLabel3;
   private JLabel jLabel4;
   private JLabel jLabel5;
   private JLabel jLabel6;
   private JLabel jLabel7;
   private JLabel jLabel8;
   private JLabel jLabel9;
   private JLabel jLabelCards;
   private JPanel jPanel1;
   private JPanel jPanel4;
   private JPanel jPanel5;
   private JPanel jPanelBottom;
   private JSeparator jSeparator1;
   private JTextField m_jCards;
   private JTextField m_jCash;
   private JButton m_jCloseCash;
   private JTextField m_jCount;
   private JTextField m_jGiftCards;
   private JTextField m_jMaxDate;
   private JTextField m_jMinDate;
   private JTextField m_jNoCashSales;
   private JButton m_jPreviewCash;
   private JButton m_jPrintCash;
   private JTextField m_jSales;
   private JTextField m_jSalesSubtotal;
   private JTextField m_jSalesTaxes;
   private JTextField m_jSalesTotal;
   private JScrollPane m_jScrollSales;
   private JScrollPane m_jScrollTableTicket;
   private JTextField m_jSequence;
   private JTable m_jTicketTable;
   private JTable m_jsalestable;

   public JPanelCloseMoney() {
      this.initComponents();
   }

   /** Cash whose categories and receipt count are printed: the one just closed or reloaded; null = the active cash. */
   private String printMoney;

   public void init(AppView app) throws BeanFactoryException {
      this.dbQuery = (DataLogicSystem)app.getBean("uk.chromis.pos.datalogic.DataLogicSystem");
      this.m_App = app;
      this.m_TTP = new TicketParser(this.m_App.getDeviceTicket(), this.dbQuery);
      this.m_jTicketTable.setDefaultRenderer(Object.class, new TableRendererBasic(new Formats[]{new JPanelCloseMoney.FormatsPayment(), Formats.CURRENCY}));
      this.m_jTicketTable.setAutoResizeMode(0);
      this.m_jScrollTableTicket.getVerticalScrollBar().setPreferredSize(new Dimension(25, 25));
      this.m_jTicketTable.getTableHeader().setReorderingAllowed(false);
      this.m_jTicketTable.setRowHeight(25);
      this.m_jTicketTable.getSelectionModel().setSelectionMode(0);
      this.m_jsalestable
         .setDefaultRenderer(Object.class, new TableRendererBasic(new Formats[]{Formats.STRING, Formats.CURRENCY, Formats.CURRENCY, Formats.CURRENCY}));
      this.m_jsalestable.setAutoResizeMode(0);
      this.m_jScrollSales.getVerticalScrollBar().setPreferredSize(new Dimension(25, 25));
      this.m_jsalestable.getTableHeader().setReorderingAllowed(false);
      this.m_jsalestable.setRowHeight(25);
      this.m_jsalestable.getSelectionModel().setSelectionMode(0);
      this.m_jCloseCash.setVisible(true);
      this.jPanelBottom.setVisible(true);
      this.jLabelCards.setVisible(SystemProperty.HANDLINGFEES);
      this.m_jCards.setVisible(SystemProperty.HANDLINGFEES);
   }

   public Object getBean() {
      return this;
   }

   public JComponent getComponent() {
      return this;
   }

   public String getTitle() {
      return AppLocal.getIntString("menu.closeCash");
   }

   public void activate() throws BasicException {
      this.loadData();
   }

   public boolean deactivate() {
      return true;
   }

   private void loadData() throws BasicException {
      this.reload = false;
      this.printMoney = null;
      this.clearFields();
      this.m_PaymentsToClose = PaymentsModel.loadInstance(this.m_App);
      this.populateData();
      if (this.m_PaymentsToClose.getPayments() != 0 || this.m_PaymentsToClose.getSales() != 0) {
         this.m_jCloseCash.setEnabled(true);
      }

      this.hSales = this.dbQuery.getHourlySales(this.m_App.getActiveCashIndex());
      this.userSales = this.dbQuery.getUserSales(this.m_App.getActiveCashIndex());
   }

   /** Empties the screen; populateData only fills the totals of a cash with sales. */
   private void clearFields() {
      this.m_jSequence.setText(null);
      this.m_jMinDate.setText(null);
      this.m_jMaxDate.setText(null);
      this.m_jPrintCash.setEnabled(false);
      this.m_jCloseCash.setEnabled(false);
      this.m_jPreviewCash.setEnabled(false);
      this.m_jCount.setText(null);
      this.m_jCash.setText(null);
      this.m_jSales.setText(null);
      this.m_jSalesSubtotal.setText(null);
      this.m_jSalesTaxes.setText(null);
      this.m_jSalesTotal.setText(null);
      this.m_jGiftCards.setText(null);
      this.m_jCards.setText(null);
      this.m_jTicketTable.setModel(new DefaultTableModel());
      this.m_jsalestable.setModel(new DefaultTableModel());
   }

   private void populateData() {
      this.m_jSequence.setText(this.m_PaymentsToClose.printSequence());
      this.m_jMinDate.setText(this.m_PaymentsToClose.printDateStart());
      this.m_jMaxDate.setText(this.m_PaymentsToClose.printDateEnd());
      if (this.m_PaymentsToClose.getPayments() != 0 || this.m_PaymentsToClose.getSales() != 0) {
         this.m_jPrintCash.setEnabled(true);
         this.m_jPreviewCash.setEnabled(true);
         this.m_jCount.setText(this.m_PaymentsToClose.printPayments());
         this.m_jCash.setText(this.m_PaymentsToClose.printPaymentsTotal());
         this.m_jSales.setText(this.m_PaymentsToClose.printSales());
         this.m_jSalesSubtotal.setText(this.m_PaymentsToClose.printSalesBase());
         this.m_jSalesTaxes.setText(this.m_PaymentsToClose.printSalesTaxes());
         this.m_jSalesTotal.setText(this.m_PaymentsToClose.printSalesTotal());
         this.m_jGiftCards.setText(this.m_PaymentsToClose.printGiftCardSalesTotal());
         this.m_jCards.setText(this.m_PaymentsToClose.printCardFees());
      }

      this.m_jTicketTable.setModel(this.m_PaymentsToClose.getPaymentsModel());
      TableColumnModel jColumns = this.m_jTicketTable.getColumnModel();
      jColumns.getColumn(0).setPreferredWidth(178);
      jColumns.getColumn(0).setResizable(false);
      jColumns.getColumn(1).setPreferredWidth(80);
      jColumns.getColumn(1).setResizable(false);
      this.m_jsalestable.setModel(this.m_PaymentsToClose.getSalesModel());
      DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
      centerRenderer.setHorizontalAlignment(0);
      jColumns = this.m_jsalestable.getColumnModel();
      jColumns.getColumn(0).setPreferredWidth(108);
      jColumns.getColumn(0).setResizable(false);
      jColumns.getColumn(1).setPreferredWidth(60);
      jColumns.getColumn(1).setResizable(false);
      jColumns.getColumn(1).setCellRenderer(centerRenderer);
      jColumns.getColumn(2).setPreferredWidth(75);
      jColumns.getColumn(2).setResizable(false);
      jColumns.getColumn(3).setPreferredWidth(75);
      jColumns.getColumn(3).setResizable(false);
      this.result = this.dbQuery.getNoSales(this.m_PaymentsToClose.getStartDate());
      this.m_jNoCashSales.setText(this.result.toString());
   }

   public String printUser() {
      return this.m_App.getAppUserView().getUser().getName();
   }

   private void printPayments(String report) {
      String source = this.dbQuery.getResourceAsXML(report);
      IncludeFile incFile = new IncludeFile(source, this.dbQuery);
      List<String> categories = new ArrayList<>();

      try {
         categories = this.dbQuery.getParentCategories(this.printMoney != null ? this.printMoney : this.m_App.getActiveCashIndex());
      } catch (BasicException var9) {
      }

      if (source == null) {
         MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotprintticket"));
         msg.show(this);
      } else {
         String sresource = incFile.processInclude();

         try {
            ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
            script.put("payments", this.m_PaymentsToClose);
            script.put("nosales", this.result);
            script.put("hourlysales", this.hSales);
            script.put("user", this.printUser());
            script.put("sales", this.dbQuery.getReceiptCount(this.printMoney != null ? this.printMoney : this.m_App.getActiveCashIndex()));
            script.put("usersales", this.userSales);
            script.put("categories", categories);
            script.put("company", new CompanyInfo());
            this.m_TTP.printTicket(script.eval(sresource).toString());
         } catch (TicketPrinterException | ScriptException var8) {
            MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotprintticket"), var8);
            msg.show(this);
         }
      }
   }

   private void initComponents() {
      this.jPanel1 = new JPanel();
      this.jPanel4 = new JPanel();
      this.jLabel11 = new JLabel();
      this.m_jSequence = new JTextField();
      this.jLabel2 = new JLabel();
      this.m_jMinDate = new JTextField();
      this.jLabel3 = new JLabel();
      this.m_jMaxDate = new JTextField();
      this.filler1 = new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(32767, 0));
      this.jPanel5 = new JPanel();
      this.m_jScrollTableTicket = new JScrollPane();
      this.m_jTicketTable = new JTable();
      this.jLabel1 = new JLabel();
      this.m_jCount = new JTextField();
      this.jLabel4 = new JLabel();
      this.m_jCash = new JTextField();
      this.jLabel5 = new JLabel();
      this.m_jSales = new JTextField();
      this.jLabel6 = new JLabel();
      this.m_jSalesSubtotal = new JTextField();
      this.m_jScrollSales = new JScrollPane();
      this.m_jsalestable = new JTable();
      this.m_jSalesTaxes = new JTextField();
      this.jLabel12 = new JLabel();
      this.jLabel7 = new JLabel();
      this.m_jSalesTotal = new JTextField();
      this.jLabel8 = new JLabel();
      this.m_jNoCashSales = new JTextField();
      this.jSeparator1 = new JSeparator();
      this.m_jGiftCards = new JTextField();
      this.jLabel9 = new JLabel();
      this.jLabelCards = new JLabel();
      this.m_jCards = new JTextField();
      this.jPanelBottom = new JPanel();
      this.m_jPrintCash = new JButton();
      this.m_jPreviewCash = new JButton();
      this.m_jCloseCash = new JButton();
      this.btnReloadDay = new JButton();
      this.btnResetDay = new JButton();
      this.setLayout(new BorderLayout());
      this.jPanel1.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.jPanel1.setPreferredSize(new Dimension(931, 650));
      this.jPanel4.setBorder(BorderFactory.createTitledBorder(null, AppLocal.getIntString("label.datestitle"), 0, 0, ChromisFonts.DEFAULTFONTBOLD));
      this.jLabel11.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel11.setText(AppLocal.getIntString("label.sequence"));
      this.m_jSequence.setEditable(false);
      this.m_jSequence.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jSequence.setHorizontalAlignment(4);
      this.jLabel2.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel2.setHorizontalAlignment(4);
      this.jLabel2.setText(AppLocal.getIntString("label.startDate"));
      this.m_jMinDate.setEditable(false);
      this.m_jMinDate.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jMinDate.setHorizontalAlignment(4);
      this.jLabel3.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel3.setHorizontalAlignment(4);
      this.jLabel3.setText(AppLocal.getIntString("label.endDate"));
      this.m_jMaxDate.setEditable(false);
      this.m_jMaxDate.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jMaxDate.setHorizontalAlignment(4);
      GroupLayout jPanel4Layout = new GroupLayout(this.jPanel4);
      this.jPanel4.setLayout(jPanel4Layout);
      jPanel4Layout.setHorizontalGroup(
         jPanel4Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               jPanel4Layout.createSequentialGroup()
                  .addContainerGap(48, 32767)
                  .addComponent(this.jLabel11, -2, 80, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jSequence, -2, 80, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.jLabel2, -2, 74, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jMinDate, -2, 160, -2)
                  .addGap(10, 10, 10)
                  .addComponent(this.jLabel3, -2, 80, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jMaxDate, -2, 160, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.filler1, -2, 0, -2)
                  .addContainerGap()
            )
      );
      jPanel4Layout.setVerticalGroup(
         jPanel4Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               jPanel4Layout.createSequentialGroup()
                  .addGroup(
                     jPanel4Layout.createParallelGroup(Alignment.LEADING)
                        .addGroup(
                           jPanel4Layout.createSequentialGroup()
                              .addGroup(
                                 jPanel4Layout.createParallelGroup(Alignment.BASELINE)
                                    .addComponent(this.jLabel11, -2, 25, -2)
                                    .addComponent(this.m_jSequence, -2, 25, -2)
                                    .addComponent(this.jLabel2, -2, 25, -2)
                                    .addComponent(this.m_jMinDate, -2, 25, -2)
                                    .addComponent(this.jLabel3, -2, 25, -2)
                                    .addComponent(this.m_jMaxDate, -2, 25, -2)
                              )
                              .addGap(0, 5, 32767)
                        )
                        .addGroup(jPanel4Layout.createSequentialGroup().addContainerGap().addComponent(this.filler1, -1, -1, 32767))
                  )
                  .addContainerGap()
            )
      );
      this.jPanel5.setBorder(BorderFactory.createTitledBorder(null, AppLocal.getIntString("label.paymentstitle"), 0, 0, ChromisFonts.DEFAULTFONTBOLD));
      this.jPanel5.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.jPanel5.setPreferredSize(new Dimension(879, 480));
      this.m_jScrollTableTicket.setBorder(null);
      this.m_jScrollTableTicket.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jScrollTableTicket.setMinimumSize(new Dimension(350, 140));
      this.m_jScrollTableTicket.setPreferredSize(new Dimension(350, 140));
      this.m_jTicketTable.setFont(ChromisFonts.DEFAULTFONT.deriveFont(15.0F));
      this.m_jTicketTable.setModel(new DefaultTableModel(new Object[0][], new String[0]));
      this.m_jTicketTable.setFocusable(false);
      this.m_jTicketTable.setIntercellSpacing(new Dimension(0, 1));
      this.m_jTicketTable.setRequestFocusEnabled(false);
      this.m_jScrollTableTicket.setViewportView(this.m_jTicketTable);
      this.jLabel1.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel1.setText(AppLocal.getIntString("label.transactions"));
      this.m_jCount.setEditable(false);
      this.m_jCount.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jCount.setHorizontalAlignment(4);
      this.jLabel4.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.jLabel4.setText(AppLocal.getIntString("label.Money"));
      this.m_jCash.setEditable(false);
      this.m_jCash.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jCash.setHorizontalAlignment(4);
      this.jLabel5.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel5.setText(AppLocal.getIntString("label.sales"));
      this.m_jSales.setEditable(false);
      this.m_jSales.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jSales.setHorizontalAlignment(4);
      this.jLabel6.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel6.setText(AppLocal.getIntString("label.totalnet"));
      this.m_jSalesSubtotal.setEditable(false);
      this.m_jSalesSubtotal.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jSalesSubtotal.setHorizontalAlignment(4);
      this.m_jScrollSales.setBorder(null);
      this.m_jScrollSales.setFont(ChromisFonts.DEFAULTFONT.deriveFont(16.0F));
      this.m_jsalestable.setFont(ChromisFonts.DEFAULTFONT.deriveFont(15.0F));
      this.m_jsalestable.setFocusable(false);
      this.m_jsalestable.setIntercellSpacing(new Dimension(0, 1));
      this.m_jsalestable.setRequestFocusEnabled(false);
      this.m_jScrollSales.setViewportView(this.m_jsalestable);
      this.m_jSalesTaxes.setEditable(false);
      this.m_jSalesTaxes.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jSalesTaxes.setHorizontalAlignment(4);
      this.jLabel12.setFont(ChromisFonts.DEFAULTFONT);
      this.jLabel12.setText(AppLocal.getIntString("label.taxes"));
      this.jLabel7.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.jLabel7.setText(AppLocal.getIntString("label.total"));
      this.m_jSalesTotal.setEditable(false);
      this.m_jSalesTotal.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jSalesTotal.setHorizontalAlignment(4);
      this.m_jSalesTotal.setPreferredSize(new Dimension(6, 21));
      this.jLabel8.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(14.0F));
      this.jLabel8.setHorizontalAlignment(4);
      this.jLabel8.setText(AppLocal.getIntString("label.nocashsales"));
      this.m_jNoCashSales.setEditable(false);
      this.m_jNoCashSales.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jNoCashSales.setHorizontalAlignment(4);
      this.m_jGiftCards.setEditable(false);
      this.m_jGiftCards.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jGiftCards.setHorizontalAlignment(4);
      this.m_jGiftCards.setPreferredSize(new Dimension(6, 21));
      this.jLabel9.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.jLabel9.setText(AppLocal.getIntString("label.giftcards"));
      this.jLabelCards.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.jLabelCards.setText(AppLocal.getIntString("label.CardsFees"));
      this.m_jCards.setEditable(false);
      this.m_jCards.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jCards.setHorizontalAlignment(4);
      GroupLayout jPanel5Layout = new GroupLayout(this.jPanel5);
      this.jPanel5.setLayout(jPanel5Layout);
      jPanel5Layout.setHorizontalGroup(
         jPanel5Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               jPanel5Layout.createSequentialGroup()
                  .addGap(2, 2, 2)
                  .addComponent(this.m_jScrollTableTicket, -2, 260, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jScrollSales, -2, 320, -2)
                  .addGroup(
                     jPanel5Layout.createParallelGroup(Alignment.LEADING)
                        .addGroup(
                           jPanel5Layout.createSequentialGroup()
                              .addPreferredGap(ComponentPlacement.RELATED, -1, 32767)
                              .addComponent(this.m_jNoCashSales, -2, 100, -2)
                        )
                        .addGroup(
                           jPanel5Layout.createSequentialGroup()
                              .addGap(16, 16, 16)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                    .addComponent(this.jLabel8, Alignment.TRAILING, -1, -1, 32767)
                                    .addComponent(this.jSeparator1, Alignment.TRAILING)
                                    .addGroup(
                                       jPanel5Layout.createSequentialGroup()
                                          .addGroup(
                                             jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                                .addGroup(
                                                   Alignment.TRAILING,
                                                   jPanel5Layout.createSequentialGroup()
                                                      .addGroup(
                                                         jPanel5Layout.createParallelGroup(Alignment.TRAILING)
                                                            .addComponent(this.jLabel6, -2, 118, -2)
                                                            .addGroup(
                                                               jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                                                  .addComponent(this.jLabel1, -1, -1, 32767)
                                                                  .addComponent(this.jLabel5, -1, -1, 32767)
                                                                  .addComponent(this.jLabel12, -1, -1, 32767)
                                                                  .addComponent(this.jLabel7, Alignment.TRAILING, -1, -1, 32767)
                                                            )
                                                      )
                                                      .addGap(29, 29, 29)
                                                )
                                                .addGroup(
                                                   Alignment.TRAILING,
                                                   jPanel5Layout.createSequentialGroup()
                                                      .addGroup(
                                                         jPanel5Layout.createParallelGroup(Alignment.TRAILING)
                                                            .addComponent(this.jLabelCards, -1, -1, 32767)
                                                            .addComponent(this.jLabel9, -1, -1, 32767)
                                                      )
                                                      .addPreferredGap(ComponentPlacement.RELATED)
                                                )
                                                .addGroup(
                                                   jPanel5Layout.createSequentialGroup()
                                                      .addGap(2, 2, 2)
                                                      .addComponent(this.jLabel4, -1, -1, 32767)
                                                      .addPreferredGap(ComponentPlacement.RELATED)
                                                )
                                          )
                                          .addGroup(
                                             jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                                .addComponent(this.m_jSalesTotal, Alignment.TRAILING, -2, 100, -2)
                                                .addComponent(this.m_jSalesSubtotal, Alignment.TRAILING, -2, 100, -2)
                                                .addComponent(this.m_jCards, -2, 101, -2)
                                                .addComponent(this.m_jCash, -2, 101, -2)
                                                .addComponent(this.m_jGiftCards, Alignment.TRAILING, -2, 100, -2)
                                                .addGroup(
                                                   Alignment.TRAILING,
                                                   jPanel5Layout.createParallelGroup(Alignment.LEADING, false)
                                                      .addComponent(this.m_jSalesTaxes, -1, 100, 32767)
                                                      .addComponent(this.m_jSales)
                                                      .addComponent(this.m_jCount, Alignment.TRAILING)
                                                )
                                          )
                                    )
                              )
                        )
                  )
                  .addGap(49, 49, 49)
            )
      );
      jPanel5Layout.setVerticalGroup(
         jPanel5Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               Alignment.TRAILING,
               jPanel5Layout.createSequentialGroup()
                  .addContainerGap()
                  .addGroup(
                     jPanel5Layout.createParallelGroup(Alignment.TRAILING)
                        .addGroup(
                           jPanel5Layout.createSequentialGroup()
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.BASELINE)
                                    .addComponent(this.jLabel1, -2, 25, -2)
                                    .addComponent(this.m_jCount, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                    .addComponent(this.m_jSales, -2, 25, -2)
                                    .addComponent(this.jLabel5, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                    .addComponent(this.m_jSalesSubtotal, -2, 25, -2)
                                    .addComponent(this.jLabel6, Alignment.TRAILING, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                    .addComponent(this.m_jSalesTaxes, -2, 25, -2)
                                    .addComponent(this.jLabel12, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.LEADING)
                                    .addComponent(this.m_jSalesTotal, -2, 25, -2)
                                    .addComponent(this.jLabel7, Alignment.TRAILING, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.BASELINE)
                                    .addComponent(this.jLabel9, -2, 25, -2)
                                    .addComponent(this.m_jGiftCards, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.BASELINE)
                                    .addComponent(this.jLabelCards, -2, 25, -2)
                                    .addComponent(this.m_jCards, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addGroup(
                                 jPanel5Layout.createParallelGroup(Alignment.BASELINE)
                                    .addComponent(this.jLabel4, -2, 25, -2)
                                    .addComponent(this.m_jCash, -2, 25, -2)
                              )
                              .addPreferredGap(ComponentPlacement.RELATED, -1, 32767)
                              .addComponent(this.jSeparator1, -2, 10, -2)
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addComponent(this.jLabel8, -2, 23, -2)
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addComponent(this.m_jNoCashSales, -2, 25, -2)
                        )
                        .addComponent(this.m_jScrollSales, Alignment.LEADING, -1, 445, 32767)
                        .addComponent(this.m_jScrollTableTicket, Alignment.LEADING, -1, -1, 32767)
                  )
                  .addContainerGap()
            )
      );
      this.m_jPrintCash.setFont(ChromisFonts.DEFAULTBUTTONFONT.deriveFont(16.0F));
      this.m_jPrintCash.setIcon(IconFactory.getIcon("printer.png"));
      this.m_jPrintCash.setText(AppLocal.getIntString("button.printCash"));
      this.m_jPrintCash.setHorizontalAlignment(2);
      this.m_jPrintCash.setIconTextGap(2);
      this.m_jPrintCash.setMaximumSize(new Dimension(85, 33));
      this.m_jPrintCash.setMinimumSize(new Dimension(85, 33));
      this.m_jPrintCash.setPreferredSize(new Dimension(85, 33));
      this.m_jPrintCash.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelCloseMoney.this.m_jPrintCashActionPerformed(evt);
         }
      });
      this.m_jPreviewCash.setFont(ChromisFonts.DEFAULTBUTTONFONT.deriveFont(16.0F));
      this.m_jPreviewCash.setIcon(IconFactory.getIcon("printer.png"));
      this.m_jPreviewCash.setText(AppLocal.getIntString("button.printCashPreview"));
      this.m_jPreviewCash.setHorizontalAlignment(2);
      this.m_jPreviewCash.setIconTextGap(2);
      this.m_jPreviewCash.setMaximumSize(new Dimension(85, 33));
      this.m_jPreviewCash.setMinimumSize(new Dimension(85, 33));
      this.m_jPreviewCash.setPreferredSize(new Dimension(85, 33));
      this.m_jPreviewCash.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelCloseMoney.this.m_jPreviewCashActionPerformed(evt);
         }
      });
      this.m_jCloseCash.setFont(ChromisFonts.DEFAULTBUTTONFONT.deriveFont(16.0F));
      this.m_jCloseCash.setIcon(IconFactory.getIcon("calculator.png"));
      this.m_jCloseCash.setText(AppLocal.getIntString("button.closeCash"));
      this.m_jCloseCash.setHorizontalAlignment(2);
      this.m_jCloseCash.setIconTextGap(2);
      this.m_jCloseCash.setInheritsPopupMenu(true);
      this.m_jCloseCash.setMaximumSize(new Dimension(85, 33));
      this.m_jCloseCash.setMinimumSize(new Dimension(85, 33));
      this.m_jCloseCash.setPreferredSize(new Dimension(85, 33));
      this.m_jCloseCash.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelCloseMoney.this.m_jCloseCashActionPerformed(evt);
         }
      });
      this.btnReloadDay.setFont(ChromisFonts.DEFAULTBUTTONFONT.deriveFont(16.0F));
      this.btnReloadDay.setIcon(IconFactory.getIcon("reload.png"));
      this.btnReloadDay.setText(AppLocal.getIntString("button.reloadreport"));
      this.btnReloadDay.setMaximumSize(new Dimension(85, 33));
      this.btnReloadDay.setMinimumSize(new Dimension(85, 33));
      this.btnReloadDay.setPreferredSize(new Dimension(85, 33));
      this.btnReloadDay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelCloseMoney.this.btnReloadDayActionPerformed(evt);
         }
      });
      this.btnResetDay.setFont(ChromisFonts.DEFAULTBUTTONFONT.deriveFont(16.0F));
      this.btnResetDay.setIcon(IconFactory.getIcon("reload.png"));
      this.btnResetDay.setText(AppLocal.getIntString("button.reset"));
      this.btnResetDay.setMaximumSize(new Dimension(85, 33));
      this.btnResetDay.setMinimumSize(new Dimension(85, 33));
      this.btnResetDay.setPreferredSize(new Dimension(85, 33));
      this.btnResetDay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelCloseMoney.this.btnResetDayActionPerformed(evt);
         }
      });
      GroupLayout jPanelBottomLayout = new GroupLayout(this.jPanelBottom);
      this.jPanelBottom.setLayout(jPanelBottomLayout);
      jPanelBottomLayout.setHorizontalGroup(
         jPanelBottomLayout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               Alignment.TRAILING,
               jPanelBottomLayout.createSequentialGroup()
                  .addComponent(this.btnReloadDay, -2, 145, -2)
                  .addPreferredGap(ComponentPlacement.RELATED, -1, 32767)
                  .addComponent(this.btnResetDay, -2, 145, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jPreviewCash, -2, 145, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jPrintCash, -2, 145, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.m_jCloseCash, -2, 145, -2)
                  .addContainerGap()
            )
      );
      jPanelBottomLayout.setVerticalGroup(
         jPanelBottomLayout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               jPanelBottomLayout.createSequentialGroup()
                  .addGroup(
                     jPanelBottomLayout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(this.m_jPrintCash, -2, 45, -2)
                        .addComponent(this.m_jPreviewCash, -2, 45, -2)
                        .addComponent(this.m_jCloseCash, -2, 45, -2)
                  )
                  .addGap(0, 0, 32767)
            )
            .addGroup(
               Alignment.TRAILING,
               jPanelBottomLayout.createSequentialGroup()
                  .addGroup(
                     jPanelBottomLayout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(this.btnReloadDay, -1, 45, 32767)
                        .addComponent(this.btnResetDay, -2, 43, -2)
                  )
                  .addContainerGap()
            )
      );
      GroupLayout jPanel1Layout = new GroupLayout(this.jPanel1);
      this.jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
         jPanel1Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               jPanel1Layout.createSequentialGroup()
                  .addContainerGap()
                  .addGroup(
                     jPanel1Layout.createParallelGroup(Alignment.LEADING)
                        .addComponent(this.jPanel4, -2, -1, -2)
                        .addComponent(this.jPanel5, -2, -1, -2)
                        .addComponent(this.jPanelBottom, -2, -1, -2)
                  )
                  .addContainerGap(42, 32767)
            )
      );
      jPanel1Layout.setVerticalGroup(
         jPanel1Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               jPanel1Layout.createSequentialGroup()
                  .addGap(2, 2, 2)
                  .addComponent(this.jPanel4, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.UNRELATED)
                  .addComponent(this.jPanel5, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.UNRELATED)
                  .addComponent(this.jPanelBottom, -2, -1, -2)
                  .addContainerGap()
            )
      );
      this.add(this.jPanel1, "First");
   }

   private void m_jCloseCashActionPerformed(ActionEvent evt) {
      if (JAlertPane.messageBox(3, AppLocal.getIntString("message.wannaclosecash"), 16, new Dimension(125, 50), 3) == 5) {
         // No second click while the cash is being closed
         this.m_jCloseCash.setEnabled(false);
         Date dNow = new Date();
         String closedMoney = this.m_App.getActiveCashIndex();
         int closedSequence = this.m_App.getActiveCashSequence();
         CashCloser.Result next;
         try {
            // Close this cash and open the next one together: if anything fails nothing changes
            next = CashCloser.close(this.m_App.getSession().getConnection(), closedMoney, this.m_App.getProperties().getHost(),
               TerminalInfo.getTerminalID(), dNow, this.result);
         } catch (Exception var7) {
            java.nio.file.Path log = CashCloser.log("No se pudo cerrar el corte " + closedSequence + " (" + closedMoney + ")", var7);
            MessageInf msg = new MessageInf(-67108864, "No se pudo hacer el corte. No se cambió nada: las ventas siguen en el corte actual "
               + "y puede intentarlo de nuevo.\nEl detalle del error quedó en " + log, var7);
            msg.show(this);
            try {
               this.loadData();
            } catch (BasicException var6) {
               CashCloser.log("No se pudo volver a leer el corte", var6);
            }
            return;
         }

         this.m_App.setActiveCash(next.money, next.sequence, next.start, null);
         try {
            this.dbQuery.execDrawerOpened(new Object[]{UUID.randomUUID().toString(), this.m_App.getAppUserView().getUser().getId(), "Close Cash"});
         } catch (BasicException var6) {
            CashCloser.log("No se pudo registrar la apertura del cajón del corte", var6);
         }

         this.m_PaymentsToClose.setDateEnd(dNow);
         this.printMoney = closedMoney;
         try {
            this.printPayments("Printer.CloseCash");
         } catch (RuntimeException var6) {
            java.nio.file.Path log = CashCloser.log("Se cerró el corte " + closedSequence + " pero no se pudo imprimir", var6);
            MessageInf msg = new MessageInf(-33554432, "El corte se cerró correctamente, pero no se pudo imprimir.\n"
               + "Puede reimprimirlo con Recargar día. Detalle en " + log, var6);
            msg.show(this);
         } finally {
            this.printMoney = null;
         }

         JAlertPane.messageBox(new Dimension(300, 100), 1, AppLocal.getIntString("message.closecashok"), 16, new Dimension(125, 50), 4);
         try {
            AutoAttendance.onCashClosed(this.m_App.getSession().getConnection(), this.m_App.getAppUserView().getUser().getId(), this);
         } catch (SQLException var8) {
            // Attendance is optional: the cash is already closed
         }

         try {
            this.reload = false;
            this.loadData();
         } catch (BasicException var5) {
            MessageInf msg = new MessageInf(-67108864, AppLocal.getIntString("label.noticketstoclose"), var5);
            msg.show(this);
         }
      }
   }

   private void m_jPrintCashActionPerformed(ActionEvent evt) {
      if (this.reload) {
         this.printPayments("Printer.CloseCash");
      } else {
         this.printPayments("Printer.PartialCash");
      }
   }

   private void m_jPreviewCashActionPerformed(ActionEvent evt) {
      this.printPayments("Printer.CloseCash.Preview");
   }

   private void btnReloadDayActionPerformed(ActionEvent evt) {
      Object[] result = JAlertPane.closedCashBox();
      if ((Integer)result[0] != 0 || result[1] == null) {
         return; // cancelled: the current cash stays on screen
      }

      ClosedCashInfo cash = (ClosedCashInfo)result[1];
      try {
         if (cash.getEndDate() == null) {
            cash = this.closeLeftOpen(cash);
         }

         this.m_jCloseCash.setEnabled(false);
         this.reload = true;
         this.printMoney = cash.getMoneyGuid();
         this.clearFields();
         this.m_PaymentsToClose = PaymentsModel.loadInstance(cash);
         this.populateData();
         this.hSales = this.dbQuery.getHourlySales(cash.getMoneyGuid());
         this.userSales = this.dbQuery.getUserSales(cash.getMoneyGuid());
      } catch (Exception var4) {
         java.nio.file.Path log = CashCloser.log("No se pudo recargar el corte " + cash.getHostSequence(), var4);
         MessageInf msg = new MessageInf(-33554432, "No se pudo cargar el corte " + cash.getHostSequence() + ". Detalle en " + log, var4);
         msg.show(this);
         this.btnResetDayActionPerformed(null);
      }
   }

   /** A cash left open by an error: offers to close it at its last sale so it can be printed and reloaded later. */
   private ClosedCashInfo closeLeftOpen(ClosedCashInfo cash) throws SQLException {
      if (JAlertPane.messageBox(3, "El corte " + cash.getHostSequence() + " quedó abierto por un error y sus ventas no están en ningún corte.\n"
            + "¿Cerrarlo ahora con la hora de su última venta?", 16, new Dimension(125, 50), 3) != 5) {
         return cash;
      }
      Date end = CashCloser.lastActivity(this.m_App.getSession().getConnection(), cash.getMoneyGuid());
      if (!CashCloser.closeLeftOpen(this.m_App.getSession().getConnection(), cash.getMoneyGuid(), end)) {
         return cash;
      }
      return new ClosedCashInfo(cash.getStartDate(), end, cash.getHost(), cash.getHostSequence(), cash.getMoneyGuid());
   }

   private void btnResetDayActionPerformed(ActionEvent evt) {
      try {
         this.reload = false;
         this.printMoney = null;
         this.clearFields();
         this.m_PaymentsToClose = PaymentsModel.loadInstance(this.m_App);
         this.populateData();
         this.m_jCloseCash.setEnabled(this.m_PaymentsToClose.getPayments() != 0 || this.m_PaymentsToClose.getSales() != 0);
         this.hSales = this.dbQuery.getHourlySales(this.m_App.getActiveCashIndex());
         this.userSales = this.dbQuery.getUserSales(this.m_App.getActiveCashIndex());
      } catch (BasicException var3) {
         Logger.getLogger(JPanelCloseMoney.class.getName()).log(Level.SEVERE, null, var3);
      }
   }

   private class FormatsPayment extends Formats {
      protected String formatValueInt(Object value) {
         return AppLocal.getIntString("paymentdescription." + (String)value);
      }

      protected Object parseValueInt(String value) throws ParseException {
         return value;
      }

      public int getAlignment() {
         return 2;
      }
   }
}
