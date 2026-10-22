package uk.chromis.administration.products;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.geometry.Side;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.cell.PropertyValueFactory;
import uk.chromis.pos.settings.PosSettings;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.apache.commons.lang.StringUtils;
import uk.chromis.administration.base.BasePanelController;
import uk.chromis.administration.base.FieldChecks;
import uk.chromis.administration.dialogs.Dialogs;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.recipes.RecipeLineInfo;
import uk.chromis.administration.suppliers.SupplierDataLogic;
import uk.chromis.administration.tax.TaxDataLogic;
import uk.chromis.basic.BasicException;
import uk.chromis.custom.controls.ExtendedComboBox;
import uk.chromis.custom.controls.ImageEditor;
import uk.chromis.custom.toggleswitch.ToggleSwitch;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.costpricing.CostPricingStore;
import uk.chromis.format.Formats;
import uk.chromis.globals.IconFactory;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.forms.LocalResource;
import uk.chromis.pos.util.BarcodeValidator;
import uk.chromis.pos.util.ThumbNailBuilder;

public class ProductController extends BasePanelController implements Initializable {
   public AnchorPane apAnchor;
   public Tab openingTab;
   public Tab tabStockLevels;
   public FlowPane btnFlow;
   public TabPane tpDataPane;
   public Button btnAddPLU;
   public Button btnAddSupplier;
   public Button btnAddStockLevel;
   public Button btnClear1;
   public Button btnClear2;
   public Button btnClear3;
   public Button btnClear4;
   public Button btnClear5;
   public ExtendedComboBox cmbAttributes;
   public ExtendedComboBox cmbBuyUOM;
   public ExtendedComboBox cmbStockUOM;
   public ExtendedComboBox cmbSupplierUOM;
   public ExtendedComboBox cmbSuppliers;
   public ExtendedComboBox cmbUnitsOfMeasure;
   public ExtendedComboBox cmbRecipeItem;
   public ExtendedComboBox cmbRemotePrinter;
   public ExtendedComboBox cmbLocations;
   public ExtendedComboBox cmbSellingUOM;
   public ExtendedComboBox cmbTaxCategory;
   public ExtendedComboBox cmbCategory;
   public ExtendedComboBox cmbBuyTaxCategory;
   public Label lblAge;
   public Label lblButton;
   public Label lblCatalog;
   public Label lblMax;
   public Label lblPanelName;
   public Label lblPercent;
   public Label lblSeletedTitle;
   public Label buyUOMValue;
   public ToggleSwitch tswtAgeWarning;
   public ToggleSwitch tswtIngredient;
   public ToggleSwitch tswtAlwaysAvailable;
   public ToggleSwitch tswtAuxiliary;
   public ToggleSwitch tswtBoldL1;
   public ToggleSwitch tswtBoldL2;
   public ToggleSwitch tswtButtonImage;
   public ToggleSwitch tswtCanDiscount;
   public ToggleSwitch tswtCostPricing;
   public ToggleSwitch tswtInCatalog;
   public ToggleSwitch tswtIsScale;
   public ToggleSwitch tswtIsServiceItem;
   public ToggleSwitch tswtItalicL1;
   public ToggleSwitch tswtItalicL2;
   public ToggleSwitch tswtManageStockLevel;
   public ToggleSwitch tswtMandatoryAttributes;
   public ToggleSwitch tswtVariablePrice;
   public ToggleSwitch tswtWarrantyReceipt;
   public ToggleSwitch tswtRetireProduct;
   public ToggleSwitch tswtPrinter;
   public ToggleSwitch tswtScreen;
   public ToggleSwitch tswtCanHaveAuxiliary;
   public Pane paneButtons;
   public Pane paneCollectX;
   public Pane paneEarnX;
   public Pane paneImageBox;
   public Pane paneLoyalty;
   public Spinner discountSpinner;
   public Spinner spinRemoteScreen;
   public RadioButton rbLine1L1;
   public RadioButton rbLine1L2;
   public RadioButton rbLine1N;
   public RadioButton rbLine1S1;
   public RadioButton rbLine1S2;
   public RadioButton rbLine2L1;
   public RadioButton rbLine2L2;
   public RadioButton rbLine2N;
   public RadioButton rbLine2S1;
   public RadioButton rbLine2S2;
   public TableView barcodeTable;
   public TableView supplierTable;
   public TableView recipeTable;
   public TableView<ProductTableInfo> mainTable = new TableView();
   public TableView<StockLevelInfo> stockLevelTable = new TableView();
   public TextArea txtProperties;
   public TextField txtSellPriceInc;
   public TextField txtSellPrice;
   public TextField txtBuyPrice;
   public TextField txtGrossMargin;
   public TextField txtGrossProfit;
   public TextField txtCommission;
   public TextField txtAddBarcode;
   public TextField txtAge;
   public TextField txtBarcode;
   public Button btnGenerateCode;
   public TextField txtCatalogOrder;
   public TextField txtBurnValue;
   public TextField txtDescription;
   public TextField txtDisplayLine1;
   public TextField txtDisplayLine2;
   public TextField txtEarnValue;
   public TextField txtLoyaltyMultiplier;
   public TextField txtReference;
   public TextField txtShortName;
   public TextField txtSupplierStockCode;
   public TextField txtTierDiscount1;
   public TextField txtTierDiscount2;
   public TextField txtTierDiscount3;
   public TextField txtTierDiscount4;
   public TextField txtTierDiscount5;
   public TextField txtSellingQty;
   public TextField txtTierPrice1;
   public TextField txtTierPrice2;
   public TextField txtTierPrice3;
   public TextField txtTierPrice4;
   public TextField txtTierPrice5;
   public TextField txtTierPriceInc1;
   public TextField txtTierPriceInc2;
   public TextField txtTierPriceInc3;
   public TextField txtTierPriceInc4;
   public TextField txtTierPriceInc5;
   public TextField txtTierQty1;
   public TextField txtTierQty2;
   public TextField txtTierQty3;
   public TextField txtTierQty4;
   public TextField txtTierQty5;
   public TextField txtMinimum;
   public TextField txtMaximum;
   public TextField txtKitchenDescription;
   public TextField txtPackCost;
   public TableColumn reference;
   public TableColumn barcodeProdTable;
   public TableColumn name;
   public TableColumn category;
   public TableColumn sellIncTax;
   public TableColumn sellPrice;
   public TableColumn buyPrice;
   public TableColumn isIngredient;
   public TableColumn isAuxiliary;
   public TableColumn supplierName;
   public TableColumn supplierCode;
   public TableColumn uomfactor;
   public TableColumn uomdescription;
   public TableColumn packcost;
   public TableColumn barcode;
   public TableColumn sellQty;
   public TableColumn uomdescriptionBcTable;
   public TableColumn location;
   public TableColumn current;
   public TableColumn minimum;
   public TableColumn maximum;
   private ProductTableInfo productInfo;
   private Properties props;
   private List<BarcodeInfo> barcodes;
   private List<ProductSupplierInfo> productSuppliers;
   private List<StockLevelInfo> stocking;
   private List<RecipeLineInfo> recipeLines;
   private final ProductDataLogic productDataLogic = new ProductDataLogic();
   private final TaxDataLogic taxDataLogic = new TaxDataLogic();
   private final SupplierDataLogic supplierDataLogic = new SupplierDataLogic();
   private ProductInfo selectedProduct = null;
   private Boolean systemobject = false;
   private Boolean showImage = false;
   private Boolean APIcall = false;
   private LinkedHashMap<String, Integer> displayMap;
   private ToggleGroup line1Font;
   private ToggleGroup line2Font;
   private List<TierPricing> prices;
   private HashMap<String, String> entries;
   private List<FieldChecks> fields;
   private ObservableList<ProductTableInfo> productData = FXCollections.observableArrayList();
   private ObservableList<BarcodeInfo> barcodeData = FXCollections.observableArrayList();
   private ObservableList<ProductSupplierInfo> supplierData = FXCollections.observableArrayList();
   private ObservableList<StockLevelInfo> stockLevelsData = FXCollections.observableArrayList();
   private ObservableList<ProductTableInfo> search = FXCollections.observableArrayList();
   private FilteredList<ProductTableInfo> filteredList;
   private SortedList<ProductTableInfo> sortedData;

   @Override
   public void reloadData() {
      this.productData = FXCollections.observableArrayList(new ArrayList(this.productDataLogic.getProductsForTableView(this.localGuid)));
      this.loadComboValues();
      dirty.set(false);
      this.clearSearchResults();
   }

   public void initialize(URL location, ResourceBundle resources) {
      this.apAnchor.prefWidthProperty().bind(Start.INTERNALANCHOR.widthProperty());
      this.apAnchor.prefHeightProperty().bind(Start.INTERNALANCHOR.heightProperty().subtract(25));
      this.apAnchor.getChildren().add(this.actionButtons);
      this.actionButtons.addButtonListener(this.mainTable, this);
      this.imageEd.setLayoutX(655.0);
      this.imageEd.setLayoutY(3.0);
      this.imageEd.setMaxHeight(275.0);
      this.imageEd.setMaxWidth(262.0);
      this.paneImageBox.getChildren().add(this.imageEd);
      ImageEditor.dirty.addListener((observable, oldValue, newValue) -> {
         if (ImageEditor.dirty.getValue()) {
            dirty.setValue(true);
            ImageEditor.dirty.setValue(false);
         }
      });
      this.txtMinimum.setText(this.localeString(Double.valueOf(0.0)));
      this.txtMaximum.setText(this.localeString(Double.valueOf(0.0)));
      this.buyUOMValue.setText(this.localeString(Double.valueOf(1.0)));
      this.txtPackCost.setText(this.localeString(Double.valueOf(1.0)));
      this.txtSellingQty.setText(this.localeString(Double.valueOf(1.0)));
      this.lblPanelName.setText("");
      this.entries = new HashMap<>();
      this.entries.put("reference", "Reference already exists \n");
      this.entries.put("code", "Barcode already exists \n");
      this.entries.put("name", "Description already exists \n");
      this.fields = new ArrayList<>();
      this.fields.add(new FieldChecks(this.txtReference, "No 'Reference' data entered. \n"));
      this.fields.add(new FieldChecks(this.txtBarcode, "No 'Barcode' data entered. \n"));
      this.fields.add(new FieldChecks(this.txtDescription, "No 'Ticket Description' data entered. \n"));
      this.fields.add(new FieldChecks(this.cmbCategory, "No 'Category' selected. \n"));
      this.fields.add(new FieldChecks(this.cmbTaxCategory, "No 'Tax Class' selected. \n"));
      this.fields.add(new FieldChecks(this.txtSellPrice, "No 'Selling price' entered. \n"));
      this.fields.add(new FieldChecks(this.txtBuyPrice, "No 'Buy Price' entered. \n"));
      this.loadComboValues();
      this.cmbUnitsOfMeasure.setSelectedItem("000-each");
      this.cmbStockUOM.setSelectedItem("000-each");
      this.cmbSellingUOM.setSelectedItem("000-each");
      this.cmbBuyUOM.setSelectedItem("000-each");
      this.cmbSupplierUOM.setSelectedItem("000-each");
      this.paneLoyalty.setVisible(SystemProperty.LOYALTYENABLED);
      if (SystemProperty.LOYALTYTYPE.equals("earnx")) {
         this.paneCollectX.setVisible(true);
         this.paneCollectX.setLayoutX(0.0);
         this.paneCollectX.setLayoutY(25.0);
         this.paneEarnX.setVisible(false);
      } else {
         this.paneCollectX.setVisible(false);
         this.paneEarnX.setVisible(true);
      }

      this.setComboListener(this.cmbAttributes);
      this.setComboListener(this.cmbStockUOM);
      this.setComboListener(this.cmbRemotePrinter);
      this.setComboListener(this.cmbBuyTaxCategory);
      this.cmbBuyUOM.getSelectionModel().selectedItemProperty().addListener((o, ol, nw) -> {
         this.buyUOMValue.setText(String.valueOf(this.productDataLogic.getSuppliedUOMQty(this.cmbBuyUOM.getSelectedKey(), this.localGuid)));
         dirty.setValue(true);
      });
      this.cmbCategory.getSelectionModel().selectedItemProperty().addListener((o, ol, nw) -> {
         this.setPriceState();
         dirty.setValue(true);
      });
      this.setToggleListener(this.tswtIsServiceItem);
      this.setToggleListener(this.tswtAlwaysAvailable);
      this.setToggleListener(this.tswtAuxiliary);
      this.setToggleListener(this.tswtCanHaveAuxiliary);
      this.setToggleListener(this.tswtIsScale);
      this.setToggleListener(this.tswtVariablePrice);
      this.setToggleListener(this.tswtRetireProduct);
      this.setToggleListener(this.tswtWarrantyReceipt);
      this.setToggleListener(this.tswtCostPricing);
      this.setToggleListener(this.tswtInCatalog);
      this.setToggleListener(this.tswtAgeWarning);
      this.setToggleListener(this.tswtIngredient);
      this.setToggleListener(this.tswtPrinter);
      this.setToggleListener(this.tswtScreen);
      this.tabStockLevels.disableProperty().bind(this.tswtManageStockLevel.selectedProperty().not());
      this.btnAddStockLevel.disableProperty().bind(this.tswtManageStockLevel.selectedProperty().not());
      this.tswtManageStockLevel.selectedProperty().addListener((observable, oldValue, newValue) -> {
         this.stockLevelsData.clear();
         dirty.set(true);
      });
      this.enableNumericOnly(this.txtCatalogOrder);
      this.enableNumericOnly(this.txtAge);
      this.enableNumericWithDecimal(this.txtCommission);
      this.enableNumericOnly(this.txtEarnValue);
      this.enableNumericOnly(this.txtBurnValue);
      this.enableNumericWithDecimal(this.txtLoyaltyMultiplier);
      this.enableNumericWithDecimalNoListener(this.txtPackCost);
      this.enableNumericWithDecimalNoListener(this.txtMinimum);
      this.enableNumericWithDecimalNoListener(this.txtMaximum);
      this.enableNumericWithDecimalNoListener(this.txtSellingQty);
      this.setTextTrimListener(this.txtReference);
      this.setTextTrimListener(this.txtBarcode);
      this.btnGenerateCode.disableProperty().bind(this.txtBarcode.disableProperty().or(this.txtBarcode.editableProperty().not()));
      this.buildCodeMenu();
      this.setTextTrimListener(this.txtDescription);
      this.setTextListener(this.txtShortName);
      this.setTextListener(this.txtCatalogOrder);
      this.setTextListener(this.txtKitchenDescription);
      this.txtCatalogOrder.disableProperty().bind(this.tswtInCatalog.selectedProperty().not());
      this.lblCatalog.disableProperty().bind(this.tswtInCatalog.selectedProperty().not());
      this.discountSpinner.disableProperty().bind(this.tswtCanDiscount.selectedProperty().not());
      this.lblMax.disableProperty().bind(this.tswtCanDiscount.selectedProperty().not());
      this.lblPercent.disableProperty().bind(this.tswtCanDiscount.selectedProperty().not());
      this.tswtAgeWarning.selectedProperty().addListener((observable, oldValue, newValue) -> {
         if (newValue) {
            this.txtAge.setDisable(false);
            this.lblAge.setDisable(false);
         } else {
            this.txtAge.setText("");
            this.txtAge.setDisable(true);
            this.lblAge.setDisable(true);
         }

         dirty.set(true);
      });
      SpinnerValueFactory<Double> valueFactoryPickup = new DoubleSpinnerValueFactory(0.0, 99.0, 10.0, 0.5);
      this.discountSpinner.setValueFactory(valueFactoryPickup);
      this.tswtCanDiscount.selectedProperty().addListener((observable, oldValue, newValue) -> {
         if (newValue) {
            this.discountSpinner.getEditor().setText(this.localeString("10"));
         } else {
            this.discountSpinner.getEditor().setText("");
         }

         dirty.set(true);
      });
      this.discountSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
         if (newValue == null) {
            this.discountSpinner.getValueFactory().setValue(this.parseLocaleString("10.0"));
         }

         dirty.setValue(true);
      });
      this.createPriceListeners();
      this.createTierListeners(this.txtTierQty1, this.txtTierPrice1, this.txtTierPriceInc1, this.txtTierDiscount1);
      this.createTierListeners(this.txtTierQty2, this.txtTierPrice2, this.txtTierPriceInc2, this.txtTierDiscount2);
      this.createTierListeners(this.txtTierQty3, this.txtTierPrice3, this.txtTierPriceInc3, this.txtTierDiscount3);
      this.createTierListeners(this.txtTierQty4, this.txtTierPrice4, this.txtTierPriceInc4, this.txtTierDiscount4);
      this.createTierListeners(this.txtTierQty5, this.txtTierPrice5, this.txtTierPriceInc5, this.txtTierDiscount5);
      this.line1Font = new ToggleGroup();
      this.rbLine1S2.setToggleGroup(this.line1Font);
      this.rbLine1S1.setToggleGroup(this.line1Font);
      this.rbLine1N.setToggleGroup(this.line1Font);
      this.rbLine1L1.setToggleGroup(this.line1Font);
      this.rbLine1L2.setToggleGroup(this.line1Font);
      this.rbLine1N.setSelected(true);
      this.line2Font = new ToggleGroup();
      this.rbLine2S2.setToggleGroup(this.line2Font);
      this.rbLine2S1.setToggleGroup(this.line2Font);
      this.rbLine2N.setToggleGroup(this.line2Font);
      this.rbLine2L1.setToggleGroup(this.line2Font);
      this.rbLine2L2.setToggleGroup(this.line2Font);
      this.rbLine2N.setSelected(true);
      IntegerSpinnerValueFactory screenFactory = new IntegerSpinnerValueFactory(1, 20, 0);
      this.spinRemoteScreen.setValueFactory(screenFactory);
      this.spinRemoteScreen.valueProperty().addListener((obs, oldValue, newValue) -> dirty.setValue(true));
      this.spinRemoteScreen.disableProperty().bind(this.tswtScreen.selectedProperty().not());
      this.cmbRemotePrinter.disableProperty().bind(this.tswtPrinter.selectedProperty().not());
      this.buildProductTable();
      this.buildBarcodeTable();
      this.buildSupplierTable();
      this.createSwingButton();
      this.updateButton();
      this.buildStockLevelsTable();
      this.populateProductTable();
      this.addDirtyListener();
      dirty.setValue(false);
   }

   private void setPriceState() {
      Boolean disable = false;
      if (this.cmbCategory.getSelectedKey() != null) {
         disable = this.cmbCategory.getSelectedKey().equalsIgnoreCase("giftcards");
      }

      this.txtSellPriceInc.setDisable(disable);
      this.txtSellPrice.setDisable(disable);
      this.txtBuyPrice.setDisable(disable);
      this.txtGrossMargin.setDisable(disable);
      this.txtGrossProfit.setDisable(disable);
      this.txtCommission.setDisable(disable);
      this.tswtCanDiscount.setDisable(disable);
      this.tswtIngredient.setDisable(disable);
      this.tswtVariablePrice.setDisable(disable);
      this.tswtAuxiliary.setDisable(disable);
      this.tswtIsServiceItem.setDisable(disable);
      this.tswtIsScale.setDisable(disable);
      this.tswtAlwaysAvailable.setDisable(disable);
      this.tswtManageStockLevel.setDisable(disable);
      this.tswtCanHaveAuxiliary.setDisable(disable);
      this.tswtWarrantyReceipt.setDisable(disable);
   }

   private Double calculateGrossProfit() {
      return (this.txtSellPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtSellPrice.getText()))
         - (this.txtBuyPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtBuyPrice.getText()));
   }

   public void actionClearPricing(ActionEvent evt) {
      String var2 = ((Button)evt.getSource()).getId();
      switch (var2) {
         case "btnClear1":
            this.resetPricingPanel(5);
            break;
         case "btnClear2":
            this.resetPricingPanel(4);
            break;
         case "btnClear3":
            this.resetPricingPanel(3);
            break;
         case "btnClear4":
            this.resetPricingPanel(2);
            break;
         case "btnClear5":
            this.resetPricingPanel(1);
      }
   }

   public void addBarcodeTableRow() {
      Boolean barcodeInUse = this.txtAddBarcode.getText().equals(this.txtBarcode.getText());
      ObservableList<BarcodeInfo> barcodeData = this.barcodeTable.getItems();

      for (BarcodeInfo b : barcodeData) {
         if (b.getBarcode().equals(this.txtAddBarcode.getText())) {
            barcodeInUse = true;
         }
      }

      if (!this.txtAddBarcode.getText().isBlank()) {
         try {
            if (this.productDataLogic.barcodeInUse(this.txtAddBarcode.getText())) {
               barcodeInUse = true;
            }
         } catch (BasicException var5) {
            Logger.getLogger(ProductController.class.getName()).log(Level.SEVERE, null, var5);
         }

         if (barcodeInUse) {
            Dialogs.showDialog(8, LocalResource.getString("dialog.barcodeFailedContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
            this.cmbUnitsOfMeasure.getSelectionModel().selectFirst();
            this.txtSellingQty.setText(this.localeString(Double.valueOf(1.0)));
            this.txtAddBarcode.setText("");
         } else {
            barcodeData.add(
               new BarcodeInfo(
                  UUID.randomUUID().toString(),
                  this.productInfo.getId(),
                  this.txtAddBarcode.getText(),
                  this.parseLocaleString(this.txtSellingQty.getText()),
                  this.cmbUnitsOfMeasure.getSelectedKey(),
                  this.productDataLogic.getUomFactor(this.cmbUnitsOfMeasure.getSelectedKey()),
                  this.productDataLogic.getUOMDescription(this.cmbUnitsOfMeasure.getSelectedKey())
               )
            );
            this.cmbUnitsOfMeasure.getSelectionModel().clearSelection();
            this.cmbUnitsOfMeasure.setSelectedItem("000-each");
            this.txtSellingQty.setText(this.localeString(Double.valueOf(1.0)));
            this.txtAddBarcode.setText("");
            dirty.setValue(true);
         }
      }
   }

   public void addProductTableRow(ProductInfo p) {
      this.productData
         .add(
            new ProductTableInfo(
               p.getId(),
               p.getReference(),
               p.getCode(),
               p.getName(),
               p.getAlias(),
               this.cmbCategory.getDescription(p.getCategory()),
               this.localeString(p.getPriceSell()),
               String.format(currentLocale, "%.3f", p.getPriceBuy()),
               this.localeString(p.getPriceSellInc()),
               p.isIngredient(),
               p.isCom()
            )
         );
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.productsTitle") + " :- " + this.mainTable.getItems().size() + " records");
   }

   public void addSupplierTableRow() {
      this.supplierData
         .add(
            new ProductSupplierInfo(
               UUID.randomUUID().toString(),
               this.productInfo.getId(),
               this.cmbSuppliers.getSelectedKey(),
               this.cmbSuppliers.getItem(),
               this.txtSupplierStockCode.getText(),
               this.cmbSupplierUOM.getSelectedKey(),
               this.productDataLogic.getUomFactor(this.cmbSupplierUOM.getSelectedKey()),
               this.productDataLogic.getUOMDescription(this.cmbSupplierUOM.getSelectedKey()),
               this.parseLocaleString(this.txtPackCost.getText()),
               this.taxDataLogic.getTaxRate(this.cmbTaxCategory.getSelectedKey())
            )
         );
      this.cmbSuppliers.getSelectionModel().clearSelection();
      this.cmbSupplierUOM.setSelectedItem("000-each");
      this.txtSupplierStockCode.setText("");
      this.txtPackCost.setText(this.localeString(Double.valueOf(1.0)));
      dirty.setValue(true);
   }

   public void addStockLevelRow() {
      if (this.cmbLocations.getSelectedKey() != null
         && (this.parseLocaleString(this.txtMinimum.getText()) != 0.0 || this.parseLocaleString(this.txtMaximum.getText()) != 0.0)) {
         List<uk.chromis.administration.KeyedData> locations = new ArrayList<>(
            uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, name from locations order by name")
         );
         this.stockLevelTable.getItems().forEach(l -> locations.remove(new uk.chromis.administration.KeyedData(l.getLocationId(), l.getLocationName())));
         this.stockLevelsData
            .add(
               new StockLevelInfo(
                  UUID.randomUUID().toString(),
                  this.cmbLocations.getSelectedKey(),
                  this.cmbLocations.getItem(),
                  this.selectedProduct.getId(),
                  0.0,
                  this.parseLocaleString(this.txtMinimum.getText()),
                  this.parseLocaleString(this.txtMaximum.getText()),
                  this.localGuid
               )
            );
         locations.remove(new uk.chromis.administration.KeyedData(this.cmbLocations.getSelectedKey(), this.cmbLocations.getItem()));
         this.cmbLocations.setItemList(locations);
         this.cmbLocations.getSelectionModel().clearSelection();
         this.txtMinimum.setText(this.localeString(Double.valueOf(0.0)));
         this.txtMaximum.setText(this.localeString(Double.valueOf(0.0)));
         dirty.setValue(true);
      }
   }

   private void buildBarcodeTable() {
      this.barcode.setCellValueFactory(new PropertyValueFactory("barcode"));
      this.sellQty.setCellValueFactory(new PropertyValueFactory("sellQty"));
      this.uomdescriptionBcTable.setCellValueFactory(new PropertyValueFactory("uomdescription"));
      this.barcodeTable.setItems(this.barcodeData);
      this.barcodeTable
         .addEventHandler(
            MouseEvent.MOUSE_CLICKED,
            new EventHandler<MouseEvent>() {
               public void handle(MouseEvent t) {
                  if (t.getButton() == MouseButton.SECONDARY
                     && Dialogs.showDialog(
                           7, LocalResource.getString("dialog.removeBarcode"), (Stage)ProductController.this.apAnchor.getScene().getWindow(), true
                        )
                        == 0) {
                     ProductController.this.barcodeTable.getItems().removeAll(ProductController.this.barcodeTable.getSelectionModel().getSelectedItems());
                     BasePanelController.dirty.setValue(true);
                  }
               }
            }
         );
   }

   private String buildDisplayString() {
      StringBuilder displayStr = new StringBuilder();
      if (!this.txtDisplayLine1.getText().trim().equals("")) {
         displayStr.append("<html>");
         if (this.rbLine1S1.isSelected()) {
            displayStr.append("<font size=-1>");
         } else if (this.rbLine1S2.isSelected()) {
            displayStr.append("<font size=-2>");
         } else if (this.rbLine1L1.isSelected()) {
            displayStr.append("<font size=+1>");
         } else if (this.rbLine1L2.isSelected()) {
            displayStr.append("<font size=+2>");
         }

         if (this.tswtItalicL1.isSelected()) {
            displayStr.append("<i>");
         }

         if (this.tswtBoldL1.isSelected()) {
            displayStr.append("<b>");
         }

         displayStr.append(this.txtDisplayLine1.getText());
         if (this.tswtBoldL1.isSelected()) {
            displayStr.append("</b>");
         }

         if (this.tswtItalicL1.isSelected()) {
            displayStr.append("</i>");
         }

         if (this.rbLine1S1.isSelected()) {
            displayStr.append("</font size=-1>");
         } else if (this.rbLine1S2.isSelected()) {
            displayStr.append("</font size=-2>");
         } else if (this.rbLine1L1.isSelected()) {
            displayStr.append("</font size=+1>");
         } else if (this.rbLine1L2.isSelected()) {
            displayStr.append("</font size=+2>");
         }
      }

      if (!this.txtDisplayLine2.getText().trim().equals("")) {
         displayStr.append("<br>");
         displayStr.append("<center>");
         if (this.rbLine2S1.isSelected()) {
            displayStr.append("<font size=-1>");
         } else if (this.rbLine2S2.isSelected()) {
            displayStr.append("<font size=-2>");
         } else if (this.rbLine2L1.isSelected()) {
            displayStr.append("<font size=+1>");
         } else if (this.rbLine2L2.isSelected()) {
            displayStr.append("<font size=+2>");
         }

         if (this.tswtItalicL2.isSelected()) {
            displayStr.append("<i>");
         }

         if (this.tswtBoldL2.isSelected()) {
            displayStr.append("<b>");
         }

         displayStr.append(this.txtDisplayLine2.getText());
         if (this.tswtBoldL2.isSelected()) {
            displayStr.append("</b>");
         }

         if (this.tswtItalicL2.isSelected()) {
            displayStr.append("</i>");
         }

         if (this.rbLine2S1.isSelected()) {
            displayStr.append("</font size=-1>");
         } else if (this.rbLine2S2.isSelected()) {
            displayStr.append("</font size=-2>");
         } else if (this.rbLine2L1.isSelected()) {
            displayStr.append("</font size=+1>");
         } else if (this.rbLine2L2.isSelected()) {
            displayStr.append("</font size=+2>");
         }

         displayStr.append("</center>");
      }

      return displayStr.toString();
   }

   private void buildProductTable() {
      this.reference.setCellValueFactory(new PropertyValueFactory("reference"));
      this.barcodeProdTable.setCellValueFactory(new PropertyValueFactory("barcode"));
      this.name.setCellValueFactory(new PropertyValueFactory("name"));
      this.category.setCellValueFactory(new PropertyValueFactory("category"));
      this.sellIncTax
         .setCellFactory(
            tableColumn -> new TableCell<ProductTableInfo, String>() {
                  protected void updateItem(String text, boolean empty) {
                     if (!empty) {
                        this.setText(
                           ProductController.this.localeString(
                              ((ProductTableInfo)this.getTableView().getItems().get(this.indexProperty().getValue())).getSellPriceInc()
                           )
                        );
                     } else {
                        this.setText("");
                     }
                  }
               }
         );
      this.sellPrice
         .setCellFactory(
            tableColumn -> new TableCell<ProductTableInfo, String>() {
                  protected void updateItem(String text, boolean empty) {
                     if (!empty) {
                        this.setText(
                           ProductController.this.localeString(
                              ((ProductTableInfo)this.getTableView().getItems().get(this.indexProperty().getValue())).getSellPrice()
                           )
                        );
                     } else {
                        this.setText("");
                     }
                  }
               }
         );
      this.buyPrice
         .setCellFactory(
            tableColumn -> new TableCell<ProductTableInfo, String>() {
                  protected void updateItem(String text, boolean empty) {
                     if (!empty) {
                        this.setText(
                           ProductController.this.localeString(
                              ((ProductTableInfo)this.getTableView().getItems().get(this.indexProperty().getValue())).getBuyPrice()
                           )
                        );
                     } else {
                        this.setText("");
                     }
                  }
               }
         );
      this.isIngredient.setCellValueFactory(new PropertyValueFactory("ingredientImage"));
      this.isAuxiliary.setCellValueFactory(new PropertyValueFactory("auxiliariesImage"));
      this.mainTable.setItems(this.filteredList);
      this.mainTableListener();
   }

   private void buildSupplierTable() {
      this.supplierName.setCellValueFactory(new PropertyValueFactory("supplierName"));
      this.supplierCode.setCellValueFactory(new PropertyValueFactory("supplierCode"));
      this.uomfactor.setCellValueFactory(new PropertyValueFactory("uomfactor"));
      this.uomdescription.setCellValueFactory(new PropertyValueFactory("uomdescription"));
      this.packcost.setCellValueFactory(new PropertyValueFactory("cost"));
      this.supplierTable.setItems(this.supplierData);
      this.supplierTable
         .addEventHandler(
            MouseEvent.MOUSE_CLICKED,
            new EventHandler<MouseEvent>() {
               public void handle(MouseEvent t) {
                  if (t.getButton() == MouseButton.SECONDARY
                     && Dialogs.showDialog(
                           7, LocalResource.getString("dialog.removeSupplier"), (Stage)ProductController.this.apAnchor.getScene().getWindow(), true
                        )
                        == 0) {
                     ProductController.this.supplierTable.getItems().removeAll(ProductController.this.supplierTable.getSelectionModel().getSelectedItems());
                     BasePanelController.dirty.setValue(true);
                  }
               }
            }
         );
   }

   private void buildStockLevelsTable() {
      this.location.setCellValueFactory(new PropertyValueFactory("locationName"));
      this.minimum.setCellValueFactory(new PropertyValueFactory("minimumLevel"));
      this.maximum.setCellValueFactory(new PropertyValueFactory("maximumLevel"));
      this.current
         .setCellFactory(
            tableColumn -> new TableCell<StockLevelInfo, String>() {
                  protected void updateItem(String text, boolean empty) {
                     if (!empty) {
                        this.setText(
                           ProductController.this.localeString(
                              ((StockLevelInfo)this.getTableView().getItems().get(this.indexProperty().getValue())).getCurrent()
                           )
                        );
                     } else {
                        this.setText("");
                     }
                  }
               }
         );
      this.stockLevelTable.setItems(this.stockLevelsData);
      this.stockLevelTable
         .addEventHandler(
            MouseEvent.MOUSE_CLICKED,
            new EventHandler<MouseEvent>() {
               public void handle(MouseEvent t) {
                  if (t.getButton() == MouseButton.SECONDARY
                     && Dialogs.showDialog(
                           7, LocalResource.getString("dialog.removeManagedLevels"), (Stage)ProductController.this.apAnchor.getScene().getWindow(), true
                        )
                        == 0) {
                     ProductController.this.stockLevelTable.getItems().removeAll(ProductController.this.stockLevelTable.getSelectionModel().getSelectedItems());
                     BasePanelController.dirty.setValue(true);
                  }
               }
            }
         );
   }

   private Double calculateGrossMargin() {
      Double sell = this.txtSellPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtSellPrice.getText());
      Double buy = this.txtBuyPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtBuyPrice.getText());
      return sell != 0.0 && buy != 0.0 ? (sell - buy) / sell * 100.0 : 0.0;
   }

   private void calculatePricingTiers() {
      this.txtTierPrice1
         .setText(
            this.txtTierDiscount1.getText().equals("0.00")
               ? this.localeString(Double.valueOf(0.0))
               : this.localeString(this.calculateUnitPriceFromDiscount(this.txtTierDiscount1))
         );
      this.txtTierPriceInc1.setText(this.localeString(this.calculateUnitPriceInc(this.txtTierPrice1)));
      this.txtTierPrice2
         .setText(
            this.txtTierDiscount2.getText().equals("0.00")
               ? this.localeString(Double.valueOf(0.0))
               : this.localeString(this.calculateUnitPriceFromDiscount(this.txtTierDiscount2))
         );
      this.txtTierPriceInc2.setText(this.localeString(this.calculateUnitPriceInc(this.txtTierPrice2)));
      this.txtTierPrice3
         .setText(
            this.txtTierDiscount3.getText().equals("0.00")
               ? this.localeString(Double.valueOf(0.0))
               : this.localeString(this.calculateUnitPriceFromDiscount(this.txtTierDiscount3))
         );
      this.txtTierPriceInc3.setText(this.localeString(this.calculateUnitPriceInc(this.txtTierPrice3)));
      this.txtTierPrice4
         .setText(
            this.txtTierDiscount4.getText().equals("0.00")
               ? this.localeString(Double.valueOf(0.0))
               : this.localeString(this.calculateUnitPriceFromDiscount(this.txtTierDiscount4))
         );
      this.txtTierPriceInc4.setText(this.localeString(this.calculateUnitPriceInc(this.txtTierPrice4)));
      this.txtTierPrice5
         .setText(
            this.txtTierDiscount5.getText().equals("0.00")
               ? this.localeString(Double.valueOf(0.0))
               : this.localeString(this.calculateUnitPriceFromDiscount(this.txtTierDiscount5))
         );
      this.txtTierPriceInc5.setText(this.localeString(this.calculateUnitPriceInc(this.txtTierPrice5)));
   }

   private Double calculateSellPrice() {
      Double sell = this.txtSellPriceInc.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtSellPriceInc.getText());
      return this.cmbTaxCategory.getSelectedKey() == null ? 0.0 : sell / (1.0 + this.taxDataLogic.getTaxRate(this.cmbTaxCategory.getSelectedKey()));
   }

   private Double calculateSellPriceFromMargin() {
      Double margin = this.txtGrossMargin.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtGrossMargin.getText());
      return this.parseLocaleString(this.txtBuyPrice.getText()) / (1.0 - margin / 100.0);
   }

   private Double calculateSellPriceInc() {
      Double sell = this.txtSellPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtSellPrice.getText());
      return this.cmbTaxCategory.getSelectedKey() == null ? 0.0 : sell + sell * this.taxDataLogic.getTaxRate(this.cmbTaxCategory.getSelectedKey());
   }

   private String calculateUnitDiscount(Double discountedRetailInc) {
      return discountedRetailInc == 0.0 ? "0.00" : String.valueOf((1.0 - discountedRetailInc / this.parseLocaleString(this.txtSellPriceInc.getText())) * 100.0);
   }

   private Double calculateUnitPriceInc(TextField unitPriceInc) {
      Double sell = unitPriceInc.getText().isEmpty() ? 0.0 : this.parseLocaleString(unitPriceInc.getText());
      return this.cmbTaxCategory.getSelectedKey() == null ? 0.0 : sell + sell * this.taxDataLogic.getTaxRate(this.cmbTaxCategory.getSelectedKey());
   }

   private Double calculateUnitPrice(TextField unitPrice) {
      Double sell = unitPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(unitPrice.getText());
      return this.cmbTaxCategory.getSelectedKey() == null ? 0.0 : sell / (1.0 + this.taxDataLogic.getTaxRate(this.cmbTaxCategory.getSelectedKey()));
   }

   private Double calculateUnitPriceFromDiscount(TextField unitDiscount) {
      Double discount = unitDiscount.getText().isEmpty() ? 0.0 : this.parseLocaleString(unitDiscount.getText());
      Double sell = this.txtSellPrice.getText().isEmpty() ? 0.0 : this.parseLocaleString(this.txtSellPriceInc.getText());
      return sell - sell * (discount / 100.0);
   }

   private void createPriceListeners() {
      this.txtSellPrice.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               this.txtSellPrice.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".") && !newValue.equals(",")) {
                  this.txtSellPriceInc.setText(this.localeString(this.calculateSellPriceInc()));
                  this.txtGrossMargin.setText(this.localeString(this.calculateGrossMargin()));
                  this.txtGrossProfit.setText(this.localeString(this.calculateGrossProfit()));
                  this.calculatePricingTiers();
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      this.txtSellPrice
         .focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  this.txtSellPrice
                     .setText(
                        this.localeString(
                           Double.valueOf(
                              !this.txtSellPrice.getText().equals(".") && !this.txtSellPrice.getText().equals(",") && !this.txtSellPrice.getText().isEmpty()
                                 ? this.parseLocaleString(this.txtSellPrice.getText())
                                 : 0.0
                           )
                        )
                     );
               }
            }
         );
      this.txtBuyPrice.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               this.txtBuyPrice.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".")) {
                  this.txtGrossMargin.setText(this.localeString(this.calculateGrossMargin()));
                  this.txtGrossProfit.setText(this.localeString(this.calculateGrossProfit()));
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      this.txtBuyPrice
         .focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  this.txtBuyPrice
                     .setText(
                        this.localeString(
                           Double.valueOf(
                              !this.txtBuyPrice.getText().equals(".") && !this.txtBuyPrice.getText().equals(",") && !this.txtBuyPrice.getText().isEmpty()
                                 ? this.parseLocaleString(this.txtBuyPrice.getText())
                                 : 0.0
                           )
                        )
                     );
               }
            }
         );
      this.txtSellPriceInc.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               this.txtSellPriceInc.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".")) {
                  this.txtSellPrice.setText(this.localeString(this.calculateSellPrice()));
                  this.txtGrossMargin.setText(this.localeString(this.calculateGrossMargin()));
                  this.txtGrossProfit.setText(this.localeString(this.calculateGrossProfit()));
                  this.calculatePricingTiers();
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      this.txtSellPriceInc
         .focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  this.txtSellPriceInc
                     .setText(
                        this.localeString(
                           Double.valueOf(
                              !this.txtSellPriceInc.getText().equals(".")
                                    && !this.txtSellPriceInc.getText().equals(",")
                                    && !this.txtSellPriceInc.getText().isEmpty()
                                 ? this.parseLocaleString(this.txtSellPriceInc.getText())
                                 : 0.0
                           )
                        )
                     );
               }
            }
         );
      this.txtGrossMargin.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               this.txtGrossMargin.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".")) {
                  this.txtSellPrice.setText(this.localeString(this.calculateSellPriceFromMargin()));
                  this.txtSellPriceInc.setText(this.localeString(this.calculateSellPriceInc()));
                  this.txtGrossProfit.setText(this.localeString(this.calculateGrossProfit()));
                  this.calculatePricingTiers();
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      this.txtGrossMargin
         .focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  this.txtGrossMargin
                     .setText(
                        this.localeString(
                           Double.valueOf(
                              !this.txtGrossMargin.getText().equals(".")
                                    && !this.txtGrossMargin.getText().equals(",")
                                    && !this.txtGrossMargin.getText().isEmpty()
                                 ? this.parseLocaleString(this.txtGrossMargin.getText())
                                 : 0.0
                           )
                        )
                     );
               }
            }
         );
      this.cmbTaxCategory.getSelectionModel().selectedItemProperty().addListener((o, ol, nw) -> {
         this.APIcall = true;
         this.txtSellPriceInc.setText(this.localeString(this.calculateSellPriceInc()));
         this.APIcall = false;
         dirty.setValue(true);
      });
   }

   private void createTierListeners(TextField startingQty, TextField unitPrice, TextField unitPriceInc, TextField unitDiscount) {
      startingQty.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall && !newValue.matches("\\d*?")) {
            startingQty.setText(oldValue);
         }

         dirty.setValue(true);
      });
      unitPrice.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               unitPrice.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".")) {
                  unitPriceInc.setText(this.localeString(this.calculateUnitPriceInc(unitPrice)));
                  unitDiscount.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(unitPriceInc.getText()))));
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      unitPriceInc.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               unitPrice.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".")) {
                  unitPrice.setText(this.localeString(this.calculateUnitPrice(unitPriceInc)));
                  unitDiscount.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(unitPriceInc.getText()))));
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      unitDiscount.textProperty().addListener((observable, oldValue, newValue) -> {
         if (!this.APIcall) {
            if (!newValue.matches("^(?:\\d{0,6}(?:[\\.\\,]\\d{0,2})?)?$")) {
               unitDiscount.setText(oldValue);
            } else {
               this.APIcall = true;
               if (!newValue.equals(".")) {
                  unitPriceInc.setText(this.localeString(this.calculateUnitPriceFromDiscount(unitDiscount)));
                  unitPrice.setText(this.localeString(this.calculateUnitPrice(unitPriceInc)));
               }

               this.APIcall = false;
            }
         }

         dirty.setValue(true);
      });
      unitPrice.focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  unitPrice.setText(
                     this.localeString(
                        Double.valueOf(!unitPrice.getText().equals(".") && !unitPrice.getText().isEmpty() ? this.parseLocaleString(unitPrice.getText()) : 0.0)
                     )
                  );
               }
            }
         );
      unitPriceInc.focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  unitPriceInc.setText(
                     this.localeString(
                        Double.valueOf(
                           !unitPriceInc.getText().equals(".") && !unitPriceInc.getText().isEmpty() ? this.parseLocaleString(unitPriceInc.getText()) : 0.0
                        )
                     )
                  );
               }
            }
         );
      startingQty.focusedProperty().addListener((obs, oldVal, newVal) -> {
         if (!newVal) {
            startingQty.setText(startingQty.getText().isEmpty() ? "0" : startingQty.getText());
         }
      });
      unitDiscount.focusedProperty()
         .addListener(
            (obs, oldVal, newVal) -> {
               if (!newVal) {
                  unitDiscount.setText(
                     this.localeString(
                        Double.valueOf(
                           !unitDiscount.getText().equals(".") && !unitDiscount.getText().isEmpty() ? this.parseLocaleString(unitDiscount.getText()) : 0.0
                        )
                     )
                  );
               }
            }
         );
   }

   private void createSwingButton() {
      String displayStr;
      try {
         displayStr = this.selectedProduct.getDisplay();
      } catch (Exception var3) {
         displayStr = "";
      }

      this.tswtButtonImage.setSelected(false);
      this.showImage = false;
      this.tswtButtonImage.selectedProperty().addListener((observable, oldValue, newValue) -> {
         this.showImage = newValue;
         this.updateButton();
      });
      this.setButtonToggleListener(this.tswtItalicL1);
      this.setButtonToggleListener(this.tswtItalicL2);
      this.setButtonToggleListener(this.tswtBoldL1);
      this.setButtonToggleListener(this.tswtBoldL2);
      this.setToggleGroupListener(this.line1Font);
      this.setToggleGroupListener(this.line2Font);
      this.setButtonTextListener(this.txtDisplayLine1);
      this.setButtonTextListener(this.txtDisplayLine2);
      if (displayStr != null) {
         this.populateScreenButton(displayStr);
      }
   }

   public void loadComboValues() {
      this.cmbCategory
         .setItemList(new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, name from categories order by name")));
      this.cmbUnitsOfMeasure
         .setItemList(
            new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, uomdescription from uom order by uomdescription"))
         );
      this.cmbAttributes
         .setItemListWithNull(
            new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, name from attributeset order by name"))
         );
      this.cmbTaxCategory
         .setItemList(new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, name from taxcategories order by name")));
      this.cmbBuyTaxCategory
         .setItemList(new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, name from taxcategories order by name")));
      this.cmbStockUOM
         .setItemList(
            new ArrayList<>(
               uk.chromis.administration.DatabaseLogic.getInstance()
                  .getKeyedData("select id, uomdescription from uom where uomfactor = 1.00 order by uomdescription")
            )
         );
      this.cmbSellingUOM
         .setItemList(
            new ArrayList<>(
               uk.chromis.administration.DatabaseLogic.getInstance()
                  .getKeyedData("select id, uomdescription from uom where uomfactor = 1.00 order by uomdescription")
            )
         );
      this.cmbBuyUOM
         .setItemList(
            new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, uomdescription from uom order by uomdescription"))
         );
      this.cmbSupplierUOM
         .setItemList(
            new ArrayList<>(uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, uomdescription from uom order by uomdescription"))
         );
      this.cmbSuppliers
         .setItemList(
            new ArrayList<>(
               uk.chromis.administration.DatabaseLogic.getInstance()
                  .getKeyedData("select id, suppliername from suppliers where accountactive = true order by suppliername")
            )
         );
      List<uk.chromis.administration.KeyedData> printers = new ArrayList<>();
      printers.add(new uk.chromis.administration.KeyedData("kitchenprinter", "Kitchen Printer"));
      printers.add(new uk.chromis.administration.KeyedData("kitchenprinter2", "Kitchen Printer 2"));
      printers.add(new uk.chromis.administration.KeyedData("barprinter", "Bar Printer"));
      this.cmbRemotePrinter.setItemListWithNull(printers);
   }

   private String mapString(StringBuilder passedSB) {
      this.displayMap.put("<font size=-2>", Integer.valueOf(passedSB.indexOf("<font size=-2>")));
      this.displayMap.put("</font size=-2>", Integer.valueOf(passedSB.indexOf("</font size=-2>", passedSB.indexOf("<font size=-2>") + 14)));
      if (passedSB.indexOf("<font size=-2>") >= 0) {
         passedSB.replace(passedSB.indexOf("<font size=-2>"), passedSB.indexOf("<font size=-2>") + 14, "");
      }

      if (passedSB.indexOf("</font size=-2>") >= 0) {
         passedSB.replace(passedSB.indexOf("</font size=-2>"), passedSB.indexOf("</font size=-2>") + 15, "");
      }

      this.displayMap.put("<font size=-1>", Integer.valueOf(passedSB.indexOf("<font size=-1>")));
      this.displayMap.put("</font size=-1>", Integer.valueOf(passedSB.indexOf("</font size=-1>", passedSB.indexOf("<font size=-1>") + 14)));
      if (passedSB.indexOf("<font size=-1>") >= 0) {
         passedSB.replace(passedSB.indexOf("<font size=-1>"), passedSB.indexOf("<font size=-1>") + 14, "");
      }

      if (passedSB.indexOf("</font size=-1>") >= 0) {
         passedSB.replace(passedSB.indexOf("</font size=-1>"), passedSB.indexOf("</font size=-1>") + 15, "");
      }

      this.displayMap.put("<font size=+1>", Integer.valueOf(passedSB.indexOf("<font size=+1>")));
      this.displayMap.put("</font size=+1>", Integer.valueOf(passedSB.indexOf("</font size=+1>", passedSB.indexOf("<font size=+1>") + 14)));
      if (passedSB.indexOf("<font size=+1>") >= 0) {
         passedSB.replace(passedSB.indexOf("<font size=+1>"), passedSB.indexOf("<font size=+1>") + 14, "");
      }

      if (passedSB.indexOf("</font size=+1>") >= 0) {
         passedSB.replace(passedSB.indexOf("</font size=+1>"), passedSB.indexOf("</font size=+1>") + 15, "");
      }

      this.displayMap.put("<font size=+2>", Integer.valueOf(passedSB.indexOf("<font size=+2>")));
      this.displayMap.put("</font size=+2>", Integer.valueOf(passedSB.indexOf("</font size=+2>", passedSB.indexOf("<font size=+2>") + 14)));
      if (passedSB.indexOf("<font size=+2>") >= 0) {
         passedSB.replace(passedSB.indexOf("<font size=+2>"), passedSB.indexOf("<font size=+2>") + 14, "");
      }

      if (passedSB.indexOf("</font size=+2>") >= 0) {
         passedSB.replace(passedSB.indexOf("</font size=+2>"), passedSB.indexOf("</font size=+2>") + 15, "");
      }

      this.displayMap.put("<i>", Integer.valueOf(passedSB.indexOf("<i>", passedSB.indexOf("<i>"))));
      this.displayMap.put("</i>", Integer.valueOf(passedSB.indexOf("</i>", passedSB.indexOf("<1>") + 3)));
      if (passedSB.indexOf("<i>") >= 0) {
         passedSB.replace(passedSB.indexOf("<i>"), passedSB.indexOf("<i>") + 3, "");
      }

      if (passedSB.indexOf("</i>") >= 0) {
         passedSB.replace(passedSB.indexOf("</i>"), passedSB.indexOf("</i>") + 4, "");
      }

      this.displayMap.put("<b>", Integer.valueOf(passedSB.indexOf("<b>", passedSB.indexOf("<b>"))));
      this.displayMap.put("</b>", Integer.valueOf(passedSB.indexOf("</b>", passedSB.indexOf("<b>") + 3)));
      if (passedSB.indexOf("<b>") >= 0) {
         passedSB.replace(passedSB.indexOf("<b>"), passedSB.indexOf("<b>") + 3, "");
      }

      if (passedSB.indexOf("</b>") >= 0) {
         passedSB.replace(passedSB.indexOf("</b>"), passedSB.indexOf("</b>") + 4, "");
      }

      this.displayMap.put("<center>", Integer.valueOf(passedSB.indexOf("<center>", passedSB.indexOf("<center>"))));
      this.displayMap.put("</center>", Integer.valueOf(passedSB.indexOf("</center>", passedSB.indexOf("<center>") + 8)));
      if (passedSB.indexOf("<center>") >= 0) {
         passedSB.replace(passedSB.indexOf("<center>"), passedSB.indexOf("<center>") + 8, "");
      }

      if (passedSB.indexOf("</center>") >= 0) {
         passedSB.replace(passedSB.indexOf("</center>"), passedSB.indexOf("</center>") + 9, "");
      }

      return passedSB.toString();
   }

   private void populateBarcodesTable() {
      this.barcodeTable.getItems().clear();
      if (this.barcodes != null) {
         this.barcodes
            .forEach(
               b -> this.barcodeData
                     .add(new BarcodeInfo(b.getId(), b.getProductid(), b.getBarcode(), b.getSellQty(), b.getUomid(), b.getUomfactor(), b.getUomdescription()))
            );
      }
   }

   private void populatePricing() {
      this.APIcall = true;
      this.resetPricingPanel(5);
      if (this.prices != null) {
         this.APIcall = true;
         this.txtTierQty1.setText(String.valueOf(this.prices.get(0).getTierQty()));
         this.txtTierPrice1.setText(this.localeString(this.prices.get(0).getTierPrice()));
         this.txtTierPriceInc1.setText(this.localeString(this.prices.get(0).getTierPriceInc()));
         this.txtTierDiscount1.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(this.txtTierPriceInc1.getText()))));
         this.txtTierQty2.setText(String.valueOf(this.prices.get(1).getTierQty()));
         this.txtTierPrice2.setText(this.localeString(this.prices.get(1).getTierPrice()));
         this.txtTierPriceInc2.setText(this.localeString(this.prices.get(1).getTierPriceInc()));
         this.txtTierDiscount2.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(this.txtTierPriceInc2.getText()))));
         this.txtTierQty3.setText(String.valueOf(this.prices.get(2).getTierQty()));
         this.txtTierPrice3.setText(this.localeString(this.prices.get(2).getTierPrice()));
         this.txtTierPriceInc3.setText(this.localeString(this.prices.get(2).getTierPriceInc()));
         this.txtTierDiscount3.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(this.txtTierPriceInc3.getText()))));
         this.txtTierQty4.setText(String.valueOf(this.prices.get(3).getTierQty()));
         this.txtTierPrice4.setText(this.localeString(this.prices.get(3).getTierPrice()));
         this.txtTierPriceInc4.setText(this.localeString(this.prices.get(3).getTierPriceInc()));
         this.txtTierDiscount4.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(this.txtTierPriceInc4.getText()))));
         this.txtTierQty5.setText(String.valueOf(this.prices.get(4).getTierQty()));
         this.txtTierPrice5.setText(this.localeString(this.prices.get(4).getTierPrice()));
         this.txtTierPriceInc5.setText(this.localeString(this.prices.get(4).getTierPriceInc()));
         this.txtTierDiscount5.setText(this.localeString(this.calculateUnitDiscount(this.parseLocaleString(this.txtTierPriceInc5.getText()))));
         dirty.setValue(false);
         this.APIcall = false;
      } else {
         this.resetPricingPanel(4);
      }

      this.APIcall = false;
   }

   public void populateProductFields() {
      this.APIcall = true;
      this.productInfo = (ProductTableInfo)this.mainTable.getItems().get(this.mainTable.getSelectionModel().getSelectedIndex());
      this.selectedProduct = this.productDataLogic.getProductInfo(this.productInfo.getId());
      this.lblPanelName.setText(this.selectedProduct.getName());
      this.txtReference.setText(this.selectedProduct.getReference());
      this.txtReference.setDisable(this.selectedProduct.isSystemObject());
      this.txtBarcode.setText(this.selectedProduct.getCode());
      this.txtDescription.setText(this.selectedProduct.getName());
      this.txtShortName.setText(this.selectedProduct.getAlias());
      this.cmbCategory.setSelectedItem(this.selectedProduct.getCategory());
      this.cmbCategory.setDisable(this.selectedProduct.isSystemObject());
      this.cmbTaxCategory.setSelectedItem(this.selectedProduct.getTaxcat());
      if (this.selectedProduct.getBuytax() == null) {
         this.cmbBuyTaxCategory
            .setSelectedItem(uk.chromis.administration.DatabaseLogic.getInstance().getString("select id from taxcategories where systemobject = true limit 1"));
      } else {
         this.cmbBuyTaxCategory.setSelectedItem(this.selectedProduct.getBuytax());
      }

      this.cmbBuyTaxCategory.setDisable(this.selectedProduct.isSystemObject());
      this.txtKitchenDescription.setText(this.selectedProduct.getKitchenDescription());
      this.cmbAttributes.setSelectedItem(this.selectedProduct.getAttributeset_id());
      this.tswtMandatoryAttributes.setSelected(this.selectedProduct.isVerpatrib());
      this.txtSellPrice.setText(this.localeString(this.selectedProduct.getPriceSell()));
      this.txtBuyPrice.setText(this.localeString(this.selectedProduct.getPriceBuy()));
      this.txtSellPriceInc.setText(this.localeString(this.selectedProduct.getPriceSellInc()));
      this.txtGrossMargin.setText(this.localeString(this.calculateGrossMargin()));
      this.txtGrossProfit.setText(this.localeString(this.calculateGrossProfit()));
      this.txtCommission.setText(this.localeString(this.selectedProduct.getCommission()));
      this.imageEd.setImage(this.selectedProduct.getImage());
      this.txtEarnValue.setText(String.valueOf(this.selectedProduct.getEarnValue()));
      this.txtBurnValue.setText(String.valueOf(this.selectedProduct.getBurnValue()));
      this.txtLoyaltyMultiplier.setText(this.localeString(this.selectedProduct.getLoyaltyMultiplier()));
      this.tswtInCatalog.setSelected(this.selectedProduct.isCatalog());
      this.txtCatalogOrder.setText(String.valueOf(this.selectedProduct.getCatorder()));
      this.tswtWarrantyReceipt.setSelected(this.selectedProduct.getWarranty());
      this.tswtVariablePrice.setSelected(this.selectedProduct.isVprice());
      this.tswtAuxiliary.setSelected(this.selectedProduct.isCom());
      this.tswtCanHaveAuxiliary.setSelected(this.selectedProduct.getAuxiliaryPermitted());
      this.tswtIsServiceItem.setSelected(this.selectedProduct.isService());
      this.tswtIsScale.setSelected(this.selectedProduct.isScale());
      this.tswtAlwaysAvailable.setSelected(this.selectedProduct.isAlwaysAvailable());
      this.tswtCanDiscount.setSelected(this.selectedProduct.canDiscount());
      this.tswtCostPricing.setSelected(!this.isCostPricingExcluded(this.selectedProduct.getId()));
      this.discountSpinner.getEditor().setText(this.selectedProduct.canDiscount() ? String.valueOf(this.selectedProduct.getDiscount()) : "");
      this.tswtManageStockLevel.setSelected(this.selectedProduct.manageStock());
      this.tswtAgeWarning.setSelected(this.selectedProduct.isAgeRestricted());
      if (this.selectedProduct.isAgeRestricted()) {
         this.txtAge.setDisable(false);
         this.lblAge.setDisable(false);
         this.txtAge.setText(String.valueOf(this.selectedProduct.getRestrictionAge()));
         this.tswtIngredient.setSelected(true);
      } else {
         this.txtAge.setText("");
         this.txtAge.setDisable(true);
         this.lblAge.setDisable(true);
      }

      this.tswtIngredient.setSelected(this.selectedProduct.isIngredient());
      this.tswtPrinter.setSelected(this.selectedProduct.isKitchen());
      this.tswtScreen.setSelected(this.selectedProduct.getRemoteDisplay());
      this.spinRemoteScreen.getValueFactory().setValue(this.selectedProduct.getDisplayid());
      this.cmbRemotePrinter.setSelectedItem(this.selectedProduct.getRemotePrinter());
      this.populateScreenButton(this.selectedProduct.getDisplay());
      this.txtProperties.setText(Formats.BYTEA.formatValue(this.selectedProduct.getAttributes()));
      TierPricing tiers = new TierPricing();
      this.prices = new ArrayList<>();
      this.prices = tiers.getPricing(this.selectedProduct.getId(), this.selectedProduct.getSiteGuid());
      this.systemobject = this.selectedProduct.isSystemObject();
      this.cmbBuyUOM.setSelectedItem(this.selectedProduct.getBuyUnit());
      this.cmbSellingUOM.setSelectedItem(this.selectedProduct.getSellUnit());
      this.cmbStockUOM.setSelectedItem(this.selectedProduct.getstockUnit());
      this.cmbBuyUOM.setDisable(this.systemobject);
      this.cmbStockUOM.setDisable(this.systemobject);
      this.cmbSellingUOM.setDisable(this.systemobject);
      this.cmbUnitsOfMeasure.setDisable(this.systemobject);
      this.cmbSupplierUOM.setDisable(this.systemobject);
      this.txtAddBarcode.setDisable(this.systemobject);
      this.txtSellingQty.setDisable(this.systemobject);
      this.btnAddPLU.setDisable(this.systemobject);
      this.cmbSuppliers.setDisable(this.systemobject);
      this.cmbSuppliers.getSelectionModel().clearSelection();
      this.cmbSupplierUOM.setSelectedItem("000-each");
      this.txtSupplierStockCode.setText("");
      this.txtPackCost.setText(this.localeString("1.00"));
      this.txtSupplierStockCode.setDisable(this.systemobject);
      this.cmbLocations.getSelectionModel().clearSelection();
      this.txtMinimum.setText(this.localeString(Double.valueOf(0.0)));
      this.txtMaximum.setText(this.localeString(Double.valueOf(0.0)));
      this.txtAddBarcode.setText("");
      this.txtSellingQty.setText(this.localeString("1.00"));
      this.cmbUnitsOfMeasure.getSelectionModel().clearSelection();
      this.btnAddSupplier.setDisable(this.systemobject);
      this.tswtRetireProduct.setDisable(this.systemobject);
      this.tswtRetireProduct.setSelected(false);
      this.txtTierQty1.setDisable(this.systemobject);
      this.txtTierQty2.setDisable(this.systemobject);
      this.txtTierQty3.setDisable(this.systemobject);
      this.txtTierQty4.setDisable(this.systemobject);
      this.txtTierQty5.setDisable(this.systemobject);
      this.txtTierPriceInc1.disableProperty().bind(this.txtTierQty1.disabledProperty());
      this.txtTierPriceInc2.disableProperty().bind(this.txtTierQty2.disabledProperty());
      this.txtTierPriceInc3.disableProperty().bind(this.txtTierQty3.disabledProperty());
      this.txtTierPriceInc4.disableProperty().bind(this.txtTierQty4.disabledProperty());
      this.txtTierPriceInc5.disableProperty().bind(this.txtTierQty5.disabledProperty());
      this.txtTierPrice1.disableProperty().bind(this.txtTierQty1.disabledProperty());
      this.txtTierPrice2.disableProperty().bind(this.txtTierQty2.disabledProperty());
      this.txtTierPrice3.disableProperty().bind(this.txtTierQty3.disabledProperty());
      this.txtTierPrice4.disableProperty().bind(this.txtTierQty4.disabledProperty());
      this.txtTierPrice5.disableProperty().bind(this.txtTierQty5.disabledProperty());
      this.txtTierDiscount1.disableProperty().bind(this.txtTierQty1.disabledProperty());
      this.txtTierDiscount2.disableProperty().bind(this.txtTierQty2.disabledProperty());
      this.txtTierDiscount3.disableProperty().bind(this.txtTierQty3.disabledProperty());
      this.txtTierDiscount4.disableProperty().bind(this.txtTierQty4.disabledProperty());
      this.txtTierDiscount5.disableProperty().bind(this.txtTierQty5.disabledProperty());
      this.btnClear1.disableProperty().bind(this.txtTierQty1.disabledProperty());
      this.btnClear2.disableProperty().bind(this.txtTierQty2.disabledProperty());
      this.btnClear3.disableProperty().bind(this.txtTierQty3.disabledProperty());
      this.btnClear4.disableProperty().bind(this.txtTierQty4.disabledProperty());
      this.btnClear5.disableProperty().bind(this.txtTierQty5.disabledProperty());
      this.productSuppliers = this.productDataLogic.getProductSuppliers(this.selectedProduct.getId());
      this.barcodes = this.productDataLogic.getProductBarcodes(this.selectedProduct.getId());
      this.populatePricing();
      this.populateBarcodesTable();
      this.populateSuppliersTable();
      this.populateStockLevelsTable();
      this.updateButton();
      this.tswtVariablePrice.setDisable(this.productInfo.getId().contains("giftcard") || this.productInfo.getId().contains("loyalty"));
      this.APIcall = false;
      dirty.setValue(false);
      this.actionButtons.enableAfterNewRecord(this.mainTable);
   }

   public void populateProductTable() {
      this.productData = FXCollections.observableArrayList(new ArrayList(this.productDataLogic.getProductsForTableView(this.localGuid)));
      this.filteredList = new FilteredList(this.productData, t -> true);
      this.updateTable();
      this.nextRow();
   }

   private void populateScreenButton(String display) {
      this.lblButton.setPrefSize((double)SystemProperty.PRODUCTBUTTONWIDTH, (double)SystemProperty.PRODUCTBUTTONHEIGHT);
      this.lblButton.setMaxSize((double)SystemProperty.PRODUCTBUTTONWIDTH, (double)SystemProperty.PRODUCTBUTTONHEIGHT);
      this.lblButton.setMinSize((double)SystemProperty.PRODUCTBUTTONWIDTH, (double)SystemProperty.PRODUCTBUTTONHEIGHT);
      this.rbLine1N.setSelected(true);
      this.rbLine2N.setSelected(true);
      this.tswtItalicL1.setSelected(false);
      this.tswtItalicL2.setSelected(false);
      this.tswtBoldL1.setSelected(false);
      this.tswtBoldL2.setSelected(false);
      this.txtDisplayLine1.setText("");
      this.txtDisplayLine2.setText("");
      if (display != null) {
         StringBuilder tmpSB = new StringBuilder(display);
         StringBuilder tmpLine1 = new StringBuilder();
         StringBuilder tmpLine2 = new StringBuilder();
         this.displayMap = new LinkedHashMap<>();
         if (tmpSB.indexOf("<html>") == -1) {
            this.txtDisplayLine1.setText(display);
         } else {
            tmpSB.delete(tmpSB.indexOf("<html>"), tmpSB.indexOf("<html>") + 6);
            if (tmpSB.indexOf("<br>") == -1) {
               this.txtDisplayLine1.setText(this.mapString(tmpSB).trim());

               for (Iterator it = this.displayMap.entrySet().iterator(); it.hasNext(); it.remove()) {
                  Entry pair = (Entry)it.next();
                  if (pair.getKey().toString().equals("<font size=-2>") && (Integer)pair.getValue() >= 0) {
                     this.rbLine1S2.setSelected(true);
                  }

                  if (pair.getKey().toString().equals("<font size=-1>") && (Integer)pair.getValue() >= 0) {
                     this.rbLine1S1.setSelected(true);
                  }

                  if (pair.getKey().toString().equals("<font size=+1>") && (Integer)pair.getValue() >= 0) {
                     this.rbLine1L1.setSelected(true);
                  }

                  if (pair.getKey().toString().equals("<font size=+2>") && (Integer)pair.getValue() >= 0) {
                     this.rbLine1L2.setSelected(true);
                  }

                  if (pair.getKey().toString().equals("<i>") && (Integer)pair.getValue() >= 0) {
                     this.tswtItalicL1.setSelected(true);
                  }

                  if (pair.getKey().toString().equals("<b>") && (Integer)pair.getValue() >= 0) {
                     this.tswtBoldL1.setSelected(true);
                  }
               }
            } else {
               tmpLine1.append(tmpSB, 0, tmpSB.indexOf("<br>"));
               this.txtDisplayLine1.setText(this.mapString(tmpLine1).trim());

               for (Iterator it = this.displayMap.entrySet().iterator(); it.hasNext(); it.remove()) {
                  Entry pairx = (Entry)it.next();
                  if (pairx.getKey().toString().equals("<font size=-2>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1S2.setSelected(true);
                     this.rbLine2S2.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("<font size=-1>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1S1.setSelected(true);
                     this.rbLine2S1.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("<font size=+1>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1L1.setSelected(true);
                     this.rbLine2L1.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("<font size=+2>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1L2.setSelected(true);
                     this.rbLine2L2.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("</font size=-2>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1S2.setSelected(true);
                     this.rbLine2N.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("</font size=-1>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1S1.setSelected(true);
                     this.rbLine2N.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("</font size=+1>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1L1.setSelected(true);
                     this.rbLine2N.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("</font size=+2>") && (Integer)pairx.getValue() != -1) {
                     this.rbLine1L2.setSelected(true);
                     this.rbLine2N.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("<i>") && (Integer)pairx.getValue() != -1) {
                     this.tswtItalicL1.setSelected(true);
                     this.tswtItalicL2.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("</i>") && (Integer)pairx.getValue() != -1) {
                     this.tswtItalicL2.setSelected(false);
                  }

                  if (pairx.getKey().toString().equals("<b>") && (Integer)pairx.getValue() != -1) {
                     this.tswtBoldL1.setSelected(true);
                     this.tswtBoldL2.setSelected(true);
                  }

                  if (pairx.getKey().toString().equals("</b>") && (Integer)pairx.getValue() != -1) {
                     this.tswtBoldL2.setSelected(false);
                  }
               }

               tmpLine2.append(tmpSB, tmpSB.indexOf("<br>") + 4, tmpSB.length());
               this.txtDisplayLine2.setText(this.mapString(tmpLine2).trim());

               for (Iterator var7 = this.displayMap.entrySet().iterator(); var7.hasNext(); var7.remove()) {
                  Entry pairxx = (Entry)var7.next();
                  if (pairxx.getKey().toString().equals("<font size=-2>") && (Integer)pairxx.getValue() >= 0) {
                     this.rbLine2S2.setSelected(true);
                  }

                  if (pairxx.getKey().toString().equals("<font size=-1>") && (Integer)pairxx.getValue() >= 0) {
                     this.rbLine2S1.setSelected(true);
                  }

                  if (pairxx.getKey().toString().equals("<font size=+1>") && (Integer)pairxx.getValue() >= 0) {
                     this.rbLine2L1.setSelected(true);
                  }

                  if (pairxx.getKey().toString().equals("<font size=+2>") && (Integer)pairxx.getValue() >= 0) {
                     this.rbLine2L2.setSelected(true);
                  }

                  if (pairxx.getKey().toString().equals("<i>") && (Integer)pairxx.getValue() >= 0) {
                     this.tswtItalicL2.setSelected(true);
                  }

                  if (pairxx.getKey().toString().equals("<b>") && (Integer)pairxx.getValue() >= 0) {
                     this.tswtBoldL2.setSelected(true);
                  }
               }
            }
         }
      }
   }

   private void populateSuppliersTable() {
      this.supplierTable.getItems().clear();
      if (this.productSuppliers != null) {
         this.productSuppliers
            .forEach(
               supplier -> this.supplierData
                     .add(
                        new ProductSupplierInfo(
                           supplier.getId(),
                           supplier.getProductId(),
                           supplier.getSupplierId(),
                           supplier.getSupplierName(),
                           supplier.getSupplierCode(),
                           supplier.getUomId(),
                           supplier.getUomFactor(),
                           supplier.getUomDescription(),
                           supplier.getPackCost(),
                           supplier.getTaxRate()
                        )
                     )
            );
      }
   }

   private void populateStockLevelsTable() {
      this.stockLevelTable.getItems().clear();
      this.stocking = this.productDataLogic.getProductLevelsList(this.selectedProduct.getId(), this.localGuid);
      List<uk.chromis.administration.KeyedData> locations = new ArrayList<>(
         uk.chromis.administration.DatabaseLogic.getInstance().getKeyedData("select id, name from locations order by name")
      );
      this.stocking
         .forEach(
            level -> {
               this.stockLevelsData
                  .add(
                     new StockLevelInfo(
                        level.getId(),
                        level.getLocationId(),
                        level.getLocationName(),
                        level.getProduct(),
                        level.getCurrent(),
                        level.getMinimum(),
                        level.getMaximum(),
                        level.getSiteguid()
                     )
                  );
               locations.remove(new uk.chromis.administration.KeyedData(level.getLocationId(), level.getLocationName()));
            }
         );
      this.cmbLocations.setItemList(locations);
   }

   private static final String CODE128 = "CODE128";
   private static final String EAN13 = "EAN13";
   private final RadioMenuItem defaultCode128 = new RadioMenuItem("Al guardar sin código: Code 128 con el nombre");
   private final RadioMenuItem defaultEan13 = new RadioMenuItem("Al guardar sin código: EAN-13 interno");
   private final MenuItem lengthItem = new MenuItem();
   private final ContextMenu codeMenu = new ContextMenu();

   /**
    * "Generar código" opens a menu to make either kind of code and to set the
    * kind made when a product is saved without one and the Code 128 length
    * (shared by every Admin through pos_settings).
    */
   private void buildCodeMenu() {
      MenuItem code128 = new MenuItem("Generar Code 128 con el nombre");
      code128.setOnAction(e -> this.generate(CODE128));
      MenuItem ean13 = new MenuItem("Generar EAN-13 interno (20...)");
      ean13.setOnAction(e -> this.generate(EAN13));
      ToggleGroup group = new ToggleGroup();
      this.defaultCode128.setToggleGroup(group);
      this.defaultEan13.setToggleGroup(group);
      this.defaultCode128.setOnAction(e -> this.saveCodeSetting(PosSettings.PRODUCT_CODE_TYPE, CODE128));
      this.defaultEan13.setOnAction(e -> this.saveCodeSetting(PosSettings.PRODUCT_CODE_TYPE, EAN13));
      this.lengthItem.setOnAction(e -> this.askLength());
      this.codeMenu.getItems().setAll(code128, ean13, new SeparatorMenuItem(), this.defaultCode128, this.defaultEan13, this.lengthItem);
   }

   private void refreshCodeMenu() {
      boolean ean = EAN13.equals(this.codeType());
      this.defaultEan13.setSelected(ean);
      this.defaultCode128.setSelected(!ean);
      this.lengthItem.setText("Largo máximo del Code 128: " + this.codeLength() + "...");
   }

   private String codeSetting(String name, String def) {
      try {
         return PosSettings.get(SessionFactory.getInstance().getSession().getConnection(), name, def);
      } catch (SQLException var4) {
         Logger.getLogger(ProductController.class.getName()).log(java.util.logging.Level.WARNING, "No se pudo leer la configuración de códigos", var4);
         return def;
      }
   }

   private void saveCodeSetting(String name, String value) {
      try {
         PosSettings.put(SessionFactory.getInstance().getSession().getConnection(), name, value);
      } catch (SQLException var4) {
         Logger.getLogger(ProductController.class.getName()).log(java.util.logging.Level.WARNING, "No se pudo guardar la configuración de códigos", var4);
      }
      this.refreshCodeMenu();
   }

   private String codeType() {
      return EAN13.equalsIgnoreCase(this.codeSetting(PosSettings.PRODUCT_CODE_TYPE, CODE128)) ? EAN13 : CODE128;
   }

   private int codeLength() {
      return ConsonantCode.parseLength(this.codeSetting(PosSettings.PRODUCT_CODE_LENGTH, String.valueOf(ConsonantCode.DEFAULT_LENGTH)));
   }

   private void askLength() {
      TextInputDialog dialog = new TextInputDialog(String.valueOf(this.codeLength()));
      dialog.setTitle("Código Code 128");
      dialog.setHeaderText(null);
      dialog.setContentText("Largo máximo (" + ConsonantCode.MIN_LENGTH + " a " + ConsonantCode.MAX_LENGTH + " caracteres):");
      dialog.initOwner(this.apAnchor.getScene().getWindow());
      dialog.showAndWait().ifPresent(text -> {
         try {
            int n = Integer.parseInt(text.trim());
            this.saveCodeSetting(PosSettings.PRODUCT_CODE_LENGTH, String.valueOf(ConsonantCode.clampLength(n)));
         } catch (NumberFormatException var4) {
            Dialogs.showDialog(8, "\"" + text + "\" no es un número.", (Stage)this.apAnchor.getScene().getWindow(), true);
         }
      });
   }

   /** Button click: shows the menu under the button. */
   public void generateCode() {
      this.refreshCodeMenu();
      this.codeMenu.show(this.btnGenerateCode, Side.BOTTOM, 0.0, 0.0);
   }

   private void generate(String type) {
      String code = EAN13.equals(type) ? "" : this.consonantCodeBase();
      if (!EAN13.equals(type) && code.isEmpty()) {
         Dialogs.showDialog(8, "Escribe primero la descripción (o la referencia) del producto: el código se arma con sus consonantes.",
            (Stage)this.apAnchor.getScene().getWindow(), true);
         return;
      }

      String current = this.txtBarcode.getText() == null ? "" : this.txtBarcode.getText().trim();
      if (!current.isEmpty()
         && Dialogs.showDialog(
               7,
               "Este producto ya tiene el código " + current + ".\n¿Reemplazarlo por un código "
                  + (EAN13.equals(type) ? "EAN-13 interno" : "Code 128 con el nombre") + "?",
               (Stage)this.apAnchor.getScene().getWindow(),
               true
            )
            != 0) {
         return;
      }

      code = EAN13.equals(type) ? this.nextEan13() : this.uniqueConsonantCode(code);
      if (code == null) {
         return;
      }
      this.txtBarcode.setText(code);
      this.txtBarcode.requestFocus();
      this.txtBarcode.positionCaret(code.length());
   }

   /** The code made when the product is saved without one, or null. */
   private String automaticCode() {
      if (EAN13.equals(this.codeType())) {
         return this.nextEan13();
      }
      String code = this.consonantCodeBase();
      return code.isEmpty() ? null : this.uniqueConsonantCode(code);
   }

   private String nextEan13() {
      try {
         Connection con = SessionFactory.getInstance().getSession().getConnection();
         return InternalEan13.next(con, this.selectedProduct == null ? null : this.selectedProduct.getId());
      } catch (SQLException var2) {
         Logger.getLogger(ProductController.class.getName()).log(java.util.logging.Level.WARNING, "No se pudo calcular el EAN-13", var2);
         Dialogs.showDialog(8, "No se pudo calcular el siguiente EAN-13: " + var2.getMessage(), (Stage)this.apAnchor.getScene().getWindow(), true);
         return null;
      }
   }

   /** Consonants and digits of the ticket description, or of the reference when it has none. */
   private String consonantCodeBase() {
      int length = this.codeLength();
      String code = ConsonantCode.fromName(this.txtDescription.getText(), length);
      return code.isEmpty() ? ConsonantCode.fromName(this.txtReference.getText(), length) : code;
   }

   private String uniqueConsonantCode(String code) {
      try {
         return ConsonantCode.unique(
            SessionFactory.getInstance().getSession().getConnection(), code, this.selectedProduct == null ? null : this.selectedProduct.getId(),
            this.codeLength()
         );
      } catch (SQLException var3) {
         Logger.getLogger(ProductController.class.getName()).log(java.util.logging.Level.WARNING, "No se pudo revisar si el código ya existe", var3);
         return code;
      }
   }

   private void resetPricingPanel(int count) {
      this.APIcall = true;
      if (count >= 1) {
         this.txtTierQty5.setText("0");
         this.txtTierDiscount5.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPrice5.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPriceInc5.setText(this.localeString(Double.valueOf(0.0)));
      }

      if (count >= 2) {
         this.txtTierQty4.setText("0");
         this.txtTierDiscount4.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPrice4.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPriceInc4.setText(this.localeString(Double.valueOf(0.0)));
      }

      if (count >= 3) {
         this.txtTierQty3.setText("0");
         this.txtTierDiscount3.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPrice3.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPriceInc3.setText(this.localeString(Double.valueOf(0.0)));
      }

      if (count >= 4) {
         this.txtTierQty2.setText("0");
         this.txtTierDiscount2.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPrice2.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPriceInc2.setText(this.localeString(Double.valueOf(0.0)));
      }

      if (count >= 5) {
         this.txtTierQty1.setText("0");
         this.txtTierDiscount1.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPrice1.setText(this.localeString(Double.valueOf(0.0)));
         this.txtTierPriceInc1.setText(this.localeString(Double.valueOf(0.0)));
      }

      this.APIcall = false;
   }

   private BufferedImage resizeImage(BufferedImage img, int m_maxsizewith, int m_maxsizeheight) {
      int myheight = img.getHeight();
      int mywidth = img.getWidth();
      if (myheight > m_maxsizeheight) {
         mywidth = mywidth * m_maxsizeheight / myheight;
         myheight = m_maxsizeheight;
      }

      if (mywidth > m_maxsizewith) {
         myheight = myheight * m_maxsizewith / mywidth;
         mywidth = m_maxsizewith;
      }

      BufferedImage thumb = new BufferedImage(mywidth, myheight, 6);
      double scalex = (double)mywidth / (double)img.getWidth(null);
      double scaley = (double)myheight / (double)img.getHeight(null);
      Graphics2D g2d = thumb.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.fillRect(0, 0, mywidth, myheight);
      if (scalex < scaley) {
         g2d.drawImage(
            img, 0, (int)(((double)myheight - (double)img.getHeight(null) * scalex) / 2.0), mywidth, (int)((double)img.getHeight(null) * scalex), null
         );
      } else {
         g2d.drawImage(img, (int)(((double)mywidth - (double)img.getWidth(null) * scaley) / 2.0), 0, (int)((double)img.getWidth(null) * scaley), myheight, null);
      }

      g2d.dispose();
      return thumb;
   }

   private void setButtonTextListener(TextField txtField) {
      txtField.textProperty().addListener((observable, oldValue, newValue) -> {
         this.updateButton();
         dirty.set(true);
      });
   }

   private void setButtonToggleListener(ToggleSwitch toggle) {
      toggle.selectedProperty().addListener(new ChangeListener<Boolean>() {
         public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            ProductController.this.updateButton();
            BasePanelController.dirty.set(true);
         }
      });
   }

   private void setToggleGroupListener(ToggleGroup toggleGroup) {
      toggleGroup.selectedToggleProperty().addListener((ov, old_toggle, new_toggle) -> {
         if (toggleGroup.getSelectedToggle() != null) {
            this.updateButton();
            dirty.set(true);
         }
      });
   }

   private void updateButton() {
      String displayStr = this.buildDisplayString();
      BufferedImage img = null;
      if (this.showImage) {
         if (this.imageEd.getImage() == null) {
            img = IconFactory.getBufferedImage("no-picture80.png");
         } else {
            img = this.imageEd.getImage();
         }
      } else {
         img = IconFactory.getBufferedImage("package.png");
      }

      new Text(this.txtDisplayLine1.getText());
      StackPane pane = new StackPane();
      SwingNode swingNode = new SwingNode();
      pane.getChildren().clear();
      pane.getChildren().add(swingNode);
      ThumbNailBuilder tnbbutton = new ThumbNailBuilder(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT, img);
      JButton btn = new JButton(new ImageIcon(tnbbutton.getThumbNailText(img, displayStr)));
      btn.setPreferredSize(new Dimension(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT));
      btn.setMaximumSize(new Dimension(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT));
      btn.setMinimumSize(new Dimension(SystemProperty.PRODUCTBUTTONWIDTH, SystemProperty.PRODUCTBUTTONHEIGHT));
      swingNode.setContent(btn);
      pane.setMaxSize((double)SystemProperty.PRODUCTBUTTONWIDTH, (double)SystemProperty.PRODUCTBUTTONHEIGHT);
      this.btnFlow.getChildren().clear();
      this.btnFlow.getChildren().add(pane);
   }

   @Override
   public void addTableRow(Object o) {
      ProductInfo p = (ProductInfo)o;
      this.productData
         .add(
            new ProductTableInfo(
               p.getId(),
               p.getReference(),
               p.getCode(),
               p.getName(),
               p.getAlias(),
               this.cmbCategory.getDescription(p.getCategory()),
               this.localeString(p.getPriceSell()),
               String.format(currentLocale, "%.3f", p.getPriceBuy()),
               this.localeString(p.getPriceSellInc()),
               p.isIngredient(),
               p.isCom()
            )
         );
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.productsTitle") + " :- " + this.mainTable.getItems().size() + " records");
      this.lblPanelName.setText(p.getName());
      this.filteredList = new FilteredList(this.productData, t -> true);
      this.updateTable();
   }

   @Override
   public void deleteRecord() {
      ProductTableInfo product = (ProductTableInfo)this.mainTable.getItems().get(this.mainTable.getSelectionModel().getSelectedIndex());
      this.selectedProduct = this.productDataLogic.getProductInfo(product.getId());
      if (this.selectedProduct.isSystemObject()) {
         Dialogs.showDialog(
            1, LocalResource.getString("dialog.deleteFailedContextSystem"), (Stage)this.apAnchor.getScene().getWindow(), this.selectedProduct.getName(), true
         );
      } else {
         if (Dialogs.showDialog(
               7, LocalResource.getString("dialog.questionDelete"), (Stage)this.apAnchor.getScene().getWindow(), this.selectedProduct.getName(), true
            )
            == 0) {
            try {
               this.productDataLogic.removeStockCurrentIfNoStock(this.selectedProduct.getId(), this.selectedProduct.getSiteGuid());
               this.productDataLogic.removeProduct(this.selectedProduct.getId(), this.selectedProduct.getSiteGuid());
               this.productDataLogic.removeProductBarcodes(this.selectedProduct.getId(), this.selectedProduct.getSiteGuid());
               this.productDataLogic.removeProductSuppliers(this.selectedProduct.getId(), this.selectedProduct.getSiteGuid());
               this.productDataLogic.removeTierPricing(this.selectedProduct.getId(), this.selectedProduct.getSiteGuid());
               this.resetFields();
               this.populateProductTable();
            } catch (BasicException var3) {
               Dialogs.showDialog(8, LocalResource.getString("dialog.deleteFailedContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
            }
         }
      }
   }

   @Override
   public void mainTableListener() {
      this.mainTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
         if (newSelection != null) {
            this.checkDirty();
            this.mainTable.requestFocus();
            this.mainTable.getFocusModel().focus(this.mainTable.getSelectionModel().getSelectedIndex());
            this.populateProductFields();
            this.selectedRow = this.mainTable.getSelectionModel().getSelectedIndex();
         } else {
            this.lblPanelName.setText("");
         }
      });
   }

   @Override
   public void newRecord() {
      this.resetFields();
      this.lblPanelName.setText(LocalResource.getString("lbl.newProduct"));
      this.selectedProduct = new ProductInfo();
      this.tpDataPane.getSelectionModel().selectFirst();
      this.actionButtons.disableForNewRecord(this.mainTable);
      this.updateButton();
      dirty.setValue(false);
   }

   @Override
   public void nextRow() {
      this.checkDirty();
      this.mainTable.getSelectionModel().selectBelowCell();
   }

   @Override
   public void previousRow() {
      this.checkDirty();
      this.mainTable.getSelectionModel().selectAboveCell();
   }

   @Override
   public void refreshRecords() {
      this.checkDirty();
      this.productData = FXCollections.observableArrayList(new ArrayList(this.productDataLogic.getProductsForTableView(this.localGuid)));
      this.filteredList = new FilteredList(this.productData, t -> true);
      this.mainTable.getSortOrder().clear();
      this.mainTable.getSelectionModel().selectFirst();
      this.updateTable();
      this.tswtRetireProduct.setSelected(false);
      this.actionButtons.enableAfterNewRecord(this.mainTable);
      dirty.setValue(false);
   }

   @Override
   public void resetFields() {
      this.APIcall = true;
      this.tswtCostPricing.setSelected(true);
      this.txtReference.setText("");
      this.txtReference.setDisable(false);
      this.txtBarcode.setText("");
      this.txtDescription.setText("");
      this.txtShortName.setText("");
      this.cmbCategory.getSelectionModel().clearSelection();
      this.cmbCategory.setDisable(false);
      this.cmbAttributes.getSelectionModel().clearSelection();
      this.tswtMandatoryAttributes.setSelected(false);
      this.cmbTaxCategory.getSelectionModel().selectFirst();
      this.cmbBuyTaxCategory
         .setSelectedItem(uk.chromis.administration.DatabaseLogic.getInstance().getString("select id from taxcategories where systemobject = true limit 1"));
      this.txtSellPrice.setText(this.localeString(Double.valueOf(0.0)));
      this.txtSellPriceInc.setText(this.localeString(Double.valueOf(0.0)));
      this.txtBuyPrice.setText(this.localeString(Double.valueOf(0.0)));
      this.txtGrossMargin.setText(this.localeString(Double.valueOf(0.0)));
      this.txtGrossProfit.setText(this.localeString(Double.valueOf(0.0)));
      this.txtCommission.setText(this.localeString(Double.valueOf(0.0)));
      this.imageEd.setImage(null);
      this.txtEarnValue.setText("0");
      this.txtBurnValue.setText("0");
      this.txtLoyaltyMultiplier.setText(this.localeString(Double.valueOf(1.0)));
      this.tswtInCatalog.setSelected(true);
      this.txtCatalogOrder.setText("");
      this.tswtWarrantyReceipt.setSelected(false);
      this.tswtVariablePrice.setSelected(false);
      this.tswtAuxiliary.setSelected(false);
      this.tswtCanHaveAuxiliary.setSelected(false);
      this.tswtIsServiceItem.setSelected(false);
      this.tswtIsScale.setSelected(false);
      this.tswtAlwaysAvailable.setSelected(false);
      this.tswtCanDiscount.setSelected(false);
      this.tswtAgeWarning.setSelected(false);
      this.txtAge.setText("");
      this.tswtManageStockLevel.setSelected(true);
      this.txtKitchenDescription.setText("");
      this.tswtPrinter.setSelected(false);
      this.tswtScreen.setSelected(false);
      this.spinRemoteScreen.getValueFactory().setValue(1);
      this.cmbRemotePrinter.getSelectionModel().clearSelection();
      this.txtDisplayLine1.setText("");
      this.txtDisplayLine2.setText("");
      this.rbLine1N.setSelected(true);
      this.rbLine2N.setSelected(true);
      this.tswtItalicL1.setSelected(false);
      this.tswtItalicL2.setSelected(false);
      this.tswtBoldL1.setSelected(false);
      this.tswtBoldL2.setSelected(false);
      this.tswtButtonImage.setSelected(false);
      this.tswtIngredient.setSelected(false);
      this.cmbUnitsOfMeasure.setSelectedItem("000-each");
      this.cmbStockUOM.setSelectedItem("000-each");
      this.cmbSellingUOM.setSelectedItem("000-each");
      this.cmbBuyUOM.setSelectedItem("000-each");
      this.cmbSupplierUOM.setSelectedItem("000-each");
      this.barcodeTable.getItems().clear();
      this.supplierTable.getItems().clear();
      this.stockLevelTable.getItems().clear();
      this.resetPricingPanel(5);
      this.txtProperties
         .setText("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">");
      this.systemobject = false;
      this.txtAddBarcode.setDisable(this.systemobject);
      this.cmbUnitsOfMeasure.setDisable(this.systemobject);
      this.txtSellingQty.setDisable(this.systemobject);
      this.btnAddPLU.setDisable(this.systemobject);
      this.cmbSuppliers.setDisable(this.systemobject);
      this.cmbSuppliers.getSelectionModel().clearSelection();
      this.txtSupplierStockCode.setText("");
      this.txtPackCost.setText(this.localeString("1.00"));
      this.txtSupplierStockCode.setDisable(this.systemobject);
      this.cmbSupplierUOM.setDisable(this.systemobject);
      this.btnAddSupplier.setDisable(this.systemobject);
      this.txtTierQty1.setDisable(this.systemobject);
      this.txtTierQty2.setDisable(this.systemobject);
      this.txtTierQty3.setDisable(this.systemobject);
      this.txtTierQty4.setDisable(this.systemobject);
      this.txtTierQty5.setDisable(this.systemobject);
      this.tswtRetireProduct.setSelected(false);
      this.APIcall = false;
      dirty.setValue(false);
   }

   @Override
   public Boolean saveRecord() {
      // Products without a barcode get the default automatic code instead of failing the "Barcode" check
      if (this.txtBarcode.getText() == null || this.txtBarcode.getText().isBlank()) {
         String code = this.automaticCode();
         if (code != null) {
            this.txtBarcode.setText(code);
         }
      }

      if (!this.checkCompulsoryFields(this.fields, (Stage)this.apAnchor.getScene().getWindow())) {
         return false;
      } else {
         if (this.selectedProduct.getId() == null && this.txtDisplayLine1.getText().isEmpty()) {
            this.txtDisplayLine1.setText(this.txtDescription.getText());
         }

         Object[] record = new Object[ProductData.FIELD_COUNT];
         record[ProductData.INDEX_REFERENCE] = this.txtReference.getText();
         record[ProductData.INDEX_CODE] = this.txtBarcode.getText();
         record[ProductData.INDEX_CODETYPE] = BarcodeValidator.BarcodeValidate(this.txtBarcode.getText());
         record[ProductData.INDEX_NAME] = this.txtDescription.getText();
         record[ProductData.INDEX_PRICEBUY] = this.parseLocaleString(this.txtBuyPrice.getText());
         record[ProductData.INDEX_PRICESELL] = this.parseLocaleString(this.txtSellPrice.getText());
         record[ProductData.INDEX_PRICESELLINC] = this.parseLocaleString(this.txtSellPriceInc.getText());
         record[ProductData.INDEX_CATEGORY] = this.cmbCategory.getSelectedKey();
         record[ProductData.INDEX_TAXCAT] = this.cmbTaxCategory.getSelectedKey();
         record[ProductData.INDEX_ATTRIBUTESET_ID] = this.cmbAttributes.getSelectedKey();
         record[ProductData.INDEX_ISCOM] = this.tswtAuxiliary.isSelected();
         record[ProductData.INDEX_ISSCALE] = this.tswtIsScale.isSelected();
         record[ProductData.INDEX_ISKITCHEN] = this.tswtPrinter.isSelected();
         record[ProductData.INDEX_KITCHENDESCRIPTION] = this.txtKitchenDescription.getText();
         record[ProductData.INDEX_ISSERVICE] = this.tswtIsServiceItem.isSelected();
         record[ProductData.INDEX_DISPLAY] = this.buildDisplayString();

         try {
            record[ProductData.INDEX_ATTRIBUTES] = Formats.BYTEA.parseValue(this.txtProperties.getText());
         } catch (BasicException var8) {
            record[ProductData.INDEX_ATTRIBUTES] = null;
         }

         record[ProductData.INDEX_ISVPRICE] = this.tswtVariablePrice.isSelected();
         record[ProductData.INDEX_ISVERPATRIB] = this.tswtMandatoryAttributes.isSelected();
         record[ProductData.INDEX_WARRANTY] = this.tswtWarrantyReceipt.isSelected();
         record[ProductData.INDEX_IMAGE] = this.imageEd.getImage();
         record[ProductData.INDEX_ALIAS] = this.txtShortName.getText();
         record[ProductData.INDEX_ALWAYSAVAILABLE] = this.tswtAlwaysAvailable.isSelected();
         record[ProductData.INDEX_CANDISCOUNT] = this.tswtCanDiscount.isSelected() ? this.discountSpinner.getValue() : -1.0;
         record[ProductData.INDEX_ISCATALOG] = this.tswtInCatalog.isSelected();
         record[ProductData.INDEX_CATORDER] = this.txtCatalogOrder.getText().isEmpty() ? 0 : Integer.valueOf(this.txtCatalogOrder.getText());
         record[ProductData.INDEX_MANAGESTOCK] = this.tswtManageStockLevel.isSelected();
         record[ProductData.INDEX_COMMISSION] = this.parseLocaleString(this.txtCommission.getText());
         record[ProductData.INDEX_REMOTEDISPLAY] = this.tswtScreen.isSelected();
         record[ProductData.INDEX_BURNVALUE] = Integer.valueOf(this.txtBurnValue.getText());
         record[ProductData.INDEX_EARNVALUE] = Integer.valueOf(this.txtEarnValue.getText());
         record[ProductData.INDEX_LOYALTYMULTIPLIER] = this.parseLocaleString(this.txtLoyaltyMultiplier.getText());
         record[ProductData.INDEX_SELLUNIT] = this.cmbSellingUOM.getSelectedKey();
         record[ProductData.INDEX_STOCKUNIT] = this.cmbStockUOM.getSelectedKey();
         record[ProductData.INDEX_BUYUNIT] = this.cmbBuyUOM.getSelectedKey();
         record[ProductData.INDEX_AGERESTRICTED] = this.tswtAgeWarning.isSelected()
            ? (Integer.valueOf(this.txtAge.getText()) == 0 ? -1 : Integer.valueOf(this.txtAge.getText()))
            : -1;
         record[ProductData.INDEX_ISRECIPE] = false;
         record[ProductData.INDEX_ISINGREDIENT] = this.tswtIngredient.isSelected();
         record[ProductData.INDEX_RETIRED] = this.tswtRetireProduct.isSelected();
         record[ProductData.INDEX_AUXILIARYPERMITTED] = this.tswtCanHaveAuxiliary.isSelected();
         record[ProductData.INDEX_SYSTEMOBJECT] = false;
         record[ProductData.INDEX_REMOTEPRINTER] = this.cmbRemotePrinter.getSelectedKey();
         record[ProductData.INDEX_DISPLAYID] = this.spinRemoteScreen.getValue();
         record[ProductData.INDEX_BUYTAX] = this.cmbBuyTaxCategory.getSelectedKey();
         record[ProductData.INDEX_TABLEVIEWBARCODES] = this.barcodeTable;
         record[ProductData.INDEX_TABLEVIEWSUPPLIERS] = this.supplierTable;
         record[ProductData.INDEX_STOCKLEVELS] = this.stockLevelTable;
         this.prices = new ArrayList<>();
         this.prices
            .add(
               new TierPricing(
                  Integer.valueOf(this.txtTierQty1.getText()),
                  Integer.valueOf(this.txtTierQty1.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPriceInc1.getText()),
                  Integer.valueOf(this.txtTierQty1.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPrice1.getText())
               )
            );
         this.prices
            .add(
               new TierPricing(
                  Integer.valueOf(this.txtTierQty2.getText()),
                  Integer.valueOf(this.txtTierQty2.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPriceInc2.getText()),
                  Integer.valueOf(this.txtTierQty2.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPrice2.getText())
               )
            );
         this.prices
            .add(
               new TierPricing(
                  Integer.valueOf(this.txtTierQty3.getText()),
                  Integer.valueOf(this.txtTierQty3.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPriceInc3.getText()),
                  Integer.valueOf(this.txtTierQty3.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPrice3.getText())
               )
            );
         this.prices
            .add(
               new TierPricing(
                  Integer.valueOf(this.txtTierQty4.getText()),
                  Integer.valueOf(this.txtTierQty4.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPriceInc4.getText()),
                  Integer.valueOf(this.txtTierQty4.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPrice4.getText())
               )
            );
         this.prices
            .add(
               new TierPricing(
                  Integer.valueOf(this.txtTierQty5.getText()),
                  Integer.valueOf(this.txtTierQty5.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPriceInc5.getText()),
                  Integer.valueOf(this.txtTierQty5.getText()) == 0 ? 0.0 : this.parseLocaleString(this.txtTierPrice5.getText())
               )
            );
         List<TierPricing> filtered = this.prices.stream().filter(b -> b.getTierQty() == 0).collect(Collectors.toList());
         this.prices.removeAll(filtered);
         Collections.sort(this.prices, new Comparator<TierPricing>() {
            public int compare(TierPricing qty1, TierPricing qty2) {
               return qty1.getTierQty().compareTo(qty2.getTierQty());
            }
         });

         for (int i = this.prices.size(); i < 5; i++) {
            this.prices.add(new TierPricing(0, 0.0, 0.0));
         }

         record[ProductData.INDEX_PRICING] = this.prices;
         if (this.selectedProduct.getId() == null) {
            String id = UUID.randomUUID().toString();
            record[ProductData.INDEX_ID] = id;
            record[ProductData.INDEX_SITEGUID] = uk.chromis.administration.DatabaseLogic.getInstance().getSiteGUID();
            record[ProductData.INDEX_AVERAGECOST] = this.parseLocaleString(this.txtBuyPrice.getText());
            List<uk.chromis.administration.KeyedData> results = this.productDataLogic
               .getProductFieldChecks(
                  this.txtReference.getText(), this.txtBarcode.getText(), this.txtDescription.getText(), (String)record[ProductData.INDEX_ID]
               );
            if (!this.checkForDuplicateEntries(results, this.entries)) {
               return false;
            }

            if (this.txtDisplayLine1.getText().isEmpty()) {
               this.txtDisplayLine1.setText(this.txtDescription.getText());
            }

            try {
               this.productDataLogic.productInsert().exec(record);
               this.saveCostPricing(id);
               dirty.setValue(false);
               if (!this.tswtRetireProduct.isSelected()) {
                  this.addTableRow(this.productDataLogic.getProduct(id));
               } else {
                  this.tswtRetireProduct.setSelected(false);
               }
            } catch (BasicException var7) {
               Dialogs.errorAlert(var7, "Save Error");
               return false;
            }
         } else {
            record[ProductData.INDEX_ID] = this.selectedProduct.getId();
            record[ProductData.INDEX_SITEGUID] = this.selectedProduct.getSiteGuid();
            List<uk.chromis.administration.KeyedData> resultsx = this.productDataLogic
               .getProductFieldChecks(this.txtReference.getText(), this.txtBarcode.getText(), this.txtDescription.getText(), this.selectedProduct.getId());
            if (!this.checkForDuplicateEntries(resultsx, this.entries)) {
               return false;
            }

            try {
               this.productDataLogic.productUpdate().exec(record);
               this.saveCostPricing(this.selectedProduct.getId());
               dirty.setValue(false);
               if (this.tswtRetireProduct.isSelected()) {
                  this.resetFields();
                  this.populateProductTable();
               } else {
                  this.updateRow(this.selectedRow, this.productDataLogic.getProduct(this.selectedProduct.getId()));
               }

               this.selectedRow = this.mainTable.getSelectionModel().getSelectedIndex();
            } catch (BasicException var6) {
               Dialogs.errorAlert(var6, "Save Error");
               return false;
            }
         }

         dirty.setValue(false);
         return true;
      }
   }

   // Products switched off keep their normal price for cost-plus customers in Chromis POS
   private boolean isCostPricingExcluded(String productId) {
      try {
         return CostPricingStore.isExcluded(SessionFactory.getInstance().getSession().getConnection(), productId);
      } catch (SQLException ex) {
         Logger.getLogger(ProductController.class.getName()).log(Level.SEVERE, null, ex);
         return false;
      }
   }

   private void saveCostPricing(String productId) {
      try {
         CostPricingStore.setExcluded(SessionFactory.getInstance().getSession().getConnection(), productId, !this.tswtCostPricing.isSelected());
      } catch (SQLException ex) {
         Logger.getLogger(ProductController.class.getName()).log(Level.SEVERE, null, ex);
         Dialogs.showDialog(4, "No se pudo guardar la opción de precio sobre costo:\n" + ex.getMessage(), (Stage)this.apAnchor.getScene().getWindow(), true);
      }
   }

   @Override
   public void searchRecords() {
      this.td = this.createTextDialog(this.actionButtons, "txtDialog.searchProducts", Double.valueOf(400.0));
      Optional<String> result = this.td.showAndWait();
      if (result.isPresent()) {
         this.searchStr = result.get();
         if (!this.searchStr.isBlank()) {
            if (this.searchStr.startsWith("!") && this.searchStr.length() > 1) {
               this.resetFields();
               if (this.productDataLogic.getProductsByValueSearch(this.searchStr.substring(1), this.localGuid) != null) {
                  this.search = this.productDataLogic.getProductsByValueSearch(this.searchStr.substring(1), this.localGuid);
                  this.filteredList = new FilteredList(this.search, t -> true);
               } else {
                  this.filteredList = new FilteredList(this.search, t -> false);
               }

               this.updateTable();
            } else if (this.searchStr.startsWith("$") && this.searchStr.length() > 1) {
               this.resetFields();
               if (this.productDataLogic.getProductsFreeSearch(this.searchStr.substring(1)) != null) {
                  this.search = this.productDataLogic.getProductsFreeSearch(this.searchStr.substring(1));
                  this.filteredList = new FilteredList(this.search, t -> true);
               } else {
                  this.filteredList = new FilteredList(this.search, t -> false);
               }

               this.updateTable();
            } else {
               Map<String, String> optionsMap = new LinkedHashMap<>();
               optionsMap.put("pricebuy", "pricebuy");
               optionsMap.put("buyprice", "pricebuy");
               optionsMap.put("buy", "pricebuy");
               optionsMap.put("sellpriceinc", "sellpriceinc");
               optionsMap.put("pricesellinc", "pricesellinc");
               optionsMap.put("pricesell", "pricesell");
               optionsMap.put("sellprice", "pricesell");
               optionsMap.put("sellinc", "pricesellinc");
               optionsMap.put("sell", "pricesell");
               optionsMap.put("profit", "(pricesell - pricebuy)");
               optionsMap.put("margin", "(((pricesell - pricebuy) / pricesell) * 100)");
               List<String> operators = new ArrayList<>(Arrays.asList(">=", "<=", "<>", "!=", ">", "=", "<"));
               String option = null;
               String operator = null;
               StringBuilder sb = new StringBuilder(this.searchStr);

               for (String s : optionsMap.keySet()) {
                  if (sb.toString().toLowerCase().contains(s)) {
                     if (option != null) {
                        Dialogs.showDialog(8, LocalResource.getString("dialog.searchParserContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
                        return;
                     }

                     option = s;
                     sb.replace(sb.indexOf(s), sb.indexOf(s) + s.length(), "");
                  }
               }

               sb = new StringBuilder(sb.toString().trim());

               for (String o : operators) {
                  if (sb.toString().toLowerCase().contains(o)) {
                     if (sb.indexOf(o) != 0) {
                        Dialogs.showDialog(8, LocalResource.getString("dialog.searchParserContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
                        return;
                     }

                     if (operator != null) {
                        Dialogs.showDialog(8, LocalResource.getString("dialog.searchParserContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
                        return;
                     }

                     operator = o;
                     sb.replace(sb.indexOf(o), sb.indexOf(o) + o.length(), "");
                  }
               }

               if (option != null & operator != null) {
                  Pattern p = Pattern.compile("^[0-9.]++$");
                  Matcher m = p.matcher(sb.toString().trim());
                  if (m.matches()) {
                     this.resetFields();
                     this.search = this.productDataLogic.getProductsByValueSearch(optionsMap.get(option) + operator + sb.toString().trim(), this.localGuid);
                     this.filteredList = new FilteredList(this.search, t -> true);
                     this.updateTable();
                  } else {
                     Dialogs.showDialog(8, LocalResource.getString("dialog.searchParserContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
                  }
               } else {
                  Pattern p = Pattern.compile("^[A-Za-z0-9-_ /&]++$");
                  Matcher m = p.matcher(this.searchStr);
                  if (m.matches()) {
                     if (this.searchStr.length() > 0) {
                        this.resetFields();
                        ObservableList<ProductTableInfo> search = this.mainTable.getItems();
                        this.filteredList = new FilteredList<>(
                           search,
                           t -> StringUtils.containsIgnoreCase(t.getName(), this.searchStr)
                                 || StringUtils.containsIgnoreCase(t.getId(), this.searchStr)
                                 || StringUtils.containsIgnoreCase(t.getReference(), this.searchStr)
                                 || StringUtils.containsIgnoreCase(t.getBarcode(), this.searchStr)
                                 || StringUtils.containsIgnoreCase(t.getCategory(), this.searchStr)
                                 || t.getShortName() != null && StringUtils.containsIgnoreCase(t.getShortName(), this.searchStr)
                        );
                        this.updateTable();
                     }
                  } else {
                     Dialogs.showDialog(8, LocalResource.getString("dialog.searchParserError"), (Stage)this.apAnchor.getScene().getWindow(), true);
                  }
               }
            }
         }
      }
   }

   @Override
   public void updateRow(Integer oldRow, Object o) {
      ProductInfo p = (ProductInfo)o;
      ObservableList<ProductTableInfo> tempList = FXCollections.observableArrayList();
      this.filteredList
         .forEach(
            f -> {
               if (f.getId().equals(p.getId())) {
                  tempList.add(
                     new ProductTableInfo(
                        p.getId(),
                        p.getReference(),
                        p.getCode(),
                        p.getName(),
                        p.getAlias(),
                        this.cmbCategory.getDescription(p.getCategory()),
                        this.localeString(p.getPriceSell()),
                        String.format(currentLocale, "%.3f", p.getPriceBuy()),
                        this.localeString(p.getPriceSellInc()),
                        p.isIngredient(),
                        p.isCom()
                     )
                  );
               } else {
                  tempList.add(f);
               }
            }
         );
      this.filteredList = new FilteredList(tempList, t -> true);
      this.updateTable();
   }

   @Override
   public void topRow() {
      this.checkDirty();
      this.mainTable.getSelectionModel().selectFirst();
      Platform.runLater(new Runnable() {
         @Override
         public void run() {
            ProductController.this.mainTable.requestFocus();
            ProductController.this.mainTable.scrollTo(0);
         }
      });
   }

   @Override
   public void bottomRow() {
      this.checkDirty();
      this.mainTable.getSelectionModel().selectLast();
      Platform.runLater(new Runnable() {
         @Override
         public void run() {
            ProductController.this.mainTable.requestFocus();
            ProductController.this.mainTable.scrollTo(ProductController.this.mainTable.getItems().size() - 1);
         }
      });
   }

   @Override
   public void updateTable() {
      this.sortedData = new SortedList(this.filteredList);
      this.sortedData.comparatorProperty().bind(this.mainTable.comparatorProperty());
      this.mainTable.setItems(this.sortedData);
      Platform.runLater(new Runnable() {
         @Override
         public void run() {
            ProductController.this.mainTable.requestFocus();
            ProductController.this.mainTable.getSelectionModel().select(0);
            ProductController.this.mainTable.getFocusModel().focus(0);
         }
      });
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.productsTitle") + " :- " + this.mainTable.getItems().size() + " records");
   }

   @Override
   public void resetFilterLists() {
      this.filteredList = new FilteredList(this.productData, t -> true);
      this.sortedData = new SortedList(this.filteredList);
      this.sortedData.comparatorProperty().bind(this.mainTable.comparatorProperty());
      this.mainTable.setItems(this.sortedData);
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.productsTitle") + " :- " + this.mainTable.getItems().size() + " records");
      this.mainTable.requestFocus();
      this.mainTable.getSelectionModel().select(0);
      this.mainTable.getFocusModel().focus(0);
   }

   @Override
   public void clearSearchResults() {
      this.filteredList = new FilteredList(this.productData, t -> true);
      this.updateTable();
   }
}
