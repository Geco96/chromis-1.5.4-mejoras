package uk.chromis.pos.forms;

import java.sql.SQLException;
import uk.chromis.pos.attendance.AutoAttendance;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;
import uk.chromis.basic.BasicException;
import uk.chromis.beans.JPasswordPanel;
import uk.chromis.commons.dialogs.JAlertPane;
import uk.chromis.data.gui.JMessageDialog;
import uk.chromis.data.gui.MessageInf;
import uk.chromis.globals.IconFactory;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.customers.CustomerInfo;
import uk.chromis.pos.customers.CustomersPayment;
import uk.chromis.pos.datalogic.DataLogicSystem;
import uk.chromis.pos.panels.JPanelPayments;
import uk.chromis.pos.scripting.ScriptEngine;
import uk.chromis.pos.scripting.ScriptException;
import uk.chromis.pos.scripting.ScriptFactory;
import uk.chromis.pos.util.Hashcypher;
import uk.chromis.pos.util.StringUtils;

public class JPrincipalApp extends JPanel implements AppUserView {
   private static final Logger logger = Logger.getLogger("uk.chromis.pos.forms.JPrincipalApp");
   private final JRootApp m_appview;
   private final AppUser m_appuser;
   private DataLogicSystem m_dlSystem;
   private JLabel m_principalnotificator;
   private JPanelView m_jLastView;
   private Action m_actionfirst;
   private Map<String, JPanelView> m_aPreparedViews;
   private Map<String, JPanelView> m_aCreatedViews;
   private Icon menu_open;
   private Icon menu_close;
   private List<JPrincipalApp.MenuList> menuList = new ArrayList<>();
   private JPopupMenu actionMenu;
   private CustomerInfo customerInfo;
   private JButton btnActionMenu;
   private JButton jBtnSales;
   private JButton jButton1;
   private JPanel jPanel1;
   private JPanel m_jPanelContainer;
   public static JScrollPane m_jPanelLeft;
   private JPanel m_jPanelRight;
   private JPanel m_jPanelTitle;
   private JLabel m_jTitle;

   public JPrincipalApp(JRootApp appview, AppUser appuser) {
      this.initComponents();
      m_jPanelLeft.setVisible(false);
      this.menuList = new ArrayList<>();
      this.actionMenu = new JPopupMenu("Menu");
      this.actionMenu.removeAll();
      this.m_appview = appview;
      this.m_appuser = appuser;
      this.m_dlSystem = (DataLogicSystem)this.m_appview.getBean("uk.chromis.pos.datalogic.DataLogicSystem");
      this.m_appuser.fillPermissions(this.m_dlSystem);
      this.m_actionfirst = null;
      this.m_jLastView = null;
      this.m_aPreparedViews = new HashMap<>();
      this.m_aCreatedViews = new HashMap<>();
      this.applyComponentOrientation(appview.getComponentOrientation());
      this.m_principalnotificator = new JLabel();
      this.m_principalnotificator.applyComponentOrientation(this.getComponentOrientation());
      this.m_principalnotificator.setText(this.m_appuser.getName());
      this.m_principalnotificator.setIcon(this.m_appuser.getIcon());
      this.m_jPanelContainer.add(new JPanel(), "<NULL>");
      this.showView("<NULL>");

      try {
         m_jPanelLeft.setViewportView(this.getScriptMenu(this.m_dlSystem.getResourceAsText("Menu.Root")));
      } catch (ScriptException var10) {
         try {
            this.menuList.clear();
            m_jPanelLeft.setViewportView(this.getScriptMenu(StringUtils.readResource("/uk/chromis/pos/templates/Menu.Root.txt")));
         } catch (ScriptException | IOException var9) {
            logger.log(Level.SEVERE, "Cannot read default menu", (Throwable)var9);
         }
      }

      for (int j = 0; j < this.menuList.size(); j++) {
         if (this.menuList.get(j).getMenuClass().equals("uk.chromis.pos.customers.CustomersPayment")) {
            JMenuItem cPayment;
            try {
               cPayment = new JMenuItem(AppLocal.getIntString("menu.customerPayment"), IconFactory.getResizedIcon("customerpay.png", new Dimension(30, 30)));
            } catch (Exception var7) {
               cPayment = new JMenuItem(AppLocal.getIntString("menu.customerPayment"));
            }

            this.actionMenu.add(cPayment);
            cPayment.addActionListener(ev -> new CustomersPayment(this.m_appview));
         } else if (this.menuList.get(j).getMenuClass().equals("uk.chromis.pos.panels.JPanelPayments")) {
            JMenuItem payment;
            try {
               payment = new JMenuItem(AppLocal.getIntString("menu.payments"), IconFactory.getResizedIcon("payments.png", new Dimension(30, 30)));
            } catch (Exception var8) {
               payment = new JMenuItem(AppLocal.getIntString("menu.payments"));
            }

            this.actionMenu.add(payment);
            payment.addActionListener(ev -> new JPanelPayments(this.m_appview));
         } else {
            this.addItem(this.menuList.get(j).getMenuTitle(), this.menuList.get(j).getMenuClass(), this.menuList.get(j).getImageIcon());
         }
      }

      JMenuItem password;
      try {
         password = new JMenuItem("Change Password", IconFactory.getResizedIcon("password.png", new Dimension(30, 30)));
      } catch (Exception var6) {
         password = new JMenuItem("Change Password");
      }

      this.actionMenu.add(password);
      password.setPreferredSize(new Dimension(200, 40));
      password.addActionListener(ev -> JRootApp.m_principalapp.changePassword());
   }

   private void addItem(String itemMessage, String action, ImageIcon imageIcon) {
      JMenuItem tmpItem = new JMenuItem(itemMessage);
      if (imageIcon != null) {
         try {
            Image image = imageIcon.getImage();
            Image newimg = image.getScaledInstance(30, 30, 4);
            tmpItem = new JMenuItem(itemMessage, new ImageIcon(newimg));
         } catch (Exception var7) {
            tmpItem = new JMenuItem(itemMessage);
         }
      }

      this.actionMenu.add(tmpItem);
      tmpItem.setPreferredSize(new Dimension(300, 40));
      tmpItem.addActionListener(ev -> JRootApp.m_principalapp.showTask(action));
   }

   public void setMenuVisible(JButton jButton) {
      this.actionMenu.show(jButton, jButton.getWidth() / 2, jButton.getHeight() / 2);
   }

   public void showSalesButton(Boolean value) {
      this.jBtnSales.setVisible(value);
   }

   private Component getScriptMenu(String menutext) throws ScriptException {
      JPrincipalApp.ScriptMenu menu = new JPrincipalApp.ScriptMenu();
      ScriptEngine eng = ScriptFactory.getScriptEngine("beanshell");
      eng.put("menu", menu);
      eng.eval(menutext);
      return menu.getTaskPane();
   }

   public JComponent getNotificator() {
      return this.m_principalnotificator;
   }

   public void activate() {
      try {
         // Chromis can leave the terminal on a cash that is not in the database (or already closed): then no sale can be saved
         uk.chromis.pos.panels.CashCloser.Result cash = uk.chromis.pos.panels.CashCloser.ensureOpenCash(
            this.m_appview.getSession().getConnection(),
            this.m_appview.getActiveCashIndex(),
            this.m_appview.getProperties().getHost(),
            uk.chromis.commons.utils.TerminalInfo.getTerminalID()
         );
         if (cash != null) {
            uk.chromis.pos.panels.CashCloser.log("La caja estaba en un corte inexistente o cerrado (" + this.m_appview.getActiveCashIndex()
               + "); se abrió el corte " + cash.sequence, null);
            this.m_appview.setActiveCash(cash.money, cash.sequence, cash.start, null);
         }
      } catch (SQLException var3) {
         uk.chromis.pos.panels.CashCloser.log("No se pudo revisar el corte activo de la caja", var3);
      }

      try {
         AutoAttendance.onLogin(this.m_appview.getSession().getConnection(), this.m_appuser.getId(), this);
      } catch (SQLException var2) {
         // Attendance is optional: the user can always work
      }

      if (this.m_actionfirst != null) {
         this.m_actionfirst.actionPerformed(null);
         this.m_actionfirst = null;
      }
   }

   public boolean deactivate() {
      if (this.m_jLastView == null) {
         return true;
      } else if (this.m_jLastView.deactivate()) {
         this.m_jLastView = null;
         this.showView("<NULL>");
         return true;
      } else {
         return false;
      }
   }

   public void exitToLogin() {
      this.m_appview.closeAppView();
   }

   public void changePassword() {
      Object[] result = JPasswordPanel.changePassword(this.m_appuser);
      if ((Integer)result[0] == 0) {
         String sNewPassword = Hashcypher.hashString((String)result[1]);
         if (sNewPassword != null) {
            try {
               this.m_dlSystem.execChangePassword(new Object[]{sNewPassword, this.m_appuser.getId()});
               this.m_appuser.setPassword(sNewPassword);
               JAlertPane.messageBox(1, AppLocal.getIntString("message.passwordchanged"), 16, new Dimension(100, 50), 4);
            } catch (BasicException var4) {
               JAlertPane.messageBox(0, AppLocal.getIntString("message.cannotchangepassword"), 16, new Dimension(100, 50), 4);
            }
         }
      }
   }

   private void showView(String sView) {
      CardLayout cl = (CardLayout)this.m_jPanelContainer.getLayout();
      cl.show(this.m_jPanelContainer, sView);
   }

   public AppUser getUser() {
      return this.m_appuser;
   }

   public void showTask(String sTaskClass) {
      this.customerInfo = new CustomerInfo("");
      this.customerInfo.setName("");
      this.m_appview.waitCursorBegin();
      if (AppUser.hasPermission(sTaskClass)) {
         JPanelView m_jMyView = this.m_aCreatedViews.get(sTaskClass);
         if (this.m_jLastView == null || m_jMyView != this.m_jLastView && this.m_jLastView.deactivate()) {
            if (m_jMyView == null) {
               m_jMyView = this.m_aPreparedViews.get(sTaskClass);
               if (m_jMyView == null) {
                  try {
                     m_jMyView = (JPanelView)this.m_appview.getBean(sTaskClass);
                  } catch (BeanFactoryException var5) {
                     m_jMyView = new JPanelNull(this.m_appview, var5);
                  }
               }

               m_jMyView.getComponent().applyComponentOrientation(this.getComponentOrientation());
               this.m_jPanelContainer.add(m_jMyView.getComponent(), sTaskClass);
               this.m_aCreatedViews.put(sTaskClass, m_jMyView);
            }

            try {
               m_jMyView.activate();
            } catch (BasicException var4) {
               JMessageDialog.showMessage(this, new MessageInf(-33554432, AppLocal.getIntString("message.notactive"), var4));
            }

            this.m_jLastView = m_jMyView;
            this.showView(sTaskClass);
            String sTitle = m_jMyView.getTitle();
            this.m_jPanelTitle.setVisible(sTitle != null);
            this.m_jTitle.setText(sTitle);
         }
      } else {
         JAlertPane.messageBox(new Dimension(350, 100), 0, AppLocal.getIntString("message.notpermissions"), 16, new Dimension(100, 50), 4);
      }

      this.m_appview.waitCursorEnd();
   }

   public void executeTask(String sTaskClass) {
      this.m_appview.waitCursorBegin();
      if (AppUser.hasPermission(sTaskClass)) {
         try {
            ProcessAction myProcess = (ProcessAction)this.m_appview.getBean(sTaskClass);

            try {
               MessageInf m = myProcess.execute();
               if (m != null) {
                  JMessageDialog.showMessage(this, m);
               }
            } catch (BasicException var4) {
               JMessageDialog.showMessage(this, new MessageInf(var4));
            }
         } catch (BeanFactoryException var5) {
            JAlertPane.messageBox(new Dimension(350, 100), 0, AppLocal.getIntString("message.notpermissions"), 16, new Dimension(100, 50), 4);
         }
      } else {
         JAlertPane.messageBox(new Dimension(350, 100), 0, AppLocal.getIntString("message.notpermissions") + " " + sTaskClass, 16, new Dimension(100, 50), 4);
      }

      this.m_appview.waitCursorEnd();
   }

   private void initComponents() {
      this.jPanel1 = new JPanel();
      m_jPanelLeft = new JScrollPane();
      this.m_jPanelRight = new JPanel();
      this.m_jPanelTitle = new JPanel();
      this.btnActionMenu = new JButton();
      this.jButton1 = new JButton();
      this.jBtnSales = new JButton();
      this.m_jTitle = new JLabel();
      this.m_jPanelContainer = new JPanel();
      this.setLayout(new BorderLayout());
      this.jPanel1.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.jPanel1.setLayout(new BorderLayout());
      m_jPanelLeft.setMinimumSize(new Dimension(23, 0));
      this.jPanel1.add(m_jPanelLeft, "Center");
      this.add(this.jPanel1, "Before");
      this.m_jPanelRight.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 1));
      this.m_jPanelRight.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.m_jPanelRight.setLayout(new BorderLayout());
      this.m_jPanelTitle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
      this.btnActionMenu.setIcon(IconFactory.getIcon("menu.png"));
      this.btnActionMenu.setMaximumSize(new Dimension(18, 18));
      this.btnActionMenu.setMinimumSize(new Dimension(32, 32));
      this.btnActionMenu.setPreferredSize(new Dimension(52, 40));
      this.btnActionMenu.setRequestFocusEnabled(false);
      this.btnActionMenu.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPrincipalApp.this.btnActionMenuActionPerformed(evt);
         }
      });
      this.jButton1.setIcon(IconFactory.getIcon("logout.png"));
      this.jButton1.setPreferredSize(new Dimension(52, 40));
      this.jButton1.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPrincipalApp.this.jButton1ActionPerformed(evt);
         }
      });
      this.jBtnSales.setIcon(IconFactory.getIcon("sale_new.png"));
      this.jBtnSales.setMaximumSize(new Dimension(52, 40));
      this.jBtnSales.setMinimumSize(new Dimension(52, 40));
      this.jBtnSales.setPreferredSize(new Dimension(52, 40));
      this.jBtnSales.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JPrincipalApp.this.jBtnSalesActionPerformed(evt);
         }
      });
      this.m_jTitle.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(24.0F));
      this.m_jTitle.setHorizontalAlignment(2);
      GroupLayout m_jPanelTitleLayout = new GroupLayout(this.m_jPanelTitle);
      this.m_jPanelTitle.setLayout(m_jPanelTitleLayout);
      m_jPanelTitleLayout.setHorizontalGroup(
         m_jPanelTitleLayout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               m_jPanelTitleLayout.createSequentialGroup()
                  .addComponent(this.btnActionMenu, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.jButton1, -2, -1, -2)
                  .addPreferredGap(ComponentPlacement.RELATED)
                  .addComponent(this.jBtnSales, -2, 59, -2)
                  .addPreferredGap(ComponentPlacement.UNRELATED)
                  .addComponent(this.m_jTitle, -1, 360, 32767)
                  .addGap(5, 5, 5)
            )
      );
      m_jPanelTitleLayout.setVerticalGroup(
         m_jPanelTitleLayout.createParallelGroup(Alignment.LEADING)
            .addGroup(
               m_jPanelTitleLayout.createSequentialGroup()
                  .addGap(5, 5, 5)
                  .addGroup(
                     m_jPanelTitleLayout.createParallelGroup(Alignment.LEADING)
                        .addComponent(this.jBtnSales, -2, 40, -2)
                        .addComponent(this.jButton1, -2, -1, -2)
                        .addGroup(
                           m_jPanelTitleLayout.createParallelGroup(Alignment.LEADING)
                              .addComponent(this.btnActionMenu, Alignment.TRAILING, -2, -1, -2)
                              .addComponent(this.m_jTitle, Alignment.TRAILING, -2, 40, -2)
                        )
                  )
            )
      );
      this.m_jPanelRight.add(this.m_jPanelTitle, "North");
      this.m_jPanelContainer.setFont(ChromisFonts.DEFAULTFONT.deriveFont(14.0F));
      this.m_jPanelContainer.setLayout(new CardLayout());
      this.m_jPanelRight.add(this.m_jPanelContainer, "Center");
      this.add(this.m_jPanelRight, "Center");
   }

   private void btnActionMenuActionPerformed(ActionEvent evt) {
      this.actionMenu.show(this.btnActionMenu, this.btnActionMenu.getWidth() / 2, this.btnActionMenu.getHeight() / 2);
   }

   private void jBtnSalesActionPerformed(ActionEvent evt) {
      this.showTask("uk.chromis.pos.sales.JPanelTicketSales");
   }

   private void jButton1ActionPerformed(ActionEvent evt) {
      System.out.println("Logout");
      this.m_appview.exitToLogin();
   }

   private class ExitAction extends AbstractAction {
      public ExitAction(String icon, String keytext) {
         this.putValue("SmallIcon", new ImageIcon(JPrincipalApp.class.getResource(icon)));
         this.putValue("Name", AppLocal.getIntString(keytext));
         this.putValue("taskname", keytext);
      }

      @Override
      public void actionPerformed(ActionEvent evt) {
         JPrincipalApp.this.m_appview.closeAppView();
      }
   }

   public class MenuList {
      private String menu = null;
      private String menuTitle = null;
      private String menuClass = null;
      private ImageIcon imageIcon = null;

      private MenuList() {
      }

      private MenuList(String menu, String menuTitle, String menuClass, ImageIcon imageIcon) {
         this.menu = menu;
         this.menuTitle = menuTitle;
         this.menuClass = menuClass;
         this.imageIcon = imageIcon;
      }

      public String getMenu() {
         return this.menu;
      }

      public String getMenuTitle() {
         return this.menuTitle;
      }

      public String getMenuClass() {
         return this.menuClass;
      }

      public ImageIcon getImageIcon() {
         return this.imageIcon;
      }
   }

   public class ScriptGroup {
      private final JXTaskPane taskGroup = new JXTaskPane();

      private ScriptGroup(String key) {
         this.taskGroup.applyComponentOrientation(JPrincipalApp.this.getComponentOrientation());
         this.taskGroup.setFocusable(false);
         this.taskGroup.setRequestFocusEnabled(false);
         this.taskGroup.setTitle(AppLocal.getIntString(key));
         this.taskGroup.setVisible(false);
      }

      public void addPanel(String icon, String key, String classname) {
         if (!SystemProperty.USEFOBS || !classname.equals("uk.chromis.pos.panels.JPanelCloseMoney")) {
            this.addAction(new MenuPanelAction(JPrincipalApp.this.m_appview, icon, key, classname));
         }
      }

      public void addExecution(String icon, String key, String classname) {
         this.addAction(new MenuExecAction(JPrincipalApp.this.m_appview, icon, key, classname));
      }

      public JPrincipalApp.ScriptSubmenu addSubmenu(String icon, String key, String classname) {
         JPrincipalApp.ScriptSubmenu submenu = JPrincipalApp.this.new ScriptSubmenu(key);
         JPrincipalApp.this.m_aPreparedViews.put(classname, new JPanelMenu(submenu.getMenuDefinition()));
         this.addAction(new MenuPanelAction(JPrincipalApp.this.m_appview, icon, key, classname));
         return submenu;
      }

      private void addAction(Action act) {
         if (AppUser.hasPermission((String)act.getValue("taskname"))) {
            JPrincipalApp.this.menuList
               .add(
                  JPrincipalApp.this.new MenuList(
                     this.taskGroup.getTitle(), (String)act.getValue("Name"), (String)act.getValue("taskname"), (ImageIcon)act.getValue("SmallIcon")
                  )
               );
            if (JPrincipalApp.this.m_actionfirst == null) {
               JPrincipalApp.this.m_actionfirst = act;
            }
         }
      }

      public JXTaskPane getTaskGroup() {
         return this.taskGroup;
      }
   }

   public class ScriptMenu {
      private final JXTaskPaneContainer taskPane = new JXTaskPaneContainer();

      private ScriptMenu() {
         this.taskPane.applyComponentOrientation(JPrincipalApp.this.getComponentOrientation());
      }

      public JPrincipalApp.ScriptGroup addGroup(String key) {
         JPrincipalApp.ScriptGroup group = JPrincipalApp.this.new ScriptGroup(key);
         this.taskPane.add(group.getTaskGroup());
         return group;
      }

      public JXTaskPaneContainer getTaskPane() {
         return this.taskPane;
      }
   }

   public class ScriptSubmenu {
      private final MenuDefinition menudef;

      private ScriptSubmenu(String key) {
         this.menudef = new MenuDefinition(key);
      }

      public void addTitle(String key) {
         this.menudef.addMenuTitle(key);
      }

      public void addPanel(String icon, String key, String classname) {
         this.menudef.addMenuItem(new MenuPanelAction(JPrincipalApp.this.m_appview, icon, key, classname));
      }

      public void addExecution(String icon, String key, String classname) {
         this.menudef.addMenuItem(new MenuExecAction(JPrincipalApp.this.m_appview, icon, key, classname));
      }

      public JPrincipalApp.ScriptSubmenu addSubmenu(String icon, String key, String classname) {
         JPrincipalApp.ScriptSubmenu submenu = JPrincipalApp.this.new ScriptSubmenu(key);
         JPrincipalApp.this.m_aPreparedViews.put(classname, new JPanelMenu(submenu.getMenuDefinition()));
         this.menudef.addMenuItem(new MenuPanelAction(JPrincipalApp.this.m_appview, icon, key, classname));
         return submenu;
      }

      public MenuDefinition getMenuDefinition() {
         return this.menudef;
      }
   }
}
