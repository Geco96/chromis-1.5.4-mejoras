package uk.chromis.administration.restock;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import uk.chromis.administration.base.MenuPanelInterface;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.main.StartController;
import uk.chromis.pos.forms.LocalResource;

/** Menu entry for the restock screen (thresholds and order PDF). */
public class Restock implements MenuPanelInterface {

    private static RestockController panelControl;
    private static Parent root;
    public StringProperty jobClass = new SimpleStringProperty();

    @Override
    public void showPanel() throws Exception {
        StartController.jobClass.bind(jobClass);
        if (panelControl == null) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/uk/chromis/administration/restock/restock.fxml"),
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
        // Thresholds are saved as soon as each cell is edited
    }
}
