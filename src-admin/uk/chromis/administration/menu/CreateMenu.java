package uk.chromis.administration.menu;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.chromis.administration.dialogs.Dialogs;
import uk.chromis.administration.main.StartController;
import uk.chromis.data.loader.ConnectionFactory;
import uk.chromis.pos.forms.LocalResource;

public class CreateMenu {
   private final MenuBar menuBar;
   private Menu menu = null;
   private Menu subMenu;
   private MenuItem lineItem;
   private InputStream xmlFile;
   private Boolean separatorFlag = true;
   private Boolean mainItemFlag = true;
   private CheckMenuItem checkMenuItem;
   private HashSet<String> m_apermissions;
   private Integer rightsLevel;
   private final LogicalUse lu = new LogicalUse();

   public CreateMenu(MenuBar menuBar) {
      this.menuBar = menuBar;
      this.m_apermissions = new HashSet<>();
   }

   public void setPermission(HashSet<String> permissions, Integer rightslevel) {
      this.m_apermissions = permissions;
      this.rightsLevel = rightslevel;
   }

   public void setPermission() {
      this.m_apermissions = new HashSet<>();
   }

   public void populateLoginMenu() {
      this.menuBar.getMenus().clear();
      this.xmlFile = this.getClass().getResourceAsStream("loginmenu.xml");
      this.populate();
      this.populateFullMenu();
   }

   public void populateLogoutMenu() {
      this.menuBar.getMenus().clear();
      this.xmlFile = this.getClass().getResourceAsStream("logoutmenu.xml");
      this.populate();
      this.populateFullMenu();
   }

   private void populateFullMenu() {
      this.xmlFile = this.getClass().getResourceAsStream("menu.xml");
      this.populate();
   }

   private void populate() {
      try {
         DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder dBuilder = builderFactory.newDocumentBuilder();
         Document document = dBuilder.parse(this.xmlFile);
         document.normalize();
         NodeList rootNodes = document.getElementsByTagName("menuBar");
         Node rootNode = rootNodes.item(0);
         Element rootElement = (Element)rootNode;
         NodeList menuList = rootElement.getElementsByTagName("menuTitle");

         for (int i = 0; i < menuList.getLength(); i++) {
            Node menuTitle = menuList.item(i);
            Element menuTitleElement = (Element)menuTitle;
            this.menu = new Menu(LocalResource.getString(menuTitleElement.getAttribute("text")));
            this.menuBar.getMenus().addAll(new Menu[]{this.menu});
            NodeList menuItems = menuTitleElement.getElementsByTagName("menuEntry");

            for (int h = 0; h < menuItems.getLength(); h++) {
               Node itemLine = menuItems.item(h);
               Element itemLineElement = (Element)itemLine;
               if (this.getNodePath(itemLine).equals("/#document/menuBar/menuTitle/menuEntry")) {
                  this.processEntry(itemLineElement, this.menu);
               }
            }

            if (!this.mainItemFlag) {
               this.menuBar.getMenus().remove(this.menu);
            }

            this.mainItemFlag = false;
         }
      } catch (ParserConfigurationException var15) {
      } catch (IOException | SAXException var16) {
         Logger.getLogger(CreateMenu.class.getName()).log(Level.SEVERE, null, var16);
      }
   }

   private String getNodePath(Node node) {
      String nodePath = "";

      for (String tmp = "/" + node.getNodeName(); node.getNodeType() != 9; tmp = nodePath) {
         node = node.getParentNode();
         nodePath = "/" + node.getNodeName() + tmp;
      }

      return nodePath;
   }

   private void processEntry(Element element, Menu menu) {
      String var3 = element.getAttribute("type");
      switch (var3) {
         case "separator":
            if (!this.separatorFlag) {
               menu.getItems().add(new SeparatorMenuItem());
               this.separatorFlag = true;
            }
            break;
         case "menuitem":
            if (element.getAttribute("permissionclass").length() == 0) {
               this.lineItem = new MenuItem(String.format("%-35s", this.menuText(element.getAttribute("text")).trim()));
               this.shortCutKeys(element, this.lineItem);
               if (element.getAttribute("actionClass").length() != 0 && element.getAttribute("actionMethod").length() != 0) {
                  this.lineItem
                     .setOnAction(
                        e -> {
                           if (StartController.jobClass.getValue() != null) {
                              try {
                                 Class cls = Class.forName(StartController.jobClass.getValue());
                                 Object obj = cls.getDeclaredConstructor().newInstance();
                                 Method method = cls.getDeclaredMethod("processDataChecks");
                                 method.invoke(obj);
                              } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException | ClassNotFoundException var6x) {
                                 Dialogs.errorAlert(var6x, "Show Panel Exit Error");
                              }
                           }

                           this.callClassByName(element.getAttribute("actionClass"), element.getAttribute("actionMethod"));
                        }
                     );
                  if (this.m_apermissions.contains(element.getAttribute("previousClass"))
                     || this.m_apermissions.contains(element.getAttribute("accessName"))
                     || element.getAttribute("alwaysAvailable").equalsIgnoreCase("true")) {
                     String showSql = element.getAttribute("showCheck");
                     menu.getItems().add(this.lineItem);
                     this.mainItemFlag = true;
                  }

                  this.separatorFlag = false;
               }
            }
            break;
         case "submenu":
            this.subMenu = new Menu(String.format("%-35s", element.getAttribute("parent").trim()));
            menu.getItems().add(this.subMenu);
            NodeList subMenuItems = element.getElementsByTagName("subMenuEntry");

            for (int i = 0; i < subMenuItems.getLength(); i++) {
               Element tmpElement = (Element)subMenuItems.item(i);
               NodeList tmpList = tmpElement.getElementsByTagName("menuEntry");

               for (int k = 0; k < tmpList.getLength(); k++) {
                  Node tmpitemLine = tmpList.item(k);
                  Element subItemLineElement = (Element)tmpitemLine;
                  this.processEntry(subItemLineElement, this.subMenu);
               }
            }
            break;
         case "checkmenuitem":
            this.checkMenuItem = new CheckMenuItem(String.format("%-35s", this.menuText(element.getAttribute("text")).trim()));
            if (element.getAttribute("actionClass").length() != 0 && element.getAttribute("actionMethod").length() != 0) {
               this.checkMenuItem
                  .setOnAction(
                     e -> this.callClassByNameParameter(
                           element.getAttribute("actionClass"), element.getAttribute("actionMethod"), element.getAttribute("option")
                        )
                  );
            }

            menu.getItems().add(this.checkMenuItem);
            this.checkMenuItem.setSelected(CheckedItems.getInstance().getCheckedMenuState(element.getAttribute("option")));
            this.separatorFlag = false;
            this.mainItemFlag = true;
            break;
         case "singleuseitem":
            if (!this.lu.getBooleanCount(element.getAttribute("sql"))) {
               this.lineItem = new MenuItem(String.format("%-35s", this.menuText(element.getAttribute("text")).trim()));
               if (element.getAttribute("actionClass").length() != 0 && element.getAttribute("actionMethod").length() != 0) {
                  this.lineItem.setOnAction(e -> {
                     this.callClassByNameParameter(element.getAttribute("actionClass"), element.getAttribute("actionMethod"), (MenuItem)e.getSource());
                     if (((MenuItem)e.getSource()).isDisable()) {
                        menu.getItems().remove((MenuItem)e.getSource());
                     }
                  });
               }

               menu.getItems().add(this.lineItem);
               this.separatorFlag = false;
               this.mainItemFlag = true;
            }
      }
   }

   // Menu entries added by the improvements use plain text instead of a locale key
   private String menuText(String key) {
      String text = LocalResource.getString(key);
      return text != null && text.startsWith("!! ") ? key : text;
   }

   private void shortCutKeys(Element element, MenuItem item) {
      if (element.getAttribute("shortCutKey1").length() == 1 && this.convertToKeyCombination(element.getAttribute("shortCutKey2")) != null) {
         if (this.convertToKeyCombination(element.getAttribute("shortCutKey3")) != null) {
            item.setAccelerator(
               new KeyCodeCombination(
                  this.convertToKeyCode(element.getAttribute("shortCutKey1")),
                  new Modifier[]{
                     this.convertToKeyCombination(element.getAttribute("shortCutKey2")), this.convertToKeyCombination(element.getAttribute("shortCutKey3"))
                  }
               )
            );
         } else {
            item.setAccelerator(
               new KeyCodeCombination(
                  this.convertToKeyCode(element.getAttribute("shortCutKey1")),
                  new Modifier[]{this.convertToKeyCombination(element.getAttribute("shortCutKey2"))}
               )
            );
         }
      }
   }

   private KeyCode convertToKeyCode(String key) {
      for (KeyCode c : KeyCode.values()) {
         if (c.getName().equals(key.toUpperCase())) {
            return c;
         }
      }

      return null;
   }

   private Modifier convertToKeyCombination(String str) {
      if (str.equals("")) {
         return null;
      } else {
         switch (str) {
            case "ALT_ANY":
               return KeyCombination.ALT_ANY;
            case "ALT_DOWN":
               return KeyCombination.ALT_DOWN;
            case "CONTROL_ANY":
               return KeyCombination.CONTROL_ANY;
            case "CONTROL_DOWN":
               return KeyCombination.CONTROL_DOWN;
            case "META_ANY":
               return KeyCombination.META_ANY;
            case "META_DOWN":
               return KeyCombination.META_DOWN;
            case "SHIFT_ANY":
               return KeyCombination.SHIFT_ANY;
            case "SHIFT_DOWN":
               return KeyCombination.SHIFT_DOWN;
            default:
               return null;
         }
      }
   }

   public void callClassByName(String clsStr, String funcName) {
      if (funcName != null && clsStr != null) {
         try {
            Class cls = Class.forName(clsStr);
            Object obj = cls.getDeclaredConstructor().newInstance();
            Method method = cls.getDeclaredMethod(funcName);
            method.invoke(obj);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassNotFoundException var6) {
            Logger.getLogger(CreateMenu.class.getName()).log(Level.SEVERE, null, var6);
         } catch (InstantiationException var7) {
            Logger.getLogger(CreateMenu.class.getName()).log(Level.SEVERE, null, var7);
         }
      }
   }

   public void callClassByNameParameter(String clsStr, String funcName, String value) {
      if (funcName != null && clsStr != null) {
         try {
            Class cls = Class.forName(clsStr);
            Object obj = cls.getDeclaredConstructor().newInstance();
            Method method = cls.getDeclaredMethod(funcName, String.class);
            method.invoke(obj, value);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException | ClassNotFoundException var7) {
            Logger.getLogger(CreateMenu.class.getName()).log(Level.SEVERE, null, var7);
         }
      }
   }

   public void callClassByNameParameter(String clsStr, String funcName, MenuItem value) {
      if (funcName != null && clsStr != null) {
         try {
            Class cls = Class.forName(clsStr);
            Object obj = cls.getDeclaredConstructor().newInstance();
            Method method = cls.getDeclaredMethod(funcName, MenuItem.class);
            method.invoke(obj, value);
         } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException | ClassNotFoundException var7) {
            Logger.getLogger(CreateMenu.class.getName()).log(Level.SEVERE, null, var7);
         }
      }
   }

   private Boolean isAllowed(String classpermission, String role) {
      Connection con = ConnectionFactory.getInstance().getConnection();

      try {
         PreparedStatement stmt = con.prepareStatement("select * from rolepermissions where id = ? and class = ? ");
         stmt.setString(1, role);
         stmt.setString(2, classpermission);
         ResultSet rs = stmt.executeQuery();
         if (rs.next()) {
            return true;
         }
      } catch (SQLException var7) {
      }

      return false;
   }
}
