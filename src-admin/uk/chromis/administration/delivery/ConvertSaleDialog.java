package uk.chromis.administration.delivery;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import uk.chromis.administration.main.Start;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.delivery.SaleConversion;

/**
 * "Convertir venta en entrega": a sale already charged as a counter sale is
 * turned into a delivery. Used by Entregas and by the delivery manager.
 */
final class ConvertSaleDialog {

    private static final String DATE_PATTERN = "dd/MM/yyyy";

    private ConvertSaleDialog() {
    }

    /** Shows the dialog; true when a sale was converted. role goes to the delivery history. */
    static boolean show(Window owner, String role, String from) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Convertir venta en entrega");
        d.setHeaderText("Una venta ya cobrada como mostrador pasa a ser entrega a domicilio.\n"
                + "No cambia la venta (importes, pagos, corte ni inventario); solo se le agregan los datos de entrega.");

        TextField txtTicket = new TextField();
        txtTicket.setPromptText("Número de ticket");
        txtTicket.setPrefWidth(160);
        Button btnFind = new Button("Buscar");
        Label lblSale = new Label();
        lblSale.setStyle("-fx-font-weight: bold;");
        Label lblProblem = new Label();
        lblProblem.setStyle("-fx-text-fill: #b00020;");
        lblProblem.setWrapText(true);
        ListView<String> lines = new ListView<>();
        lines.setPrefHeight(110);

        TextField txtName = new TextField();
        TextField txtAddress1 = new TextField();
        TextField txtAddress2 = new TextField();
        TextField txtAddress3 = new TextField();
        TextField txtPostcode = new TextField();
        TextField txtPhone = new TextField();
        TextField txtDate = new TextField();
        TextArea txtComments = new TextArea();
        txtComments.setPrefRowCount(3);
        txtComments.setWrapText(true);
        CheckBox chkSave = new CheckBox("Guardar esta dirección en el cliente (no tiene dirección en su ficha)");
        chkSave.setVisible(false);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        int row = 0;
        form.addRow(row++, new Label("Recibe:"), txtName);
        form.addRow(row++, new Label("Dirección:"), txtAddress1);
        form.addRow(row++, new Label("Colonia / referencias:"), txtAddress2);
        form.addRow(row++, new Label("Ciudad:"), txtAddress3);
        form.addRow(row++, new Label("C.P.:"), txtPostcode);
        form.addRow(row++, new Label("Teléfono:"), txtPhone);
        form.addRow(row++, new Label("Fecha de entrega (dd/mm/aaaa):"), txtDate);
        form.addRow(row++, new Label("Indicaciones para el repartidor:"), txtComments);
        form.add(chkSave, 1, row);
        for (Node n : new Node[]{txtName, txtAddress1, txtAddress2, txtAddress3, txtPostcode, txtPhone, txtDate, txtComments}) {
            GridPane.setFillWidth(n, true);
            ((javafx.scene.control.Control) n).setPrefWidth(420);
        }
        form.setDisable(true);

        HBox search = new HBox(8, new Label("Ticket:"), txtTicket, btnFind);
        search.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox content = new VBox(8, search, lblSale, lines, lblProblem, form);
        content.setPadding(new Insets(10));
        d.getDialogPane().setContent(content);

        ButtonType save = new ButtonType("Convertir en entrega", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        d.getDialogPane().getButtonTypes().addAll(save, cancel);
        Node saveButton = d.getDialogPane().lookupButton(save);
        saveButton.setDisable(true);

        SaleConversion.Sale[] found = new SaleConversion.Sale[1];
        Runnable find = () -> {
            found[0] = null;
            form.setDisable(true);
            saveButton.setDisable(true);
            lines.getItems().clear();
            lblSale.setText("");
            lblProblem.setText("");
            int number;
            try {
                number = Integer.parseInt(txtTicket.getText().trim());
            } catch (NumberFormatException ex) {
                lblProblem.setText("Escribe el número de ticket (solo números).");
                return;
            }
            try {
                SaleConversion.Sale s = SaleConversion.find(connection(), number);
                if (s != null) {
                    lblSale.setText(s.summary());
                    lines.getItems().setAll(s.lines);
                }
                String problem = SaleConversion.problem(s, new Date());
                if (problem != null) {
                    lblProblem.setText(problem);
                    return;
                }
                found[0] = s;
                SaleConversion.Data data = SaleConversion.Data.fromCustomer(s);
                txtName.setText(data.name);
                txtAddress1.setText(data.address1);
                txtAddress2.setText(data.address2);
                txtAddress3.setText(data.address3);
                txtPostcode.setText(data.postcode);
                txtPhone.setText(data.phone);
                txtDate.setText(new SimpleDateFormat(DATE_PATTERN).format(new Date()));
                txtComments.setText("");
                boolean noAddress = s.customerAddress == null || s.customerAddress.trim().isEmpty();
                chkSave.setVisible(noAddress);
                chkSave.setSelected(noAddress);
                form.setDisable(false);
                saveButton.setDisable(false);
                Platform.runLater(txtAddress1.getText().isEmpty() ? txtAddress1::requestFocus : txtComments::requestFocus);
            } catch (SQLException ex) {
                lblProblem.setText("No se pudo buscar la venta: " + ex.getMessage());
            }
        };
        btnFind.setOnAction(e -> find.run());
        txtTicket.setOnAction(e -> find.run());

        boolean[] converted = new boolean[1];
        saveButton.addEventFilter(ActionEvent.ACTION, ev -> {
            SaleConversion.Sale s = found[0];
            if (s == null) {
                ev.consume();
                return;
            }
            SaleConversion.Data data = new SaleConversion.Data();
            data.name = txtName.getText();
            data.address1 = txtAddress1.getText();
            data.address2 = txtAddress2.getText();
            data.address3 = txtAddress3.getText();
            data.postcode = txtPostcode.getText();
            data.phone = txtPhone.getText();
            data.comments = txtComments.getText();
            data.saveToCustomer = chkSave.isVisible() && chkSave.isSelected();
            try {
                SimpleDateFormat f = new SimpleDateFormat(DATE_PATTERN);
                f.setLenient(false);
                data.deliveryDate = f.parse(txtDate.getText().trim());
            } catch (ParseException ex) {
                lblProblem.setText("La fecha de entrega va como día/mes/año, por ejemplo "
                        + new SimpleDateFormat(DATE_PATTERN).format(new Date()) + ".");
                ev.consume();
                return;
            }
            String problem = SaleConversion.invalid(data);
            if (problem != null) {
                lblProblem.setText(problem);
                ev.consume();
                return;
            }
            try {
                SaleConversion.convert(connection(), s, data, Start.getActiveUserId(), Start.getActiveUser(), role, from);
                converted[0] = true;
            } catch (SQLException ex) {
                lblProblem.setText("No se pudo convertir: " + ex.getMessage());
                ev.consume();
            }
        });
        Platform.runLater(txtTicket::requestFocus);
        d.showAndWait();
        return converted[0];
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }
}
