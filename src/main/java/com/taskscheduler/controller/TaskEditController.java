package com.taskscheduler.controller;

import com.taskscheduler.exception.TaskValidationException;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskCategory;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.service.TaskService;
import com.taskscheduler.util.NumberUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class TaskEditController implements Initializable {
    private TaskService service;
    private Task editing;
    private Task savedTask;
    private boolean saved = false;

    @FXML private TextField fieldTitle;
    @FXML private TextArea fieldDescription;
    @FXML private RadioButton rbLow;
    @FXML private RadioButton rbMedium;
    @FXML private RadioButton rbHigh;
    @FXML private RadioButton rbUrgent;
    @FXML private ToggleGroup priorityGroup;
    @FXML private ComboBox<TaskStatus> fieldStatus;
    @FXML private ChoiceBox<TaskCategory> fieldCategory;
    @FXML private DatePicker fieldDueDate;
    @FXML private Slider fieldProgressSlider;
    @FXML private Spinner<Integer> fieldProgressSpinner;
    @FXML private TextField fieldAssignedTo;
    @FXML private CheckBox checkHighVisibility;
    @FXML private Button btnCancel;
    @FXML private Button btnSave;
    @FXML private Label errorLabel;

    public void setService(TaskService service) {
        this.service = service;
    }

    public void setTask(Task task) {
        this.editing = task;
        if (task == null) {
            fieldDueDate.setValue(LocalDate.now());
            return;
        }
        fieldTitle.setText(task.getTitle());
        fieldDescription.setText(task.getDescription());
        switch (task.getPriority()) {
            case LOW -> rbLow.setSelected(true);
            case MEDIUM -> rbMedium.setSelected(true);
            case HIGH -> rbHigh.setSelected(true);
            case URGENT -> rbUrgent.setSelected(true);
        }
        fieldStatus.setValue(task.getStatus());
        fieldCategory.setValue(task.getCategory());
        fieldDueDate.setValue(task.getDueDate());
        fieldProgressSlider.setValue(task.getProgress());
        fieldProgressSpinner.getValueFactory().setValue(task.getProgress());
        fieldAssignedTo.setText(task.getAssignedTo());
    }

    public boolean isSaved() { return saved; }
    public Task getSavedTask() { return savedTask; }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        fieldStatus.getItems().addAll(TaskStatus.values());
        fieldStatus.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(TaskStatus s) { return s == null ? "" : s.getDisplayName(); }
            @Override public TaskStatus fromString(String s) { return TaskStatus.fromDisplayName(s); }
        });
        fieldStatus.getSelectionModel().select(TaskStatus.PENDING);

        fieldCategory.getItems().addAll(TaskCategory.values());
        fieldCategory.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(TaskCategory c) { return c == null ? "" : c.getDisplayName(); }
            @Override public TaskCategory fromString(String s) { return TaskCategory.fromDisplayName(s); }
        });
        fieldCategory.getSelectionModel().select(TaskCategory.OTHER);

        fieldDueDate.setConverter(new javafx.util.StringConverter<>() {
            private final java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            @Override public String toString(LocalDate d) { return d == null ? "" : d.format(fmt); }
            @Override public LocalDate fromString(String s) {
                try { return (s == null || s.isBlank()) ? LocalDate.now() : LocalDate.parse(s.trim(), fmt); }
                catch (Exception e) { return LocalDate.now(); }
            }
        });

        fieldProgressSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 0, 1));
        fieldProgressSpinner.setEditable(true);
        fieldProgressSpinner.getEditor().focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) {
                String text = fieldProgressSpinner.getEditor().getText();
                Integer parsed = NumberUtils.parseIntOrNull(text);
                if (parsed == null || parsed < 0) parsed = 0;
                if (parsed > 100) parsed = 100;
                fieldProgressSpinner.getValueFactory().setValue(parsed);
                fieldProgressSlider.setValue(parsed);
            }
        });

        fieldProgressSlider.valueProperty().addListener((obs, old, newVal) -> {
            int intVal = newVal.intValue();
            if (!fieldProgressSpinner.getValue().equals(intVal)) {
                fieldProgressSpinner.getValueFactory().setValue(intVal);
            }
        });
        fieldProgressSpinner.valueProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && fieldProgressSlider.getValue() != newVal) {
                fieldProgressSlider.setValue(newVal);
            }
        });

        Runnable clearError = this::clearError;
        fieldTitle.textProperty().addListener((obs, o, n) -> clearError.run());
        fieldDueDate.valueProperty().addListener((obs, o, n) -> clearError.run());

        demonstrateBigDecimal();
    }

    @FXML
    private void onCancel() {
        saved = false;
        closeWindow();
    }

    @FXML
    private void onSave() {
        clearError();
        try {
            Task t = readFromForm();
            t.validate();
            if (editing == null) {
                savedTask = service.createTask(t);
            } else {
                t.setId(editing.getId());
                savedTask = service.updateTask(t);
            }
            saved = true;
            if (checkHighVisibility.isSelected()) {
                showInfo("Dashboard", "Task highlighted on dashboard.");
            }
            closeWindow();
        } catch (TaskValidationException e) {
            setError(e.getMessage());
            Platform.runLater(() -> {
                if (fieldTitle.getText() == null || fieldTitle.getText().trim().isEmpty()) {
                    fieldTitle.requestFocus();
                } else if (fieldDueDate.getValue() == null) {
                    fieldDueDate.requestFocus();
                }
            });
        } catch (Exception e) {
            setError("Save failed: " + e.getMessage());
        }
    }

    private Task readFromForm() {
        String title = fieldTitle.getText() == null ? "" : fieldTitle.getText().trim();
        String desc = fieldDescription.getText() == null ? "" : fieldDescription.getText().trim();
        TaskPriority priority = selectedPriority();
        TaskStatus status = fieldStatus.getValue();
        TaskCategory cat = fieldCategory.getValue();
        LocalDate due = fieldDueDate.getValue();
        int progress = fieldProgressSpinner.getValue() != null ? fieldProgressSpinner.getValue() : 0;
        String assigned = fieldAssignedTo.getText() == null ? "" : fieldAssignedTo.getText().trim();
        return new Task(0, title, desc, priority,
                status == null ? TaskStatus.PENDING : status,
                cat == null ? TaskCategory.OTHER : cat,
                due, progress, assigned);
    }

    private TaskPriority selectedPriority() {
        RadioButton sel = (RadioButton) priorityGroup.getSelectedToggle();
        if (sel == rbLow) return TaskPriority.LOW;
        if (sel == rbHigh) return TaskPriority.HIGH;
        if (sel == rbUrgent) return TaskPriority.URGENT;
        return TaskPriority.MEDIUM;
    }

    private void setError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
    }

    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static void demonstrateBigDecimal() {
        BigDecimal a = new BigDecimal("100.15");
        BigDecimal b = new BigDecimal("7.99");
        BigDecimal diff = a.subtract(b).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = new BigDecimal("0.1234");
        @SuppressWarnings("unused")
        String money = NumberFormat.getCurrencyInstance().format(diff);
        @SuppressWarnings("unused")
        String pct = NumberFormat.getPercentInstance().format(percent);
    }
}
