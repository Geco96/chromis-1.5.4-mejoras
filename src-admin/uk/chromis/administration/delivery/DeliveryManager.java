package uk.chromis.administration.delivery;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Parent;
import uk.chromis.administration.base.MenuPanelInterface;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.main.StartController;

/** Menu entry for the delivery manager (zones, statuses and the map). */
public class DeliveryManager implements MenuPanelInterface {

    private static DeliveryManagerController panelControl;
    private static Parent root;
    public StringProperty jobClass = new SimpleStringProperty();

    @Override
    public void showPanel() throws Exception {
        StartController.jobClass.bind(jobClass);
        if (panelControl == null) {
            panelControl = new DeliveryManagerController();
            root = panelControl.root();
        } else {
            panelControl.reloadData();
        }
        jobClass.setValue(getClass().getName());
        Start.INTERNALANCHOR.getChildren().clear();
        Start.INTERNALANCHOR.getChildren().add(root);
    }

    @Override
    public void processDataChecks() {
        // Changes are saved as soon as they are made
    }
}
