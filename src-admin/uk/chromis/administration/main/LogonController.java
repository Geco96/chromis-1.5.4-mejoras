package uk.chromis.administration.main;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import uk.chromis.administration.dialogs.Dialogs;
import uk.chromis.data.loader.ConnectionFactory;
import uk.chromis.pos.forms.LocalResource;
import uk.chromis.pos.util.Hashcypher;

public class LogonController implements Initializable {
   public AnchorPane anchor;
   public AnchorPane INTERNALANCHOR;
   public Label name;
   public Label password;
   public ComboBox<String> userName;
   public PasswordField userPassword;
   public Button login;
   private Boolean authenticated = false;
   private String user;
   private String id;
   private Integer rightsLevel = 0;
   private String role = null;
   private HashSet<String> m_apermissions;
   private HashSet<String> reports;
   private static final Preferences PREFS = Preferences.userNodeForPackage(LogonController.class);

   public void initialize(URL location, ResourceBundle resources) {
      // Registered users to pick from; still editable so a name or a card can be typed
      try {
         Connection con = ConnectionFactory.getInstance().getConnection();
         try (PreparedStatement stmt = con.prepareStatement("select name from people where visible = 1 order by name");
               ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
               this.userName.getItems().add(rs.getString(1));
            }
         }
      } catch (SQLException ex) {
         Logger.getLogger(LogonController.class.getName()).log(Level.WARNING, "No se pudo leer la lista de usuarios", ex);
      }

      String last = PREFS.get("lastUser", "Administrator");
      if (this.userName.getItems().contains(last)) {
         this.userName.setValue(last);
      } else if (this.userName.getItems().contains("Administrator") || this.userName.getItems().isEmpty()) {
         this.userName.setValue("Administrator");
      } else {
         this.userName.setValue(this.userName.getItems().get(0));
      }
      this.userName.valueProperty().addListener((obs, oldValue, newValue) -> {
         if (newValue != null && this.userName.getItems().contains(newValue)) {
            Platform.runLater(this.userPassword::requestFocus);
         }
      });
      Platform.runLater(this.userPassword::requestFocus);
   }

   private String typedUser() {
      String text = this.userName.getEditor().getText();
      return text == null ? "" : text.trim();
   }

   public void exitSystem() {
      System.exit(0);
   }

   public void login() {
      Connection con = ConnectionFactory.getInstance().getConnection();
      Hashcypher hash = new Hashcypher();
      this.user = "";
      this.m_apermissions = new HashSet<>();
      this.reports = new HashSet<>();

      try {
         String sql = "select * from people where card = ? ";
         PreparedStatement stmt = con.prepareStatement(sql);
         stmt.setString(1, this.typedUser());

         for (ResultSet rs = stmt.executeQuery(); rs.next(); this.id = rs.getString("id")) {
            this.authenticated = true;
            this.user = rs.getString("name");
            this.role = rs.getString("role");
         }

         if (this.user.isBlank()) {
            sql = "select * from people where name = ? ";
            stmt = con.prepareStatement(sql);
            stmt.setString(1, this.typedUser());

            for (ResultSet var18 = stmt.executeQuery(); var18.next(); this.id = var18.getString("id")) {
               this.authenticated = Hashcypher.authenticate(this.userPassword.getText(), var18.getString("apppassword"));
               this.user = this.typedUser();
               this.role = var18.getString("role");
            }
         }

         sql = "select permissions from roles where id =? ";
         stmt = con.prepareStatement(sql);
         stmt.setString(1, this.role);
         ResultSet var19 = stmt.executeQuery();

         while (var19.next()) {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(var19.getBytes("permissions")));

            for (Object o : (ArrayList)ois.readObject()) {
               this.m_apermissions.add((String)o);
            }
         }

         sql = "select rightslevel from roles where id =? ";
         stmt = con.prepareStatement(sql);
         stmt.setString(1, this.role);
         var19 = stmt.executeQuery();

         while (var19.next()) {
            this.rightsLevel = var19.getInt("rightslevel");
         }
      } catch (SQLException var10) {
      } catch (ClassNotFoundException | IOException var11) {
         Logger.getLogger(LogonController.class.getName()).log(Level.SEVERE, null, var11);
      }

      if (this.authenticated) {
         PREFS.put("lastUser", this.user);
         Stage stage = (Stage)this.userName.getScene().getWindow();
         stage.close();
      } else {
         Dialogs.showDialog(8, LocalResource.getString("dialog.unableToLogin"), (Stage)this.userName.getScene().getWindow(), true);
         this.userPassword.setText("");
         this.userPassword.requestFocus();
      }
   }

   public Integer getUserRightsLevel() {
      return this.rightsLevel;
   }

   public String getActiveUser() {
      return this.user;
   }

   public String getUserId() {
      return this.id;
   }

   public Boolean hasPemissions(String access) {
      return this.m_apermissions.contains(access);
   }

   public HashSet<String> getPermissions() {
      return this.m_apermissions;
   }

   public String getRole() {
      return this.role;
   }
}
