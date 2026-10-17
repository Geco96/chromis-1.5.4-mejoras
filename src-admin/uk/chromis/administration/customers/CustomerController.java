package uk.chromis.administration.customers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.apache.commons.lang.StringUtils;
import uk.chromis.administration.base.BasePanelController;
import uk.chromis.administration.base.FieldChecks;
import uk.chromis.administration.dialogs.Dialogs;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.tax.TaxDataLogic;
import uk.chromis.basic.BasicException;
import uk.chromis.custom.controls.ButtonPanel;
import uk.chromis.custom.controls.ImageEditor;
import uk.chromis.custom.toggleswitch.ToggleSwitch;
import uk.chromis.format.Formats;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.barcodes.Barcode;
import uk.chromis.pos.cards.CardDataLogic;
import uk.chromis.pos.forms.LocalResource;

public class CustomerController extends BasePanelController implements Initializable {
   public AnchorPane apAnchor;
   public HBox tableHBox;
   public TabPane tpDataPane;
   public Label lblPanelName;
   public Label lblSeletedTitle;
   public Label txtEnableLoyalty;
   public Label txtTransactionCount;
   public Label txtLoayaltyCount;
   public Label lblTaxExempt;
   public Label lblTaxReview;
   public ToggleSwitch tswtAccountActive;
   public ToggleSwitch tswtMarketing;
   public ToggleSwitch tswtEnableLoyalty;
   public ToggleSwitch tswtTaxExempt;
   public Pane paneAccount;
   public Pane paneImageBox;
   public Pane paneLoyalty;
   public Pane paneMarketing;
   public Pane paneStatistics;
   public Pane paneRadio;
   public Pane paneName;
   public Pane paneTax;
   public RadioButton btnAccount;
   public RadioButton btnLoyalty;
   public DatePicker reviewDate;
   public Tab tabTransactions;
   public Tab tabLoyalty;
   public TableColumn accountname;
   public TableColumn accountnumber;
   public TableColumn active;
   public TableColumn marketable;
   public TableColumn loyaltycol;
   public TableColumn account;
   public TableColumn email;
   public TableColumn receiptNumber;
   public TableColumn receiptType;
   public TableColumn receiptDate;
   public TableColumn receiptTotal;
   public TableColumn cardbalance;
   public TableColumn activityDate;
   public TableColumn activity;
   public TableColumn receiptNumberloyalty;
   public TableColumn activitypoints;
   public TableColumn exempt;
   public TableColumn reviewdate;
   public TableView<CustomerInfoTable> mainTable = new TableView();
   public TableView<CustomerTransactionsTable> transactionTable = new TableView();
   public TableView<CustomerLoyaltyTransactions> loyaltyTable = new TableView();
   public TextArea txtComments;
   public TextArea exemptMessage;
   public TextField txtAccountName;
   public TextField txtAccountNo;
   public TextField txtAddressLine1;
   public TextField txtAddressLine2;
   public TextField txtCardNumber;
   public TextField txtCity;
   public TextField txtCreditLimit;
   public TextField txtCurrentCredit;
   public TextField txtDiscount;
   public TextField txtEmail;
   public TextField txtFax;
   public TextField txtLastDate;
   public TextField txtLoyaltyCardNumber;
   public TextField txtMobile;
   public TextField txtPostcode;
   public TextField txtTelephone;
   public TextField txtFirstName;
   public TextField txtSurname;
   public DatePicker dob;
   public DatePicker dpTransactions;
   public DatePicker dpLoyalty;
   public Button btnAssign;
   public Button btnClear;
   public Button btnLoyaltyAssign;
   private final ImageEditor imageEd = new ImageEditor();
   private ToggleGroup accountType;
   private List<CustomerInfo> customers;
   private List<CustomerTransactionsTable> transactions;
   private List<CustomerLoyaltyTransactions> loyalty;
   public ObservableList<CustomerInfoTable> customerData = FXCollections.observableArrayList();
   public ObservableList<CustomerTransactionsTable> transactionData = FXCollections.observableArrayList();
   public ObservableList<CustomerLoyaltyTransactions> loyaltyData = FXCollections.observableArrayList();
   private final CustomerDataLogic customerDataLogic = new CustomerDataLogic();
   private final CardDataLogic loyaltyDataLogic = new CardDataLogic();
   private final TaxDataLogic taxDataLogic = new TaxDataLogic();
   private CustomerInfo selectedCustomer = null;
   private CustomerInfoTable customerInfo;
   private String lastCardNumber;
   private String lastLoyaltyCard;
   private String loyaltyCardid;
   private HashMap<String, String> entries;
   private List<FieldChecks> fields;
   private final ZoneId defaultZoneId = ZoneId.systemDefault();
   private ObservableList<CustomerInfoTable> search = FXCollections.observableArrayList();
   private FilteredList<CustomerInfoTable> filteredList;
   private SortedList<CustomerInfoTable> sortedData;
   private Boolean APIcall = false;
   private LocalDate dateFrom;
   private Timestamp defaultDate;

   @Override
   public void reloadData() {
      this.customerData = this.customerDataLogic.getCustomersForTableView(this.localGuid);
      this.clearSearchResults();
   }

   public void initialize(URL location, ResourceBundle resources) {
      this.apAnchor.prefWidthProperty().bind(Start.INTERNALANCHOR.widthProperty());
      this.apAnchor.prefHeightProperty().bind(Start.INTERNALANCHOR.heightProperty().subtract(25));
      this.actionButtons = new ButtonPanel();
      this.apAnchor.getChildren().add(this.actionButtons);
      this.actionButtons.addButtonListener(this.mainTable, this);
      this.imageEd.setLayoutX(660.0);
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
      this.paneTax.setVisible(SystemProperty.TAXEXEMPTION);
      this.lblPanelName.setText("");
      this.txtDiscount.setText(this.localeString("0.00"));
      this.txtCreditLimit.setText(this.localeString("0.00"));
      this.txtCurrentCredit.setText(this.localeString("0.00"));
      if (!SystemProperty.LOYALTYENABLED) {
         this.tpDataPane.getTabs().remove(this.tabLoyalty);
      }

      this.tswtEnableLoyalty.setVisible(SystemProperty.LOYALTYONACCOUNTENABLED);
      this.txtEnableLoyalty.setVisible(SystemProperty.LOYALTYONACCOUNTENABLED);
      this.entries = new HashMap<>();
      this.entries.put("name", "Name already exists in customers. \n");
      this.accountType = new ToggleGroup();
      this.btnAccount.setToggleGroup(this.accountType);
      this.btnLoyalty.setToggleGroup(this.accountType);
      this.paneRadio.setVisible(SystemProperty.LOYALTYENABLED);
      this.paneAccount.setVisible(true);
      this.paneLoyalty.setVisible(false);
      this.accountType.selectedToggleProperty().addListener((ov, t, t1) -> {
         this.paneAccount.setVisible(this.btnAccount.isSelected());
         this.paneLoyalty.setVisible(!this.btnAccount.isSelected());
         this.setTaxExemptVisability();
         this.setLayout();
         this.txtAccountNo.setText("");
         dirty.setValue(true);
      });
      this.paneStatistics.visibleProperty().bind(this.btnAccount.selectedProperty());
      this.paneStatistics.layoutYProperty().bind(this.paneAccount.layoutYProperty());
      this.txtComments.textProperty().addListener((observable, oldValue, newValue) -> dirty.setValue(true));
      this.dob.valueProperty().addListener((observable, oldValue, newValue) -> dirty.setValue(true));
      this.reviewDate.valueProperty().addListener((observable, oldValue, newValue) -> dirty.setValue(true));
      this.dateFrom = LocalDate.now().minusMonths(1L);
      this.reviewDate.setValue(LocalDate.now());
      this.dpTransactions.valueProperty().addListener(new ChangeListener<LocalDate>() {
         public void changed(ObservableValue<? extends LocalDate> observable, LocalDate oldDate, LocalDate newDate) {
            CustomerController.this.populateTransactionTables();
         }
      });
      this.dpLoyalty.valueProperty().addListener(new ChangeListener<LocalDate>() {
         public void changed(ObservableValue<? extends LocalDate> observable, LocalDate oldDate, LocalDate newDate) {
            CustomerController.this.populateTransactionTables();
         }
      });
      this.tpDataPane.getSelectionModel().selectedItemProperty().addListener((ov, oldTab, newTab) -> {
         String var4 = newTab.getId();
         switch (var4) {
            case "tabTransactions":
               this.populateTransactionTables();
               break;
            case "tabLoyalty":
               this.populateTransactionTables();
         }
      });
      this.txtCardNumber
         .textProperty()
         .addListener(
            (observable, oldValue, newValue) -> {
               newValue = newValue == null ? "" : newValue;
               if (newValue != null && !newValue.isEmpty()) {
                  this.tswtEnableLoyalty.setDisable(false);
               } else {
                  this.tswtEnableLoyalty.setDisable(true);
                  this.tswtEnableLoyalty.setSelected(false);
               }

               this.btnAssign
                  .setText(
                     this.txtCardNumber.getText() != null && !this.txtCardNumber.getText().isBlank()
                        ? LocalResource.getString("btn.replaceCard")
                        : LocalResource.getString("btn.assignCard")
                  );
               dirty.setValue(true);
            }
         );
      this.txtLoyaltyCardNumber
         .textProperty()
         .addListener(
            (observable, oldValue, newValue) -> {
               this.btnLoyaltyAssign
                  .setText(
                     this.txtLoyaltyCardNumber.getText().isEmpty() ? LocalResource.getString("btn.assignCard") : LocalResource.getString("btn.replaceCard")
                  );
               dirty.setValue(true);
            }
         );
      this.tswtEnableLoyalty
         .selectedProperty()
         .addListener(
            (observable, oldValue, newValue) -> {
               if (this.txtCardNumber.getText() != null
                  && !this.txtCardNumber.getText().isBlank()
                  && newValue
                  && !CardDataLogic.isCardPresent(this.txtCardNumber.getText())
                  && Dialogs.showDialog(
                        380,
                        150,
                        7,
                        LocalResource.getString("dialog.loyaltyCardNotAvailable"),
                        (Stage)this.apAnchor.getScene().getWindow(),
                        this.txtCardNumber.getText(),
                        true
                     )
                     == 1) {
                  this.tswtEnableLoyalty.setSelected(false);
               } else {
                  dirty.set(true);
               }
            }
         );
      this.tswtAccountActive
         .selectedProperty()
         .addListener(
            (observable, oldValue, newValue) -> {
               if (!this.APIcall) {
                  this.tswtEnableLoyalty.setDisable(!this.tswtAccountActive.isSelected());
                  this.btnClear.setDisable(!newValue);
                  this.btnAssign.setDisable(!newValue);
                  this.btnLoyaltyAssign.setDisable(!newValue);
                  if (SystemProperty.LOYALTYENABLED) {
                     StringBuilder cardNumber = new StringBuilder(
                        this.btnLoyalty.isSelected() ? this.txtLoyaltyCardNumber.getText() : this.txtCardNumber.getText()
                     );
                     if (!newValue && this.tswtEnableLoyalty.isSelected()) {
                        Dialogs.showDialog(
                           400,
                           150,
                           1,
                           LocalResource.getString("dialog.deactivateCard"),
                           (Stage)this.mainTable.getScene().getWindow(),
                           cardNumber.toString(),
                           true
                        );
                        this.tswtEnableLoyalty.setSelected(false);
                     }

                     if (newValue
                        && this.txtLoyaltyCardNumber.getText().length() > 0
                        && Dialogs.showDialog(
                              400,
                              150,
                              7,
                              LocalResource.getString("dialog.questionReactivateCard"),
                              (Stage)this.apAnchor.getScene().getWindow(),
                              this.selectedCustomer.getName(),
                              true
                           )
                           == 0) {
                        this.tswtEnableLoyalty.setSelected(true);
                     }
                  }
               }

               dirty.set(true);
            }
         );
      this.tswtTaxExempt.selectedProperty().addListener((observable, oldValue, newValue) -> {
         this.reviewDate.setDisable(!newValue || !Start.hasPermission("button.taxexemption"));
         dirty.set(true);
      });
      this.setToggleListener(this.tswtMarketing);
      this.setTextListener(this.txtAccountName);
      this.setTextListener(this.txtAccountNo);
      this.setTextListener(this.txtAddressLine1);
      this.setTextListener(this.txtAddressLine2);
      this.setTextListener(this.txtCity);
      this.setTextListener(this.txtEmail);
      this.setTextListener(this.txtPostcode);
      this.setTextListener(this.txtFirstName);
      this.setTextListener(this.txtSurname);
      this.enableTelephoneNumber(this.txtFax);
      this.enableTelephoneNumber(this.txtMobile);
      this.enableTelephoneNumber(this.txtTelephone);
      this.enableNumericWithDecimal(this.txtCreditLimit, 10);
      this.enableNumericWithDecimal(this.txtDiscount);
      this.setLayout();
      this.buildMainTable();
      this.buildTransactionTable();
      this.buildLoyaltyTransactionTable();
      this.populateMainTable();
      if (SystemProperty.LOYALTYENABLED) {
         if (!SystemProperty.ACCOUNTLOYALTY) {
            this.tswtEnableLoyalty.setSelected(false);
            this.tswtEnableLoyalty.setVisible(false);
            this.txtEnableLoyalty.setVisible(false);
         }
      } else {
         this.tswtEnableLoyalty.setSelected(false);
         this.tswtEnableLoyalty.setVisible(false);
         this.txtEnableLoyalty.setVisible(false);
      }

      this.setTaxExemptVisability();
      this.addDirtyListener();
      dirty.setValue(false);
   }

   private void setTaxExemptVisability() {
      this.exemptMessage.setVisible(this.btnAccount.isSelected());
      this.lblTaxReview.setVisible(this.btnAccount.isSelected());
      this.tswtTaxExempt.setVisible(this.btnAccount.isSelected());
      this.lblTaxExempt.setVisible(this.btnAccount.isSelected());
      this.reviewDate.setVisible(this.btnAccount.isSelected());
      if (!this.btnAccount.isSelected()) {
         this.tswtTaxExempt.setSelected(false);
      }

      this.tswtTaxExempt.setDisable(!Start.hasPermission("button.taxexemption"));
   }

   private void setLayout() {
      this.paneName.setLayoutY(this.paneRadio.isVisible() ? 40.0 : 10.0);
      this.paneAccount.setLayoutY(10.0);
      this.paneLoyalty.setLayoutY(this.paneRadio.isVisible() ? 10.0 : 40.0);
   }

   private void buildMainTable() {
      this.accountname.setCellValueFactory(new PropertyValueFactory("accountname"));
      this.accountnumber.setCellValueFactory(new PropertyValueFactory("accountnumber"));
      this.active.setCellValueFactory(new PropertyValueFactory("activeImage"));
      this.exempt.setCellValueFactory(new PropertyValueFactory("exemptImage"));
      this.exempt.setVisible(SystemProperty.TAXEXEMPTION);
      this.reviewdate.setCellValueFactory(new PropertyValueFactory("reviewDate"));
      this.marketable.setCellValueFactory(new PropertyValueFactory("marketableImage"));
      this.loyaltycol.setCellValueFactory(new PropertyValueFactory("loyaltyImage"));
      this.account.setCellValueFactory(new PropertyValueFactory("accountImage"));
      this.email.getStyleClass().add("left");
      this.email.setCellValueFactory(new PropertyValueFactory("email"));
      this.mainTable.setItems(this.customerData);
      this.mainTableListener();
   }

   private void buildTransactionTable() {
      this.receiptNumber.setCellValueFactory(new PropertyValueFactory("receiptNumber"));
      this.receiptType.setCellValueFactory(new PropertyValueFactory("ticketType"));
      this.receiptDate.setCellFactory(new Callback<TableColumn<CustomerTransactionsTable, String>, TableCell<CustomerTransactionsTable, String>>() {
         public TableCell<CustomerTransactionsTable, String> call(final TableColumn<CustomerTransactionsTable, String> param) {
            return new TableCell<CustomerTransactionsTable, String>() {
               protected void updateItem(String total, boolean empty) {
                  if (!empty) {
                     DateFormat df = new SimpleDateFormat(SystemProperty.DATE);
                     int currentIndex = this.indexProperty().getValue() < 0 ? 0 : this.indexProperty().getValue();
                     this.setText(df.format(((CustomerTransactionsTable)param.getTableView().getItems().get(currentIndex)).getTransactionDate()));
                  } else {
                     this.setText("");
                  }
               }
            };
         }
      });
      this.receiptTotal.setCellFactory(new Callback<TableColumn<CustomerTransactionsTable, String>, TableCell<CustomerTransactionsTable, String>>() {
         public TableCell<CustomerTransactionsTable, String> call(final TableColumn<CustomerTransactionsTable, String> param) {
            return new TableCell<CustomerTransactionsTable, String>() {
               protected void updateItem(String total, boolean empty) {
                  if (!empty) {
                     int currentIndex = this.indexProperty().getValue() < 0 ? 0 : this.indexProperty().getValue();
                     Double amount = ((CustomerTransactionsTable)param.getTableView().getItems().get(currentIndex)).getTotal();
                     this.setText(CustomerController.this.localeString(amount));
                  } else {
                     this.setText("");
                  }
               }
            };
         }
      });
      this.transactionTable.setItems(this.transactionData);
      this.transactionTable
         .setRowFactory(
            tv -> {
               final TableRow<CustomerTransactionsTable> row = new TableRow();
               row.setOnMouseClicked(
                  new EventHandler<MouseEvent>() {
                     public void handle(MouseEvent event) {
                        if (event.getClickCount() == 2 && !row.isEmpty()) {
                           String ticketid = ((CustomerTransactionsTable)row.getItem()).getTicketId();

                           try {
                              Stage showStage = new Stage();
                              FXMLLoader loader = new FXMLLoader(
                                 this.getClass().getResource("/uk/chromis/administration/customers/receipt.fxml"), LocalResource.resources
                              );
                              ReceiptController receiptControl = new ReceiptController();
                              loader.setController(receiptControl);
                              receiptControl.setReceipt(ticketid);
                              Parent root = (Parent)loader.load();
                              Scene scene = new Scene(root, 364.0, 807.0);
                              scene.getStylesheets().add(LocalResource.cssResource);
                              showStage.setScene(scene);
                              showStage.initStyle(StageStyle.UNDECORATED);
                              showStage.initModality(Modality.APPLICATION_MODAL);
                              Stage current = (Stage)CustomerController.this.apAnchor.getScene().getWindow();
                              showStage.setX(current.getX() + current.getWidth() / 2.0 - 182.0);
                              showStage.setY(current.getY() + current.getHeight() / 2.0 - 405.0);
                              showStage.setMinWidth(364.0);
                              showStage.setMaxWidth(364.0);
                              showStage.showAndWait();
                           } catch (Exception var9) {
                              var9.printStackTrace();
                           }
                        }
                     }
                  }
               );
               return row;
            }
         );
   }

   private void buildLoyaltyTransactionTable() {
      this.activityDate.setCellFactory(new Callback<TableColumn<CustomerLoyaltyTransactions, String>, TableCell<CustomerLoyaltyTransactions, String>>() {
         public TableCell<CustomerLoyaltyTransactions, String> call(final TableColumn<CustomerLoyaltyTransactions, String> param) {
            return new TableCell<CustomerLoyaltyTransactions, String>() {
               protected void updateItem(String total, boolean empty) {
                  if (!empty) {
                     DateFormat df = new SimpleDateFormat(SystemProperty.DATE);
                     int currentIndex = this.indexProperty().getValue() < 0 ? 0 : this.indexProperty().getValue();
                     this.setText(df.format(((CustomerLoyaltyTransactions)param.getTableView().getItems().get(currentIndex)).getactivityDate()));
                  } else {
                     this.setText("");
                  }
               }
            };
         }
      });
      this.activity.setCellValueFactory(new PropertyValueFactory("activity"));
      this.activitypoints.setCellValueFactory(new PropertyValueFactory("activityPoints"));
      this.cardbalance.setCellValueFactory(new PropertyValueFactory("cardBalance"));
      this.receiptNumberloyalty.setCellValueFactory(new PropertyValueFactory("receiptNumber"));
      this.loyaltyTable.setItems(this.loyaltyData);
      this.loyaltyTable
         .setRowFactory(
            tv -> {
               TableRow<CustomerLoyaltyTransactions> row = new TableRow();
               row.setOnMouseClicked(
                  event -> {
                     if (event.getClickCount() == 2 && !row.isEmpty()) {
                        String ticketid = ((CustomerLoyaltyTransactions)row.getItem()).getTicketId();
                        if (ticketid != null) {
                           try {
                              Stage showStage = new Stage();
                              FXMLLoader loader = new FXMLLoader(
                                 this.getClass().getResource("/uk/chromis/administration/customers/receipt.fxml"), LocalResource.resources
                              );
                              ReceiptController receiptControl = new ReceiptController();
                              loader.setController(receiptControl);
                              receiptControl.setReceipt(ticketid);
                              Parent root = (Parent)loader.load();
                              Scene scene = new Scene(root, 362.0, 807.0);
                              scene.getStylesheets().add(LocalResource.cssResource);
                              showStage.setScene(scene);
                              showStage.initStyle(StageStyle.UNDECORATED);
                              showStage.initModality(Modality.APPLICATION_MODAL);
                              Stage current = (Stage)this.apAnchor.getScene().getWindow();
                              showStage.setX(current.getX() + current.getWidth() / 2.0 - 180.0);
                              showStage.setY(current.getY() + current.getHeight() / 2.0 - 405.0);
                              showStage.setMinWidth(816.0);
                              showStage.showAndWait();
                           } catch (Exception var10) {
                              var10.printStackTrace();
                           }
                        }
                     }
                  }
               );
               return row;
            }
         );
   }

   private void populateMainTable() {
      this.customerData = this.customerDataLogic.getCustomersForTableView(this.localGuid);
      this.filteredList = new FilteredList(this.customerData, t -> true);
      this.updateTable();
   }

   private void populateTransactionTables() {
      if (this.selectedCustomer != null) {
         String var1 = ((Tab)this.tpDataPane.getSelectionModel().getSelectedItem()).getId();
         switch (var1) {
            case "tabTransactions":
               this.transactionTable.getItems().clear();
               this.txtTransactionCount.setText(String.valueOf(this.customerDataLogic.getTransactionRecordCount(this.selectedCustomer.getId())));
               this.defaultDate = Timestamp.valueOf(
                  LocalDateTime.of(((LocalDate)this.dpTransactions.getValue()).minusDays(1L), LocalTime.now())
                     .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
               );
               this.transactionData = this.customerDataLogic.getCustomersTransactionList(this.selectedCustomer.getId(), this.defaultDate);
               this.transactionTable.setItems(this.transactionData);
               Platform.runLater(new Runnable() {
                  @Override
                  public void run() {
                     if (CustomerController.this.transactionTable.getItems().size() != 0) {
                        CustomerController.this.transactionTable.requestFocus();
                        CustomerController.this.transactionTable.getSelectionModel().select(0);
                        CustomerController.this.transactionTable.getFocusModel().focus(0);
                     }
                  }
               });
               break;
            case "tabLoyalty":
               this.loyaltyTable.getItems().clear();
               this.txtLoayaltyCount.setText(String.valueOf(this.customerDataLogic.getLoyaltyRecordCount(this.selectedCustomer.getId())));
               this.defaultDate = Timestamp.valueOf(
                  LocalDateTime.of(((LocalDate)this.dpLoyalty.getValue()).minusDays(1L), LocalTime.now())
                     .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
               );
               this.loyaltyData = this.customerDataLogic.getCustomersLoyaltyTransactionList(this.selectedCustomer.getId(), this.defaultDate);
               this.loyaltyTable.setItems(this.loyaltyData);
               Platform.runLater(new Runnable() {
                  @Override
                  public void run() {
                     if (CustomerController.this.loyaltyTable.getItems().size() != 0) {
                        CustomerController.this.loyaltyTable.requestFocus();
                        CustomerController.this.loyaltyTable.getSelectionModel().select(0);
                        CustomerController.this.loyaltyTable.getFocusModel().focus(0);
                     }
                  }
               });
         }
      }
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

   public void btnClearCustomerCard() {
      if (!this.txtCardNumber.getText().trim().isEmpty()) {
         if (Dialogs.showDialog(
               400,
               200,
               7,
               LocalResource.getString(this.tswtEnableLoyalty.isSelected() ? "dialog.clearCard" : "dialog.changeCustomerCard"),
               (Stage)this.apAnchor.getScene().getWindow(),
               true
            )
            == 1) {
            this.lastCardNumber = "";
            this.lastLoyaltyCard = "";
            return;
         }

         this.txtCardNumber.setText("");
      }
   }

   public void btnLoyaltyCardNumber() {
      if (this.txtLoyaltyCardNumber.getText().trim().isEmpty()
         || Dialogs.showDialog(400, 200, 7, LocalResource.getString("dialog.changeCard"), (Stage)this.apAnchor.getScene().getWindow(), true) != 1) {
         this.td = this.createTextDialog(this.actionButtons, "txtDialog.customerCard");
         Optional<String> tdResult = this.td.showAndWait();
         if (tdResult.isPresent()) {
            String newCard = tdResult.get().trim();
            if (!newCard.isBlank()) {
               Barcode bar = new Barcode(newCard);
               String cardCode = bar.getBarCode();
               if (this.txtLoyaltyCardNumber.getText().trim().equalsIgnoreCase(newCard)) {
                  Dialogs.showDialog(1, LocalResource.getString("dialog.cardNumbersSame"), (Stage)this.apAnchor.getScene().getWindow(), true);
               } else if (CardDataLogic.isCardRetired(cardCode)) {
                  Dialogs.showDialog(8, LocalResource.getString("dialog.cardRetired"), (Stage)this.apAnchor.getScene().getWindow(), cardCode, true);
               } else if (!CardDataLogic.cardHasCustomerAssigned(cardCode)
                  || Dialogs.showDialog(7, LocalResource.getString("dialog.loyaltyAssignCustomer"), (Stage)this.apAnchor.getScene().getWindow(), cardCode, true)
                     != 1) {
                  if (CardDataLogic.isCardAssignedToAnotherCustomer(cardCode)) {
                     Dialogs.showDialog(1, LocalResource.getString("dialog.loyaltyCardAssigned"), (Stage)this.apAnchor.getScene().getWindow(), newCard, true);
                  } else if (cardCode.length() <= SystemProperty.LOYALTYSTART.length() + 1) {
                     Dialogs.showDialog(8, LocalResource.getString("dialog.loyaltyCardToShort"), (Stage)this.apAnchor.getScene().getWindow(), true);
                  } else if (!cardCode.matches("^[a-zA-Z0-9]+$")) {
                     Dialogs.showDialog(8, LocalResource.getString("dialog.loyaltyCardInvalidChars"), (Stage)this.apAnchor.getScene().getWindow(), true);
                  } else if (CardDataLogic.isCardPresent(cardCode)
                     || Dialogs.showDialog(
                           null, 160, 7, LocalResource.getString("dialog.loyaltyCardNotAvailable"), (Stage)this.apAnchor.getScene().getWindow(), cardCode, true
                        )
                        != 1) {
                     this.txtLoyaltyCardNumber.setText(cardCode);
                     dirty.setValue(true);
                  }
               }
            }
         }
      }
   }

   public void btnCustomerCardNumber() {
      if (this.txtCardNumber.getText().trim().isEmpty()
         || Dialogs.showDialog(
               400,
               200,
               7,
               LocalResource.getString(this.tswtEnableLoyalty.isSelected() ? "dialog.changeCard" : "dialog.changeCustomerCard"),
               (Stage)this.apAnchor.getScene().getWindow(),
               true
            )
            != 1) {
         String card = null;

         try {
            Stage showStage = new Stage();
            FXMLLoader loader = new FXMLLoader(this.getClass().getResource("/uk/chromis/administration/customers/cardnumber.fxml"), LocalResource.resources);
            CardNumberController cardControl = new CardNumberController();
            loader.setController(cardControl);
            cardControl.setCardNumber(this.txtCardNumber.getText());
            Parent root = (Parent)loader.load();
            Scene scene = new Scene(root, 475.0, 120.0);
            scene.getStylesheets().add(LocalResource.cssResource);
            showStage.setScene(scene);
            showStage.initStyle(StageStyle.UNDECORATED);
            showStage.initModality(Modality.APPLICATION_MODAL);
            Stage current = (Stage)this.apAnchor.getScene().getWindow();
            showStage.setX(current.getX() + current.getWidth() / 2.0 - 240.0);
            showStage.setY(current.getY() + current.getHeight() / 2.0 - 120.0);
            showStage.setMinWidth(475.0);
            showStage.showAndWait();
            card = cardControl.getCardNumber();
         } catch (IOException var8) {
         }

         if (card != null) {
            if (card.equals(this.txtCardNumber.getText())) {
               return;
            }

            Barcode bar = new Barcode(card);
            String cardCode = bar.getBarCode();
            if (CardDataLogic.isCardRetired(cardCode)) {
               Dialogs.showDialog(8, LocalResource.getString("dialog.cardRetired"), (Stage)this.apAnchor.getScene().getWindow(), cardCode, true);
               return;
            }

            if (this.customerDataLogic.customerExists(cardCode) || CardDataLogic.isCardPresent(cardCode)) {
               Dialogs.showDialog(8, LocalResource.getString("dialog.cardInuse"), (Stage)this.apAnchor.getScene().getWindow(), cardCode, true);
               return;
            }

            if (cardCode.startsWith(SystemProperty.LOYALTYSTART) || cardCode.startsWith(SystemProperty.GIFTCARDSTART)) {
               String block = cardCode.startsWith(SystemProperty.LOYALTYSTART) ? "Loyalty" : "Gift";
               Dialogs.showDialog(1, LocalResource.getString("dialog.loyaltyCardInvalidNumber"), (Stage)this.apAnchor.getScene().getWindow(), block, true);
               return;
            }

            if (cardCode.length() < 5) {
               Dialogs.showDialog(8, LocalResource.getString("dialog.loyaltyCardToShort"), (Stage)this.apAnchor.getScene().getWindow(), true);
               return;
            }

            if (!cardCode.matches("^[a-zA-Z0-9]+$")) {
               Dialogs.showDialog(8, LocalResource.getString("dialog.loyaltyCardInvalidChars"), (Stage)this.apAnchor.getScene().getWindow(), true);
               return;
            }

            this.txtCardNumber.setText(cardCode);
            dirty.setValue(true);
         }
      }
   }

   private void populateCustomerFields() {
      this.APIcall = true;
      this.customerInfo = (CustomerInfoTable)this.mainTable.getItems().get(this.mainTable.getSelectionModel().getSelectedIndex());
      this.paneRadio.setVisible(false);
      this.selectedCustomer = this.customerDataLogic.getCustomerById(this.customerInfo.getId());
      this.accountType.selectToggle(this.selectedCustomer.getCustomerType().equalsIgnoreCase("account") ? this.btnAccount : this.btnLoyalty);
      this.setLayout();
      this.txtAccountName.setText(this.selectedCustomer.getName());
      this.txtAccountNo.setText(this.selectedCustomer.getAccountNumber());
      this.txtCardNumber.setText(this.selectedCustomer.getCustomerCard());
      this.txtDiscount.setText(this.localeString(Double.valueOf(this.selectedCustomer.getCustomerDiscount() * 100.0)));
      this.tswtAccountActive.setSelected(this.selectedCustomer.isActiveCustomer());
      this.btnClear.setDisable(!this.selectedCustomer.isActiveCustomer());
      this.btnAssign.setDisable(!this.selectedCustomer.isActiveCustomer());
      this.btnLoyaltyAssign.setDisable(!this.selectedCustomer.isActiveCustomer());
      this.tswtMarketing.setSelected(this.selectedCustomer.isMarketable());
      this.txtCreditLimit.setText(this.localeString(this.selectedCustomer.getCreditLimit()));
      this.txtCurrentCredit.setText(this.localeString(this.selectedCustomer.getCurrentDebt()));
      this.txtLastDate.setText(Formats.DATE.formatValue(this.selectedCustomer.getCurDate()));
      this.imageEd.setImage(this.selectedCustomer.getImage());
      this.tswtEnableLoyalty.setSelected(this.selectedCustomer.isLoyaltyenabled());
      this.txtAddressLine1.setText(this.selectedCustomer.getAddress());
      this.txtAddressLine2.setText(this.selectedCustomer.getAddress2());
      this.txtCity.setText(this.selectedCustomer.getCity());
      this.txtPostcode.setText(this.selectedCustomer.getPostal());
      this.txtTelephone.setText(this.selectedCustomer.getPhone());
      this.txtMobile.setText(this.selectedCustomer.getMobile());
      this.txtFax.setText(this.selectedCustomer.getFax());
      this.txtEmail.setText(this.selectedCustomer.getEmail());
      if (this.selectedCustomer.getDob() == null) {
         this.dob.setValue(null);
      } else {
         this.dob.setValue(this.selectedCustomer.getDob().toInstant().atZone(this.defaultZoneId).toLocalDate());
      }

      this.txtComments.setText(this.selectedCustomer.getNotes());
      this.txtFirstName.setText(this.selectedCustomer.getFirstName());
      this.txtSurname.setText(this.selectedCustomer.getLastName());
      this.txtLoyaltyCardNumber.setText(this.selectedCustomer.getLoyaltyCardNumber() == null ? "" : this.selectedCustomer.getLoyaltyCardNumber());
      this.loyaltyCardid = this.selectedCustomer.getLoyaltyCardId();
      this.lblPanelName.setText(this.selectedCustomer.getName() + "  (" + ((RadioButton)this.accountType.getSelectedToggle()).getText() + ")");
      this.dpTransactions.setValue(LocalDate.of(this.dateFrom.getYear(), this.dateFrom.getMonth(), this.dateFrom.getDayOfMonth()));
      this.dpLoyalty.setValue(LocalDate.of(this.dateFrom.getYear(), this.dateFrom.getMonth(), this.dateFrom.getDayOfMonth()));
      this.populateTransactionTables();
      this.tswtTaxExempt.setSelected(this.selectedCustomer.isTaxExempt());
      if (this.selectedCustomer.getReviewDate() != null) {
         this.reviewDate.setValue(this.selectedCustomer.getReviewDate().toInstant().atZone(this.defaultZoneId).toLocalDate());
      } else {
         this.reviewDate.setValue(LocalDate.now());
      }

      this.setTaxExemptVisability();
      dirty.setValue(false);
      this.APIcall = false;
   }

   @Override
   public void addTableRow(Object o) {
      CustomerInfo c = (CustomerInfo)o;
      this.customerData
         .add(
            new CustomerInfoTable(
               c.getId(),
               c.getName(),
               c.getAccountNumber(),
               c.isTaxExempt(),
               c.getReviewDate(),
               c.isActiveCustomer(),
               c.isMarketable(),
               c.getEmail(),
               c.isLoyaltyenabled(),
               c.isAccount(),
               c.getSearchStr()
            )
         );
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.customers") + " :- " + this.mainTable.getItems().size() + " records");
      this.lblPanelName.setText(c.getName());
      this.mainTable.getSelectionModel().select(this.mainTable.getItems().size() - 1);
      this.mainTable.scrollTo(this.mainTable.getItems().size() - 1);
   }

   @Override
   public void deleteRecord() {
      CustomerInfoTable customer = (CustomerInfoTable)this.mainTable.getItems().get(this.mainTable.getSelectionModel().getSelectedIndex());
      this.selectedCustomer = this.customerDataLogic.getCustomerById(customer.getId());
      if (Dialogs.showDialog(
            7, LocalResource.getString("dialog.questionDelete"), (Stage)this.apAnchor.getScene().getWindow(), this.selectedCustomer.getName(), true
         )
         == 0) {
         try {
            this.customerDataLogic.removeCustomer(this.selectedCustomer.getId(), this.selectedCustomer.getSiteguid());
            this.resetFields();
            this.populateMainTable();
         } catch (BasicException var3) {
            Dialogs.showDialog(8, LocalResource.getString("dialog.deleteFailedContext"), (Stage)this.apAnchor.getScene().getWindow(), true);
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
            this.populateCustomerFields();
            this.selectedRow = this.mainTable.getSelectionModel().getSelectedIndex();
         } else {
            this.lblPanelName.setText("");
         }
      });
   }

   @Override
   public void newRecord() {
      this.resetFields();
      this.lblPanelName.setText(LocalResource.getString("lbl.newRecord"));
      this.selectedCustomer = new CustomerInfo();
      this.tpDataPane.getSelectionModel().selectFirst();
      dirty.setValue(false);
      this.actionButtons.disableForNewRecord(this.mainTable);
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
      this.customerData = this.customerDataLogic.getCustomersForTableView(this.localGuid);
      this.filteredList = new FilteredList(this.customerData, t -> true);
      this.mainTable.getSortOrder().clear();
      this.mainTable.getSelectionModel().selectFirst();
      this.updateTable();
      this.actionButtons.enableAfterNewRecord(this.mainTable);
      dirty.setValue(false);
   }

   @Override
   public void resetFields() {
      this.btnAccount.setSelected(true);
      this.paneRadio.setVisible(SystemProperty.LOYALTYENABLED);
      this.setLayout();
      this.txtAccountName.setText("");
      this.txtAccountNo.setText("");
      this.txtCardNumber.setText("");
      this.txtDiscount.setText(this.localeString("0.00"));
      this.tswtAccountActive.setSelected(true);
      this.tswtMarketing.setSelected(false);
      this.txtCreditLimit.setText(this.localeString("0.00"));
      this.txtCurrentCredit.setText(this.localeString("0.00"));
      this.txtLastDate.setText("");
      this.imageEd.setImage(null);
      this.tswtEnableLoyalty.setSelected(false);
      this.txtAddressLine1.setText("");
      this.txtAddressLine2.setText("");
      this.txtCity.setText("");
      this.txtPostcode.setText("");
      this.txtTelephone.setText("");
      this.txtMobile.setText("");
      this.txtFax.setText("");
      this.txtEmail.setText("");
      this.dob.setValue(null);
      this.txtComments.setText("");
      this.txtFirstName.setText("");
      this.txtSurname.setText("");
      this.txtLoyaltyCardNumber.setText("");
      this.loyaltyCardid = null;
      this.tswtEnableLoyalty.setDisable(true);
      this.selectedCustomer = new CustomerInfo();
      this.tswtTaxExempt.setSelected(false);
      this.reviewDate.setValue(LocalDate.now());
      dirty.setValue(false);
   }

   @Override
   public Boolean saveRecord() {
      this.customerDataLogic.setStage(this.actionButtons);
      this.fields = new ArrayList<>();
      this.fields.add(new FieldChecks(this.txtAccountName, "No 'Name' data entered. \n"));
      if (this.btnLoyalty.isSelected()) {
         this.fields.add(new FieldChecks(this.txtLoyaltyCardNumber, "No 'Loyalty Card Number' data entered. \n"));
      } else {
         this.fields.add(new FieldChecks(this.txtAccountNo, "No 'Account Number' data entered. \n"));
         this.entries.put("taxid", "Account number is already in use. \n");
      }

      if (!this.checkCompulsoryFields(this.fields, (Stage)this.apAnchor.getScene().getWindow())) {
         return false;
      } else {
         List<uk.chromis.administration.KeyedData> results = null;
         Object[] customer = new Object[30];

         try {
            if (this.txtAccountName.getText().length() < 3) {
               Dialogs.showDialog(8, LocalResource.getString("dialog.nameLength"), (Stage)this.apAnchor.getScene().getWindow(), true);
               return null;
            }

            if (this.btnAccount.isSelected()) {
               customer[1] = "account";
               customer[25] = this.tswtEnableLoyalty.isSelected();
               customer[3] = this.txtAccountNo.getText().isEmpty() ? null : this.txtAccountNo.getText();
            } else {
               customer[1] = "loyalty";
               customer[25] = true;
               customer[3] = this.txtAccountNo.getText().isEmpty() ? this.txtLoyaltyCardNumber.getText() : this.txtAccountNo.getText();
            }

            customer[2] = this.txtAccountName.getText();
            customer[4] = this.txtCardNumber.getText();
            customer[5] = this.parseLocaleString(this.txtCreditLimit.getText());
            customer[6] = this.txtAddressLine1.getText();
            customer[7] = this.txtAddressLine2.getText();
            customer[8] = this.txtPostcode.getText();
            customer[9] = this.txtCity.getText();
            customer[10] = this.txtFirstName.getText();
            customer[11] = this.txtSurname.getText();
            customer[12] = this.txtEmail.getText();
            customer[13] = this.txtTelephone.getText();
            customer[14] = this.txtMobile.getText();
            customer[15] = this.txtFax.getText();
            customer[16] = this.txtComments.getText();
            customer[17] = this.tswtAccountActive.isSelected();
            customer[18] = Formats.TIMESTAMP.parseValue(this.txtLastDate.getText());
            customer[19] = this.parseLocaleString(this.txtCurrentCredit.getText());
            customer[20] = this.imageEd.getImage();
            customer[21] = this.parseLocaleString(this.txtDiscount.getText()) / 100.0;
            if (this.dob.getValue() != null) {
               customer[22] = Date.valueOf((LocalDate)this.dob.getValue());
            }

            customer[23] = this.loyaltyCardid;
            customer[24] = this.txtLoyaltyCardNumber.getText();
            customer[26] = this.tswtMarketing.isSelected();
            customer[27] = this.tswtTaxExempt.isSelected();
            if (this.tswtTaxExempt.isSelected()) {
               customer[28] = Date.valueOf((LocalDate)this.reviewDate.getValue());
            } else {
               customer[28] = null;
            }

            customer[29] = this.localGuid;
         } catch (BasicException var7) {
            System.out.println("error");
         }

         if (this.selectedCustomer != null && this.selectedCustomer.getId() != null) {
            customer[0] = this.selectedCustomer.getId();
            results = this.customerDataLogic.getCustomerFieldChecks(this.txtAccountName.getText(), this.txtAccountNo.getText(), (String)customer[0]);
            if (!this.checkForDuplicateEntries(results, this.entries)) {
               return false;
            }

            try {
               this.customerDataLogic.customerUpdate(customer);
            } catch (BasicException var5) {
               Logger.getLogger(CustomerController.class.getName()).log(Level.SEVERE, null, var5);
            }

            dirty.setValue(false);
            this.updateRow(this.selectedRow, this.customerDataLogic.getCustomerById(this.selectedCustomer.getId()));
            this.selectedRow = this.mainTable.getSelectionModel().getSelectedIndex();
         } else {
            String id = UUID.randomUUID().toString();
            customer[0] = id;
            if (this.btnLoyalty.isSelected()) {
               results = this.customerDataLogic
                  .getCustomerLoyaltyFieldChecks(this.txtAccountName.getText(), this.txtLoyaltyCardNumber.getText(), (String)customer[0]);
            } else {
               results = this.customerDataLogic.getCustomerFieldChecks(this.txtAccountName.getText(), this.txtAccountNo.getText(), (String)customer[0]);
            }

            try {
               this.customerDataLogic.customerInsert(customer);
               dirty.setValue(false);
               this.addTableRow(this.customerDataLogic.getCustomerById(id));
            } catch (BasicException var6) {
               Dialogs.showDialog(4, LocalResource.getString("dialog.saveFailed"), (Stage)this.apAnchor.getScene().getWindow(), true);
               return false;
            }

            this.refreshRecords();
            dirty.setValue(false);
         }

         return false;
      }
   }

   @Override
   public void searchRecords() {
      this.td = this.createTextDialog(this.actionButtons, "txtDialog.searchCustomers");
      Optional<String> result = this.td.showAndWait();
      if (result.isPresent()) {
         this.searchStr = result.get();
         if (!this.searchStr.isBlank()) {
            if (this.searchStr.startsWith("!") && this.searchStr.length() > 1) {
               this.resetFields();
               if (this.customerDataLogic.getCustomersByValueSearch(this.searchStr.substring(1), this.localGuid) != null) {
                  this.search = this.customerDataLogic.getCustomersByValueSearch(this.searchStr.substring(1), this.localGuid);
                  this.filteredList = new FilteredList(this.search, t -> true);
               } else {
                  this.filteredList = new FilteredList(this.search, t -> false);
               }

               this.updateTable();
            } else {
               Map<String, String> optionsMap = new LinkedHashMap<>();
               optionsMap.put("maxdebt", "maxdebt");
               optionsMap.put("credit", "maxdebt");
               optionsMap.put("balance", "curdebt");
               optionsMap.put("limit", "maxdebt");
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
                     this.search = this.customerDataLogic.getCustomersByValueSearch(optionsMap.get(option) + operator + sb.toString().trim(), this.localGuid);
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
                        ObservableList<CustomerInfoTable> search = this.mainTable.getItems();
                        this.filteredList = new FilteredList<>(search, t -> StringUtils.containsIgnoreCase(t.getSearchStr(), this.searchStr));
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
      CustomerInfo c = (CustomerInfo)o;
      ObservableList<CustomerInfoTable> tempList = FXCollections.observableArrayList();
      this.filteredList
         .forEach(
            f -> {
               if (f.getId().equals(c.getId())) {
                  tempList.add(
                     new CustomerInfoTable(
                        c.getId(),
                        c.getName(),
                        c.getAccountNumber(),
                        c.isTaxExempt(),
                        c.getReviewDate(),
                        c.isActiveCustomer(),
                        c.isMarketable(),
                        c.getEmail(),
                        c.isLoyaltyenabled(),
                        c.isAccount(),
                        c.getSearchStr()
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
            CustomerController.this.mainTable.requestFocus();
            CustomerController.this.mainTable.scrollTo(0);
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
            CustomerController.this.mainTable.requestFocus();
            CustomerController.this.mainTable.scrollTo(CustomerController.this.mainTable.getItems().size() - 1);
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
            CustomerController.this.mainTable.requestFocus();
            CustomerController.this.mainTable.getSelectionModel().select(0);
            CustomerController.this.mainTable.getFocusModel().focus(0);
         }
      });
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.customers") + " :- " + this.mainTable.getItems().size() + " records");
   }

   @Override
   public void resetFilterLists() {
      this.filteredList = new FilteredList(this.customerData, t -> true);
      this.sortedData = new SortedList(this.filteredList);
      this.sortedData.comparatorProperty().bind(this.mainTable.comparatorProperty());
      this.mainTable.setItems(this.sortedData);
      this.lblSeletedTitle.setText(LocalResource.getString("lbl.customers") + " :- " + this.mainTable.getItems().size() + " records");
      this.mainTable.requestFocus();
      this.mainTable.getSelectionModel().select(0);
      this.mainTable.getFocusModel().focus(0);
   }

   @Override
   public void clearSearchResults() {
      this.filteredList = new FilteredList(this.customerData, t -> true);
      this.updateTable();
   }
}
