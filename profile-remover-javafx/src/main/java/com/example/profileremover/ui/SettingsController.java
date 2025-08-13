package com.example.profileremover.ui;

import com.example.profileremover.core.AppContext;
import com.example.profileremover.core.model.AppSettings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.Arrays;

public class SettingsController {
    @FXML private TextField pstoolsDir;
    @FXML private Spinner<Integer> connectTimeout;
    @FXML private Spinner<Integer> execTimeout;
    @FXML private TextField ldapHost;
    @FXML private Spinner<Integer> ldapPort;
    @FXML private TextField ldapBindDn;
    @FXML private PasswordField ldapPassword;
    @FXML private TextField baseDn;
    @FXML private TextField displayAttr;
    @FXML private TextField allowedHosts;
    @FXML private Button saveBtn;
    @FXML private Button cancelBtn;

    @FXML
    public void initialize() {
        AppSettings s = AppContext.getSettings();
        pstoolsDir.setText(s.pstoolsDir);
        connectTimeout.getValueFactory().setValue(s.connectTimeoutSec);
        execTimeout.getValueFactory().setValue(s.execTimeoutSec);
        ldapHost.setText(s.ldapHost);
        ldapPort.getValueFactory().setValue(s.ldapPort);
        ldapBindDn.setText(s.ldapBindDn);
        ldapPassword.setText(s.ldapPassword);
        baseDn.setText(s.baseDn);
        displayAttr.setText(s.displayAttr);
        allowedHosts.setText(String.join(",", s.allowedHosts));
    }

    @FXML
    public void onSave() {
        AppSettings s = new AppSettings();
        s.pstoolsDir = pstoolsDir.getText().trim();
        s.connectTimeoutSec = connectTimeout.getValue();
        s.execTimeoutSec = execTimeout.getValue();
        s.ldapHost = ldapHost.getText().trim();
        s.ldapPort = ldapPort.getValue();
        s.ldapBindDn = ldapBindDn.getText().trim();
        s.ldapPassword = ldapPassword.getText();
        s.baseDn = baseDn.getText().trim();
        s.displayAttr = displayAttr.getText().trim();
        s.allowedHosts = Arrays.stream(allowedHosts.getText().split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toArray(String[]::new);
        try {
            AppContext.saveSettings(s);
        } catch (Exception ignored) {}
        close();
    }

    @FXML
    public void onCancel() { close(); }

    private void close() {
        Stage stage = (Stage) saveBtn.getScene().getWindow();
        stage.close();
    }
}


