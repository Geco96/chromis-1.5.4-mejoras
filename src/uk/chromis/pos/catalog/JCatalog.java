package uk.chromis.pos.catalog;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import uk.chromis.basic.BasicException;
import uk.chromis.data.gui.JMessageDialog;
import uk.chromis.data.gui.MessageInf;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.globals.IconFactory;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.datalogic.DataLogicSales;
import uk.chromis.pos.datalogic.DataLogicSystem;
import uk.chromis.pos.forms.AppLocal;
import uk.chromis.pos.forms.AppUser;
import uk.chromis.pos.forms.ChromisFonts;
import uk.chromis.pos.sales.TaxesLogic;
import uk.chromis.pos.scripting.ScriptEngine;
import uk.chromis.pos.scripting.ScriptException;
import uk.chromis.pos.scripting.ScriptFactory;
import uk.chromis.pos.ticket.CategoryInfo;
import uk.chromis.pos.ticket.ProductInfoExt;
import uk.chromis.pos.ticket.TaxInfo;
import uk.chromis.pos.util.ThumbNailBuilder;

public class JCatalog extends JPanel implements ListSelectionListener, CatalogSelector {
   protected EventListenerList listeners = new EventListenerList();
   private DataLogicSales m_dlSales;
   private DataLogicSystem m_dlSystem;
   private TaxesLogic taxeslogic;
   private boolean pricevisible;
   private boolean taxesincluded;
   private final Map<String, ProductInfoExt> m_productsset = new HashMap<>();
   private final Set<String> m_categoriesset = new HashSet<>();
   private ThumbNailBuilder tnbbutton;
   private ThumbNailBuilder tnbcat;
   private ThumbNailBuilder tnbsubcat;
   private List<CategoryInfo> categories;
   private CategoryInfo showingcategory = null;
   private String siteGuid;
   private int categoriesPanelWidth;
   private JPanel jPanel1;
   private JPanel jPanel2;
   private JPanel jPanel3;
   private JPanel jPanel4;
   private JPanel jPanel5;
   private JButton m_btnBack1;
   private JPanel m_jCategories;
   private JButton m_jDown;
   private JList m_jListCategories;
   private JPanel m_jProducts;
   private JPanel m_jRootCategories;
   private JPanel m_jSubCategories;
   private JButton m_jUp;
   private JScrollPane m_jscrollcat;
   private JLabel m_lblIndicator;

   public JCatalog(DataLogicSales dlSales, String siteGuid) {
      this(dlSales, false, false, siteGuid);
   }

   public JCatalog(DataLogicSales dlSales, boolean pricevisible, boolean taxesincluded, String siteGuid) {
      this.m_dlSystem = new DataLogicSystem();
      this.m_dlSystem.init(SessionFactory.getSession());
      this.siteGuid = siteGuid;
      this.m_dlSales = dlSales;
      this.pricevisible = pricevisible;
      this.taxesincluded = taxesincluded;
      this.initComponents();
      this.m_jListCategories.addListSelectionListener(this);
      this.m_jscrollcat.getVerticalScrollBar().setPreferredSize(new Dimension(25, 25));
      if (SystemProperty.HISTORICPRODUCTICON) {
         this.tnbbutton = new ThumbNailBuilder(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT, IconFactory.getIcon("package.png"));
      } else {
         this.tnbbutton = new ThumbNailBuilder(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT, IconFactory.getIcon("newproduct.png"));
      }

      if (SystemProperty.HISTORICCATEGORYICON) {
         this.tnbcat = new ThumbNailBuilder(48, 48, IconFactory.getIcon("category.png"));
         this.tnbsubcat = new ThumbNailBuilder(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT, IconFactory.getIcon("subcategory.png"));
      } else {
         this.tnbcat = new ThumbNailBuilder(192, 48, IconFactory.getIcon("defaultcategory192.png"));
         this.tnbsubcat = new ThumbNailBuilder(
            SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT, IconFactory.getIcon("defaultcategory192.png")
         );
      }
   }

   @Override
   public Component getComponent() {
      return this;
   }

   @Override
   public void showCatalogPanel(String id) {
      if (id == null) {
         this.showRootCategoriesPanel();
      } else {
         this.showProductPanel(id);
      }
   }

   public Component getCatComponent() {
      return this.m_jCategories;
   }

   public Component getProductComponent() {
      return this.m_jProducts;
   }

   public boolean setControls(String position) {
      if (position.equals("south")) {
         this.m_jRootCategories.add(this.jPanel2, "South");
         this.m_jSubCategories.add(this.jPanel1, "South");
         ((GridLayout)this.jPanel3.getLayout()).setRows(1);
         ((GridLayout)this.jPanel5.getLayout()).setRows(1);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void loadCatalog(String siteGuid) throws BasicException {
      this.siteGuid = siteGuid;
      this.m_jProducts.removeAll();
      this.m_productsset.clear();
      this.m_categoriesset.clear();
      this.showingcategory = null;
      this.taxeslogic = new TaxesLogic(this.m_dlSales.getTaxList(siteGuid).list());
      List categories;
      if (SystemProperty.CATBYNUMBERORDER) {
         categories = this.m_dlSales.getRootCategoriesByCatOrder(siteGuid);
         categories.addAll(this.m_dlSales.getRootCategoriesByName(siteGuid));
      } else {
         categories = this.m_dlSales.getRootCategories(siteGuid);
      }

      if (SystemProperty.HISTORICCATEGORYICON) {
         this.m_jListCategories.setCellRenderer(new JCatalog.SmallCategoryRenderer());
      } else {
         this.m_jListCategories.setCellRenderer(new JCatalog.NewCategoryRenderer());
      }

      this.m_jListCategories.setModel(new JCatalog.CategoriesListModel(categories));
      if (this.m_jListCategories.getModel().getSize() == 0) {
         this.m_jscrollcat.setVisible(false);
         this.jPanel2.setVisible(false);
      } else {
         this.m_jscrollcat.setVisible(true);
         this.jPanel2.setVisible(true);
         this.m_jListCategories.setSelectedIndex(0);
      }

      this.showRootCategoriesPanel();
   }

   @Override
   public void setComponentEnabled(boolean value) {
      this.m_jListCategories.setEnabled(value);
      this.m_jscrollcat.setEnabled(value);
      this.m_jUp.setEnabled(value);
      this.m_jDown.setEnabled(value);
      this.m_lblIndicator.setEnabled(value);
      this.m_btnBack1.setEnabled(value);
      this.m_jProducts.setEnabled(value);
      synchronized (this.m_jProducts.getTreeLock()) {
         int compCount = this.m_jProducts.getComponentCount();

         for (int i = 0; i < compCount; i++) {
            this.m_jProducts.getComponent(i).setEnabled(value);
         }
      }

      this.setEnabled(value);
   }

   @Override
   public void addActionListener(ActionListener l) {
      this.listeners.add(ActionListener.class, l);
   }

   @Override
   public void removeActionListener(ActionListener l) {
      this.listeners.remove(ActionListener.class, l);
   }

   @Override
   public void valueChanged(ListSelectionEvent evt) {
      if (!evt.getValueIsAdjusting()) {
         int i = this.m_jListCategories.getSelectedIndex();
         if (i >= 0) {
            Rectangle oRect = this.m_jListCategories.getCellBounds(i, i);
            this.m_jListCategories.scrollRectToVisible(oRect);
         }
      }
   }

   protected void fireSelectedProduct(ProductInfoExt prod) {
      EventListener[] l = this.listeners.getListeners(ActionListener.class);
      ActionEvent e = null;

      for (EventListener l1 : l) {
         if (e == null) {
            e = new ActionEvent(prod, 1001, prod.getID());
         }

         ((ActionListener)l1).actionPerformed(e);
      }
   }

   private void selectCategoryPanel(String catid) {
      try {
         if (!this.m_categoriesset.contains(catid)) {
            JCatalogTab jcurrTab = new JCatalogTab();
            jcurrTab.applyComponentOrientation(this.getComponentOrientation());
            this.m_jProducts.add(jcurrTab, catid);
            this.m_categoriesset.add(catid);
            if (SystemProperty.CATBYNUMBERORDER) {
               this.categories = this.m_dlSales.getSubcategoriesByCatOrder(catid, this.siteGuid);
               this.categories.addAll(this.m_dlSales.getSubcategoriesByName(catid, this.siteGuid));
            } else {
               this.categories = this.m_dlSales.getSubcategories(catid, this.siteGuid);
            }

            for (CategoryInfo cat : this.categories) {
               if (SystemProperty.HISTORICCATEGORYICON) {
                  if (cat.getCatShowName()) {
                     jcurrTab.addButton(new ImageIcon(this.tnbsubcat.getThumbNailText(cat.getImage(), cat.getName())), new JCatalog.SelectedCategory(cat));
                  } else {
                     jcurrTab.addButton(new ImageIcon(this.tnbsubcat.getThumbNailText(cat.getImage(), "")), new JCatalog.SelectedCategory(cat));
                  }
               } else {
                  jcurrTab.addButton(new ImageIcon(this.tnbsubcat.getThumbNail(cat.getImage())), cat.getName(), new JCatalog.SelectedCategory(cat));
               }
            }

            // Auxiliary products are listed in the catalog like any other product
            for (ProductInfoExt prod : this.m_dlSales.getProductCatalog(catid, this.siteGuid)) {
               if (SystemProperty.HISTORICPRODUCTICON) {
                  jcurrTab.addButton(new ImageIcon(this.tnbbutton.getThumbNailText(prod.getImage(), this.getProductLabel(prod))), new JCatalog.MouseTimer(prod));
               } else if (prod.getImage() == null) {
                  jcurrTab.addButton(
                     IconFactory.getResizedIcon("newproduct.png", new Dimension(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT)),
                     this.getProductLabel(prod),
                     new JCatalog.MouseTimer(prod)
                  );
               } else {
                  jcurrTab.addButton(new ImageIcon(this.tnbbutton.getThumbNailText(prod.getImage(), this.getProductLabel(prod))), new JCatalog.MouseTimer(prod));
               }
            }
         }

         CardLayout cl = (CardLayout)this.m_jProducts.getLayout();
         cl.show(this.m_jProducts, catid);
      } catch (BasicException var6) {
         JMessageDialog.showMessage(this, new MessageInf(-33554432, AppLocal.getIntString("message.notactive"), var6));
      }
   }

   private String getProductLabel(ProductInfoExt product) {
      if (this.pricevisible) {
         if (SystemProperty.TAXINCLUDED) {
            TaxInfo tax = this.taxeslogic.getTaxInfo(product.getTaxCategoryID());
            return "<html><center>" + product.getName() + "<br>" + product.printPriceSellInc();
         } else {
            return "<html><center>" + product.getDisplay() + "<br>" + product.printPriceSell();
         }
      } else {
         return product.getDisplay();
      }
   }

   private void selectIndicatorPanel(Icon icon, String label) {
      if (SystemProperty.HISTORICCATEGORYICON) {
         this.m_lblIndicator.setText(label);
         this.m_lblIndicator.setIcon(icon);
      } else {
         this.m_lblIndicator.setText(label);
         this.m_lblIndicator.setIcon(IconFactory.getResizedIcon("defaultsubcategory192.png", new Dimension(195, 48)));
         this.m_lblIndicator.setHorizontalTextPosition(0);
         this.m_lblIndicator.setVerticalTextPosition(0);
         this.m_lblIndicator.setForeground(new Color(Integer.decode(SystemProperty.BUTTONTEXTCOLOUR)));
      }

      CardLayout cl = (CardLayout)this.m_jCategories.getLayout();
      cl.show(this.m_jCategories, "subcategories");
   }

   private void selectIndicatorCategories() {
      CardLayout cl = (CardLayout)this.m_jCategories.getLayout();
      cl.show(this.m_jCategories, "rootcategories");
   }

   private void showRootCategoriesPanel() {
      this.m_jCategories.setBorder(BorderFactory.createTitledBorder("Categories"));
      this.selectIndicatorCategories();
      CategoryInfo cat = (CategoryInfo)this.m_jListCategories.getSelectedValue();
      if (cat != null) {
         this.selectCategoryPanel(cat.getID());
      }

      this.showingcategory = null;
   }

   private void showSubcategoryPanel(CategoryInfo category) {
      this.m_jCategories.setBorder(BorderFactory.createTitledBorder("Sub Category"));
      this.selectIndicatorPanel(this.tnbsubcat.getThumbNailIcon(category.getImage()), category.getName());
      this.selectCategoryPanel(category.getID());
      this.showingcategory = category;
   }

   private void showProductPanel(String id) {
      ProductInfoExt product = this.m_productsset.get(id);
      if (product == null) {
         if (this.m_productsset.containsKey(id)) {
            if (this.showingcategory == null) {
               this.showRootCategoriesPanel();
            } else {
               this.showSubcategoryPanel(this.showingcategory);
            }
         } else {
            try {
               List<ProductInfoExt> products = this.m_dlSales.getProductComments(id, this.siteGuid);
               if (products.isEmpty()) {
                  this.m_productsset.put(id, null);
                  if (this.showingcategory == null) {
                     this.showRootCategoriesPanel();
                  } else {
                     this.showSubcategoryPanel(this.showingcategory);
                  }
               } else {
                  product = this.m_dlSales.getProductInfo(id);
                  this.m_productsset.put(id, product);
                  JCatalogTab jcurrTab = new JCatalogTab();
                  jcurrTab.applyComponentOrientation(this.getComponentOrientation());
                  this.m_jProducts.add(jcurrTab, "PRODUCT." + id);

                  for (ProductInfoExt prod : products) {
                     jcurrTab.addButton(
                        new ImageIcon(this.tnbbutton.getThumbNailText(prod.getImage(), this.getProductLabel(prod))), new JCatalog.MouseTimer(prod)
                     );
                  }

                  this.selectIndicatorPanel(new ImageIcon(this.tnbbutton.getThumbNail(product.getImage())), product.getDisplay());
                  CardLayout cl = (CardLayout)this.m_jProducts.getLayout();
                  cl.show(this.m_jProducts, "PRODUCT." + id);
               }
            } catch (BasicException var7) {
               this.m_productsset.put(id, null);
               if (this.showingcategory == null) {
                  this.showRootCategoriesPanel();
               } else {
                  this.showSubcategoryPanel(this.showingcategory);
               }
            }
         }
      } else {
         this.selectIndicatorPanel(new ImageIcon(this.tnbbutton.getThumbNail(product.getImage())), product.getName());
         CardLayout cl = (CardLayout)this.m_jProducts.getLayout();
         cl.show(this.m_jProducts, "PRODUCT." + id);
      }
   }

   private Object evalScript(String code, ProductInfoExt prod) throws ScriptException {
      ScriptEngine script = ScriptFactory.getScriptEngine("beanshell");
      script.put("product", prod);
      script.put("dlSales", this.m_dlSales);
      script.put("dlSystem", this.m_dlSystem);
      return script.eval(this.m_dlSystem.getResourceAsXML(code));
   }

   private void initComponents() {
      this.m_jCategories = new JPanel();
      this.m_jRootCategories = new JPanel();
      this.m_jscrollcat = new JScrollPane();
      this.m_jListCategories = new JList();
      this.jPanel2 = new JPanel();
      this.jPanel3 = new JPanel();
      this.m_jUp = new JButton();
      this.m_jDown = new JButton();
      this.m_jSubCategories = new JPanel();
      this.jPanel4 = new JPanel();
      this.m_lblIndicator = new JLabel();
      this.jPanel1 = new JPanel();
      this.jPanel5 = new JPanel();
      this.m_btnBack1 = new JButton();
      this.m_jProducts = new JPanel();
      this.setLayout(new BorderLayout());
      this.m_jCategories.setBorder(BorderFactory.createTitledBorder(null, "Categories", 1, 0, new Font("Liberation Serif", 1, 14)));
      this.m_jCategories.setFont(ChromisFonts.DEFAULTFONTBOLD);
      this.m_jCategories.setMaximumSize(new Dimension(275, 600));
      this.m_jCategories.setPreferredSize(new Dimension(275, 600));
      this.m_jCategories.setLayout(new CardLayout());
      this.m_jRootCategories.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.m_jRootCategories.setMinimumSize(new Dimension(200, 100));
      this.m_jRootCategories.setPreferredSize(new Dimension(400, 130));
      this.m_jRootCategories.setLayout(new BorderLayout());
      this.m_jscrollcat.setHorizontalScrollBarPolicy(31);
      this.m_jscrollcat.setVerticalScrollBarPolicy(22);
      this.m_jscrollcat.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.m_jListCategories.setFont(ChromisFonts.DEFAULTFONT.deriveFont(14.0F));
      this.m_jListCategories.setSelectionMode(0);
      this.m_jListCategories.setFocusable(false);
      this.m_jListCategories.addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent evt) {
            JCatalog.this.m_jListCategoriesValueChanged(evt);
         }
      });
      this.m_jscrollcat.setViewportView(this.m_jListCategories);
      this.m_jRootCategories.add(this.m_jscrollcat, "Center");
      this.jPanel2.setLayout(new BorderLayout());
      this.jPanel3.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
      this.jPanel3.setLayout(new GridLayout(0, 1, 0, 5));
      this.m_jUp.setIcon(IconFactory.getIcon("1uparrow.png"));
      this.m_jUp.setFocusPainted(false);
      this.m_jUp.setFocusable(false);
      this.m_jUp.setMargin(new Insets(8, 14, 8, 14));
      this.m_jUp.setRequestFocusEnabled(false);
      this.m_jUp.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JCatalog.this.m_jUpActionPerformed(evt);
         }
      });
      this.jPanel3.add(this.m_jUp);
      this.m_jDown.setIcon(IconFactory.getIcon("1downarrow.png"));
      this.m_jDown.setFocusPainted(false);
      this.m_jDown.setFocusable(false);
      this.m_jDown.setMargin(new Insets(8, 14, 8, 14));
      this.m_jDown.setRequestFocusEnabled(false);
      this.m_jDown.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JCatalog.this.m_jDownActionPerformed(evt);
         }
      });
      this.jPanel3.add(this.m_jDown);
      this.jPanel2.add(this.jPanel3, "North");
      this.m_jRootCategories.add(this.jPanel2, "After");
      this.m_jCategories.add(this.m_jRootCategories, "rootcategories");
      this.m_jSubCategories.setFont(ChromisFonts.DEFAULTFONT.deriveFont(12.0F));
      this.m_jSubCategories.setLayout(new BorderLayout());
      this.jPanel4.setLayout(new BorderLayout());
      this.m_lblIndicator.setText("jLabel_John");
      this.jPanel4.add(this.m_lblIndicator, "North");
      this.m_jSubCategories.add(this.jPanel4, "Center");
      this.jPanel1.setLayout(new BorderLayout());
      this.jPanel5.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
      this.jPanel5.setLayout(new GridLayout(0, 1, 0, 5));
      this.m_btnBack1.setIcon(IconFactory.getIcon("2uparrow.png"));
      this.m_btnBack1.setFocusPainted(false);
      this.m_btnBack1.setFocusable(false);
      this.m_btnBack1.setMargin(new Insets(8, 14, 8, 14));
      this.m_btnBack1.setRequestFocusEnabled(false);
      this.m_btnBack1.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            JCatalog.this.m_btnBack1ActionPerformed(evt);
         }
      });
      this.jPanel5.add(this.m_btnBack1);
      this.jPanel1.add(this.jPanel5, "North");
      this.m_jSubCategories.add(this.jPanel1, "After");
      this.m_jCategories.add(this.m_jSubCategories, "subcategories");
      this.add(this.m_jCategories, "Before");
      this.m_jProducts.setBorder(BorderFactory.createTitledBorder(null, "Products", 0, 0, new Font("Liberation Serif", 1, 14)));
      this.m_jProducts.setFont(ChromisFonts.DEFAULTFONTBOLD.deriveFont(18.0F));
      this.m_jProducts.setLayout(new CardLayout());
      this.add(this.m_jProducts, "Center");
   }

   private void m_jDownActionPerformed(ActionEvent evt) {
      int i = this.m_jListCategories.getSelectionModel().getMaxSelectionIndex();
      if (i < 0) {
         i = 0;
      } else if (++i >= this.m_jListCategories.getModel().getSize()) {
         i = this.m_jListCategories.getModel().getSize() - 1;
      }

      if (i >= 0 && i < this.m_jListCategories.getModel().getSize()) {
         this.m_jListCategories.getSelectionModel().setSelectionInterval(i, i);
      }
   }

   private void m_jUpActionPerformed(ActionEvent evt) {
      int i = this.m_jListCategories.getSelectionModel().getMinSelectionIndex();
      if (i < 0) {
         i = this.m_jListCategories.getModel().getSize() - 1;
      } else if (--i < 0) {
         i = 0;
      }

      if (i >= 0 && i < this.m_jListCategories.getModel().getSize()) {
         this.m_jListCategories.getSelectionModel().setSelectionInterval(i, i);
      }
   }

   private void m_jListCategoriesValueChanged(ListSelectionEvent evt) {
      if (!evt.getValueIsAdjusting()) {
         CategoryInfo cat = (CategoryInfo)this.m_jListCategories.getSelectedValue();
         if (cat != null) {
            this.selectCategoryPanel(cat.getID());
         }
      }
   }

   private void m_btnBack1ActionPerformed(ActionEvent evt) {
      this.showRootCategoriesPanel();
   }

   private class CategoriesListModel extends AbstractListModel {
      private final List m_aCategories;

      public CategoriesListModel(List aCategories) {
         this.m_aCategories = aCategories;
      }

      @Override
      public int getSize() {
         return this.m_aCategories.size();
      }

      @Override
      public Object getElementAt(int i) {
         return this.m_aCategories.get(i);
      }
   }

   private class MouseTimer extends MouseAdapter {
      private final ProductInfoExt prod;
      private long start;

      public MouseTimer(ProductInfoExt prod) {
         this.prod = prod;
      }

      @Override
      public void mousePressed(MouseEvent e) {
         this.start = System.currentTimeMillis();
         ((JButton)e.getSource()).setPressedIcon(IconFactory.getIcon("empty.png"));
      }

      @Override
      public void mouseReleased(MouseEvent e) {
         long duration = (System.currentTimeMillis() - this.start) / 1000L;
         if (duration < 1L) {
            JCatalog.this.fireSelectedProduct(this.prod);
         } else {
            if (!SystemProperty.PRODUCTSCRIPT || !AppUser.hasPermission("access.productheld")) {
               JCatalog.this.fireSelectedProduct(this.prod);
               return;
            }

            try {
               JCatalog.this.evalScript("script.productheld", this.prod);
            } catch (ScriptException var5) {
               System.out.println("Not found");
            }
         }
      }
   }

   private class NewCategoryRenderer extends DefaultListCellRenderer {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
         super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
         JCatalog.this.categoriesPanelWidth = JCatalog.this.m_jscrollcat.getWidth() - 30;
         CategoryInfo cat = (CategoryInfo)value;
         this.setHorizontalTextPosition(0);
         this.setVerticalTextPosition(0);
         this.setText(cat.getCatShowName() ? cat.getButtonText() : "");
         this.setForeground(new Color(Integer.decode(SystemProperty.BUTTONTEXTCOLOUR)));
         this.setIcon(
            IconFactory.resizeIcon(new ImageIcon(JCatalog.this.tnbcat.getThumbNail(cat.getImage())), new Dimension(JCatalog.this.categoriesPanelWidth, 48))
         );
         this.setSize(JCatalog.this.categoriesPanelWidth, 48);
         this.setFont(this.getFont().deriveFont(1, 16.0F));
         return this;
      }
   }

   private class SelectedAction implements ActionListener {
      private final ProductInfoExt prod;

      public SelectedAction(ProductInfoExt prod) {
         this.prod = prod;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         JCatalog.this.fireSelectedProduct(this.prod);
      }
   }

   private class SelectedCategory implements ActionListener {
      private final CategoryInfo category;

      public SelectedCategory(CategoryInfo category) {
         this.category = category;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         JCatalog.this.showSubcategoryPanel(this.category);
      }
   }

   private class SmallCategoryRenderer extends DefaultListCellRenderer {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
         super.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
         CategoryInfo cat = (CategoryInfo)value;
         this.setText(cat.getCatShowName() ? cat.getButtonText() : "");
         this.setIcon(new ImageIcon(JCatalog.this.tnbcat.getThumbNail(cat.getImage())));
         return this;
      }
   }
}
