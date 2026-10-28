package uk.chromis.pos.sales.shared;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import uk.chromis.basic.BasicException;
import uk.chromis.commons.dialogs.JAlertPane;
import uk.chromis.data.gui.MessageInf;
import uk.chromis.globals.IconFactory;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.auditing.Audit;
import uk.chromis.pos.datalogic.DataLogicLoyalty;
import uk.chromis.pos.datalogic.DataLogicReceipts;
import uk.chromis.pos.datalogic.DataLogicSales;
import uk.chromis.pos.datalogic.DataLogicSystem;
import uk.chromis.pos.forms.AppLocal;
import uk.chromis.pos.forms.AppUser;
import uk.chromis.pos.forms.AppView;
import uk.chromis.pos.forms.ChromisFonts;
import uk.chromis.pos.loyalty.LoyaltyCard;
import uk.chromis.pos.printer.DeviceDisplayAdvance;
import uk.chromis.pos.printer.TicketParser;
import uk.chromis.pos.printer.TicketPrinterException;
import uk.chromis.pos.sales.CustomerDisplay;
import uk.chromis.pos.sales.JPanelTicket;
import uk.chromis.pos.sales.JTicketsBag;
import uk.chromis.pos.sales.SharedTicketInfo;
import uk.chromis.pos.sales.TicketsEditor;
import uk.chromis.pos.scripting.ScriptEngine;
import uk.chromis.pos.scripting.ScriptException;
import uk.chromis.pos.scripting.ScriptFactory;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;
import uk.chromis.pos.ticket.UserInfo;
import uk.chromis.pos.util.AutoLogoff;

public class JTicketsBagShared extends JTicketsBag {
   private String m_sCurrentTicket = null;
   private DataLogicReceipts dlReceipts = null;
   private DataLogicSales dlSales;
   private DataLogicSystem dlSystem;
   private DataLogicLoyalty dlLoyalty;
   private StringBuilder pickupBarcode;
   private JPanelTicket panelTicket;
   private JPanel jPanel1;
   private JButton m_jDelTicket;
   private JButton m_jListTickets;
   private JButton m_jNewTicket;

   public JTicketsBagShared(AppView app, TicketsEditor panelticket) {
      super(app, panelticket);
      this.panelTicket = (JPanelTicket)panelticket;
      this.dlReceipts = (DataLogicReceipts)app.getBean("uk.chromis.pos.datalogic.DataLogicReceipts");
      this.dlSales = (DataLogicSales)app.getBean("uk.chromis.pos.datalogic.DataLogicSales");
      this.dlSystem = (DataLogicSystem)app.getBean("uk.chromis.pos.datalogic.DataLogicSystem");
      this.dlLoyalty = new DataLogicLoyalty();
      this.initComponents();
      this.checkLayaways();
      this.m_jListTickets.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent pressedEvent) {
            if (pressedEvent.getButton() == 3) {
               JTicketsBagShared.this.deleteAllShared();
            }
         }
      });
      this.m_jDelTicket.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (JAlertPane.messageBox(0, AppLocal.getIntString("message.wannadelete"), 16, new Dimension(125, 50), 3) == 5) {
               Boolean remote = false;

               for (TicketLineInfo line : JTicketsBagShared.this.m_panelticket.getActiveTicket().getLines()) {
                  Audit.itemRemoved(JTicketsBagShared.this.m_panelticket.getActiveTicket(), line, "Full Ticket Removed");
                  if ("OK".equals(line.getProperty("sendstatus"))) {
                     line.setProperty("sendstatus", "Cancel");
                     remote = true;
                  }
               }

               if (remote) {
                  JTicketsBagShared.this.panelTicket.printTicket("Printer.TicketKitchen", JTicketsBagShared.this.m_panelticket.getActiveTicket(), null, true);
                  JAlertPane.messageBox(1, AppLocal.getIntString("message.orderCancellation"), 16, new Dimension(125, 50), 4);
               }

               JTicketsBagShared.this.m_sCurrentTicket = null;
               CustomerDisplay.updateDisplay("display.Message");
               JTicketsBagShared.this.newTicket();
            }
         }
      });
   }

   /**
    * "Borrar todas" (right click on the held tickets button, or F10) is turned off: a touch held a little too long
    * on a touch screen and a "Yes" to a question in English deleted every held ticket of every till.
    */
   @Override
   public void deleteAllShared() {
      JAlertPane.messageBox(
         1,
         "Borrar todas las ventas en espera está desactivado.\nPara quitar una venta en espera, ábrela y bórrala con el bote de basura.",
         16,
         new Dimension(125, 50),
         4
      );
   }

   public void deleteSharedTicket(TicketInfo ticket) {
      Boolean remote = false;

      for (TicketLineInfo line : ticket.getLines()) {
         Audit.itemRemoved(ticket, line, "Shared Ticket Removed");
         if ("OK".equals(line.getProperty("sendstatus"))) {
            line.setProperty("sendstatus", "Cancel");
            remote = true;
         }
      }

      if (remote) {
         this.panelTicket.printTicket("Printer.TicketKitchen", ticket, ticket.getPlace(), true);
         JAlertPane.messageBox(1, AppLocal.getIntString("message.orderCancellation"), 16, new Dimension(125, 50), 4);
      }
   }

   @Override
   public void activate() {
      this.m_sCurrentTicket = null;
      this.selectValidTicket();
      this.m_jDelTicket.setEnabled(AppUser.hasPermission("uk.chromis.pos.sales.JPanelTicketEdits"));
      this.checkLayaways();
   }

   @Override
   public boolean deactivate() {
      this.saveCurrentTicket();
      this.m_sCurrentTicket = null;
      this.m_panelticket.setActiveTicket(null, null);
      return true;
   }

   @Override
   public void deleteTicket() {
      this.m_sCurrentTicket = null;
      this.newTicket();
   }

   private void updateCustomerDisplay(String sresourcename) {
      String source = this.dlSystem.getResourceAsXML(sresourcename);

      try {
         if (!this.m_App.getDeviceTicket().getDeviceDisplay().getDisplayName().equals("Display not available")) {
            DeviceDisplayAdvance advDisplay = (DeviceDisplayAdvance)this.m_App.getDeviceTicket().getDeviceDisplay();
            advDisplay.setProductImage(null);
         }

         ScriptEngine script = ScriptFactory.getScriptEngine("velocity");
         new TicketParser(this.m_App.getDeviceTicket(), this.dlSystem).printTicket(script.eval(source).toString());
      } catch (TicketPrinterException | ScriptException var4) {
         Logger.getLogger(JTicketsBagShared.class.getName()).log(Level.SEVERE, null, var4);
      }
   }

   @Override
   protected JComponent getBagComponent() {
      return this;
   }

   @Override
   protected JComponent getNullComponent() {
      return new JPanel();
   }

   private void saveCurrentTicket() {
      if (this.m_sCurrentTicket != null) {
         try {
            if (SystemProperty.USEPICKUPFORLAYAWAY) {
               if (this.m_panelticket.getActiveTicket().getPickupId() == 0 && this.m_panelticket.getActiveTicket().getArticlesCount() > 0.0) {
                  this.m_panelticket.getActiveTicket().setPickupId(this.dlSales.getNextPickupIndex());
               }

               this.m_panelticket.getActiveTicket().setSharedTicket(Boolean.TRUE);
               this.dlReceipts
                  .insertSharedTicketUsingPickUpID(
                     this.m_sCurrentTicket,
                     this.m_panelticket.getActiveTicket(),
                     this.m_panelticket.getActiveTicket().getPickupId(),
                     "P" + this.m_panelticket.getActiveTicket().getId().substring(24),
                     this.m_panelticket.getActiveTicket().fetchDeliveryInfo()
                  );
            } else {
               this.m_panelticket.getActiveTicket().setSharedTicket(Boolean.TRUE);
               this.dlReceipts
                  .insertSharedTicket(
                     this.m_sCurrentTicket,
                     this.m_panelticket.getActiveTicket(),
                     this.m_panelticket.getActiveTicket().getPickupId(),
                     "P" + this.m_panelticket.getActiveTicket().getId().substring(24),
                     this.m_panelticket.getActiveTicket().fetchDeliveryInfo()
                  );
            }

            TicketInfo l = this.dlReceipts.getSharedTicket(this.m_sCurrentTicket);
            if (l.getLinesCount() == 0) {
               this.dlReceipts.deleteSharedTicket(this.m_sCurrentTicket);
            }

            this.checkLayaways();
         } catch (BasicException var2) {
            new MessageInf(var2).show(this);
         }
      }
   }

   private void setActiveTicket(String id) throws BasicException {
      TicketInfo ticket = this.dlReceipts.getSharedTicket(id);
      if (ticket == null) {
         this.m_jListTickets.setText("");
         throw new BasicException(AppLocal.getIntString("message.noticket"));
      } else {
         Date tmpDate = ticket.getdDate();
         UserInfo tmpUser = ticket.getSharedTicketUser();
         Integer pickUp = this.dlReceipts.getPickupId(id);
         String tName = this.dlReceipts.getTicketName(id);
         String loyalty = this.dlReceipts.getLoyaltyCard(id);
         ticket.setDeliveryInfo(this.dlReceipts.fetchDeliveryInfo(id));
         if (loyalty != null) {
            Integer points = DataLogicLoyalty.getBalanceByCardNumber(loyalty);

            for (int count = ticket.getLines().size() - 1; count >= 0; count--) {
               if (ticket.getLines().get(count).getProperty("redeemed_points") != null) {
                  if (points >= Integer.valueOf(ticket.getLines().get(count).getProperty("redeemed_points"))) {
                     points = points - Integer.valueOf(ticket.getLines().get(count).getProperty("redeemed_points"));
                  } else {
                     ticket.getLines().remove(count);
                  }
               }
            }
         }

         this.dlReceipts.deleteSharedTicket(id);
         this.m_sCurrentTicket = id;
         if (loyalty != null) {
            ticket.setLoyaltyCard(new LoyaltyCard(loyalty));
         }

         this.m_panelticket.setActiveTicket(ticket, null);
         ticket.setPickupId(pickUp);
         ticket.setdDate(tmpDate);
         ticket.setUser(tmpUser);
         this.m_panelticket.setTicketName(tName);
         this.checkLayaways();
      }
   }

   private void checkLayaways() {
      try {
         this.m_App.getAppUserView().getUser();
         List<SharedTicketInfo> nl;
         if (AppUser.hasPermission("access.alltickets")) {
            nl = this.dlReceipts.getSharedTicketList();
         } else if (SystemProperty.SHAREDTICKETBYUSER) {
            this.m_jListTickets.setText("");
            this.m_jListTickets.setIcon(IconFactory.getIcon("sale_pending.png"));
            this.m_jListTickets.setEnabled(false);
            nl = this.dlReceipts.getSharedTicketListByUser(this.m_App.getAppUserView().getUser().getName());
         } else {
            nl = this.dlReceipts.getSharedTicketList();
         }

         if (nl.isEmpty()) {
            this.m_jListTickets.setText("");
            this.m_jListTickets.setIcon(IconFactory.getIcon("sale_pending.png"));
            this.m_jListTickets.setEnabled(false);
         } else {
            this.m_jListTickets.setText(Integer.toString(nl.size()));
            this.m_jListTickets.setIcon(IconFactory.getIcon("sales_active.png"));
            this.m_jListTickets.setEnabled(true);
         }
      } catch (BasicException var3) {
      }
   }

   private void selectValidTicket() {
      this.newTicket();
      if (SystemProperty.LAYAWAYPOPUP) {
         try {
            List<SharedTicketInfo> l = this.dlReceipts.getSharedTicketList();
            if (l.isEmpty()) {
               this.m_jListTickets.setText("");
               this.newTicket();
               AutoLogoff.getInstance().activateTimer();
            } else {
               this.m_jListTicketsActionPerformed(null);
            }
         } catch (BasicException var2) {
            new MessageInf(var2).show(this);
            this.newTicket();
            AutoLogoff.getInstance().activateTimer();
         }
      }
   }

   public void newTicket() {
      this.saveCurrentTicket();
      this.pickupBarcode = new StringBuilder();
      TicketInfo ticket = new TicketInfo();
      ticket.setTicketOwner(this.m_App.getAppUserView().getUser().getId());
      this.m_sCurrentTicket = UUID.randomUUID().toString();
      this.m_panelticket.setActiveTicket(ticket, null);
      this.pickupBarcode.append("P").append(ticket.getId().substring(24));
   }

   @Override
   public void getTicketByCode(String id) {
      this.saveCurrentTicket();
      List<SharedTicketInfo> l = null;

      try {
         l = this.dlReceipts.getSharedTicketList();
      } catch (BasicException var7) {
      }

      for (SharedTicketInfo t : l) {
         if (t.getPickupBarcode().equals(id)) {
            this.saveCurrentTicket();

            try {
               this.setActiveTicket(t.getId());
            } catch (BasicException var6) {
               Logger.getLogger(JTicketsBagShared.class.getName()).log(Level.SEVERE, null, var6);
            }
         }
      }
   }

   private void initComponents() {
      this.jPanel1 = new JPanel();
      this.m_jNewTicket = new JButton();
      this.m_jDelTicket = new JButton();
      this.m_jListTickets = new JButton();
      this.setFont(ChromisFonts.DEFAULTFONT.deriveFont(14.0F));
      this.setLayout(new BorderLayout());
      this.m_jNewTicket.setIcon(IconFactory.getIcon("sale_new.png"));
      this.m_jNewTicket.setFocusPainted(false);
      this.m_jNewTicket.setFocusable(false);
      this.m_jNewTicket.setMargin(new Insets(0, 4, 0, 4));
      this.m_jNewTicket.setMaximumSize(new Dimension(50, 40));
      this.m_jNewTicket.setMinimumSize(new Dimension(50, 40));
      this.m_jNewTicket.setPreferredSize(new Dimension(52, 40));
      this.m_jNewTicket.setRequestFocusEnabled(false);
      this.m_jNewTicket.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JTicketsBagShared.this.m_jNewTicketActionPerformed(evt);
         }
      });
      this.jPanel1.add(this.m_jNewTicket);
      this.m_jDelTicket.setIcon(IconFactory.getIcon("sale_delete.png"));
      this.m_jDelTicket.setFocusPainted(false);
      this.m_jDelTicket.setFocusable(false);
      this.m_jDelTicket.setMargin(new Insets(0, 4, 0, 4));
      this.m_jDelTicket.setMaximumSize(new Dimension(50, 40));
      this.m_jDelTicket.setMinimumSize(new Dimension(50, 40));
      this.m_jDelTicket.setPreferredSize(new Dimension(52, 40));
      this.m_jDelTicket.setRequestFocusEnabled(false);
      this.jPanel1.add(this.m_jDelTicket);
      this.m_jListTickets.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jListTickets.setIcon(IconFactory.getIcon("sale_pending.png"));
      this.m_jListTickets.setText("99");
      this.m_jListTickets.setFocusPainted(false);
      this.m_jListTickets.setFocusable(false);
      this.m_jListTickets.setHorizontalAlignment(2);
      this.m_jListTickets.setIconTextGap(-3);
      this.m_jListTickets.setMargin(new Insets(0, 4, 0, 4));
      this.m_jListTickets.setMaximumSize(new Dimension(55, 40));
      this.m_jListTickets.setMinimumSize(new Dimension(55, 40));
      this.m_jListTickets.setPreferredSize(new Dimension(52, 40));
      this.m_jListTickets.setRequestFocusEnabled(false);
      this.m_jListTickets.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JTicketsBagShared.this.m_jListTicketsActionPerformed(evt);
         }
      });
      this.jPanel1.add(this.m_jListTickets);
      this.add(this.jPanel1, "West");
   }

   private void m_jListTicketsActionPerformed(ActionEvent evt) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            try {
               JTicketsBagShared.this.m_App.getAppUserView().getUser();
               List<SharedTicketInfo> l;
               if (AppUser.hasPermission("access.alltickets")) {
                  l = JTicketsBagShared.this.dlReceipts.getSharedTicketList();
               } else if (SystemProperty.SHAREDTICKETBYUSER) {
                  l = JTicketsBagShared.this.dlReceipts.getSharedTicketListByUser(JTicketsBagShared.this.m_App.getAppUserView().getUser().getName());
               } else {
                  l = JTicketsBagShared.this.dlReceipts.getSharedTicketList();
               }

               JTicketsBagSharedList listDialog = JTicketsBagSharedList.newJDialog(JTicketsBagShared.this);
               String id = listDialog.showTicketsList(l);
               if (id != null) {
                  JTicketsBagShared.this.saveCurrentTicket();
                  JTicketsBagShared.this.m_sCurrentTicket = id;
                  JTicketsBagShared.this.setActiveTicket(id);
               }
            } catch (BasicException var4) {
               new MessageInf(var4).show(JTicketsBagShared.this);
               JTicketsBagShared.this.newTicket();
            }
         }
      });
   }

   public void selectLayaway() {
      this.m_jListTicketsActionPerformed(null);
   }

   public void delTicketAction() {
      AutoLogoff.getInstance().deactivateTimer();

      for (TicketLineInfo line : this.m_panelticket.getActiveTicket().getLines()) {
         if ("OK".equals(line.getProperty("sendstatus")) && !SystemProperty.ALLOWSENTITEMREFUND) {
            JAlertPane.messageBox(1, AppLocal.getIntString("message.cannotdeletesentorder"), 16, new Dimension(125, 50), 4);
            return;
         }
      }

      if (JAlertPane.messageBox(3, AppLocal.getIntString("message.wannadelete"), 16, new Dimension(125, 50), 3) == 5) {
         this.updateCustomerDisplay("display.Message");
         this.deleteTicket();
      }

      AutoLogoff.getInstance().activateTimer();
   }

   private void m_jNewTicketActionPerformed(ActionEvent evt) {
      this.newTicket();
   }
}
