package uk.chromis.pos.sales;

import bsh.EvalError;
import bsh.Interpreter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.LineBorder;
import uk.chromis.basic.BasicException;
import uk.chromis.beans.JNumberEvent;
import uk.chromis.beans.JNumberEventListener;
import uk.chromis.beans.JNumberKeys;
import uk.chromis.commons.dbmanager.DbUtils;
import uk.chromis.commons.dialogs.JAlertPane;
import uk.chromis.commons.dialogs.Receipt;
import uk.chromis.commons.utils.KeyedData;
import uk.chromis.commons.utils.TerminalInfo;
import uk.chromis.data.gui.ComboBoxValModel;
import uk.chromis.data.gui.ListKeyed;
import uk.chromis.data.gui.MessageInf;
import uk.chromis.data.loader.SentenceList;
import uk.chromis.format.Formats;
import uk.chromis.globals.CompanyInfo;
import uk.chromis.globals.IconFactory;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.auditing.Audit;
import uk.chromis.pos.barcodes.Barcode;
import uk.chromis.pos.backup.BackupService;
import uk.chromis.pos.catalog.JCatalog;
import uk.chromis.pos.customers.CustomerInfo;
import uk.chromis.pos.customers.CustomerInfoExt;
import uk.chromis.pos.customers.JCustomerFinder;
import uk.chromis.pos.datalogic.DataLogicCustomers;
import uk.chromis.pos.datalogic.DataLogicLoyalty;
import uk.chromis.pos.datalogic.DataLogicReceipts;
import uk.chromis.pos.datalogic.DataLogicSales;
import uk.chromis.pos.datalogic.DataLogicSystem;
import uk.chromis.pos.forms.AppConfig;
import uk.chromis.pos.forms.AppLocal;
import uk.chromis.pos.forms.AppUser;
import uk.chromis.pos.forms.AppView;
import uk.chromis.pos.forms.BeanFactoryApp;
import uk.chromis.pos.forms.BeanFactoryException;
import uk.chromis.pos.forms.ChromisFonts;
import uk.chromis.pos.forms.JPanelView;
import uk.chromis.pos.forms.JPrincipalApp;
import uk.chromis.pos.forms.JRootApp;
import uk.chromis.pos.forms.JRootFrame;
import uk.chromis.pos.forms.LocalResource;
import uk.chromis.pos.giftcards.GiftCardLogic;
import uk.chromis.pos.inventory.AlternativeBarcode;
import uk.chromis.pos.inventory.TaxCategoryInfo;
import uk.chromis.pos.loyalty.CollectItemLoyaltyPoints;
import uk.chromis.pos.loyalty.CollectTransactionLoyaltyPoints;
import uk.chromis.pos.loyalty.LoyaltyCard;
import uk.chromis.pos.panels.JProductFinder;
import uk.chromis.pos.payment.JPaymentSelect;
import uk.chromis.pos.payment.JPaymentSelectReceipt;
import uk.chromis.pos.payment.JPaymentSelectRefund;
import uk.chromis.pos.payment.PaymentInfo;
import uk.chromis.pos.printer.DeviceDisplayAdvance;
import uk.chromis.pos.printer.IncludeFile;
import uk.chromis.pos.printer.TicketParser;
import uk.chromis.pos.printer.TicketPrinterException;
import uk.chromis.pos.sales.restaurant.RestaurantDBUtils;
import uk.chromis.pos.scale.ScaleException;
import uk.chromis.pos.scripting.ScriptEngine;
import uk.chromis.pos.scripting.ScriptException;
import uk.chromis.pos.scripting.ScriptFactory;
import uk.chromis.pos.ticket.ProductInfoExt;
import uk.chromis.pos.ticket.ReceiptTaxesInfo;
import uk.chromis.pos.ticket.TaxInfo;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;
import uk.chromis.pos.util.AutoLogoff;

public abstract class JPanelTicket extends JPanel implements JPanelView, BeanFactoryApp, TicketsEditor {
   private static final int NUMBERZERO = 0;
   private static final int NUMBERVALID = 1;
   private static final int NUMBERINVALID = 2;
   private static final int NUMBER_INPUTZERO = 0;
   private static final int NUMBER_INPUTZERODEC = 1;
   private static final int NUMBER_INPUTINT = 2;
   private static final int NUMBER_INPUTDEC = 3;
   private static final int NUMBER_PORZERO = 4;
   private static final int NUMBER_PORZERODEC = 5;
   private static final int NUMBER_PORINT = 6;
   private static final int NUMBER_PORDEC = 7;
   private StringBuilder barCodeStart;
   private String barCode;
   protected String siteGuid;
   private String ticketPrintType;
   protected DataLogicReceipts dlReceipts;
   private CostPricing costPricing;
   private RestockAlerts restockAlerts;
   private final Map<String, Boolean> auxiliaryLinks = new HashMap<>();
   protected DataLogicSystem dlSystem;
   protected DataLogicSales dlSales;
   protected DataLogicCustomers dlCustomers;
   protected JTicketLines m_ticketlines;
   protected JTicketLines m_ticketlines2;
   protected TicketInfo m_oTicket;
   protected JPanelButtons m_jbtnconfig;
   protected AppView m_App;
   protected Object m_oTicketExt;
   protected TicketsEditor m_panelticket;
   private int m_iNumberStatus;
   private int m_iNumberStatusInput;
   private int m_iNumberStatusQty;
   private TicketParser m_TTP;
   private StringBuffer m_sBarcode;
   private JTicketsBag m_ticketsbag;
   private SentenceList senttax;
   private ListKeyed taxcollection;
   private SentenceList senttaxcategories;
   private ListKeyed taxcategoriescollection;
   private ComboBoxValModel taxcategoriesmodel;
   private TaxesLogic taxeslogic;
   private JPaymentSelect paymentdialogreceipt;
   private JPaymentSelect paymentdialogrefund;
   private Action logout;
   private Integer delay = 0;
   private JPaymentSelect paymentdialog;
   private RestaurantDBUtils restDB;
   private KitchenDisplay kitchenDisplay;
   private Boolean receiptRequired = false;
   private TicketInfo m_ticket;
   private TicketInfo m_ticketCopy;
   private AppConfig m_config;
   private Boolean fromNumberPad = true;
   protected Component southcomponent;
   private LoyaltyCard loyaltyCard;
   private JPrincipalApp principalApp;
   private AlternativeBarcode alternativeBarcode;
   private Barcode bar;
   private ProductInfoExt defaultProduct = new ProductInfoExt();
   private CustomerDeliveryInfo deliveryInfo;
   private final HashMap<String, Object> parameters = new HashMap<>();
   private final HashMap<String, Integer> ageRestrictedProducts = new HashMap<>();
   private final HashMap<String, String> mappedKeys = new HashMap<>();
   private final HashMap<String, Object> instanceMap = new HashMap<>();
   private final GiftCardLogic giftCard = new GiftCardLogic();
   private final DateFormat format = new SimpleDateFormat(SystemProperty.DATETIME);
   private JButton btnActionMenu;
   private JButton btnCustomer;
   private JButton btnLogout;
   private JButton btnReprint;
   private JButton btnSplit;
   protected JPanel catcontainer;
   private JButton jEditAttributes;
   private JPanel jPanel1;
   protected JPanel jPanel2;
   protected JPanel jPanel3;
   protected JPanel jPanel4;
   private JPanel jPanel6;
   private JPanel jPanel7;
   private JPanel jPanel9;
   private JButton j_btnKitchenPrt;
   private JPanel m_jButtons;
   private JPanel m_jButtonsExt;
   protected JPanel m_jContEntries;
   private JButton m_jDelete;
   private JButton m_jDown;
   private JButton m_jEditLine;
   private JButton m_jEnter;
   private JTextField m_jKeyFactory;
   protected JLabel m_jLblLoyaltyCard;
   private JLabel m_jLblTotalEuros1;
   private JLabel m_jLblTotalEuros2;
   private JLabel m_jLblTotalEuros3;
   private JButton m_jList;
   private JNumberKeys m_jNumberKey;
   private JPanel m_jOptions;
   protected JPanel m_jPanContainer;
   protected JPanel m_jPanEntries;
   protected JPanel m_jPanEntriesE;
   protected JPanel m_jPanTicket;
   protected JPanel m_jPanTotals;
   private JPanel m_jPanelBag;
   private JPanel m_jPanelScripts;
   private JLabel m_jPrice;
   private JLabel m_jQty;
   private JLabel m_jSubtotalEuros;
   private JLabel m_jTaxesEuros;
   protected JLabel m_jTicketId;
   private JLabel m_jTotalEuros;
   private JButton m_jUp;
   private JButton m_jbtnScale;
   protected JPanel ticketActions;
   private JPanel ticketPanel;

   public JPanelTicket() {
      this.initComponents();
      this.m_jButtons.setLocation(50, 0);
   }

   @Override
   public void init(AppView app) throws BeanFactoryException {
      this.m_App = app;
      this.dlSystem = (DataLogicSystem)this.m_App.getBean("uk.chromis.pos.datalogic.DataLogicSystem");
      this.dlSales = (DataLogicSales)this.m_App.getBean("uk.chromis.pos.datalogic.DataLogicSales");
      this.dlCustomers = (DataLogicCustomers)this.m_App.getBean("uk.chromis.pos.datalogic.DataLogicCustomers");
      this.dlReceipts = (DataLogicReceipts)app.getBean("uk.chromis.pos.datalogic.DataLogicReceipts");
      this.costPricing = new CostPricing(this.m_App.getSession());
      BackupService.start(
         AppConfig.getDatabaseURL(),
         AppConfig.getDatabaseUser(),
         AppConfig.getClearDatabasePassword(),
         AppConfig.getString("terminalID"),
         message -> SwingUtilities.invokeLater(
               () -> JAlertPane.messageBox(0, message, 16, new Dimension(125, 50), 4)
            )
      );
      // Copies of deleted held tickets, so they can be put back from Chromis Admin
      HeldTicketWatcher.start(
         AppConfig.getDatabaseURL(), AppConfig.getDatabaseUser(), AppConfig.getClearDatabasePassword(), this::refreshHeldTicketsButton
      );
      this.restockAlerts = new RestockAlerts(
         AppConfig.getDatabaseURL(), AppConfig.getDatabaseUser(), AppConfig.getClearDatabasePassword(), this.m_App.getInventoryLocation(), this
      );

      try {
         for (KeyedData k : this.dlSales.getRestrictedProducts()) {
            this.ageRestrictedProducts.put(k.getId(), k.getInt());
         }
      } catch (BasicException var5) {
      }

      this.m_jNumberKey.dotIs00(SystemProperty.PRICEWITH00);
      this.siteGuid = this.dlSystem.getSiteGUID();
      this.alternativeBarcode = new AlternativeBarcode(this.dlSales);
      this.restDB = new RestaurantDBUtils();
      this.m_jbtnScale.setVisible(this.m_App.getDeviceScale().existsScale());
      this.m_ticketsbag = this.getJTicketsBag();
      this.m_jPanelBag.add(this.m_ticketsbag.getBagComponent(), "Before");
      this.add(this.m_ticketsbag.getNullComponent(), "null");
      if (SystemProperty.TAXINCLUDED) {
         this.m_ticketlines = new JTicketLines(this.dlSystem.getResourceAsXML("Ticket.LineIncTaxes"));
      } else {
         this.m_ticketlines = new JTicketLines(this.dlSystem.getResourceAsXML("Ticket.LineExclTaxes"));
      }

      this.ticketPanel.add(this.m_ticketlines, "Center");
      this.ticketLinelListener();
      this.m_TTP = new TicketParser(this.m_App.getDeviceTicket(), this.dlSystem);
      this.m_jbtnconfig = new JPanelButtons("Ticket.Buttons", this);
      this.m_jButtonsExt.add(this.m_jbtnconfig);
      this.southcomponent = this.getSouthComponent();
      this.catcontainer.add(this.southcomponent, "Center");
      this.southcomponent.setPreferredSize(new Dimension(0, 320));
      this.senttax = this.dlSales.getTaxList(this.siteGuid);
      this.senttaxcategories = this.dlSales.getTaxCategoriesList(this.siteGuid);
      this.taxcategoriesmodel = new ComboBoxValModel();
      this.jEditAttributes.setEnabled(false);
      this.stateToZero();
      this.m_oTicket = null;
      this.m_oTicketExt = null;
      this.m_ticketlines2 = new JTicketLines(this.dlSystem.getResourceAsXML("Display.TicketLines"));
      this.customDisplay(TerminalInfo.hasCustomerDisplay());
      SaleScreens.createLayout(this);
      this.btnLogout.addActionListener(e -> this.jbtnLogout());
      this.btnCustomer.addActionListener(e -> this.btnCustomerAction());
      this.btnReprint.addActionListener(e -> this.btnReprintAction());
   }

   private void addGiftCardSale() {
      System.out.println("GiftCard");
      if (SystemProperty.GIFTCARDSENABLED) {
         Object[] result = JAlertPane.inputBox(
            new Dimension(450, 200), AppLocal.getIntString("message.giftcardNumber"), 16, new Dimension(100, 35), 132, SystemProperty.GIFTCARDSTART
         );
         if ((Integer)result[0] == 4) {
            return;
         }

         if (!((String)result[1]).startsWith(SystemProperty.GIFTCARDSTART)) {
            JAlertPane.messageBox(0, AppLocal.getIntString("message.invalidCardNumber"), 16, new Dimension(125, 50), 4);
            return;
         }

         TicketLineInfo newline = null;

         try {
            newline = JGiftCardEdit.showMessage(this, this.m_App, (String)result[1], this.dlSales, this.giftCard.isCardActivated((String)result[1]));
         } catch (BasicException var4) {
            Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var4);
         }

         if (newline != null) {
            this.addTicketLine(newline);
            newline.setProperty("cardnotes", "Giftcard Number  : " + (String)result[1]);
            newline.setProperty("cardbalance", Formats.CURRENCY.formatValue(this.giftCard.getCardBalance((String)result[1]) + newline.getPrice()));
            newline.setProperty("cardnumber", (String)result[1]);
            this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProductAttSetInstDesc("Giftcard Number - " + (String)result[1]);
            this.refreshTicket();
         }

         this.stateToZero();
      }
   }

   public void addLoyaltyLine(ProductInfoExt product, Boolean redeem) {
      this.addTicketLine(product, 1.0, 0.0);
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProductAttSetInstDesc("Loyalty purchase - " + product.getBurnValue() + " points.");
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProperty("redeemed_points", Integer.toString(product.getBurnValue()));
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProperty("cardnotes", "Loyalty purchase - " + product.getBurnValue() + " points.");
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setPrice(0.0);
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setSellingPrice(0.0);
      this.refreshTicket();
   }

   private void addTicketDefaultLine(ProductInfoExt oProduct, double dMul) {
      if (SystemProperty.DEFAULTPRODUCT) {
         if (SystemProperty.TAXINCLUDED) {
            this.addTicketLine(oProduct, 1.0, oProduct.getPriceSellinc());
         } else {
            this.addTicketLine(oProduct, 1.0, oProduct.getPriceSell());
         }
      } else {
         JAlertPane.messageBox(1, AppLocal.getIntString("message.defaultnotallowed"), 16, new Dimension(125, 50), 4);
         this.stateToZero();
      }
   }

   private void addTicketLine(ProductInfoExt oProduct, double dMul, double dPrice) {
      TaxInfo tax = this.taxeslogic.getTaxInfo(oProduct.getTaxCategoryID());
      String var7 = oProduct.getID().toLowerCase();
      switch (var7) {
         case "servicecharge":
            if (this.m_oTicket.getNoSC().equals("0")) {
               this.m_oTicket.setNoSC("1");
               this.addTicketLine(new TicketLineInfo(oProduct, dMul, oProduct.getUnitPrice(dMul), tax, (Properties)oProduct.getProperties().clone()));
            } else {
               JAlertPane.messageBox(1, AppLocal.getIntString("message.multipleservicecharge"), 16, new Dimension(125, 50), 4);
               this.stateToZero();
            }

            return;
         case "deliverycharge":
            if (!this.m_oTicket.getNoDelivery().equals("0")) {
               JAlertPane.messageBox(1, AppLocal.getIntString("message.multipledeliverycharge"), 16, new Dimension(125, 50), 4);
               this.stateToZero();
               return;
            } else {
               Object[] result = JAlertPane.inputDeliveryBox();
               if ((Integer)result[0] != 0) {
                  return;
               } else {
                  this.deliveryInfo = (CustomerDeliveryInfo)result[1];
                  this.m_oTicket.setDeliveryInfo(this.deliveryInfo);
                  this.m_oTicket.setNoDelivery("1");
               }
            }
         case "xxx999_999xxx_x9x9x9":
            if (SystemProperty.LOTTOPAYOUT) {
               oProduct.setPriceSell(-dPrice);
               this.addTicketLine(new TicketLineInfo(oProduct, 1.0, -dPrice, tax, (Properties)oProduct.getProperties().clone()));
               this.stateToZero();
               return;
            }
         default:
            if (oProduct.isVprice()) {
               oProduct.setPriceSell(this.excludeTaxes(this.defaultProduct.getTaxCategoryID(), this.getInputValue()));
               this.addTicketLine(new TicketLineInfo(oProduct, dMul, dPrice, tax, (Properties)oProduct.getProperties().clone()));
            } else {
               this.addTicketLine(new TicketLineInfo(oProduct, dMul, oProduct.getUnitPrice(dMul), tax, (Properties)oProduct.getProperties().clone()));
            }
      }
   }

   protected void addTicketLine(TicketLineInfo oLine) {
      Double markup = this.getCostPlusMarkup(this.m_oTicket.getCustomer());
      if (markup != null && !this.m_oTicket.isRefund() && this.isCostPlusProduct(oLine) && !this.hasCost(oLine)) {
         this.showMissingCost(oLine.getProductName());
         this.stateToZero();
         return;
      }

      if (!this.m_oTicket.isRefund() && SystemProperty.SCONOFF && this.m_oTicket.getNoSC().equals("0")) {
         this.m_oTicket.setNoSC("1");
         if (!"restaurant".equals(TerminalInfo.getPosType()) & !SystemProperty.SCRESTAURANT) {
            this.addServiceChargeLine();
         } else if ("restaurant".equals(TerminalInfo.getPosType())) {
            this.addServiceChargeLine();
         }
      }

      Integer requiredAge = this.ageRestrictedProducts.get(oLine.getProductID());
      if (requiredAge != null && requiredAge > this.m_oTicket.getAgeChecked()) {
         if (!this.checkAgeAlert(requiredAge)) {
            JAlertPane.messageBox(1, AppLocal.getIntString("message.age.declined"), 16, new Dimension(125, 50), 4);
            this.stateToZero();
            return;
         }

         this.m_oTicket.setAgeChecked(requiredAge >= this.m_oTicket.getAgeChecked() ? requiredAge : this.m_oTicket.getAgeChecked());
      }

      if (this.executeEventAndRefresh("ticket.addline", new JPanelTicket.ScriptArg("line", oLine)) == null) {
         if (!oLine.isProductCom()) {
            this.m_oTicket.addLine(oLine);
            this.m_ticketlines.addTicketLine(oLine);

            try {
               int i = this.m_ticketlines.getSelectedIndex();
               TicketLineInfo line = this.m_oTicket.getLine(i);
               if (line.compulsoryAttributes()) {
                  if (SystemProperty.SHOWGUI) {
                     JProductAttEditNew attedit = JProductAttEditNew.getAttributesEditor(this, this.m_App.getSession());
                     attedit.editAttributes(line.getProductAttSetId(), line.getProductAttSetInstId());
                     attedit.setVisible(true);
                     if (attedit.isOK()) {
                        line.setProductAttSetInstId(attedit.getAttributeSetInst());
                        line.setProductAttSetInstDesc(attedit.getAttributeSetInstDescription());
                        this.paintTicketLine(i, line);
                     }
                  } else {
                     JProductAttEdit attedit = JProductAttEdit.getAttributesEditor(this, this.m_App.getSession());
                     attedit.editAttributes(line.getProductAttSetId(), line.getProductAttSetInstId());
                     attedit.setVisible(true);
                     if (attedit.isOK()) {
                        line.setProductAttSetInstId(attedit.getAttributeSetInst());
                        line.setProductAttSetInstDesc(attedit.getAttributeSetInstDescription());
                        this.paintTicketLine(i, line);
                     }
                  }
               }
            } catch (BasicException var8) {
               MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotfindattributes"), var8);
               msg.show(this);
            }
         } else {
            // Product the selected line belongs to (the selected line itself, or the one above its auxiliaries)
            int parent = this.m_ticketlines.getSelectedIndex();
            while (parent >= 0 && this.m_oTicket.getLine(parent).isProductCom()) {
               parent--;
            }

            if (parent >= 0 && this.isAuxiliaryOf(this.m_oTicket.getLine(parent).getProductID(), oLine.getProductID())) {
               int i = parent + 1;
               while (i < this.m_oTicket.getLinesCount() && this.m_oTicket.getLine(i).isProductCom()) {
                  i++;
               }

               this.m_oTicket.insertLine(i, oLine);
               this.m_ticketlines.insertTicketLine(i, oLine);
            } else {
               // Not an auxiliary of that product (or nothing selected): it is a line of its own
               this.m_oTicket.addLine(oLine);
               this.m_ticketlines.addTicketLine(oLine);
            }
         }

         if (this.m_oTicket.isTaxExempt()) {
            this.applyTaxExemption(oLine);
         }

         if (this.m_oTicket.hasCustomer() && this.m_oTicket.getCustomer().getCustomerDiscount() > 0.0) {
            this.applyDiscount(oLine);
         }

         this.checkSellingPrices(this.m_oTicket);
         this.checkServiceCharge(this.m_oTicket);
         if (SystemProperty.CONSOLIDATED) {
            HashMap<String, Double> products = new HashMap<>();

            for (int ix = 0; ix < this.m_oTicket.getLinesCount(); ix++) {
               TicketLineInfo current_line = this.m_oTicket.getLine(ix);
               if (!current_line.isProductVprice() & !current_line.isSystemObject()) {
                  products.putIfAbsent(current_line.getProductID(), 0.0);
                  products.put(current_line.getProductID(), products.get(current_line.getProductID()) + current_line.getMultiply());
               }
            }

            TicketLineInfo current_line = this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex());
            int selectedIndex = this.m_ticketlines.getSelectedIndex();

            for (int ixx = 0; ixx < this.m_oTicket.getLinesCount(); ixx++) {
               TicketLineInfo current_ticketline = this.m_oTicket.getLine(ixx);
               if (current_ticketline.getProductID().equals(current_line.getProductID()) & ixx != selectedIndex
                  && products.get(current_ticketline.getProductID()) != null) {
                  current_ticketline.setMultiply(products.get(current_ticketline.getProductID()));
                  this.removeTicketLine(selectedIndex, false);
               }
            }

            this.refreshTicket();
         }

         this.executeEventAndRefresh("ticket.pretotals");
         CustomerDisplay.updateDisplay(oLine);
         this.printPartialTotals();
         this.stateToZero();
         this.executeEvent(this.m_oTicket, this.m_oTicketExt, "ticket.change");
      }

      this.refreshTicket();
   }

   private void ticketLinelListener() {
      this.m_ticketlines.addListSelectionListener(e -> {
         if (this.m_ticketlines.getSelectedIndex() >= 0 && this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).getProductID() != null) {
            TicketLineInfo current_line = this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex());
            this.jEditAttributes.setEnabled(current_line.hasAttributes());
            // Tier priced lines can be edited too: a manual price is kept, a quantity
            // change is re-priced by tier (see editTicketLine)
            this.m_jEditLine.setEnabled(true);

            if (current_line.isServiceCharge()) {
               this.m_jEditLine.setEnabled(false);
            }
         }
      });
   }

   private void customDisplay(Boolean showDisplay) {
      if (showDisplay
         && this.m_App.getDeviceTicket().getDeviceDisplay() != null
         && this.m_App.getDeviceTicket().getDeviceDisplay() instanceof DeviceDisplayAdvance) {
         DeviceDisplayAdvance advDisplay = (DeviceDisplayAdvance)this.m_App.getDeviceTicket().getDeviceDisplay();
         if (advDisplay.hasFeature(2)) {
            this.m_ticketlines2 = new JTicketLines(this.dlSystem.getResourceAsXML("Display.TicketLines"));
            advDisplay.setTicketLines(this.m_ticketlines2);
         }

         this.m_ticketlines.addListSelectionListener(e -> {
            if (advDisplay.hasFeature(2)) {
               int i = this.m_ticketlines.getSelectedIndex();
               if (this.m_oTicket == null || this.m_oTicket.getLinesCount() == 0) {
                  CustomerDisplay.updateDisplay("display.Message");
               }
            }
         });
      }
   }

   private void saveCurrentTicket() {
      if ((String)this.m_oTicketExt != null) {
         try {
            this.dlReceipts
               .updateSharedTicket(
                  (String)this.m_oTicketExt, this.m_oTicket, this.m_oTicket.getPickupId(), "P" + this.m_oTicket.getId().substring(24), this.deliveryInfo
               );
         } catch (BasicException var2) {
            new MessageInf(var2).show(this);
         }
      }
   }

   /** Updates the held tickets button (count and icon) as Chromis does after a click; its method is private. */
   private void refreshHeldTicketsButton() {
      if (this.m_ticketsbag == null) {
         return;
      }

      try {
         java.lang.reflect.Method m = this.m_ticketsbag.getClass().getDeclaredMethod("checkLayaways");
         m.setAccessible(true);
         m.invoke(this.m_ticketsbag);
      } catch (ReflectiveOperationException | RuntimeException var2) {
         Logger.getLogger(JPanelTicket.class.getName()).log(Level.FINE, "No se pudo actualizar el botón de ventas en espera", var2);
      }
   }

   protected abstract JTicketsBag getJTicketsBag();

   protected abstract Component getSouthComponent();

   protected abstract void resetSouthComponent();

   protected abstract void reLoadCatalog();

   @Override
   public void setActiveTicket(TicketInfo oTicket, Object oTicketExt) {
      this.btnCustomer.setEnabled(true);
      this.loyaltyCard = null;
      this.m_jLblLoyaltyCard.setText(null);
      String ex = TerminalInfo.getPosType();
      switch (ex) {
         case "restaurant":
            if (SystemProperty.ENABLEAUTOLOGOFF && SystemProperty.AUTOLOGOFFINACTIVITYTIMER && !AutoLogoff.getInstance().isTimerRunning()) {
               AutoLogoff.getInstance().activateTimer();
            }
         default:
            this.m_jNumberKey.setEnabled(true);
            this.jEditAttributes.setVisible(true);
            this.m_jList.setVisible(true);
            this.m_oTicket = oTicket;
            this.m_oTicketExt = oTicketExt;
            if (this.m_oTicket != null) {
               this.m_oTicket.setUser(this.m_App.getAppUserView().getUser().getUserInfo());
               this.m_oTicket.setActiveCash(this.m_App.getActiveCashIndex());
               this.m_oTicket.setDate(new Date());
               if (this.m_oTicket.getLoyaltyCard() != null) {
                  this.loyaltyCard = this.m_oTicket.getLoyaltyCard();
                  this.m_jLblLoyaltyCard.setText("Loyalty card : " + this.loyaltyCard.getCardNumber());
               }

               if (TerminalInfo.getPosType().equals("restaurant") && !oTicket.getOldTicket()) {
                  if (this.restDB.getCustomerNameInTable(oTicketExt.toString()).equals("") && this.m_oTicket.getCustomer() != null) {
                     this.restDB.setCustomerNameInTable(this.m_oTicket.getCustomer().toString(), oTicketExt.toString());
                  }

                  if (this.restDB.getWaiterNameInTable(oTicketExt.toString()).equals("")) {
                     this.restDB.setWaiterNameInTable(this.m_App.getAppUserView().getUser().getName(), oTicketExt.toString());
                  }

                  this.restDB.setTicketIdInTable(this.m_oTicket.getId(), oTicketExt.toString());
                  this.restDB.setTableLock(this.m_oTicket.getId(), this.m_App.getAppUserView().getUser().getName());
               }
            } else {
               this.loyaltyCard = null;
            }

            if (this.m_oTicket != null
               && (SystemProperty.SHOWCUSTOMERDETAILS || SystemProperty.SHOWWAITERDETAILS)
               && this.restDB.getTableMovedFlag(this.m_oTicket.getId())) {
               this.restDB.moveCustomer(oTicketExt.toString(), this.m_oTicket.getId());
            }

            if (this.m_oTicket != null && this.m_oTicket.getCustomer() != null) {
               try {
                  this.m_oTicket.getCustomer().setCurrentDebt(this.dlSales.getCustomerDebt(this.m_oTicket.getCustomer().getId()));
               } catch (BasicException var5) {
                  Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var5);
               }
            }

            this.executeEvent(this.m_oTicket, this.m_oTicketExt, "ticket.show");
            this.j_btnKitchenPrt.setVisible(AppUser.hasPermission("sales.PrintKitchen"));
            this.refreshTicket();
      }
   }

   @Override
   public TicketInfo getActiveTicket() {
      return this.m_oTicket;
   }

   private void refreshTicket() {
      if (this.m_oTicket != null) {
         this.m_jDelete.setVisible(!this.m_oTicket.isRefund());
      }

      CardLayout cl = (CardLayout)this.getLayout();
      if (this.m_oTicket == null) {
         this.btnSplit.setEnabled(false);
         this.m_jTicketId.setText(null);
         this.m_jLblLoyaltyCard.setText(null);
         this.m_ticketlines.clearTicketLines();
         this.m_jSubtotalEuros.setText(null);
         this.m_jTaxesEuros.setText(null);
         this.m_jTotalEuros.setText(null);
         this.stateToZero();
         this.repaint();
         cl.show(this, "null");
         if (this.m_oTicket != null && this.m_oTicket.getLinesCount() == 0) {
            this.resetSouthComponent();
         }
      } else {
         this.btnSplit.setEnabled(AppUser.hasPermission("sales.Total") && this.m_oTicket.getArticlesCount() > 1.0);
         if (this.m_oTicket.isRefund()) {
            this.m_jNumberKey.justEquals();
            this.jEditAttributes.setVisible(false);
            this.m_jEditLine.setVisible(false);
            this.m_jList.setVisible(false);
         }

         this.setTicketName(this.m_oTicket.getName(this.m_oTicketExt));
         int line = this.m_ticketlines.getSelectedIndex();
         this.m_ticketlines.clearTicketLines();
         this.m_ticketlines2.clearTicketLines();

         for (int i = 0; i < this.m_oTicket.getLinesCount(); i++) {
            this.m_ticketlines.addTicketLine(this.m_oTicket.getLine(i));
            this.m_ticketlines2.addTicketLine(this.m_oTicket.getLine(i));
         }

         this.printPartialTotals();
         this.stateToZero();
         cl.show(this, "ticket");
         if (this.m_oTicket.getLinesCount() == 0) {
            this.resetSouthComponent();
         }

         this.m_jKeyFactory.setText(null);
         EventQueue.invokeLater(() -> this.m_jKeyFactory.requestFocus());
      }
   }

   @Override
   public void setTicketName(String tName) {
      this.m_jTicketId.setText(tName);
   }

   @Override
   public void activate() throws BasicException {
      this.principalApp = JRootApp.getPricipalApp();
      this.m_jEditLine.setVisible(AppUser.hasPermission("button.lineeditor"));
      this.m_jDelete.setEnabled(AppUser.hasPermission("sales.EditLines"));
      this.m_jNumberKey.setMinusEnabled(AppUser.hasPermission("sales.EditLines"));
      this.btnSplit.setEnabled(AppUser.hasPermission("sales.Total"));
      this.m_jNumberKey.setEqualsEnabled(AppUser.hasPermission("sales.Total"));
      this.m_jbtnconfig.setPermissions(this.m_App.getAppUserView().getUser());
      this.m_ticketsbag.activate();

      try {
         this.defaultProduct = this.dlSales.getDefaultProductInfo("DefaultProduct", this.siteGuid);
      } catch (BasicException var4) {
         this.defaultProduct = null;
      }

      this.logout = new JPanelTicket.logout();
      if (SystemProperty.ENABLEAUTOLOGOFF && SystemProperty.AUTOLOGOFFINACTIVITYTIMER) {
         try {
            this.delay = SystemProperty.AUTOLOGOFFPERIOD;
            if (this.delay != 0) {
               AutoLogoff.getInstance().setTimer(this.delay * 1000, this.logout);
            }
         } catch (NumberFormatException var3) {
            this.delay = 0;
         }
      }

      this.paymentdialogreceipt = JPaymentSelectReceipt.getDialog(this);
      this.paymentdialogreceipt.init(this.m_App);
      this.paymentdialogrefund = JPaymentSelectRefund.getDialog(this);
      this.paymentdialogrefund.init(this.m_App);
      List<TaxInfo> taxlist = this.senttax.list();
      this.taxcollection = new ListKeyed<>(taxlist);
      List<TaxCategoryInfo> taxcategorieslist = this.senttaxcategories.list();
      this.taxcategoriescollection = new ListKeyed<>(taxcategorieslist);
      this.taxcategoriesmodel = new ComboBoxValModel(taxcategorieslist);
      this.taxeslogic = new TaxesLogic(taxlist);
      this.instanceMap.put("m_ticketsbag", this.m_ticketsbag);
      this.instanceMap.put("principalApp", this.principalApp);
      this.btnReprint.setEnabled(AppUser.hasPermission("button.reprintlastticket"));
   }

   @Override
   public boolean deactivate() {
      AutoLogoff.getInstance().deactivateTimer();
      if (this.m_oTicket != null && TerminalInfo.getPosType().equals("restaurant")) {
         this.restDB.clearTableLockByTicket(this.m_oTicket.getId());
      }

      return this.m_ticketsbag.deactivate();
   }

   @Override
   public Object getBean() {
      return this;
   }

   @Override
   public JComponent getComponent() {
      return this;
   }

   private void printPartialTotals() {
      if (this.m_oTicket.getLinesCount() == 0) {
         this.m_jSubtotalEuros.setText(null);
         this.m_jTaxesEuros.setText(null);
         this.m_jTotalEuros.setText(null);
      } else {
         this.m_jTotalEuros.setText(this.m_oTicket.printTotal());
         this.m_jTaxesEuros.setText(this.m_oTicket.printTaxAmount());
         this.m_jSubtotalEuros.setText(this.m_oTicket.printSubTotal());
      }

      this.repaint();
   }

   private void paintTicketLine(int index, TicketLineInfo oLine) {
      if (this.executeEventAndRefresh("ticket.setline", new JPanelTicket.ScriptArg("index", index), new JPanelTicket.ScriptArg("line", oLine)) == null) {
         this.m_oTicket.setLine(index, oLine);
         this.m_ticketlines.setTicketLine(index, oLine);
         this.m_ticketlines.setSelectedIndex(index);
         CustomerDisplay.updateDisplay(oLine);
         this.printPartialTotals();
         this.stateToZero();
         this.executeEventAndRefresh("ticket.pretotals");
         this.executeEventAndRefresh("ticket.change");
      }
   }

   private ProductInfoExt getInputProduct() {
      if (SystemProperty.TAXINCLUDED) {
         this.defaultProduct.setPriceSellInc(this.getInputValue());
      } else {
         this.defaultProduct.setPriceSell(this.getInputValue());
      }

      return this.defaultProduct;
   }

   private void removeTicketLine(int i, Boolean audit) {
      if ("OK".equals(this.m_oTicket.getLine(i).getProperty("sendstatus")) && SystemProperty.ALLOWSENTITEMREFUND
         || !"OK".equals(this.m_oTicket.getLine(i).getProperty("sendstatus")) && AppUser.hasPermission("sales.EditLines")) {
         if ("OK".equals(this.m_oTicket.getLine(i).getProperty("sendstatus"))) {
            this.m_oTicket.getLine(i).setProperty("sendstatus", "Cancel");
            this.printTicket("Printer.TicketKitchen", this.m_oTicket, this.m_oTicketExt, true);
            JAlertPane.messageBox(1, AppLocal.getIntString("message.orderCancellation"), 16, new Dimension(125, 50), 4);
         }

         if (this.executeEventAndRefresh("ticket.removeline", new JPanelTicket.ScriptArg("index", i)) == null) {
            if (audit) {
               Audit.itemRemoved(this.m_oTicket, i, "Single Item Removed");
            }

            if (this.m_oTicket.getLine(i).isProductCom()) {
               this.m_oTicket.removeLine(i);
               this.m_ticketlines.removeTicketLine(i);
            } else {
               String parentId = this.m_oTicket.getLine(i).getProductID();
               this.m_oTicket.removeLine(i);
               this.m_ticketlines.removeTicketLine(i);

               // Only the auxiliaries of this product go with it; other products below stay in the ticket
               int j = i;
               while (j < this.m_oTicket.getLinesCount() && this.m_oTicket.getLine(j).isProductCom()) {
                  if (this.isAuxiliaryOf(parentId, this.m_oTicket.getLine(j).getProductID())) {
                     this.m_oTicket.removeLine(j);
                     this.m_ticketlines.removeTicketLine(j);
                  } else {
                     j++;
                  }
               }
            }

            CustomerDisplay.clearDisplay();
            this.printPartialTotals();
            this.stateToZero();
            this.executeEventAndRefresh("ticket.pretotals");
            this.executeEventAndRefresh("ticket.change");
         }
      } else {
         JAlertPane.messageBox(1, AppLocal.getIntString("message.cannotdeletesentline"), 16, new Dimension(125, 50), 4);
      }
   }

   private double includeTaxes(String tcid, double dValue) {
      return 0.0;
   }

   private double excludeTaxes(String tcid, double dValue) {
      TaxInfo tax = this.taxeslogic.getTaxInfo(tcid, this.m_oTicket.getCustomer());
      double dTaxRate = tax == null ? 0.0 : tax.getRate();
      return dValue / (1.0 + dTaxRate);
   }

   private double getInputValue() {
      try {
         return Double.parseDouble(this.m_jPrice.getText());
      } catch (NumberFormatException var2) {
         return 0.0;
      }
   }

   private double getPorValue() {
      try {
         return Double.parseDouble(this.m_jQty.getText().substring(1));
      } catch (StringIndexOutOfBoundsException | NumberFormatException var2) {
         return 1.0;
      }
   }

   private void stateToZero() {
      this.m_jQty.setText("");
      this.m_jPrice.setText("");
      this.m_sBarcode = new StringBuffer();
      this.m_iNumberStatus = 0;
      this.m_iNumberStatusInput = 0;
      this.m_iNumberStatusQty = 0;
      this.repaint();
   }

   private void incProductByCode(String barCode) {
      this.alternativeBarcode = new AlternativeBarcode(barCode, this.siteGuid, this.dlSales);

      try {
         ProductInfoExt oProduct = this.dlSales.getProductInfoByCode(this.alternativeBarcode.getM_sbaseBarcode());
         if (oProduct == null) {
            this.errorBeep();
            JAlertPane.messageBox(1, "\n" + barCode + " - " + AppLocal.getIntString("message.noproduct"), 16, new Dimension(125, 50), 4);
            this.stateToZero();
         } else {
            oProduct.setName(this.alternativeBarcode.getDescription());
            if (this.bar.getBarcodeType() == 8) {
               if (oProduct.getProperty("price" + barCode.charAt(10)) != null) {
                  if (SystemProperty.TAXINCLUDED) {
                     oProduct.setPriceSellInc(Double.valueOf(oProduct.getProperty("price" + barCode.charAt(10))));
                  } else {
                     oProduct.setSellingPrice(Double.valueOf(oProduct.getProperty("price" + barCode.charAt(10))));
                  }
               }

               if (oProduct.getProperty("day" + barCode.charAt(11)) != null) {
                  oProduct.setName(oProduct.getName() + oProduct.getProperty("day" + barCode.charAt(11)));
               }
            }

            this.incProduct(this.alternativeBarcode.getSalesCount(), oProduct);
         }
      } catch (BasicException var3) {
         this.stateToZero();
         new MessageInf(var3).show(this);
      }
   }

   private void incProductByCodePrice(String barCode, double dPriceSell) {
      try {
         ProductInfoExt oProduct = this.dlSales.getProductInfoByCode(barCode);
         if (oProduct == null) {
            this.errorBeep();
            new MessageInf(-33554432, AppLocal.getIntString("message.noproduct")).show(this);
            this.stateToZero();
         } else {
            this.addTicketLine(oProduct, 1.0, dPriceSell);
         }
      } catch (BasicException var5) {
         this.stateToZero();
         new MessageInf(var5).show(this);
      }
   }

   private void incProduct(ProductInfoExt prod) {
      if (prod.isScale() && this.m_App.getDeviceScale().existsScale()) {
         try {
            Double value = this.m_App.getDeviceScale().readWeight();
            if (value != null) {
               this.incProduct(value, prod);
               this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProperty("sendstatus", "scale");
               this.refreshTicket();
            }
         } catch (ScaleException var3) {
            this.errorBeep();
            new MessageInf(-33554432, AppLocal.getIntString("message.noweight"), var3).show(this);
            this.stateToZero();
         }
      } else if (prod.getID().equalsIgnoreCase("giftcard-sale") || prod.getID().equalsIgnoreCase("giftcard-topup")) {
         this.addGiftCardSale();
      } else if (prod.getID().equalsIgnoreCase("deliverycharge") && this.m_oTicket.getNoDelivery() == "1") {
         this.incProduct(1.0, prod);
      } else if (!prod.isVprice()) {
         this.incProduct(1.0, prod);
      } else {
         this.errorBeep();
         JAlertPane.messageBox(0, AppLocal.getIntString("message.novprice"), 16, new Dimension(125, 50), 4);
      }
   }

   private void incProduct(double dPor, ProductInfoExt prod) {
      if (!prod.isScale() && prod.isVprice()) {
         this.addTicketLine(prod, this.getPorValue(), this.getInputValue());
      } else {
         this.addTicketLine(prod, dPor, prod.getPriceSell());
      }
   }

   protected void buttonTransition(ProductInfoExt prod) {
      if (this.m_iNumberStatusInput == 0 && this.m_iNumberStatusQty == 0) {
         this.incProduct(prod);
      } else if (this.m_iNumberStatusInput == 1 && this.m_iNumberStatusQty == 0) {
         this.incProduct(this.getInputValue(), prod);
      } else if (prod.isVprice()) {
         this.addTicketLine(prod, this.getPorValue(), this.getInputValue());
      } else {
         this.errorBeep();
      }
   }

   private Boolean checkAgeAlert(int age) {
      return JAlertPane.messageBox(1, AppLocal.getIntString("message.age.required", age), 16, new Dimension(125, 50), 3) == 5;
   }

   private void addGiftCardLine(ProductInfoExt product, Double amount, String cardNumber) {
      this.addTicketLine(product, 1.0, amount);
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProductAttSetInstDesc("Giftcard Number - " + cardNumber);
      this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProperty("cardnotes", "Giftcard Number - " + cardNumber);
      this.refreshTicket();
   }

   @Override
   public void addServiceChargeLine() {
      try {
         ProductInfoExt oProduct = this.dlSales.getProductInfo("ServiceCharge");
         this.addTicketLine(oProduct, 1.0, 0.0);
         this.refreshTicket();
      } catch (BasicException var2) {
         Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var2);
      }
   }

   private void processTicketLineReceipt() {
      if (this.m_oTicket.getLinesCount() > 0) {
         if (this.closeTicket(this.m_oTicket, this.m_oTicketExt)) {
            if (this.m_oTicket.isRefund()) {
               try {
                  JRefundLines.updateRefunds();
               } catch (BasicException var2) {
               }
            }

            this.m_ticketsbag.deleteTicket();
            if (!"restaurant".equals(TerminalInfo.getPosType()) && SystemProperty.ENABLEAUTOLOGOFF && SystemProperty.AUTOLOGOFFAFTERSALE) {
               ((JRootApp)this.m_App).closeAppView();
            } else if ("restaurant".equals(TerminalInfo.getPosType())
               && SystemProperty.ENABLEAUTOLOGOFF
               && SystemProperty.AUTOLOGOFFAFTERSALE
               && !SystemProperty.AUTOLOGOFFTOTABLES) {
               ((JRootApp)this.m_App).closeAppView();
            }
         } else {
            this.refreshTicket();
         }
      } else {
         this.errorBeep();
      }
   }

   private void getScaleWeightFromUserDialogInput() {
      int i = this.m_ticketlines.getSelectedIndex();
      if (i < 0) {
         this.errorBeep();
      } else if (this.m_App.getDeviceScale().existsScale()) {
         try {
            Double value = this.m_App.getDeviceScale().readWeight();
            if (value != null) {
               TicketLineInfo newline = new TicketLineInfo(this.m_oTicket.getLine(i));
               newline.setMultiply(value);
               newline.setPrice(Math.abs(newline.getPrice()));
               this.paintTicketLine(i, newline);
            }
         } catch (ScaleException var4) {
            this.errorBeep();
            new MessageInf(-33554432, AppLocal.getIntString("message.noweight"), var4).show(this);
            this.stateToZero();
         }
      } else {
         this.errorBeep();
      }
   }

   private void decreaseSelectedTicketLineByMultiplier() {
      int i = this.m_ticketlines.getSelectedIndex();
      if (i < 0) {
         this.errorBeep();
      } else {
         double dPor = this.getPorValue();
         TicketLineInfo newline = new TicketLineInfo(this.m_oTicket.getLine(i));
         if (this.m_oTicket.getLine(i).isServiceCharge()) {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.deleteservicecharge"), 16, new Dimension(125, 50), 3) == 6) {
               return;
            }

            this.m_oTicket.setNoSC("0");
         }

         if (this.m_oTicket.getLine(i).getMultiply() > dPor && this.m_oTicket.isNormal()) {
            newline.setMultiply(this.m_oTicket.getLine(i).getMultiply() - dPor);
            newline.setPrice(-Math.abs(newline.getPrice()));
            this.paintTicketLine(i, newline);
            this.checkSellingPrices(this.m_oTicket);
            this.checkServiceCharge(this.m_oTicket);
            this.refreshTicket();
         }
      }
   }

   private void increaseSelectedTicketLineByMultiplier() {
      int i = this.m_ticketlines.getSelectedIndex();
      if (i < 0) {
         this.errorBeep();
      } else {
         double dPor = this.getPorValue();
         TicketLineInfo newline = new TicketLineInfo(this.m_oTicket.getLine(i));
         if (this.m_oTicket.getLine(i).isServiceCharge() && SystemProperty.SCONOFF) {
            return;
         }

         if (this.m_oTicket.isRefund()) {
            newline.setMultiply(-dPor);
            newline.setPrice(Math.abs(newline.getPrice()));
            this.paintTicketLine(i, newline);
         } else {
            newline.setMultiply(this.m_oTicket.getLine(i).getMultiply() + dPor);
            newline.setPrice(Math.abs(newline.getPrice()));
            this.paintTicketLine(i, newline);
         }

         this.checkSellingPrices(this.m_oTicket);
         this.checkServiceCharge(this.m_oTicket);
         this.refreshTicket();
      }
   }

   protected void removeSCLine() {
      int l = 0;

      for (TicketLineInfo tl : this.m_oTicket.getLines()) {
         if (tl.isServiceCharge()) {
            if (l < 0) {
               this.errorBeep();
            } else {
               TicketLineInfo newline = new TicketLineInfo(this.m_oTicket.getLine(l));
               if (this.m_oTicket.isRefund()) {
                  newline.setMultiply(newline.getMultiply() + 1.0);
                  if (newline.getMultiply() >= 0.0) {
                     this.removeTicketLine(l, true);
                  } else {
                     this.paintTicketLine(l, newline);
                  }
               } else {
                  newline.setMultiply(newline.getMultiply() - 1.0);
                  if (newline.getMultiply() <= 0.0) {
                     this.removeTicketLine(l, true);
                  } else {
                     this.paintTicketLine(l, newline);
                  }
               }

               this.checkSellingPrices(this.m_oTicket);
               this.checkServiceCharge(this.m_oTicket);
               this.refreshTicket();
            }
         }
      }
   }

   private void decreaseSelectedTicketLine() {
      int i = this.m_ticketlines.getSelectedIndex();
      if (i < 0) {
         this.errorBeep();
      } else {
         TicketLineInfo newline = new TicketLineInfo(this.m_oTicket.getLine(i));
         if (this.m_oTicket.getLine(i).isServiceCharge()) {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.deleteservicecharge"), 16, new Dimension(125, 50), 3) == 6) {
               return;
            }

            this.removeTicketLine(i, true);
            this.m_oTicket.setNoSC("0");
            return;
         }

         if (this.m_oTicket.getLine(i).isDeliveryCharge()) {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.deletedeliverycharge"), 16, new Dimension(125, 50), 3) == 6) {
               JAlertPane.messageBox(1, AppLocal.getIntString("message.multipleservicecharge"), 16, new Dimension(125, 50), 4);
               return;
            }

            this.removeTicketLine(i, true);
            this.m_oTicket.setNoDelivery("0");
            this.deliveryInfo = null;
            return;
         }

         if (this.m_oTicket.isRefund()) {
            newline.setMultiply(newline.getMultiply() + 1.0);
            if (newline.getMultiply() >= 0.0) {
               this.removeTicketLine(i, true);
            } else {
               this.paintTicketLine(i, newline);
            }
         } else {
            if ("OK".equals(this.m_oTicket.getLine(i).getProperty("sendstatus")) && !SystemProperty.ALLOWSENTITEMREFUND) {
               JAlertPane.messageBox(1, AppLocal.getIntString("message.cannotdeletesentline"), 16, new Dimension(125, 50), 4);
               return;
            }

            if (newline.getMultiply() == 1.0) {
               if (JAlertPane.messageBox(0, AppLocal.getIntString("message.finallineitem"), 16, new Dimension(125, 50), 3) == 5) {
                  newline.setMultiply(newline.getMultiply() - 1.0);
                  if (newline.getMultiply() <= 0.0) {
                     this.removeTicketLine(i, true);
                  } else {
                     this.paintTicketLine(i, newline);
                  }
               }
            } else {
               newline.setMultiply(newline.getMultiply() - 1.0);
               Audit.itemRemoved(this.m_oTicket, i, 1.0, "Single Item Removed");
               if (newline.getMultiply() <= 0.0) {
                  this.removeTicketLine(i, true);
               } else {
                  this.paintTicketLine(i, newline);
               }
            }
         }

         this.checkSellingPrices(this.m_oTicket, true, 1.0, newline.getProductName(), newline.getProductID());
         this.checkServiceCharge(this.m_oTicket);
         this.refreshTicket();
      }
   }

   private void checkServiceCharge(TicketInfo m_oTicket) {
      Double serviceCalculation = 0.0;

      for (int i = 0; i < m_oTicket.getLinesCount(); i++) {
         if (!m_oTicket.getLine(i).isServiceCharge()) {
            serviceCalculation = serviceCalculation + m_oTicket.getLine(i).getSellingPrice() * m_oTicket.getLine(i).getMultiply();
         }
      }

      for (int ix = 0; ix < m_oTicket.getLinesCount(); ix++) {
         if (m_oTicket.getLine(ix).isServiceCharge()) {
            if (m_oTicket.isRefund()) {
               m_oTicket.getLine(ix).setPrice(-serviceCalculation * (SystemProperty.SCRATE / 100.0));
            } else {
               m_oTicket.getLine(ix).setPrice(serviceCalculation * (SystemProperty.SCRATE / 100.0) * (1.0 + m_oTicket.getLine(ix).getTaxInfo().getRate()));
            }
         }
      }
   }

   private void checkSellingPrices(TicketInfo m_oTicket) {
      this.checkSellingPrices(m_oTicket, false, 0.0, null, null);
   }

   private void checkSellingPrices(TicketInfo m_oTicket, String name, String id) {
      this.checkSellingPrices(m_oTicket, false, 0.0, name, id);
   }

   private void checkSellingPrices(TicketInfo m_oTicket, Boolean itemRemoved, Double qty, String name, String id) {
      HashMap<String, Double> products = new HashMap<>();
      Double costPlusMarkup = this.getCostPlusMarkup(m_oTicket.getCustomer());

      for (int i = 0; i < m_oTicket.getLinesCount(); i++) {
         TicketLineInfo current_line = m_oTicket.getLine(i);
         if (!current_line.isProductVprice() & !current_line.isSystemObject()) {
            products.putIfAbsent(current_line.getProductID(), 0.0);
            if (!current_line.isDiscounted()) {
               products.put(current_line.getProductID(), products.get(current_line.getProductID()) + current_line.getMultiply());
            }
         }
      }

      for (int ix = 0; ix < m_oTicket.getLinesCount(); ix++) {
         TicketLineInfo current_line = m_oTicket.getLine(ix);
         if (!current_line.isProductVprice()
            & !current_line.isSystemObject()
            & current_line.getProperty("edited") == null
            & !m_oTicket.getTicketType().toString().equalsIgnoreCase("REFUND")) {
            if (m_oTicket.isTaxExempt()) {
               m_oTicket.getLine(ix).setPrice(current_line.getProductInfoExt().getUnitPriceExempt(products.get(current_line.getProductID())));
            } else {
               m_oTicket.getLine(ix).setPrice(current_line.getProductInfoExt().getUnitPrice(products.get(current_line.getProductID())));
            }

            current_line.setProperty("tierpriced", String.valueOf(current_line.getProductInfoExt().isTierPriced(products.get(current_line.getProductID()))));
            this.applyCostPlus(current_line, costPlusMarkup);
         } else if (m_oTicket.getTicketType().toString().equalsIgnoreCase("REFUND") && current_line.getPrice() > 0.0) {
            current_line.setPrice(current_line.getPrice() * -1.0);
         }
      }

      if (itemRemoved) {
         this.parameters.clear();
         this.parameters.put("qty", qty);
         this.parameters.put("name", name != null ? name : m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).getProductName());
         this.parameters.put("id", id);
         CustomerDisplay.updateDisplay("Display.ItemRemoved", this.parameters);
      } else if (this.m_ticketlines.getSelectedIndex() > -1) {
         CustomerDisplay.updateDisplay(m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()));
      }
   }

   private void increaseSelectedTicketLine() {
      int i = this.m_ticketlines.getSelectedIndex();
      if ("OK".equals(this.m_oTicket.getLine(i).getProperty("sendstatus"))) {
         JAlertPane.messageBox(1, AppLocal.getIntString("message.cannotincreasesentline"), 16, new Dimension(125, 50), 4);
      } else if (i == -1 || !this.m_oTicket.getLine(i).isDeliveryCharge() && !this.m_oTicket.getLine(i).isServiceCharge()) {
         if (i < 0) {
            this.errorBeep();
         } else {
            if (this.m_oTicket.getLine(i).getProperty("cardnotes") != null && this.m_oTicket.getLine(i).getProperty("cardnotes").startsWith("Loyalty purchase")
               )
             {
               JAlertPane.messageBox(0, AppLocal.getIntString("message.rescanloyalty"), 16, new Dimension(125, 50), 4);
               this.stateToZero();
               return;
            }

            if (!this.m_oTicket.getLine(i).isGiftCardSale() & !this.m_oTicket.getLine(i).isGiftCardTopUp()) {
               TicketLineInfo newline = new TicketLineInfo(this.m_oTicket.getLine(i));
               if (this.m_oTicket.isRefund()) {
                  newline.setMultiply(newline.getMultiply() - 1.0);
                  this.paintTicketLine(i, newline);
               } else {
                  newline.setMultiply(newline.getMultiply() + 1.0);
                  this.paintTicketLine(i, newline);
               }

               this.checkSellingPrices(this.m_oTicket);
               this.checkServiceCharge(this.m_oTicket);
               this.refreshTicket();
            } else {
               this.stateToZero();
            }
         }
      } else {
         JAlertPane.messageBox(
            1,
            AppLocal.getIntString(this.m_oTicket.getLine(i).isDeliveryCharge() ? "message.multipledeliverycharge" : "message.multipleservicecharge"),
            16,
            new Dimension(125, 50),
            4
         );
      }
   }

   private void readWeightFromScales() {
      if (this.m_App.getDeviceScale().existsScale() && AppUser.hasPermission("sales.EditLines")) {
         try {
            Double value = this.m_App.getDeviceScale().readWeight();
            if (value != null) {
               ProductInfoExt product = this.getInputProduct();
               this.addTicketLine(product, value, product.getPriceSell());
            }
         } catch (ScaleException var3) {
            this.errorBeep();
            new MessageInf(-33554432, AppLocal.getIntString("message.noweight"), var3).show(this);
            this.stateToZero();
         }
      } else {
         this.errorBeep();
      }
   }

   private void errorBeep() {
      Toolkit.getDefaultToolkit().beep();
   }

   private void pricePeriod(String strValue) {
      this.m_jPrice.setText(SystemProperty.PRICEWITH00 ? this.setTempjPrice(strValue) : strValue);
   }

   private void pricePeriodWithNumber(String strValue) {
      this.m_jPrice.setText(SystemProperty.PRICEWITH00 ? this.setTempjPrice(this.m_jPrice.getText() + strValue) : this.m_jPrice.getText() + strValue);
   }

   private boolean closeTicket(TicketInfo ticket, Object ticketext) {
      AutoLogoff.getInstance().deactivateTimer();
      boolean resultok = false;
      if (AppUser.hasPermission("sales.Total")) {
         this.warrantyCheck(ticket);

         try {
            this.taxeslogic.calculateTaxes(ticket);
            if (ticket.getTicketTotal() >= 0.0) {
               ticket.resetPayments();
            }

            if (this.executeEvent(ticket, ticketext, "ticket.total") == null) {
               this.parameters.clear();
               this.parameters.put("ticket", ticket);
               CustomerDisplay.updateDisplay("Display.TicketTotal", this.parameters);
               this.paymentdialog = ticket.isRefund() ? this.paymentdialogrefund : this.paymentdialogreceipt;
               this.paymentdialog.setTransactionID(ticket.getTransactionID());
               if (ticket.isRefund()) {
                  this.printTicket("Display.RefundTotal", ticket, ticketext, false);
               }

               if (this.paymentdialog.showDialog(ticket.getTicketTotal(), ticket.getCustomer(), ticket.getLoyaltyCard())) {
                  ticket.setPayments(this.paymentdialog.getSelectedPayments());

                  for (PaymentInfo p : this.paymentdialog.getSelectedPayments()) {
                     if (p.getName().equals("giftcard")) {
                        this.giftCard.updateRedeemedValue(p.getCardName(), ticket, p.getPaid());
                     }
                  }

                  ticket.setUser(this.m_App.getAppUserView().getUser().getUserInfo());
                  ticket.setActiveCash(this.m_App.getActiveCashIndex());
                  ticket.setDate(new Date());
                  ticket.setCardFees(this.paymentdialog.getSurcharge());
                  if (this.executeEvent(ticket, ticketext, "ticket.save") == null) {
                     try {
                        this.dlSales.saveTicket(ticket, this.m_App.getInventoryLocation(), this.loyaltyCard, ticketext, this.deliveryInfo);

                        for (TicketLineInfo line : this.m_oTicket.getLines()) {
                           if (line.getProperty("vCode") != "" && line.getProperty("vCode") != null) {
                              try {
                                 this.dlSales.sellVoucher(new Object[]{line.getProperty("vCode"), Integer.toString(ticket.getTicketId()), line.getPrice()});
                              } catch (BasicException var9) {
                              }
                           }

                           String tendered = line.getProductID();
                           switch (tendered) {
                              case "giftcard-sale":
                                 this.giftCard.activateCard(line.getProperty("cardnumber"), line.getPrice(), ticket);
                                 break;
                              case "giftcard-topup":
                                 this.giftCard.updateCard(line.getProperty("cardnumber"), line.getPrice(), ticket);
                           }
                        }

                        AppConfig.put("lastticket.number", Integer.toString(ticket.getTicketId()));
                        AppConfig.put("lastticket.type", Integer.toString(ticket.getTicketType().getId()));
                        this.restockAlerts.afterSale(ticket);
                        JCatalog.refreshAllStock();
                     } catch (BasicException var10) {
                        MessageInf msg = new MessageInf(-67108864, AppLocal.getIntString("message.nosaveticket"), var10);
                        msg.show(this);
                     }

                     boolean isCash = false;
                     String change = "";
                     Double tendered = 0.0;

                     for (PaymentInfo px : ticket.getPayments()) {
                        tendered = tendered + px.getTendered();
                        if ("cash".equals(px.getName())) {
                           isCash = true;
                           if (px.getTotal() < 0.0) {
                              change = Formats.CURRENCY.formatValue(px.getTotal());
                           } else {
                              change = Formats.CURRENCY.formatValue(px.getChange());
                           }
                        }
                     }

                     ticket.setProperty("change", change);
                     this.openDrawer(ticket);
                     this.parameters.clear();
                     this.parameters.put("ticket", ticket);
                     CustomerDisplay.updateDisplay("Display.TicketChange", this.parameters);
                     this.paymentdialog
                        .setReceiptRequired(
                           Receipt.required(
                              Formats.CURRENCY.formatValue(ticket.getTicketTotal()),
                              Formats.CURRENCY.formatValue(tendered),
                              change,
                              SystemProperty.RECEIPTPRINTOFF
                           )
                        );
                     if (this.loyaltyCard != null) {
                        this.loyaltyCard.processTicketPoints(this.loyaltyCard.getCardNumber(), ticket);
                     }

                     if (this.paymentdialog.isGiftReceiptRequired()) {
                        this.printTicket("Printer.GiftReceipt");
                     }

                     if (this.paymentdialog.printReceipt() || this.receiptRequired) {
                        this.printTicket("Printer.Ticket", ticket, ticketext, false);
                     }

                     CustomerDisplay.updateDisplay("display.Message");
                     resultok = true;
                     if ("restaurant".equals(TerminalInfo.getPosType()) && !ticket.getOldTicket()) {
                        this.restDB.clearCustomerNameInTable(ticketext.toString());
                        this.restDB.clearWaiterNameInTable(ticketext.toString());
                        this.restDB.clearTicketIdInTable(ticketext.toString());
                        this.restDB.clearTableLockByName(ticketext.toString());
                     }
                  }
               }
            }
         } catch (TaxesException var11) {
            MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotcalculatetaxes"));
            msg.show(this);
            resultok = false;
         }

         this.m_oTicket.resetTaxes();
         this.m_oTicket.resetPayments();
      }

      AutoLogoff.getInstance().activateTimer();
      return resultok;
   }

   private boolean checkVoucherCurrentTicket(String voucher) {
      for (TicketLineInfo line : this.m_oTicket.getLines()) {
         if (line.getProperty("vCode") != null && line.getProperty("vCode").equals(voucher)) {
            return true;
         }
      }

      return false;
   }

   private boolean warrantyCheck(TicketInfo ticket) {
      this.receiptRequired = false;

      for (int lines = 0; lines < ticket.getLinesCount(); lines++) {
         if (!this.receiptRequired) {
            this.receiptRequired = ticket.getLine(lines).warrantyRequired();
            return true;
         }
      }

      return false;
   }

   public String getPickupString(TicketInfo pTicket) {
      if (pTicket == null) {
         return "0";
      } else {
         String tmpPickupId = Integer.toString(pTicket.getPickupId());
         if (SystemProperty.PICKUPSIZE >= tmpPickupId.length()) {
            while (tmpPickupId.length() < SystemProperty.PICKUPSIZE) {
               tmpPickupId = "0" + tmpPickupId;
            }
         }

         return tmpPickupId;
      }
   }

   private void printVoucher(String sresourcename) {
      String source = this.dlSystem.getResourceAsXML(sresourcename);
      IncludeFile incFile = new IncludeFile(source, this.dlSystem);
      if (source == null) {
         new MessageInf(-33554432, AppLocal.getIntString("message.cannotprintticket"));
      } else {
         String sresource = incFile.processInclude();

         try {
            ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
            this.m_TTP.printTicket(script.eval(sresource).toString());
         } catch (TicketPrinterException | ScriptException var7) {
            new MessageInf(-33554432, AppLocal.getIntString("message.cannotprintticket"), var7);
         }
      }
   }

   private void openDrawer(TicketInfo ticket) {
      String sresource = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><output><opendrawerNoLog/></output>";

      try {
         ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
         this.m_TTP.printTicket(script.eval(sresource).toString(), ticket);
      } catch (TicketPrinterException | ScriptException var4) {
         Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var4);
      }
   }

   public void printTicket(String sresourcename, TicketInfo ticket, Object ticketext, Boolean cancel) {
      String source = this.dlSystem.getResourceAsXML(sresourcename);
      IncludeFile incFile = new IncludeFile(source, this.dlSystem);
      if (source.isEmpty()) {
         MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotprintticket"));
         msg.show(this);
      } else {
         String sresource = incFile.processInclude();
         if (SystemProperty.PICKUPENABLED) {
            if (ticket.getPickupId() == 0) {
               try {
                  ticket.setPickupId(this.dlSales.getNextPickupIndex());
               } catch (BasicException var13) {
                  ticket.setPickupId(0);
               }
            }
         } else {
            ticket.setPickupId(0);
         }

         try {
            ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
            if (ticket.getLinesCount() > 0) {
               script.put("salesticket", ticket.getLine(0).getProperty("salesticket"));
            }

            script.put("ticket", ticket);
            script.put("place", ticketext);
            script.put("warranty", this.receiptRequired);
            script.put("ticketpanel", this);
            script.put("loyalty", this.loyaltyCard);
            script.put("company", new CompanyInfo());
            script.put("giftcard", new GiftCardLogic());
            script.put("printers", this.setRemoteUnits(ticket, cancel));
            script.put("pickupcode", "P" + ticket.getId().substring(24));
            script.put("taxincluded", ticket.isTaxInclusive());
            script.put("pickupenabled", SystemProperty.PICKUPENABLED);
            script.put("cancellation", cancel);
            script.put("currentDate", this.format.format(new Date()));
            ReceiptTaxesInfo ltr = new ReceiptTaxesInfo();
            List<ReceiptTaxesInfo> lines = ltr.getReceiptTaxLines(this.dlSales.getLineTaxRates(ticket.getId()));
            script.put(
               "nett",
               ticket.isTaxInclusive() ? ltr.printReceiptSubTotal(lines, ticket.getTicketTotal()) : Formats.CURRENCY.formatValue(ticket.getSubTotalExcluding())
            );
            script.put("tickettaxdetails", lines);
            script.put("deliveryinfo", this.deliveryInfo);
            script.put(
               "cardType",
               new String[]{
                  "",
                  LocalResource.getString("paymentdescription.cmagcard1"),
                  LocalResource.getString("paymentdescription.cmagcard2"),
                  LocalResource.getString("paymentdescription.cmagcard3"),
                  LocalResource.getString("paymentdescription.cmagcard4"),
                  LocalResource.getString("transpayment.cmagcard5")
               }
            );
            script.put(
               "cardRefundType",
               new String[]{
                  "",
                  LocalResource.getString("paymentdescription.cmagcard1refund"),
                  LocalResource.getString("paymentdescription.cmagcard2refund"),
                  LocalResource.getString("paymentdescription.cmagcard3refund"),
                  LocalResource.getString("paymentdescription.cmagcard4refund"),
                  LocalResource.getString("paymentdescription.cmagcard5refund")
               }
            );
            this.refreshTicket();
            this.m_TTP.printTicket(script.eval(sresource).toString(), ticket);
         } catch (ScriptException var11) {
            MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotprintticket"), var11);
            msg.show(this);
         } catch (TicketPrinterException | BasicException var12) {
            Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var12);
         }
      }
   }

   private Set<String> setRemoteUnits(TicketInfo ticket, Boolean cancel) {
      Set<String> ptrs = new HashSet<>();
      if (cancel) {
         ticket.getLines()
            .stream()
            .filter(t -> t.isProductKitchen() && t.getProperty("sendstatus").equalsIgnoreCase("cancel"))
            .forEachOrdered(t -> ptrs.add(t.getRemotePrinter()));
      } else {
         ticket.getLines().stream().filter(t -> t.isProductKitchen()).forEachOrdered(t -> ptrs.add(t.getRemotePrinter()));
      }

      return ptrs;
   }

   public void kitchenOrderScreen() {
      this.kitchenOrderScreen(this.kitchenOrderId(), 1, false);
   }

   public void kitchenOrderScreen(String id) {
      this.kitchenOrderScreen(id, 1, true);
   }

   public void kitchenOrderScreen(Integer display, String ticketid) {
      this.kitchenOrderScreen(this.kitchenOrderId(), display, false);
   }

   public void kitchenOrderScreen(Integer display) {
      this.kitchenOrderScreen(this.kitchenOrderId(), display, false);
   }

   public void kitchenOrderScreen(Integer display, Boolean primary) {
      this.kitchenOrderScreen(this.kitchenOrderId(), display, primary);
   }

   public String kitchenOrderId() {
      if (this.m_oTicket.getCustomer() != null) {
         return this.m_oTicket.getCustomer().getName();
      } else if (this.m_oTicketExt != null) {
         return this.m_oTicketExt.toString();
      } else {
         if (SystemProperty.PICKUPENABLED) {
            if (this.m_oTicket.getPickupId() == 0) {
               try {
                  this.m_oTicket.setPickupId(this.dlSales.getNextPickupIndex());
               } catch (BasicException var2) {
                  this.m_oTicket.setPickupId(0);
               }
            }
         } else {
            this.m_oTicket.setPickupId(0);
         }

         return this.getPickupString(this.m_oTicket);
      }
   }

   private void kitchenOrderScreen(String id, Integer display, boolean primary) {
      Integer lastDisplay = null;
      String orderUUID = UUID.randomUUID().toString();

      for (int i = 0; i < this.m_oTicket.getLinesCount(); i++) {
         if ("No".equals(this.m_oTicket.getLine(i).getProperty("sendstatus")) && this.m_oTicket.getLine(i).isProductDisplay()) {
            if (!primary) {
               display = this.m_oTicket.getLine(i).getDisplayId() == 0 ? 1 : this.m_oTicket.getLine(i).getDisplayId();
            }

            try {
               if (this.m_oTicket.getLine(i).isProductCom()) {
                  if (lastDisplay != null && !primary) {
                     display = lastDisplay;
                  }

                  if (this.m_oTicket.getLine(i).isProductDisplay()) {
                     this.dlSystem
                        .addOrder(
                           UUID.randomUUID().toString(),
                           orderUUID,
                           (int)this.m_oTicket.getLine(i).getMultiply(),
                           "+ " + this.m_oTicket.getLine(i).getKitchenName(),
                           this.m_oTicket.getLine(i).getProductAttSetInstDesc(),
                           this.m_oTicket.getLine(i).getProperty("notes"),
                           id,
                           display,
                           1,
                           i
                        );
                  }
               } else if (this.m_oTicket.getLine(i).isProductDisplay()) {
                  this.dlSystem
                     .addOrder(
                        UUID.randomUUID().toString(),
                        orderUUID,
                        (int)this.m_oTicket.getLine(i).getMultiply(),
                        this.m_oTicket.getLine(i).getKitchenName(),
                        this.m_oTicket.getLine(i).getProductAttSetInstDesc(),
                        this.m_oTicket.getLine(i).getProperty("notes"),
                        id,
                        display,
                        0,
                        i
                     );
               }
            } catch (BasicException var8) {
               Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var8);
            }

            lastDisplay = display;
         }
      }
   }

   public void doRefresh() {
      this.refreshTicket();
   }

   private Object evalScript(JPanelTicket.ScriptObject scr, String resource, JPanelTicket.ScriptArg... args) {
      try {
         scr.setSelectedIndex(this.m_ticketlines.getSelectedIndex());
         return scr.evalScript(this.dlSystem.getResourceAsXML(resource), args);
      } catch (ScriptException var6) {
         MessageInf msg = new MessageInf(-33554432, resource + ": " + AppLocal.getIntString("message.cannotexecute"), var6);
         msg.show(this);
         return msg;
      }
   }

   public void evalScriptAndRefresh(String resource, JPanelTicket.ScriptArg... args) {
      if (resource == null) {
         MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotexecute"));
         msg.show(this);
      } else {
         JPanelTicket.ScriptObject scr = new JPanelTicket.ScriptObject(this.m_oTicket, this.m_oTicketExt);
         scr.setSelectedIndex(this.m_ticketlines.getSelectedIndex());
         this.evalScript(scr, resource, args);
         this.refreshTicket();
         this.setSelectedIndex(scr.getSelectedIndex());
      }
   }

   public void printTicket(String resource) {
      this.printTicket(resource, this.m_oTicket, this.m_oTicketExt, false);
   }

   private Object executeEventAndRefresh(String eventkey, JPanelTicket.ScriptArg... args) {
      String resource = this.m_jbtnconfig.getEvent(eventkey);
      if (resource == null) {
         return null;
      } else {
         JPanelTicket.ScriptObject scr = new JPanelTicket.ScriptObject(this.m_oTicket, this.m_oTicketExt);
         scr.setSelectedIndex(this.m_ticketlines.getSelectedIndex());
         Object result = this.evalScript(scr, resource, args);
         this.refreshTicket();
         this.setSelectedIndex(scr.getSelectedIndex());
         return result;
      }
   }

   private Object executeEvent(TicketInfo ticket, Object ticketext, String eventkey, JPanelTicket.ScriptArg... args) {
      String resource = this.m_jbtnconfig.getEvent(eventkey);
      if (resource == null) {
         return null;
      } else {
         JPanelTicket.ScriptObject scr = new JPanelTicket.ScriptObject(ticket, ticketext);
         return this.evalScript(scr, resource, args);
      }
   }

   protected String getResourceAsXML(String sresourcename) {
      return this.dlSystem.getResourceAsXML(sresourcename);
   }

   protected BufferedImage getResourceAsImage(String sresourcename) {
      return this.dlSystem.getResourceAsImage(sresourcename);
   }

   private void setSelectedIndex(int i) {
      this.m_ticketlines.setSelectedIndex(i >= 0 && i < this.m_oTicket.getLinesCount() ? i : this.m_oTicket.getLinesCount() - 1);
   }

   private String setTempjPrice(String jPrice) {
      jPrice = jPrice.replace(".", "");
      long tempL = Long.parseLong(jPrice);
      jPrice = Long.toString(tempL);

      while (jPrice.length() < 3) {
         jPrice = "0" + jPrice;
      }

      return jPrice.length() <= 2 ? jPrice : new StringBuffer(jPrice).insert(jPrice.length() - 2, ".").toString();
   }

   private void validateISSNBarcode(String barcode) {
   }

   // Some scanners and keyboard emulators inject invisible characters (shown as
   // empty boxes) that corrupt the barcode, so they are dropped before processing.
   private boolean isInvisibleChar(char c) {
      if (c == '\n' || c == 127 || c == 167) {
         return false;
      }
      switch (Character.getType(c)) {
         case Character.CONTROL:
         case Character.FORMAT:
         case Character.PRIVATE_USE:
         case Character.SURROGATE:
         case Character.UNASSIGNED:
            return true;
         default:
            return !this.m_jPrice.getFont().canDisplay(c);
      }
   }

   /** True when aux is set up in Admin as an auxiliary of product (table products_com). */
   private boolean isAuxiliaryOf(String product, String aux) {
      if (product == null || aux == null) {
         return false;
      }

      String key = product + "|" + aux;
      Boolean linked = this.auxiliaryLinks.get(key);
      if (linked == null) {
         linked = false;

         try (PreparedStatement ps = this.m_App.getSession().getConnection()
               .prepareStatement("select 1 from products_com where product = ? and product2 = ?")) {
            ps.setString(1, product);
            ps.setString(2, aux);

            try (ResultSet rs = ps.executeQuery()) {
               linked = rs.next();
            }
         } catch (SQLException ex) {
            // Without the link the auxiliary is treated as a line of its own, so nothing is deleted by mistake
         }

         this.auxiliaryLinks.put(key, linked);
      }

      return linked;
   }

   private void stateTransition(char cTrans) {
      if (this.isInvisibleChar(cTrans)) {
         return;
      }

      if (cTrans == '\n') {
         if (this.m_sBarcode.length() == 0) {
            this.processTicketLineReceipt();
         } else {
            this.bar = new Barcode(this.m_sBarcode.toString());
            this.barCode = this.bar.getBarCode();
            if (this.dlSystem.isFobPresent(this.barCode.toString())) {
               this.stateToZero();
               this.principalApp.showTask("uk.chromis.pos.panels.JPanelCloseMoney");
               return;
            }

            switch (this.bar.getBarcodeType()) {
               case -1:
                  this.errorBeep();
                  break;
               case 0:
                  System.out.println("Normal");
                  break;
               case 1:
                  System.out.println("Customer");
                  if (DataLogicLoyalty.isCardRetired(this.barCode)) {
                     this.errorBeep();
                     JAlertPane.messageBox(0, AppLocal.getIntString("message.cardretired", this.barCode), 16, new Dimension(125, 50), 4);
                     this.stateToZero();
                     return;
                  }

                  CustomerInfoExt newCustomer = null;

                  try {
                     newCustomer = this.dlSales.findCustomerExt(this.barCode);
                     if (newCustomer == null) {
                        this.errorBeep();
                        JAlertPane.messageBox(0, AppLocal.getIntString("message.nocustomer", this.barCode), 16, new Dimension(125, 50), 4);
                     }
                  } catch (BasicException var15) {
                  }

                  if (!this.m_oTicket.hasCustomer() && newCustomer != null) {
                     this.processCustomer(newCustomer);
                  } else if (newCustomer != null && this.removeCustomer()) {
                     this.processCustomer(newCustomer);
                  }

                  this.stateToZero();
                  return;
               case 2:
                  System.out.println("CustomerWithLoyalty");
                  if (this.loyaltyCard == null) {
                     this.loyaltyCard = (LoyaltyCard)(SystemProperty.LOYALTYTYPE.equals("earnx")
                        ? new CollectTransactionLoyaltyPoints(this.barCode)
                        : new CollectItemLoyaltyPoints(this.barCode));
                     this.m_oTicket.setLoyaltyCard(this.loyaltyCard);
                     if (this.loyaltyCard != null) {
                        this.m_jLblLoyaltyCard.setText("Loyalty card : " + this.barCode);
                        this.loyaltyCard.showCardStatus(this);
                     }
                  } else if (!this.loyaltyCard.getCardNumber().equals(this.barCode)) {
                     JAlertPane.messageBox(0, AppLocal.getIntString("message.cardinuse", this.loyaltyCard.getCardNumber()), 16, new Dimension(125, 50), 4);
                  } else if (SystemProperty.LOYALTYTYPE.equals("collectx")) {
                     this.loyaltyCard.showCardStatus(this);
                  }

                  this.stateToZero();
                  return;
               case 3:
                  System.out.println("Loyalty");
                  if (SystemProperty.LOYALTYENABLED) {
                     if (!DataLogicLoyalty.isCardRetired(this.barCode) && !DataLogicLoyalty.cardReplaced(this.barCode)) {
                        if (DataLogicLoyalty.isCardLocked(this.barCode)) {
                           JAlertPane.messageBox(0, AppLocal.getIntString("message.loyaltydisabled", this.barCode), 16, new Dimension(125, 50), 4);
                           this.stateToZero();
                           return;
                        }

                        if (this.loyaltyCard == null) {
                           this.loyaltyCard = (LoyaltyCard)(SystemProperty.LOYALTYTYPE.equals("earnx")
                              ? new CollectTransactionLoyaltyPoints(this.barCode)
                              : new CollectItemLoyaltyPoints(this.barCode));
                           this.loyaltyCard = this.loyaltyCard.isCardActive() ? this.loyaltyCard : null;
                           this.m_oTicket.setLoyaltyCard(this.loyaltyCard);
                           if (this.loyaltyCard != null && this.loyaltyCard.isCardActive()) {
                              this.m_jLblLoyaltyCard.setText("Loyalty card : " + this.barCode);
                              this.loyaltyCard.showCardStatus(this);
                           }
                        } else if (!this.loyaltyCard.getCardNumber().equals(this.barCode)) {
                           JAlertPane.messageBox(0, AppLocal.getIntString("message.cardinuse", this.loyaltyCard.getCardNumber()), 16, new Dimension(125, 50), 4);
                        } else if (SystemProperty.LOYALTYTYPE.equals("collectx")) {
                           this.loyaltyCard.showCardStatus(this);
                        }

                        this.stateToZero();
                        return;
                     }

                     JAlertPane.messageBox(0, AppLocal.getIntString("message.loyaltyreplaced", this.barCode), 16, new Dimension(125, 50), 4);
                     this.stateToZero();
                     return;
                  }
               case 4:
                  System.out.println("GiftCard");
                  if (SystemProperty.GIFTCARDSENABLED) {
                     TicketLineInfo newline = null;

                     try {
                        newline = JGiftCardEdit.showMessage(this, this.m_App, this.barCode, this.dlSales, this.giftCard.isCardActivated(this.barCode));
                     } catch (BasicException var14) {
                        Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var14);
                     }

                     if (newline != null) {
                        this.addTicketLine(newline);
                        newline.setProperty("cardnotes", "Giftcard Number  : " + this.barCode);
                        newline.setProperty("cardbalance", Formats.CURRENCY.formatValue(this.giftCard.getCardBalance(this.barCode) + newline.getPrice()));
                        newline.setProperty("cardnumber", this.barCode);
                        this.m_oTicket.getLine(this.m_ticketlines.getSelectedIndex()).setProductAttSetInstDesc("Giftcard Number - " + this.barCode);
                        this.refreshTicket();
                     }

                     this.stateToZero();
                     return;
                  }
               case 5:
                  System.out.println("GiftVoucher");
                  break;
               case 6:
                  this.m_ticketsbag.getTicketByCode(this.barCode);
                  return;
               case 7:
                  System.out.println("voucher");
                  break;
               case 8:
                  System.out.println("Newspaper");
            }

            if (this.bar.getBarcodeType() != -1) {
               if (this.barCode.length() > 4) {
                  this.barCodeStart = new StringBuilder(this.m_sBarcode.substring(0, 3));
                  String var19 = this.barCodeStart.toString();
                  switch (var19) {
                     case "05V":
                     case "10V":
                     case "20V":
                        try {
                           if (this.dlSales.getVoucher(this.barCode)) {
                              this.stateToZero();
                              JOptionPane.showMessageDialog(
                                 null, "Voucher Code \"" + this.barCode + "\" already Sold. Please use another voucher", "Invalid Voucher", 2
                              );
                           } else if (this.checkVoucherCurrentTicket(this.barCodeStart.toString())) {
                              this.stateToZero();
                              JOptionPane.showMessageDialog(
                                 null, "Voucher Code \"" + this.barCode + "\" already on Ticket. Please use another voucher", "Invalid Voucher", 2
                              );
                           } else {
                              new ProductInfoExt();
                              ProductInfoExt var30 = this.dlSales.getProductInfoByCode(this.barCodeStart.toString());
                              if (var30 != null) {
                                 var30.setCode(this.barCodeStart.toString());
                                 var30.setName(var30.getName());
                                 var30.setProperty("vCode", this.barCode);
                                 var30.setTaxCategoryID(((TaxCategoryInfo)this.taxcategoriesmodel.getSelectedItem()).getID());
                                 this.addTicketLine(var30, 1.0, this.includeTaxes(var30.getTaxCategoryID(), var30.getPriceSell()));
                              } else {
                                 this.errorBeep();
                                 JOptionPane.showMessageDialog(
                                    null, "Vocher code " + this.barCodeStart + " - " + AppLocal.getIntString("message.noproduct"), "Check", 2
                                 );
                                 this.stateToZero();
                              }
                           }

                           return;
                        } catch (BasicException var18) {
                           Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var18);
                        }
                  }
               }

               if (this.barCode.startsWith("2") && (this.barCode.length() == 13 || this.barCode.length() == 12)) {
                  ProductInfoExt oProduct = null;

                  try {
                     oProduct = this.dlSales.getProductInfoByCode(this.barCode.toString());
                  } catch (BasicException var17) {
                     Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var17);
                  }

                  if (oProduct != null) {
                     this.incProductByCode(this.barCode.toString());
                  } else {
                     double dPriceSell = 0.0;
                     double weight = 1.0;
                     int iBarCodeLen = this.barCode.length();
                     String sVariableTypePrefix = this.barCode.substring(0, 2);
                     int iValueIndex = sVariableTypePrefix.equals("28") && iBarCodeLen == 13 ? iBarCodeLen - 6 : iBarCodeLen - 5;
                     String sVariableNum = this.barCode.substring(iValueIndex, iBarCodeLen - 1);
                     String prodCode = this.barCode.substring(0, iValueIndex).concat("000000");
                     prodCode = prodCode.substring(0, iBarCodeLen - 1);
                     if (iBarCodeLen == 13) {
                        switch (sVariableTypePrefix) {
                           case "20":
                              dPriceSell = Double.parseDouble(sVariableNum) / 100.0;
                              break;
                           case "21":
                              dPriceSell = Double.parseDouble(sVariableNum) / 10.0;
                              break;
                           case "22":
                              dPriceSell = Double.parseDouble(sVariableNum);
                              break;
                           case "23":
                              weight = Double.parseDouble(sVariableNum) / 1000.0;
                              break;
                           case "24":
                              weight = Double.parseDouble(sVariableNum) / 100.0;
                              break;
                           case "25":
                              weight = Double.parseDouble(sVariableNum) / 10.0;
                              break;
                           case "28":
                              dPriceSell = Double.parseDouble(sVariableNum) / 100.0;
                        }
                     } else if (iBarCodeLen == 12 && this.barCode.substring(0, 1).equals("2")) {
                        dPriceSell = Double.parseDouble(sVariableNum) / 100.0;
                     }

                     try {
                        oProduct = this.dlSales.getProductInfoByCode(prodCode);
                        if (oProduct == null) {
                           this.errorBeep();
                           JOptionPane.showMessageDialog(null, prodCode + " - " + AppLocal.getIntString("message.noproduct"), "Check", 2);
                           this.stateToZero();
                        } else if (iBarCodeLen == 13) {
                           switch (sVariableTypePrefix) {
                              case "23":
                              case "24":
                              case "25":
                                 oProduct.setProperty("product.weight", Double.toString(weight));
                                 dPriceSell = oProduct.getPriceSell();
                           }
                        } else if (oProduct.getPriceSell() != 0.0) {
                           weight = Double.parseDouble(sVariableNum) / 100.0;
                           oProduct.setProperty("product.weight", Double.toString(weight));
                           dPriceSell = oProduct.getPriceSell();
                        } else {
                           dPriceSell = Double.parseDouble(sVariableNum) / 100.0;
                        }

                        TaxInfo tax = this.taxeslogic.getTaxInfo(oProduct.getTaxCategoryID(), this.m_oTicket.getCustomer());
                        this.addTicketLine(oProduct, weight, dPriceSell / (1.0 + tax.getRate()));
                     } catch (BasicException var16) {
                        this.stateToZero();
                        new MessageInf(var16).show(this);
                     }
                  }
               } else if (this.m_jbtnconfig.getEvent("script.CustomCodeProcessor") != null) {
                  Object oTicketLine = this.executeEvent(
                     this.m_oTicket, this.m_oTicketExt, "script.CustomCodeProcessor", new JPanelTicket.ScriptArg("sCode", this.barCode)
                  );
                  if (oTicketLine instanceof TicketLineInfo) {
                     this.addTicketLine((TicketLineInfo)oTicketLine);
                  } else {
                     this.incProductByCode(this.barCode.toString());
                  }
               } else {
                  this.incProductByCode(this.barCode.toString());
               }
            } else {
               this.errorBeep();
            }
         }
      } else {
         this.m_sBarcode.append(cTrans);
         if (cTrans == 127) {
            this.stateToZero();
         } else if (cTrans == '0' && this.m_iNumberStatus == 0) {
            this.m_jPrice.setText("0");
         } else if (Character.isDigit(cTrans) && this.m_iNumberStatus == 0) {
            this.pricePeriod(Character.toString(cTrans));
            this.m_iNumberStatus = 2;
            if (this.m_iNumberStatusInput != 2) {
               this.m_iNumberStatusInput = 1;
            }
         } else if (Character.isDigit(cTrans) && this.m_iNumberStatus == 2) {
            this.pricePeriodWithNumber(Character.toString(cTrans));
         } else if (cTrans == '.' && this.m_iNumberStatus == 0 && !SystemProperty.PRICEWITH00) {
            this.m_jPrice.setText("0.");
            this.m_iNumberStatus = 1;
         } else if (cTrans == '.' && this.m_iNumberStatus == 0) {
            this.m_jPrice.setText("");
            this.m_iNumberStatus = 0;
         } else if (cTrans == '.' && this.m_iNumberStatus == 2 && !SystemProperty.PRICEWITH00) {
            this.m_jPrice.setText(this.m_jPrice.getText() + ".");
            this.m_iNumberStatus = 3;
         } else if (cTrans == '.' && this.m_iNumberStatus == 2) {
            this.pricePeriodWithNumber("00");
            this.m_iNumberStatus = 2;
         } else if (cTrans != '0' || this.m_iNumberStatus != 1 && this.m_iNumberStatus != 3) {
            if (!Character.isDigit(cTrans) || this.m_iNumberStatus != 1 && this.m_iNumberStatus != 3) {
               if (cTrans != '*' || this.m_iNumberStatus != 2 && this.m_iNumberStatus != 3) {
                  if (cTrans != '*' || this.m_iNumberStatus != 0 && this.m_iNumberStatus != 1) {
                     if (cTrans == '0' && this.m_iNumberStatus == 4) {
                        this.m_jQty.setText("x0");
                     } else if (Character.isDigit(cTrans) && this.m_iNumberStatus == 4) {
                        this.m_jQty.setText("x" + Character.toString(cTrans));
                        this.m_iNumberStatus = 6;
                        this.m_iNumberStatusQty = 1;
                     } else if (Character.isDigit(cTrans) && this.m_iNumberStatus == 6) {
                        this.m_jQty.setText(this.m_jQty.getText() + cTrans);
                     } else if (cTrans == '.' && this.m_iNumberStatus == 4 && !SystemProperty.PRICEWITH00) {
                        this.m_jQty.setText("x0.");
                        this.m_iNumberStatus = 5;
                     } else if (cTrans == '.' && this.m_iNumberStatus == 4) {
                        this.m_jQty.setText("x");
                        this.m_iNumberStatus = 1;
                     } else if (cTrans == '.' && this.m_iNumberStatus == 6 && !SystemProperty.PRICEWITH00) {
                        this.m_jQty.setText(this.m_jQty.getText() + ".");
                        this.m_iNumberStatus = 7;
                     } else if (cTrans == '.' && this.m_iNumberStatus == 6) {
                        this.m_jQty.setText(this.m_jQty.getText() + "00");
                        this.m_iNumberStatus = 1;
                     } else if (cTrans != '0' || this.m_iNumberStatus != 5 && this.m_iNumberStatus != 7) {
                        if (!Character.isDigit(cTrans) || this.m_iNumberStatus != 5 && this.m_iNumberStatus != 7) {
                           if (cTrans == 167 && this.m_iNumberStatusInput == 1 && this.m_iNumberStatusQty == 0) {
                              this.readWeightFromScales();
                           } else if (cTrans == 167 && this.m_iNumberStatusInput == 0 && this.m_iNumberStatusQty == 0) {
                              this.getScaleWeightFromUserDialogInput();
                           } else if (cTrans == '+' && this.m_iNumberStatusInput == 0 && this.m_iNumberStatusQty == 0) {
                              this.increaseSelectedTicketLine();
                           } else if (cTrans == '-'
                              && this.m_iNumberStatusInput == 0
                              && this.m_iNumberStatusQty == 0
                              && AppUser.hasPermission("sales.EditLines")) {
                              this.decreaseSelectedTicketLine();
                           } else if (cTrans == '+' && this.m_iNumberStatusInput == 0 && this.m_iNumberStatusQty == 1) {
                              this.increaseSelectedTicketLineByMultiplier();
                           } else if (cTrans == '-'
                              && (this.m_iNumberStatusInput == 0 || this.m_iNumberStatusQty == 1)
                              && AppUser.hasPermission("sales.EditLines")) {
                              this.decreaseSelectedTicketLineByMultiplier();
                           } else if (cTrans == '+'
                              && this.m_iNumberStatusInput == 1
                              && this.m_iNumberStatusQty == 0
                              && AppUser.hasPermission("sales.EditLines")) {
                              ProductInfoExt product = this.getInputProduct();
                              this.addTicketDefaultLine(product, 1.0);
                              if (!SystemProperty.HIDEDEFAULTPRODUCT) {
                                 this.m_jEditLine.doClick();
                              }
                           } else if (cTrans == '-'
                              && this.m_iNumberStatusInput == 1
                              && this.m_iNumberStatusQty == 0
                              && AppUser.hasPermission("sales.EditLines")
                              && this.fromNumberPad) {
                              ProductInfoExt product = this.getInputProduct();
                              this.addTicketLine(product, 1.0, -product.getPriceSell());
                           } else if (cTrans == '+'
                              && this.m_iNumberStatusInput == 1
                              && this.m_iNumberStatusQty == 1
                              && AppUser.hasPermission("sales.EditLines")) {
                              ProductInfoExt product = this.getInputProduct();
                              this.addTicketLine(product, this.getPorValue(), product.getPriceSell());
                           } else if (cTrans == '-'
                              && this.m_iNumberStatusInput == 1
                              && this.m_iNumberStatusQty == 1
                              && AppUser.hasPermission("sales.EditLines")) {
                              ProductInfoExt product = this.getInputProduct();
                              this.addTicketLine(product, this.getPorValue(), -product.getPriceSell());
                           } else if (cTrans == ' ' || cTrans == '=') {
                              this.processTicketLineReceipt();
                           } else if (!Character.isDigit(cTrans)) {
                              this.m_iNumberStatusInput = 2;
                           }
                        } else {
                           this.m_jQty.setText(this.m_jQty.getText() + cTrans);
                           this.m_iNumberStatus = 7;
                           this.m_iNumberStatusQty = 1;
                        }
                     } else {
                        this.m_jQty.setText(this.m_jQty.getText() + cTrans);
                     }
                  } else {
                     this.m_jPrice.setText("0");
                     this.m_jQty.setText("x");
                     this.m_iNumberStatus = 4;
                  }
               } else {
                  this.m_jQty.setText("x");
                  this.m_iNumberStatus = 4;
               }
            } else {
               this.m_jPrice.setText(this.m_jPrice.getText() + cTrans);
               this.m_iNumberStatus = 3;
               this.m_iNumberStatusInput = 1;
            }
         } else {
            this.pricePeriodWithNumber(Character.toString(cTrans));
         }
      }
   }

   private void btnCustomerAction() {
      AutoLogoff.getInstance().deactivateTimer();
      if (this.m_oTicket.hasCustomer()) {
         this.removeCustomer();
      } else {
         JCustomerFinder finder = new JCustomerFinder(this.dlCustomers);
         finder.search(this.m_oTicket.getCustomer());
         finder.setLocationRelativeTo(this);
         finder.setLocation(finder.getX(), finder.getY() - 200);
         finder.setVisible(true);
         if (finder.getSelectedCustomer() != null) {
            CustomerInfoExt newCustomer;
            try {
               newCustomer = this.dlSales.loadCustomerExt(finder.getSelectedCustomer().getId());
            } catch (BasicException var5) {
               MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotfindcustomer"), var5);
               msg.show(this);
               return;
            }

            this.processCustomer(newCustomer);
         }
      }
   }

   private Boolean removeCustomer() {
      int result = JAlertPane.messageBox(0, AppLocal.getIntString("dialog.removecustomer"), 16, new Dimension(125, 50), 3);
      if (result == 5) {
         this.m_oTicket.setCustomer(null);

         for (TicketLineInfo line : this.m_oTicket.getLines()) {
            if (line.isDiscounted()) {
               line.setProperty("product.name", line.getProductOrigName());
               line.setDiscounted("no");
               line.removeProperty("edited");
               line.setSoldPrice(line.getSellingPrice());
               line.setPrice(line.getPrice());
               line.setPriceExcl(line.getProductPriceExc());
               line.setSoldPriceExc(line.getProductPriceExc());
            }

            if (this.m_oTicket.isTaxExempt()) {
               this.m_oTicket.setTaxExempt(false);
               ProductInfoExt product = line.getProductInfoExt();

               try {
                  line.setTaxInfo(this.dlSales.getTaxByCategoryID(product.getTaxCategoryID()));
                  line.setPrice(Double.parseDouble(line.getProperty("SellingPrice")));
               } catch (BasicException var6) {
               }
            }
         }

         this.m_oTicket.setTicketDiscountRate(0.0);
         this.m_oTicket.setTicketDiscount(0.0);
         this.checkSellingPrices(this.m_oTicket);
         this.refreshTicket();
         return true;
      } else {
         return false;
      }
   }

   private void processCustomer(CustomerInfoExt newCustomer) {
      this.costPricing.reset();
      Double markup = this.getCostPlusMarkup(newCustomer);
      if (markup != null && !this.m_oTicket.isRefund()) {
         StringBuilder missing = new StringBuilder();
         for (TicketLineInfo line : this.m_oTicket.getLines()) {
            if (this.isCostPlusProduct(line) && !this.hasCost(line)) {
               missing.append("\n - ").append(line.getProductName());
            }
         }

         if (missing.length() > 0) {
            this.showMissingCost(missing.toString());
            return;
         }

         for (TicketLineInfo line : this.m_oTicket.getLines()) {
            if (line.getProperty("edited") == null && !this.m_oTicket.isRefund()) {
               this.applyCostPlus(line, markup);
            }
         }
      }

      Integer customerStatus = newCustomer.getCustomerStatus();
      String alertMessage = AppLocal.getIntString("message.customername", newCustomer.getName());
      String customMessage = "";
      this.m_oTicket.setTaxExempt(false);
      switch (customerStatus) {
         case 1:
            customMessage = AppLocal.getIntString("message.customerdiscountrate", newCustomer.getCustomerDiscount() * 100.0);
            break;
         case 2:
            if (SystemProperty.TAXEXEMPTION) {
               customMessage = AppLocal.getIntString("message.taxexemption");
               this.m_oTicket.setTaxExempt(true);
            }
            break;
         case 3:
            if (SystemProperty.TAXEXEMPTION) {
               customMessage = AppLocal.getIntString("message.taxexemptionwithdiscount", newCustomer.getCustomerDiscount() * 100.0);
               this.m_oTicket.setTaxExempt(true);
            } else {
               customMessage = AppLocal.getIntString("message.customerdiscountrate", newCustomer.getCustomerDiscount() * 100.0);
            }
         case 4:
         case 5:
         default:
            break;
         case 6:
            if (SystemProperty.TAXEXEMPTION) {
               customMessage = AppLocal.getIntString("message.exemptionexpired");
            }
            break;
         case 7:
            if (SystemProperty.TAXEXEMPTION) {
               customMessage = AppLocal.getIntString("message.exemptionexpiredwithdiscount", newCustomer.getCustomerDiscount() * 100.0);
            } else {
               customMessage = AppLocal.getIntString("message.customerdiscountrate", newCustomer.getCustomerDiscount() * 100.0);
            }
      }

      JAlertPane.messageBox(1, alertMessage + customMessage, 16, new Dimension(125, 50), 4);
      if (this.m_oTicket.isTaxExempt()) {
         for (TicketLineInfo line : this.m_oTicket.getLines()) {
            this.applyTaxExemption(line);
         }
      }

      if ((customerStatus & 1) == 1) {
         this.m_oTicket.setTicketDiscountRate(newCustomer.getCustomerDiscount());

         for (TicketLineInfo line : this.m_oTicket.getLines()) {
            this.applyDiscount(line);
         }
      }

      this.refreshTicket();
      this.m_oTicket.setCustomer(newCustomer);
      this.m_jTicketId.setText(this.m_oTicket.getName(this.m_oTicketExt));
      String loyaltyNumber = DataLogicLoyalty.getCardByCustomerId(newCustomer.getId());
      if (DataLogicLoyalty.isCardAvailable(loyaltyNumber) && this.loyaltyCard == null) {
         if (SystemProperty.LOYALTYTYPE.equals("earnx")) {
            this.loyaltyCard = new CollectTransactionLoyaltyPoints(loyaltyNumber);
         } else {
            this.loyaltyCard = new CollectItemLoyaltyPoints(loyaltyNumber);
         }

         this.m_oTicket.setLoyaltyCard(this.loyaltyCard);
         if (this.loyaltyCard != null) {
            this.m_jLblLoyaltyCard.setText("Loyalty card : " + loyaltyNumber);
            this.loyaltyCard.showCardStatus(this);
         }
      }

      if ("restaurant".equals(TerminalInfo.getPosType())) {
         this.restDB.setCustomerNameInTableByTicketId(newCustomer.getId(), this.m_oTicket.getId());
      }

      AutoLogoff.getInstance().activateTimer();
      this.refreshTicket();
   }

   private Double getCostPlusMarkup(CustomerInfo customer) {
      return customer == null ? null : this.costPricing.getMarkup(customer.getId());
   }

   private boolean isCostPlusProduct(TicketLineInfo line) {
      return line.getProductInfoExt() != null
         && line.getProductID() != null
         && !line.isSystemObject()
         && !line.isProductVprice()
         && !this.costPricing.isExcluded(line.getProductID());
   }

   private boolean hasCost(TicketLineInfo line) {
      Double cost = line.getProductInfoExt().getPriceBuy();
      return cost != null && cost > 0.0;
   }

   private void showMissingCost(String products) {
      JAlertPane.messageBox(
         0,
         "Este cliente paga precio sobre costo, pero no hay costo capturado para:\n" + products + "\n\nCaptura el costo del producto en Chromis Admin antes de venderlo a este cliente.",
         16,
         new Dimension(125, 50),
         4
      );
   }

   // Cost-plus customers pay cost + markup; it takes priority over tier and customer
   // discounts. Lines with a manually edited price are left alone.
   private void applyCostPlus(TicketLineInfo line, Double markup) {
      if (markup != null && this.isCostPlusProduct(line) && this.hasCost(line)) {
         line.setPrice(CostPricing.price(line.getProductInfoExt().getPriceBuy(), markup));
         line.setProperty("costplus", String.valueOf(markup));
         line.setProperty("tierpriced", "false");
      } else {
         line.removeProperty("costplus");
      }
   }

   private void applyDiscount(TicketLineInfo line) {
      if (line.discountAllowed() && !line.isDiscounted() && line.getProperty("costplus") == null) {
         this.processDiscount(line, this.m_oTicket.getTicketDiscountRate());
      }
   }

   private void processDiscount(TicketLineInfo line, Double discountRate) {
      if (line.discountAllowed() && !line.isDiscounted()) {
         Double appliedRate = 1.0 - discountRate >= 1.0 - line.getMaxDiscountRate() / 100.0 ? 1.0 - discountRate : 1.0 - line.getMaxDiscountRate() / 100.0;
         Double rate = discountRate >= line.getMaxDiscountRate() / 100.0 ? line.getMaxDiscountRate() : discountRate * 100.0;
         line.setProperty("product.oldname", line.getProductName());
         line.setProperty("product.name", line.getProductName() + " - " + Formats.PERCENT.formatValue(rate / 100.0));
         line.setProperty("prediscount", line.getPrice());
         line.setDiscounted("yes");
         this.m_oTicket.addTicketDiscount(line.getPrice() - Math.rint(line.getPrice() * appliedRate * 100.0) / 100.0);
         line.setProperty("edited", "1");
         line.setPrice(Math.rint(line.getPrice() * appliedRate * 100.0) / 100.0);
         line.setPriceExcl(Math.rint(line.getPriceExc() * appliedRate * 100.0) / 100.0);
      }
   }

   private void applyTaxExemption(TicketLineInfo line) {
      if (SystemProperty.TAXEXEMPTION) {
         String taxExempt = this.dlSales.getExemptTaxDetails();
         ProductInfoExt product = line.getProductInfoExt();

         try {
            TaxInfo tax = this.dlSales.getTaxByID(taxExempt);
            line.setTaxInfo(tax);
            line.setProperty("SellingPrice", line.getPrice());
            line.setPrice(product.getPriceSell());
            line.setPriceExcl(product.getPriceSell());
         } catch (BasicException var6) {
         }
      }
   }

   private void jbtnLogout() {
      AutoLogoff.getInstance().deactivateTimer();
      this.deactivate();

      try {
         ((JRootApp)this.m_App).closeAppView();
      } catch (Exception var2) {
      }
   }

   private void btnReprintAction() {
      AutoLogoff.getInstance().deactivateTimer();
      if (AppConfig.getString("lastticket.number") != null) {
         try {
            TicketInfo ticket = this.dlSales
               .loadTicket(Integer.parseInt(AppConfig.getString("lastticket.type")), Integer.parseInt(AppConfig.getString("lastticket.number")));
            if (ticket == null) {
               JAlertPane.messageBox(1, AppLocal.getIntString("message.notexiststicket"), 16, new Dimension(125, 50), 4);
            } else {
               this.m_ticket = ticket;
               this.m_ticketCopy = null;

               try {
                  this.taxeslogic.calculateTaxes(this.m_ticket);
               } catch (TaxesException var3) {
               }

               this.deliveryInfo = this.dlCustomers.fetchCustomerDelivery(this.m_ticket.getId());
               this.printTicket("Printer.ReprintLastTicket", this.m_ticket, null, false);
            }
         } catch (BasicException var4) {
            MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotloadticket"), var4);
            msg.show(this);
         }
      }

      AutoLogoff.getInstance().activateTimer();
   }

   private void resetCache() {
      System.out.println("Reseting !!!!!!!");
      this.dlSystem.resetResourcesCache();
   }

   public void btnKitchenPrtAction(String script) {
      AutoLogoff.getInstance().deactivateTimer();
      if (!this.m_oTicket.isRefund()) {
         String rScript = this.dlSystem.getResourceAsText(script);
         Interpreter i = new Interpreter();

         try {
            i.set("ticket", this.m_oTicket);
            i.set("place", this.m_oTicketExt);
            i.set("user", this.m_App.getAppUserView().getUser());
            i.set("sales", this);
            i.set("pickupid", this.m_oTicket.getPickupId());
            i.set("pickupcode", "P" + this.m_oTicket.getId().substring(24));
            i.set("deliveryinfo", this.deliveryInfo);
            Object ex = i.eval(rScript);
         } catch (EvalError var6) {
            Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var6);
         }

         AutoLogoff.getInstance().activateTimer();
         String var7 = TerminalInfo.getPosType();
         byte var5 = -1;
         switch (var7.hashCode()) {
            case -1772467395:
               if (var7.equals("restaurant")) {
                  var5 = 0;
               }
            default:
               switch (var5) {
                  case 0:
                     if (SystemProperty.ENABLEAUTOLOGOFF && SystemProperty.AUTOLOGOFFAFTERKITCHEN) {
                        if (SystemProperty.AUTOLOGOFFTOTABLES) {
                           this.deactivate();
                           this.setActiveTicket(null, null);
                        } else {
                           this.deactivate();
                           ((JRootApp)this.m_App).closeAppView();
                        }
                     }
               }
         }
      }
   }

   private void initComponents() {
      this.m_jPanContainer = new JPanel();
      this.m_jOptions = new JPanel();
      this.jPanel7 = new JPanel();
      this.jPanel6 = new JPanel();
      this.btnActionMenu = new JButton();
      this.m_jButtons = new JPanel();
      this.btnCustomer = new JButton();
      this.btnSplit = new JButton();
      this.btnReprint = new JButton();
      this.btnLogout = new JButton();
      this.m_jPanelScripts = new JPanel();
      this.m_jButtonsExt = new JPanel();
      this.jPanel1 = new JPanel();
      this.m_jbtnScale = new JButton();
      this.j_btnKitchenPrt = new JButton();
      this.m_jPanelBag = new JPanel();
      this.m_jPanTicket = new JPanel();
      this.ticketActions = new JPanel();
      this.jPanel2 = new JPanel();
      this.m_jUp = new JButton();
      this.m_jDown = new JButton();
      this.m_jDelete = new JButton();
      this.m_jList = new JButton();
      this.jEditAttributes = new JButton();
      this.m_jEditLine = new JButton();
      this.ticketPanel = new JPanel();
      this.jPanel4 = new JPanel();
      this.jPanel3 = new JPanel();
      this.m_jTicketId = new JLabel();
      this.m_jLblLoyaltyCard = new JLabel();
      this.m_jPanTotals = new JPanel();
      this.m_jLblTotalEuros3 = new JLabel();
      this.m_jLblTotalEuros2 = new JLabel();
      this.m_jLblTotalEuros1 = new JLabel();
      this.m_jSubtotalEuros = new JLabel();
      this.m_jTaxesEuros = new JLabel();
      this.m_jTotalEuros = new JLabel();
      this.m_jContEntries = new JPanel();
      this.m_jPanEntries = new JPanel();
      this.jPanel9 = new JPanel();
      this.m_jPrice = new JLabel();
      this.m_jQty = new JLabel();
      this.m_jEnter = new JButton();
      this.m_jNumberKey = new JNumberKeys();
      this.m_jKeyFactory = new JTextField();
      this.m_jPanEntriesE = new JPanel();
      this.catcontainer = new JPanel();
      this.setBackground(new Color(255, 204, 153));
      this.setLayout(new CardLayout());
      this.m_jPanContainer.setLayout(new BorderLayout());
      this.m_jOptions.setLayout(new BorderLayout());
      this.jPanel7.setLayout(new BorderLayout());
      this.jPanel6.setPreferredSize(new Dimension(58, 40));
      this.btnActionMenu.setIcon(IconFactory.getIcon("menu.png"));
      this.btnActionMenu.setFocusPainted(false);
      this.btnActionMenu.setFocusable(false);
      this.btnActionMenu.setMargin(new Insets(0, 0, 0, 4));
      this.btnActionMenu.setMaximumSize(new Dimension(52, 40));
      this.btnActionMenu.setMinimumSize(new Dimension(52, 40));
      this.btnActionMenu.setPreferredSize(new Dimension(52, 40));
      this.btnActionMenu.setRequestFocusEnabled(false);
      this.btnActionMenu.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.openMenu(evt);
         }
      });
      GroupLayout jPanel6Layout = new GroupLayout(this.jPanel6);
      this.jPanel6.setLayout(jPanel6Layout);
      jPanel6Layout.setHorizontalGroup(
         jPanel6Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup().addGap(6, 6, 6).addComponent(this.btnActionMenu, -2, -1, -2).addContainerGap(-1, 32767))
      );
      jPanel6Layout.setVerticalGroup(
         jPanel6Layout.createParallelGroup(Alignment.LEADING)
            .addGroup(Alignment.TRAILING, jPanel6Layout.createSequentialGroup().addGap(5, 5, 5).addComponent(this.btnActionMenu, -2, -1, -2).addContainerGap())
      );
      this.jPanel7.add(this.jPanel6, "Before");
      this.m_jButtons.setPreferredSize(new Dimension(240, 56));
      this.btnCustomer.setIcon(IconFactory.getIcon("customer_sml.png"));
      this.btnCustomer.setToolTipText("");
      this.btnCustomer.setFocusPainted(false);
      this.btnCustomer.setFocusable(false);
      this.btnCustomer.setMargin(new Insets(0, 4, 0, 4));
      this.btnCustomer.setMaximumSize(new Dimension(50, 40));
      this.btnCustomer.setMinimumSize(new Dimension(50, 40));
      this.btnCustomer.setPreferredSize(new Dimension(52, 40));
      this.btnCustomer.setRequestFocusEnabled(false);
      this.btnSplit.setIcon(IconFactory.getIcon("sale_split_sml.png"));
      this.btnSplit.setFocusPainted(false);
      this.btnSplit.setFocusable(false);
      this.btnSplit.setMargin(new Insets(0, 4, 0, 4));
      this.btnSplit.setMaximumSize(new Dimension(50, 40));
      this.btnSplit.setMinimumSize(new Dimension(50, 40));
      this.btnSplit.setPreferredSize(new Dimension(52, 40));
      this.btnSplit.setRequestFocusEnabled(false);
      this.btnSplit.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.splitTicket(evt);
         }
      });
      this.btnReprint.setIcon(IconFactory.getIcon("reprint.png"));
      this.btnReprint.setFocusPainted(false);
      this.btnReprint.setFocusable(false);
      this.btnReprint.setMargin(new Insets(0, 4, 0, 4));
      this.btnReprint.setMaximumSize(new Dimension(50, 40));
      this.btnReprint.setMinimumSize(new Dimension(50, 40));
      this.btnReprint.setPreferredSize(new Dimension(52, 40));
      this.btnReprint.setRequestFocusEnabled(false);
      this.btnLogout.setIcon(IconFactory.getIcon("logout.png"));
      this.btnLogout.setFocusPainted(false);
      this.btnLogout.setFocusable(false);
      this.btnLogout.setMargin(new Insets(0, 4, 0, 4));
      this.btnLogout.setMaximumSize(new Dimension(50, 40));
      this.btnLogout.setMinimumSize(new Dimension(50, 40));
      this.btnLogout.setPreferredSize(new Dimension(52, 40));
      this.btnLogout.setRequestFocusEnabled(false);
      GroupLayout m_jButtonsLayout = new GroupLayout(this.m_jButtons);
      this.m_jButtons.setLayout(m_jButtonsLayout);
      m_jButtonsLayout.setHorizontalGroup(
         m_jButtonsLayout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               m_jButtonsLayout.createSequentialGroup()
                  .addGap(6, 6, 6)
                  .addComponent(this.btnLogout, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.btnCustomer, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.btnSplit, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.btnReprint, -2, -1, -2)
                  .addContainerGap()
            )
      );
      m_jButtonsLayout.setVerticalGroup(
         m_jButtonsLayout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               m_jButtonsLayout.createSequentialGroup()
                  .addGap(5, 5, 5)
                  .addGroup(
                     m_jButtonsLayout.createParallelGroup(Alignment.LEADING)
                        .addComponent(this.btnLogout, -2, -1, -2)
                        .addComponent(this.btnReprint, -2, -1, -2)
                        .addComponent(this.btnCustomer, -2, -1, -2)
                        .addComponent(this.btnSplit, -2, -1, -2)
                  )
                  .addContainerGap()
            )
      );
      this.jPanel7.add(this.m_jButtons, "Center");
      this.m_jOptions.add(this.jPanel7, "Before");
      this.m_jPanelScripts.setLayout(new BorderLayout());
      this.m_jButtonsExt.setLayout(new BoxLayout(this.m_jButtonsExt, 2));
      this.jPanel1.setMinimumSize(new Dimension(235, 50));
      this.m_jbtnScale.setFont(ChromisFonts.DEFAULTFONT.deriveFont(14.0F));
      this.m_jbtnScale.setIcon(IconFactory.getIcon("scale.png"));
      this.m_jbtnScale.setFocusPainted(false);
      this.m_jbtnScale.setFocusable(false);
      this.m_jbtnScale.setMargin(new Insets(8, 14, 8, 14));
      this.m_jbtnScale.setMaximumSize(new Dimension(85, 44));
      this.m_jbtnScale.setMinimumSize(new Dimension(85, 44));
      this.m_jbtnScale.setPreferredSize(new Dimension(85, 40));
      this.m_jbtnScale.setRequestFocusEnabled(false);
      this.m_jbtnScale.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.openScalesPanel(evt);
         }
      });
      this.jPanel1.add(this.m_jbtnScale);
      this.j_btnKitchenPrt.setIcon(IconFactory.getIcon("networkprt.png"));
      this.j_btnKitchenPrt.setMargin(new Insets(0, 4, 0, 4));
      this.j_btnKitchenPrt.setMaximumSize(new Dimension(50, 40));
      this.j_btnKitchenPrt.setMinimumSize(new Dimension(50, 40));
      this.j_btnKitchenPrt.setPreferredSize(new Dimension(52, 40));
      this.j_btnKitchenPrt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.sendToKitchen(evt);
         }
      });
      this.jPanel1.add(this.j_btnKitchenPrt);
      this.m_jButtonsExt.add(this.jPanel1);
      this.m_jPanelScripts.add(this.m_jButtonsExt, "After");
      this.m_jOptions.add(this.m_jPanelScripts, "After");
      this.m_jPanelBag.setPreferredSize(new Dimension(0, 50));
      this.m_jPanelBag.setLayout(new BorderLayout());
      this.m_jOptions.add(this.m_jPanelBag, "Center");
      this.m_jPanContainer.add(this.m_jOptions, "North");
      this.m_jPanTicket.setLayout(new BorderLayout());
      this.ticketActions.setFont(ChromisFonts.DEFAULTFONT.deriveFont(14.0F));
      this.ticketActions.setPreferredSize(new Dimension(60, 200));
      this.ticketActions.setLayout(new BorderLayout());
      this.jPanel2.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
      this.jPanel2.setLayout(new GridLayout(0, 1, 5, 5));
      this.m_jUp.setIcon(IconFactory.getIcon("1uparrow.png"));
      this.m_jUp.setFocusPainted(false);
      this.m_jUp.setFocusable(false);
      this.m_jUp.setMargin(new Insets(8, 14, 8, 14));
      this.m_jUp.setMaximumSize(new Dimension(52, 36));
      this.m_jUp.setMinimumSize(new Dimension(52, 36));
      this.m_jUp.setPreferredSize(new Dimension(52, 36));
      this.m_jUp.setRequestFocusEnabled(false);
      this.m_jUp.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.selectTicketLineAbove(evt);
         }
      });
      this.jPanel2.add(this.m_jUp);
      this.m_jDown.setIcon(IconFactory.getIcon("1downarrow.png"));
      this.m_jDown.setFocusPainted(false);
      this.m_jDown.setFocusable(false);
      this.m_jDown.setMargin(new Insets(8, 14, 8, 14));
      this.m_jDown.setMaximumSize(new Dimension(42, 36));
      this.m_jDown.setMinimumSize(new Dimension(42, 36));
      this.m_jDown.setPreferredSize(new Dimension(52, 36));
      this.m_jDown.setRequestFocusEnabled(false);
      this.m_jDown.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.selectTicketLineBelow(evt);
         }
      });
      this.jPanel2.add(this.m_jDown);
      this.m_jDelete.setIcon(IconFactory.getIcon("editdelete.png"));
      this.m_jDelete.setFocusPainted(false);
      this.m_jDelete.setFocusable(false);
      this.m_jDelete.setMargin(new Insets(8, 14, 8, 14));
      this.m_jDelete.setMaximumSize(new Dimension(42, 36));
      this.m_jDelete.setMinimumSize(new Dimension(42, 36));
      this.m_jDelete.setPreferredSize(new Dimension(52, 36));
      this.m_jDelete.setRequestFocusEnabled(false);
      this.m_jDelete.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.removeTicketLine(evt);
         }
      });
      this.jPanel2.add(this.m_jDelete);
      this.m_jList.setIcon(IconFactory.getIcon("search32.png"));
      this.m_jList.setFocusPainted(false);
      this.m_jList.setFocusable(false);
      this.m_jList.setMargin(new Insets(8, 14, 8, 14));
      this.m_jList.setMaximumSize(new Dimension(42, 36));
      this.m_jList.setMinimumSize(new Dimension(42, 36));
      this.m_jList.setPreferredSize(new Dimension(52, 36));
      this.m_jList.setRequestFocusEnabled(false);
      this.m_jList.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.searchForProduct(evt);
         }
      });
      this.jPanel2.add(this.m_jList);
      this.jEditAttributes.setIcon(IconFactory.getIcon("attributes.png"));
      this.jEditAttributes.setFocusPainted(false);
      this.jEditAttributes.setFocusable(false);
      this.jEditAttributes.setMargin(new Insets(8, 14, 8, 14));
      this.jEditAttributes.setMaximumSize(new Dimension(42, 36));
      this.jEditAttributes.setMinimumSize(new Dimension(42, 36));
      this.jEditAttributes.setPreferredSize(new Dimension(52, 36));
      this.jEditAttributes.setRequestFocusEnabled(false);
      this.jEditAttributes.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.setTicketLineAttributes(evt);
         }
      });
      this.jPanel2.add(this.jEditAttributes);
      this.m_jEditLine.setIcon(IconFactory.getIcon("sale_editline.png"));
      this.m_jEditLine.setFocusPainted(false);
      this.m_jEditLine.setFocusable(false);
      this.m_jEditLine.setMargin(new Insets(8, 14, 8, 14));
      this.m_jEditLine.setMaximumSize(new Dimension(42, 36));
      this.m_jEditLine.setMinimumSize(new Dimension(42, 36));
      this.m_jEditLine.setPreferredSize(new Dimension(52, 36));
      this.m_jEditLine.setRequestFocusEnabled(false);
      this.m_jEditLine.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.editTicketLine(evt);
         }
      });
      this.jPanel2.add(this.m_jEditLine);
      this.ticketActions.add(this.jPanel2, "North");
      this.m_jPanTicket.add(this.ticketActions, "After");
      this.ticketPanel.setFont(ChromisFonts.DEFAULTFONT);
      this.ticketPanel.setPreferredSize(new Dimension(450, 250));
      this.ticketPanel.setLayout(new BorderLayout());
      this.jPanel4.setAlignmentX(3.0F);
      this.jPanel4.setOpaque(false);
      this.jPanel4.setPreferredSize(new Dimension(675, 60));
      this.jPanel4.setLayout(new BorderLayout());
      this.jPanel3.setLayout(new GridLayout(2, 1, 4, 1));
      this.m_jTicketId.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jTicketId.setHorizontalAlignment(2);
      this.jPanel3.add(this.m_jTicketId);
      this.m_jLblLoyaltyCard.setFont(ChromisFonts.DEFAULTFONT);
      this.m_jLblLoyaltyCard.setHorizontalAlignment(2);
      this.m_jLblLoyaltyCard.setVerticalAlignment(1);
      this.jPanel3.add(this.m_jLblLoyaltyCard);
      this.jPanel4.add(this.jPanel3, "Center");
      this.m_jPanTotals.setPreferredSize(new Dimension(375, 60));
      this.m_jPanTotals.setLayout(new GridLayout(2, 3, 4, 0));
      this.m_jLblTotalEuros3.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jLblTotalEuros3.setHorizontalAlignment(0);
      this.m_jLblTotalEuros3.setLabelFor(this.m_jSubtotalEuros);
      this.m_jLblTotalEuros3.setText(LocalResource.getString("label.subtotalcash"));
      this.m_jPanTotals.add(this.m_jLblTotalEuros3);
      this.m_jLblTotalEuros2.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jLblTotalEuros2.setHorizontalAlignment(0);
      this.m_jLblTotalEuros2.setLabelFor(this.m_jSubtotalEuros);
      this.m_jLblTotalEuros2.setText(LocalResource.getString("label.taxcash"));
      this.m_jPanTotals.add(this.m_jLblTotalEuros2);
      this.m_jLblTotalEuros1.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jLblTotalEuros1.setHorizontalAlignment(0);
      this.m_jLblTotalEuros1.setLabelFor(this.m_jTotalEuros);
      this.m_jLblTotalEuros1.setText(LocalResource.getString("label.total"));
      this.m_jPanTotals.add(this.m_jLblTotalEuros1);
      this.m_jSubtotalEuros.setBackground(this.m_jEditLine.getBackground());
      this.m_jSubtotalEuros.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(22.0F));
      this.m_jSubtotalEuros.setForeground(this.m_jEditLine.getForeground());
      this.m_jSubtotalEuros.setHorizontalAlignment(0);
      this.m_jSubtotalEuros.setLabelFor(this.m_jSubtotalEuros);
      this.m_jSubtotalEuros.setBorder(new LineBorder(new Color(153, 153, 153), 1, true));
      this.m_jSubtotalEuros.setMaximumSize(new Dimension(125, 25));
      this.m_jSubtotalEuros.setMinimumSize(new Dimension(80, 25));
      this.m_jSubtotalEuros.setOpaque(true);
      this.m_jSubtotalEuros.setPreferredSize(new Dimension(80, 25));
      this.m_jSubtotalEuros.setRequestFocusEnabled(false);
      this.m_jPanTotals.add(this.m_jSubtotalEuros);
      this.m_jTaxesEuros.setBackground(this.m_jEditLine.getBackground());
      this.m_jTaxesEuros.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(22.0F));
      this.m_jTaxesEuros.setForeground(this.m_jEditLine.getForeground());
      this.m_jTaxesEuros.setHorizontalAlignment(0);
      this.m_jTaxesEuros.setLabelFor(this.m_jTaxesEuros);
      this.m_jTaxesEuros.setBorder(new LineBorder(new Color(153, 153, 153), 1, true));
      this.m_jTaxesEuros.setMaximumSize(new Dimension(125, 25));
      this.m_jTaxesEuros.setMinimumSize(new Dimension(80, 25));
      this.m_jTaxesEuros.setOpaque(true);
      this.m_jTaxesEuros.setPreferredSize(new Dimension(80, 25));
      this.m_jTaxesEuros.setRequestFocusEnabled(false);
      this.m_jPanTotals.add(this.m_jTaxesEuros);
      this.m_jTotalEuros.setBackground(this.m_jEditLine.getBackground());
      this.m_jTotalEuros.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(22.0F));
      this.m_jTotalEuros.setForeground(this.m_jEditLine.getForeground());
      this.m_jTotalEuros.setHorizontalAlignment(0);
      this.m_jTotalEuros.setLabelFor(this.m_jTotalEuros);
      this.m_jTotalEuros.setBorder(new LineBorder(new Color(153, 153, 153), 1, true));
      this.m_jTotalEuros.setMaximumSize(new Dimension(125, 25));
      this.m_jTotalEuros.setMinimumSize(new Dimension(80, 25));
      this.m_jTotalEuros.setOpaque(true);
      this.m_jTotalEuros.setPreferredSize(new Dimension(100, 25));
      this.m_jTotalEuros.setRequestFocusEnabled(false);
      this.m_jPanTotals.add(this.m_jTotalEuros);
      this.jPanel4.add(this.m_jPanTotals, "After");
      this.ticketPanel.add(this.jPanel4, "South");
      this.m_jPanTicket.add(this.ticketPanel, "Center");
      this.m_jPanContainer.add(this.m_jPanTicket, "Center");
      this.m_jContEntries.setFont(ChromisFonts.DEFAULTFONT.deriveFont(14.0F));
      this.m_jContEntries.setLayout(new BorderLayout());
      this.m_jPanEntries.setLayout(new BoxLayout(this.m_jPanEntries, 1));
      this.jPanel9.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      this.jPanel9.setMaximumSize(new Dimension(250, 60));
      this.jPanel9.setMinimumSize(new Dimension(250, 60));
      this.jPanel9.setPreferredSize(new Dimension(250, 60));
      this.jPanel9.setLayout(new GridBagLayout());
      this.m_jPrice.setBackground(this.m_jEditLine.getBackground());
      this.m_jPrice.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jPrice.setForeground(this.m_jEditLine.getForeground());
      this.m_jPrice.setHorizontalAlignment(4);
      this.m_jPrice.setText("price");
      this.m_jPrice.setBorder(new LineBorder(new Color(153, 153, 153), 1, true));
      this.m_jPrice.setMaximumSize(new Dimension(125, 40));
      this.m_jPrice.setMinimumSize(new Dimension(125, 40));
      this.m_jPrice.setOpaque(true);
      this.m_jPrice.setPreferredSize(new Dimension(125, 40));
      this.m_jPrice.setRequestFocusEnabled(false);
      this.m_jPrice.setVerticalTextPosition(3);
      GridBagConstraints gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridx = 0;
      gridBagConstraints.gridy = 0;
      gridBagConstraints.gridwidth = 2;
      gridBagConstraints.fill = 2;
      gridBagConstraints.anchor = 17;
      gridBagConstraints.weightx = 1.0;
      gridBagConstraints.weighty = 1.0;
      this.jPanel9.add(this.m_jPrice, gridBagConstraints);
      this.m_jQty.setBackground(this.m_jEditLine.getBackground());
      this.m_jQty.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jQty.setForeground(this.m_jEditLine.getForeground());
      this.m_jQty.setHorizontalAlignment(4);
      this.m_jQty.setText("qty");
      this.m_jQty.setBorder(new LineBorder(new Color(153, 153, 153), 1, true));
      this.m_jQty.setMaximumSize(new Dimension(45, 40));
      this.m_jQty.setMinimumSize(new Dimension(45, 40));
      this.m_jQty.setOpaque(true);
      this.m_jQty.setPreferredSize(new Dimension(45, 40));
      this.m_jQty.setRequestFocusEnabled(false);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridx = 2;
      gridBagConstraints.gridy = 0;
      gridBagConstraints.weightx = 1.0;
      gridBagConstraints.weighty = 1.0;
      gridBagConstraints.insets = new Insets(0, 5, 0, 0);
      this.jPanel9.add(this.m_jQty, gridBagConstraints);
      this.m_jEnter.setIcon(IconFactory.getIcon("barcode.png"));
      this.m_jEnter.setFocusPainted(false);
      this.m_jEnter.setFocusable(false);
      this.m_jEnter.setIconTextGap(1);
      this.m_jEnter.setInheritsPopupMenu(true);
      this.m_jEnter.setMargin(new Insets(2, 2, 2, 2));
      this.m_jEnter.setMaximumSize(new Dimension(55, 40));
      this.m_jEnter.setMinimumSize(new Dimension(55, 40));
      this.m_jEnter.setPreferredSize(new Dimension(55, 40));
      this.m_jEnter.setRequestFocusEnabled(false);
      this.m_jEnter.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPanelTicket.this.processManualBarcode(evt);
         }
      });
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridx = 3;
      gridBagConstraints.gridy = 0;
      gridBagConstraints.gridheight = 2;
      gridBagConstraints.anchor = 17;
      gridBagConstraints.weightx = 1.0;
      gridBagConstraints.weighty = 1.0;
      gridBagConstraints.insets = new Insets(0, 5, 0, 0);
      this.jPanel9.add(this.m_jEnter, gridBagConstraints);
      this.m_jPanEntries.add(this.jPanel9);
      this.m_jNumberKey.setMinimumSize(new Dimension(200, 200));
      this.m_jNumberKey.setPreferredSize(new Dimension(250, 250));
      this.m_jNumberKey.addJNumberEventListener(new JNumberEventListener() {
         @Override
         public void keyPerformed(JNumberEvent evt) {
            JPanelTicket.this.processKeypadEntry(evt);
         }
      });
      this.m_jPanEntries.add(this.m_jNumberKey);
      this.m_jKeyFactory.setBackground(UIManager.getDefaults().getColor("Panel.background"));
      this.m_jKeyFactory.setForeground(UIManager.getDefaults().getColor("Panel.background"));
      this.m_jKeyFactory.setBorder(null);
      this.m_jKeyFactory.setCaretColor(UIManager.getDefaults().getColor("Panel.background"));
      this.m_jKeyFactory.setPreferredSize(new Dimension(1, 1));
      this.m_jKeyFactory.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent evt) {
            JPanelTicket.this.m_jKeyFactoryKeyTyped(evt);
         }
      });
      this.m_jPanEntries.add(this.m_jKeyFactory);
      this.m_jContEntries.add(this.m_jPanEntries, "North");
      this.m_jPanEntriesE.setLayout(new BorderLayout());
      this.m_jContEntries.add(this.m_jPanEntriesE, "After");
      this.m_jPanContainer.add(this.m_jContEntries, "After");
      this.catcontainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      this.catcontainer.setLayout(new BorderLayout());
      this.m_jPanContainer.add(this.catcontainer, "South");
      this.add(this.m_jPanContainer, "ticket");
   }

   private void openScalesPanel(ActionEvent evt) {
      this.stateTransition('§');
   }

   private void editTicketLine(ActionEvent evt) {
      AutoLogoff.getInstance().deactivateTimer();
      int i = this.m_ticketlines.getSelectedIndex();
      if (i < 0) {
         this.errorBeep();
      } else {
         try {
            TicketLineInfo oldLine = this.m_oTicket.getLine(i);
            Double oldPrice = oldLine.getPrice();
            boolean wasEdited = oldLine.getProperty("edited") != null;
            TicketLineInfo newline = new JProductLineEdit(this.m_App, oldLine).getTicketLine();
            if (newline != null) {
               Double newPrice = newline.getPrice();
               Double newPricExec = newline.getSoldPriceExe();
               newline.setProductInfoExt(this.dlSales.getProductInfo(newline.getProductID()));
               newline.setPrice(newPrice);
               boolean tierPriced = newline.getProductInfoExt() != null && newline.getProductInfoExt().hasTierPringing();
               if (tierPriced && !wasEdited && Math.abs(newPrice - oldPrice) < 0.005) {
                  // Only the quantity changed: leave the line under tier pricing
                  newline.removeProperty("edited");
               } else {
                  newline.setProperty("edited", "1");
               }
               if (newline.getProductID().equalsIgnoreCase("giftcard-sale") || newline.getProductID().equalsIgnoreCase("giftcard-topup")) {
                  newline.setProperty("cardbalance", Formats.CURRENCY.formatValue(this.giftCard.getCardBalance(this.barCode) + newline.getPrice()));
               }

               this.paintTicketLine(i, newline);
               if (tierPriced) {
                  this.checkSellingPrices(this.m_oTicket);
                  this.checkServiceCharge(this.m_oTicket);
                  this.refreshTicket();
               }

               if (newline.getUpdated()) {
                  this.reLoadCatalog();
               }
            }
         } catch (BasicException var6) {
            new MessageInf(var6).show(this);
         }
      }

      AutoLogoff.getInstance().activateTimer();
   }

   private void processManualBarcode(ActionEvent evt) {
      if (!this.m_jPrice.getText().trim().isEmpty()) {
         this.stateTransition('\n');
      }
   }

   private void processKeypadEntry(JNumberEvent evt) {
      this.stateTransition(evt.getKey());
   }

   private void removeTicketLine(ActionEvent evt) {
      int i = this.m_ticketlines.getSelectedIndex();
      if (i != -1) {
         Double qty = this.m_oTicket.getLine(i).getMultiply();
         String name = this.m_oTicket.getLine(i).getProductName();
         if (this.m_oTicket.getLine(i).isServiceCharge()) {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.deleteservicecharge"), 16, new Dimension(125, 50), 3) == 6) {
               return;
            }

            this.m_oTicket.setNoSC("0");
         }

         if (this.m_oTicket.getLine(i).isDeliveryCharge()) {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.deletedeliverycharge"), 16, new Dimension(125, 50), 3) == 6) {
               return;
            }

            this.m_oTicket.setNoDelivery("0");
            this.deliveryInfo = null;
         }

         if (this.m_oTicket.isRefund() && !this.m_oTicket.getLine(i).isProductCom()) {
            JRefundLines.addBackLine(
               this.m_oTicket.getLine(i).printName(),
               this.m_oTicket.getLine(i).getMultiply(),
               this.m_oTicket.getLine(i).getPrice(),
               this.m_oTicket.getLine(i).getProperty("orgLine")
            );
            String parentId = this.m_oTicket.getLine(i).getProductID();
            this.removeTicketLine(i, true);

            while (i < this.m_oTicket.getLinesCount()
               && this.m_oTicket.getLine(i).isProductCom()
               && this.isAuxiliaryOf(parentId, this.m_oTicket.getLine(i).getProductID())) {
               JRefundLines.addBackLine(
                  this.m_oTicket.getLine(i).printName(),
                  this.m_oTicket.getLine(i).getMultiply(),
                  this.m_oTicket.getLine(i).getPrice(),
                  this.m_oTicket.getLine(i).getProperty("orgLine")
               );
               this.removeTicketLine(i, true);
            }
         } else if (this.m_oTicket.isRefund()) {
            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(null, AppLocal.getIntString("message.deleteauxiliaryitem"), "auxiliary Item", 2);
         } else if (i < 0) {
            this.errorBeep();
         } else {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.finallineitem"), 16, new Dimension(125, 50), 3) == 6) {
               return;
            }

            String id = this.m_oTicket.getLine(i).getProductID();
            this.removeTicketLine(i, true);
            this.checkSellingPrices(this.m_oTicket, true, qty, name, id);
            this.checkServiceCharge(this.m_oTicket);
            this.refreshTicket();
         }
      }
   }

   private void selectTicketLineAbove(ActionEvent evt) {
      this.m_ticketlines.selectionUp();
   }

   private void selectTicketLineBelow(ActionEvent evt) {
      this.m_ticketlines.selectionDown();
   }

   private void searchForProduct(ActionEvent evt) {
      AutoLogoff.getInstance().deactivateTimer();
      JProductFinder productFinder = new JProductFinder(this.dlSales);
      productFinder.setVisible(true);
      ProductInfoExt prod = productFinder.getSelectedProduct();
      if (prod != null) {
         this.buttonTransition(prod);
      }

      AutoLogoff.getInstance().activateTimer();
   }

   private void splitTicket(ActionEvent evt) {
      AutoLogoff.getInstance().deactivateTimer();
      if (this.m_oTicket.getArticlesCount() > 1.0) {
         ReceiptSplit splitdialog;
         if (SystemProperty.TAXINCLUDED) {
            splitdialog = ReceiptSplit.getDialog(this, this.dlSystem.getResourceAsXML("Ticket.LineIncTaxes"), this.dlSales, this.dlCustomers, this.taxeslogic);
         } else {
            splitdialog = ReceiptSplit.getDialog(this, this.dlSystem.getResourceAsXML("Ticket.LineExclTaxes"), this.dlSales, this.dlCustomers, this.taxeslogic);
         }

         splitdialog.setLocationRelativeTo(this);
         TicketInfo ticket1 = this.m_oTicket.copyTicket();
         TicketInfo ticket2 = new TicketInfo();
         ticket2.setCustomer(this.m_oTicket.getCustomer());
         if (splitdialog.showDialog(ticket1, ticket2, this.m_oTicketExt)) {
            this.executeEvent(ticket2, this.m_oTicketExt, "ticket.change");
            if (this.closeTicket(ticket2, this.m_oTicketExt)) {
               this.setActiveTicket(ticket1, this.m_oTicketExt);
               this.executeEventAndRefresh("ticket.pretotals");
               this.executeEventAndRefresh("ticket.change");
            }
         }
      }

      AutoLogoff.getInstance().activateTimer();
   }

   private void setTicketLineAttributes(ActionEvent evt) {
      int i = this.m_ticketlines.getSelectedIndex();
      if (i < 0) {
         this.errorBeep();
      } else {
         try {
            TicketLineInfo line = this.m_oTicket.getLine(i);
            JProductAttEdit attedit = JProductAttEdit.getAttributesEditor(this, this.m_App.getSession());
            attedit.editAttributes(line.getProductAttSetId(), line.getProductAttSetInstId());
            attedit.setLocationRelativeTo(this);
            attedit.setVisible(true);
            if (attedit.isOK()) {
               line.setProductAttSetInstId(attedit.getAttributeSetInst());
               line.setProductAttSetInstDesc(attedit.getAttributeSetInstDescription());
               this.paintTicketLine(i, line);
            }
         } catch (BasicException var5) {
            MessageInf msg = new MessageInf(-33554432, AppLocal.getIntString("message.cannotfindattributes"), var5);
            msg.show(this);
            AutoLogoff.getInstance().activateTimer();
         }
      }
   }

   private void sendToKitchen(ActionEvent evt) {
      this.btnKitchenPrtAction("script.SendOrder");
   }

   private void openMenu(ActionEvent evt) {
      this.principalApp.setMenuVisible(this.btnActionMenu);
   }

   private void m_jKeyFactoryKeyTyped(KeyEvent evt) {
      this.mappedKeys.put("VK_F1", "class:uk.chromis.pos.sales.JPanelTicketEdits");
      this.mappedKeys.put("VK_F2", "class:uk.chromis.pos.customers.CustomersPayment");
      this.mappedKeys.put("VK_F3", "class:uk.chromis.pos.panels.JPanelPayments");
      this.mappedKeys.put("VK_F4", "class:uk.chromis.pos.panels.JPanelCloseMoney");
      this.mappedKeys.put("VK_F5", "action:btnCustomerAction");
      this.mappedKeys.put("VK_F6", "action:btnReprintAction");
      this.mappedKeys.put("VK_F7", "instance:uk.chromis.pos.sales.shared.JTicketsBagShared:newTicket:m_ticketsbag");
      this.mappedKeys.put("VK_F8", "instance:uk.chromis.pos.sales.shared.JTicketsBagShared:delTicketAction:m_ticketsbag");
      this.mappedKeys.put("VK_F9", "instance:uk.chromis.pos.sales.shared.JTicketsBagShared:selectLayaway:m_ticketsbag");
      this.mappedKeys.put("VK_F10", "instance:uk.chromis.pos.sales.JTicketsBag:deleteAllShared:m_ticketsbag");
      this.mappedKeys.put("VK_F11", "action:resetCache");
      this.mappedKeys.put("VK_F12", "instance:uk.chromis.pos.forms.JPrincipalApp:changePassword:principalApp");
      if (evt.isControlDown() && KeyEvent.getKeyText(evt.getKeyCode()).equalsIgnoreCase("s")) {
         AppConfig.putInt("POSwidth", JRootFrame.PARENTFRAME.getWidth());
         AppConfig.putInt("POSheight", JRootFrame.PARENTFRAME.getHeight());
         AppConfig.putInt("POSx", JRootFrame.PARENTFRAME.getX());
         AppConfig.putInt("POSy", JRootFrame.PARENTFRAME.getY());
      } else {
         StringBuilder newTask = new StringBuilder();
         if (evt.isAltDown()) {
            newTask.append("Alt + ");
         }

         if (evt.isShiftDown()) {
            newTask.append("Shift + ");
         }

         if (evt.isControlDown()) {
            newTask.append("Ctrl + ");
         }

         newTask.append("VK_");
         newTask.append(KeyEvent.getKeyText(evt.getKeyCode()));
         String executionTask = this.mappedKeys.get(newTask.toString());
         if (executionTask != null) {
            if (executionTask.startsWith("class:")) {
               this.principalApp.showTask(executionTask.substring(6));
            } else if (executionTask.startsWith("action:")) {
               try {
                  Method method = JPanelTicket.class.getDeclaredMethod(executionTask.substring(7));
                  method.invoke(this);
               } catch (SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException var8) {
                  Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var8);
               }
            } else if (executionTask.startsWith("instance:")) {
               try {
                  int position = 0;
                  position = executionTask.indexOf(":", 10);
                  Class<?> clazz = Class.forName(executionTask.substring(9, position));
                  Method method = clazz.getDeclaredMethod(executionTask.substring(++position, executionTask.indexOf(":", position)));
                  position = executionTask.indexOf(":", position) + 1;
                  method.invoke(clazz.cast(this.instanceMap.get(executionTask.substring(position))));
               } catch (SecurityException | IllegalArgumentException | ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException var7) {
                  Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var7);
               }
            }

            this.m_jKeyFactory.setText(null);
         } else {
            if (SystemProperty.SCANWITHDASHES) {
               this.fromNumberPad = false;
            }

            int code = evt.getKeyCode();
            this.m_jKeyFactory.setText(null);
            if ((code < 16 || code > 18) && (code < 37 || code > 40)) {
               if (evt.getModifiersEx() == 0) {
                  this.stateTransition(evt.getKeyChar());
               }

               this.fromNumberPad = true;
            }
         }
      }
   }

   public static class ScriptArg {
      private final String key;
      private final Object value;

      public ScriptArg(String key, Object value) {
         this.key = key;
         this.value = value;
      }

      public String getKey() {
         return this.key;
      }

      public Object getValue() {
         return this.value;
      }
   }

   public class ScriptObject {
      private final TicketInfo ticket;
      private final Object ticketext;
      private int selectedindex;

      private ScriptObject(TicketInfo ticket, Object ticketext) {
         this.ticket = ticket;
         this.ticketext = ticketext;
      }

      public double getInputValue() {
         return JPanelTicket.this.m_iNumberStatusInput == 1 && JPanelTicket.this.m_iNumberStatusQty == 0 ? JPanelTicket.this.getInputValue() : 0.0;
      }

      public int getSelectedIndex() {
         return this.selectedindex;
      }

      public void setSelectedIndex(int i) {
         this.selectedindex = i;
      }

      public void kitchenOrderScreen() {
         JPanelTicket.this.kitchenOrderScreen(JPanelTicket.this.kitchenOrderId(), 1, false);
      }

      public void kitchenOrderScreen(String id) {
         JPanelTicket.this.kitchenOrderScreen(id, 1, false);
      }

      public void kitchenOrderScreen(Integer display, Boolean primary) {
         JPanelTicket.this.kitchenOrderScreen(JPanelTicket.this.kitchenOrderId(), display, primary);
      }

      public void printTicket(String sresourcename) {
         JPanelTicket.this.printTicket(sresourcename, this.ticket, this.ticketext, false);
      }

      public Object evalScript(String code, JPanelTicket.ScriptArg... args) throws ScriptException {
         ScriptEngine script = ScriptFactory.getScriptEngine("beanshell");

         try {
            script.put("hostname", DbUtils.getTerminalName());
            script.put("dbPassword", AppConfig.getClearDatabasePassword());
            script.put("ticket", this.ticket);
            script.put("place", this.ticketext);
            script.put("user", JPanelTicket.this.m_App.getAppUserView().getUser());
            script.put("sales", this);
            script.put("warranty", JPanelTicket.this.receiptRequired);
            script.put("pickupid", JPanelTicket.this.getPickupString(this.ticket));
            script.put("m_App", JPanelTicket.this.m_App);
            script.put("m_TTP", JPanelTicket.this.m_TTP);
            script.put("dlSystem", JPanelTicket.this.dlSystem);
            script.put("dlSales", JPanelTicket.this.dlSales);
            script.put("pickupcode", "P" + this.ticket.getId().substring(24));
            script.put("taxincluded", SystemProperty.TAXINCLUDED);
            script.put("pickupenabled", SystemProperty.PICKUPENABLED);
            script.put("receipt", JPanelTicket.this.paymentdialog);
            script.put(
               "cardType",
               new String[]{
                  "",
                  LocalResource.getString("paymentdescription.cmagcard1"),
                  LocalResource.getString("paymentdescription.cmagcard2"),
                  LocalResource.getString("paymentdescription.cmagcard3"),
                  LocalResource.getString("paymentdescription.cmagcard4"),
                  LocalResource.getString("transpayment.cmagcard5")
               }
            );
            script.put(
               "cardRefundType",
               new String[]{
                  "",
                  LocalResource.getString("paymentdescription.cmagcard1refund"),
                  LocalResource.getString("paymentdescription.cmagcard2refund"),
                  LocalResource.getString("paymentdescription.cmagcard3refund"),
                  LocalResource.getString("paymentdescription.cmagcard4refund"),
                  LocalResource.getString("paymentdescription.cmagcard5refund")
               }
            );
            ReceiptTaxesInfo ltr = new ReceiptTaxesInfo();
            List<ReceiptTaxesInfo> lines = ltr.getReceiptTaxLines(JPanelTicket.this.dlSales.getLineTaxRates(this.ticket.getId()));
            script.put("nett", ltr.printReceiptSubTotal(lines, this.ticket.getTicketTotal()));
            script.put("tickettaxdetails", lines);
            script.put("deliveryinfo", JPanelTicket.this.deliveryInfo);

            for (JPanelTicket.ScriptArg arg : args) {
               script.put(arg.getKey(), arg.getValue());
            }
         } catch (BasicException var10) {
            Logger.getLogger(JPanelTicket.class.getName()).log(Level.SEVERE, null, var10);
         }

         return script.eval(code);
      }

      public void applyDiscount(TicketLineInfo line, Double discountRate) {
         JPanelTicket.this.processDiscount(line, discountRate);
      }

      public void removeDiscount(TicketLineInfo line) {
         line.setProperty("product.name", line.getProperty("product.oldname"));
         line.setDiscounted("no");
         line.setProperty("edited", "0");
         this.ticket.minusTicketDiscount(Double.parseDouble(line.getProperty("prediscount")) - line.getPrice());
         line.setPrice(Double.parseDouble(line.getProperty("prediscount")));
         line.setProperty("discountrate", "");
      }
   }

   private class logout extends AbstractAction {
      @Override
      public void actionPerformed(ActionEvent ae) {
         String var2 = TerminalInfo.getPosType();
         switch (var2) {
            case "restaurant":
               if (!SystemProperty.AUTOLOGOFFTOTABLES && SystemProperty.ENABLEAUTOLOGOFF) {
                  JPanelTicket.this.deactivate();
                  ((JRootApp)JPanelTicket.this.m_App).closeAppView();
               } else {
                  JPanelTicket.this.deactivate();
                  JPanelTicket.this.setActiveTicket(null, null);
                  if (AutoLogoff.getInstance().getActiveFrame() != null) {
                     AutoLogoff.getInstance().getActiveFrame().dispose();
                     AutoLogoff.getInstance().setActiveFrame(null);
                  }
               }
               break;
            default:
               JPanelTicket.this.deactivate();
               if (AutoLogoff.getInstance().getActiveFrame() != null) {
                  AutoLogoff.getInstance().getActiveFrame().dispose();
                  AutoLogoff.getInstance().setActiveFrame(null);
               }

               ((JRootApp)JPanelTicket.this.m_App).closeAppView();
         }
      }
   }
}
