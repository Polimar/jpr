package com.example.profileremover.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class Dialogs {
    private Dialogs() {}

    public static void showSettings(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(Dialogs.class.getResource("/view/settings.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.setTitle("Impostazioni");
            dialog.initOwner(owner);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(root));
            dialog.setResizable(false);
            dialog.showAndWait();
        } catch (Exception ignored) {}
    }
}


