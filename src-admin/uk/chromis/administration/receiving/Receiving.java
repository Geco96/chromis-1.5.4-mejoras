package uk.chromis.administration.receiving;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import uk.chromis.administration.base.MenuPanelInterface;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.main.StartController;
import uk.chromis.pos.forms.LocalResource;

/** Menu entry for receiving supplier orders by scanning the order barcode. */
public class Receiving implements MenuPanelInterface {

    private static ReceivingController panelControl;
    private static Parent root;
    public StringProperty jobClass = new SimpleStringProperty();

    @Override
    public void showPanel() throws Exception {
        StartController.jobClass.bind(jobClass);
        if (panelControl == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/uk/chromis/administration/receiving/receiving.fxml"),
                    LocalResource.resources);
            root = loader.load();
            panelControl = loader.getController();
        } else {
            panelControl.reloadData();
        }
        jobClass.setValue(getClass().getName());
        Start.INTERNALANCHOR.getChildren().clear();
        Start.INTERNALANCHOR.getChildren().add(root);
    }

    @Override
    public void processDataChecks() {
        // Receipts are saved when confirmed
    }
}
