package uk.chromis.administration.search;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import uk.chromis.administration.base.MenuPanelInterface;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.main.StartController;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.search.SearchTagsStore;
import uk.chromis.pos.search.SearchTagsStore.Group;

/**
 * Admin → Inventario → Sinónimos de búsqueda: grupos de palabras que valen lo
 * mismo al buscar productos en la caja y en la tableta, para todos los
 * productos ("lámpara, luz, foco"). Las palabras propias de un producto van en
 * Productos → "Etiquetas de búsqueda". Tabla search_synonyms (SearchTagsStore).
 */
public class SearchSynonyms implements MenuPanelInterface {

    private static final Logger LOGGER = Logger.getLogger(SearchSynonyms.class.getName());
    private static final String CARD = "-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #d1d4d6;"
            + " -fx-border-radius: 12; -fx-padding: 14 16 14 16;";
    private static final String CHIP = "-fx-background-color: #E3F2FD; -fx-background-radius: 10; -fx-padding: 8 10 8 12;";
    private static AnchorPane root;
    private static SearchSynonyms panel;
    public StringProperty jobClass = new SimpleStringProperty();

    private final TextField txtNew = new TextField();
    private final FlowPane groups = new FlowPane(10, 10);
    private final Label lblCount = new Label();
    private final Label lblMessage = new Label();

    @Override
    public void showPanel() throws Exception {
        StartController.jobClass.bind(jobClass);
        if (panel == null) {
            panel = this;
            root = new AnchorPane();
            root.setMinSize(0, 0);
            root.setPrefHeight(720);
            build();
        }
        jobClass.setValue(getClass().getName());
        panel.reload();
        Start.INTERNALANCHOR.getChildren().clear();
        Start.INTERNALANCHOR.getChildren().add(root);
        AnchorPane.setTopAnchor(root, 0.0);
        AnchorPane.setLeftAnchor(root, 0.0);
        AnchorPane.setRightAnchor(root, 0.0);
    }

    @Override
    public void processDataChecks() {
        // Cada cambio se guarda al momento
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    private static Label blockTitle(String text) {
        Label l = new Label(text.toUpperCase());
        l.setFont(Font.font("System", FontWeight.BOLD, 13));
        l.setStyle("-fx-text-fill: #1F5FBF;");
        return l;
    }

    private void build() {
        Label title = new Label("Sinónimos de búsqueda");
        title.setFont(Font.font("System", FontWeight.BOLD, FontPosture.ITALIC, 14));
        AnchorPane.setTopAnchor(title, 57.0);
        AnchorPane.setLeftAnchor(title, 10.0);

        // Bloque: qué es
        Label help = new Label("Cada grupo junta palabras que valen lo mismo al buscar un producto en la caja "
                + "(botón de búsqueda) y en la tableta, para todos los productos. Ejemplo: con \"lámpara, luz, foco\", "
                + "quien escriba \"lámparas led\" también verá \"Luces led\".\n"
                + "No hace falta poner plurales ni acentos: la búsqueda ya los entiende. Las palabras de un solo "
                + "producto van en Productos → \"Etiquetas de búsqueda\".");
        help.setWrapText(true);
        VBox bHelp = new VBox(6, blockTitle("¿Para qué sirve?"), help);
        bHelp.setStyle(CARD);

        // Bloque: nuevo grupo
        txtNew.setPromptText("lámpara, luz, foco");
        txtNew.setPrefWidth(420);
        txtNew.setOnAction(e -> add());
        Button btnAdd = new Button("＋ Agregar grupo");
        btnAdd.setStyle("-fx-font-weight: bold;");
        btnAdd.setOnAction(e -> add());
        HBox row = new HBox(10, txtNew, btnAdd, lblMessage);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(txtNew, Priority.SOMETIMES);
        VBox bNew = new VBox(8, blockTitle("Nuevo grupo"), new Label("Palabras equivalentes, separadas por coma"), row);
        bNew.setStyle(CARD);

        // Bloque: grupos
        groups.setPrefWrapLength(900);
        VBox bGroups = new VBox(10, blockTitle("Grupos"), lblCount, groups);
        bGroups.setStyle(CARD);

        VBox content = new VBox(12, bHelp, bNew, bGroups);
        content.setPadding(new Insets(4, 4, 12, 4));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        AnchorPane.setTopAnchor(scroll, 85.0);
        AnchorPane.setLeftAnchor(scroll, 15.0);
        AnchorPane.setRightAnchor(scroll, 15.0);
        AnchorPane.setBottomAnchor(scroll, 10.0);
        root.getChildren().addAll(title, scroll);
    }

    private void reload() {
        groups.getChildren().clear();
        List<Group> list;
        try {
            list = SearchTagsStore.groups(connection());
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "search_synonyms", ex);
            message("No se pudieron leer los sinónimos: " + ex.getMessage(), true);
            return;
        }
        lblCount.setText(list.isEmpty() ? "Todavía no hay grupos." : list.size() + (list.size() == 1 ? " grupo" : " grupos"));
        for (Group g : list) {
            Label words = new Label(g.words);
            words.setFont(Font.font("System", FontWeight.BOLD, 14));
            words.setWrapText(true);
            words.setMaxWidth(360);
            Button edit = new Button("Cambiar");
            edit.setOnAction(e -> edit(g));
            Button del = new Button("Quitar");
            del.setOnAction(e -> delete(g));
            HBox chip = new HBox(8, words, edit, del);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setStyle(CHIP);
            groups.getChildren().add(chip);
        }
    }

    private void message(String text, boolean error) {
        lblMessage.setText(text);
        lblMessage.setStyle(error ? "-fx-text-fill: #C62828;" : "-fx-text-fill: #2E7D32;");
    }

    private void add() {
        String problem = SearchTagsStore.validateGroup(txtNew.getText());
        if (problem != null) {
            message(problem, true);
            return;
        }
        if (save(new Group(null, txtNew.getText()))) {
            txtNew.clear();
        }
    }

    private void edit(Group g) {
        TextInputDialog d = new TextInputDialog(g.words);
        d.setTitle("Cambiar grupo");
        d.setHeaderText("Palabras equivalentes, separadas por coma");
        d.getEditor().setPrefWidth(420);
        Optional<String> r = d.showAndWait();
        if (!r.isPresent()) {
            return;
        }
        String problem = SearchTagsStore.validateGroup(r.get());
        if (problem != null) {
            message(problem, true);
            return;
        }
        save(new Group(g.id, r.get()));
    }

    private void delete(Group g) {
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Quitar \"" + g.words + "\"? Los productos no cambian; solo deja de buscarse así.", ButtonType.OK, ButtonType.CANCEL);
        ask.setHeaderText("Quitar grupo");
        if (ask.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            SearchTagsStore.deleteGroup(connection(), g.id);
            message("Grupo quitado", false);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "search_synonyms", ex);
            message("No se pudo quitar: " + ex.getMessage(), true);
        }
        reload();
    }

    private boolean save(Group g) {
        try {
            SearchTagsStore.saveGroup(connection(), g);
            message("Guardado: " + g.words, false);
            reload();
            return true;
        } catch (SQLException | IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "search_synonyms", ex);
            message("No se pudo guardar: " + ex.getMessage(), true);
            return false;
        }
    }
}
