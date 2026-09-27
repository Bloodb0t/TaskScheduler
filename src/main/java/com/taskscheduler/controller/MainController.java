package com.taskscheduler.controller;

import com.taskscheduler.concurrent.ProgressTrackerThread;
import com.taskscheduler.concurrent.TaskConsumer;
import com.taskscheduler.concurrent.TaskExecutionRequest;
import com.taskscheduler.model.*;
import com.taskscheduler.service.TaskService;
import com.taskscheduler.util.NumberUtils;
import com.taskscheduler.util.TaskFilter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    private final TaskService service;
    private final ObservableList<Task> tasks = FXCollections.observableArrayList();

    private Stage primaryStage;
    private Scene mainScene;
    private Scene statsScene;
    private StatsController statsController;

    private ProgressTrackerThread trackerThread;

    @FXML private MenuItem menuNew;
    @FXML private MenuItem menuExit;
    @FXML private MenuItem menuAbout;
    @FXML private MenuItem menuRunPipeline;
    @FXML private MenuItem menuExecuteSelected;
    @FXML private MenuItem menuRefresh;
    @FXML private MenuItem menuStats;

    @FXML private TextField searchField;
    @FXML private ComboBox<TaskStatus> statusFilter;
    @FXML private ChoiceBox<TaskCategory> categoryFilter;
    @FXML private ComboBox<TaskPriority> priorityFilter;
    @FXML private CheckBox hideDoneCheck;
    @FXML private CheckBox overdueOnlyCheck;

    @FXML private TableView<Task> taskTable;
    @FXML private TableColumn<Task, Number> colId;
    @FXML private TableColumn<Task, String> colTitle;
    @FXML private TableColumn<Task, TaskPriority> colPriority;
    @FXML private TableColumn<Task, TaskStatus> colStatus;
    @FXML private TableColumn<Task, TaskCategory> colCategory;
    @FXML private TableColumn<Task, LocalDate> colDue;
    @FXML private TableColumn<Task, Double> colProgress;
    @FXML private TableColumn<Task, String> colAssigned;

    @FXML private TextField detailTitle;
    @FXML private TextField detailStatus;
    @FXML private TextField detailPriority;
    @FXML private TextField detailCategory;
    @FXML private TextField detailDue;
    @FXML private TextField detailAssigned;
    @FXML private ProgressBar detailProgressBar;
    @FXML private TextArea detailDescription;

    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    @FXML private ProgressBar pipelineProgress;
    @FXML private Label pipelineLabel;
    @FXML private Label statusBar;

    public MainController() {
        this.service = new TaskService();
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setMainScene(Scene mainScene) {
        this.mainScene = mainScene;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(service.getStatuses());
        statusFilter.setCellFactory(lv -> new StatusListCell());
        statusFilter.setButtonCell(new StatusListCell());
        statusFilter.setConverter(new EnumStringConverter<>(TaskStatus::fromDisplayName, TaskStatus::getDisplayName));
        statusFilter.valueProperty().addListener((obs, o, n) -> onFilterChange());

        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(service.getCategories());
        categoryFilter.setConverter(new EnumStringConverter<>(TaskCategory::fromDisplayName, TaskCategory::getDisplayName));
        categoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> onFilterChange());

        priorityFilter.getItems().add(null);
        priorityFilter.getItems().addAll(service.getPriorities());
        priorityFilter.setConverter(new EnumStringConverter<>(TaskPriority::fromDisplayName, TaskPriority::getDisplayName));
        priorityFilter.valueProperty().addListener((obs, o, n) -> onFilterChange());

        configureTableColumns();
        taskTable.setItems(tasks);
        taskTable.getSelectionModel().selectedItemProperty().addListener(this::onTaskSelectionChanged);
        taskTable.setRowFactory(new TaskRowFactory());

        taskTable.setOnKeyPressed(onTableKey());

        service.seedSampleDataIfEmpty();
        reloadTasks();

        trackerThread = new ProgressTrackerThread("UI-Progress-Tracker", this::updatePipelineBarFromTracker);
        trackerThread.start();
    }

    private void configureTableColumns() {
        colId.setCellValueFactory(cd -> cd.getValue().idProperty());

        colTitle.setCellValueFactory(cd -> cd.getValue().titleProperty());
        colTitle.setCellFactory(tc -> new TitleTextCell());

        colPriority.setCellValueFactory(cd -> cd.getValue().priorityProperty());
        colPriority.setCellFactory(tc -> new PriorityColorCell());

        colStatus.setCellValueFactory(cd -> cd.getValue().statusProperty());
        colStatus.setCellFactory(tc -> new StatusColorCell());

        colCategory.setCellValueFactory(cd -> cd.getValue().categoryProperty());

        colDue.setCellValueFactory(cd -> cd.getValue().dueDateProperty());
        colDue.setCellFactory(tc -> new DateCell());
        colDue.setComparator((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return a.compareTo(b);
        });

        colProgress.setCellValueFactory(cd -> cd.getValue().progressProperty().divide(100.0).asObject());
        colProgress.setCellFactory(ProgressBarTableCell.forTableColumn());

        colAssigned.setCellValueFactory(cd -> cd.getValue().assignedToProperty());
    }

    private void onTaskSelectionChanged(ObservableValue<? extends Task> obs, Task old, Task t) {
        if (t == null) {
            clearDetails();
            return;
        }
        detailTitle.setText(t.getTitle());
        detailStatus.setText(t.getStatus().getDisplayName());
        detailPriority.setText(t.getPriority().getDisplayName());
        detailCategory.setText(t.getCategory().getDisplayName());
        detailDue.setText(t.formattedDueDate());
        detailAssigned.setText(t.getAssignedTo());
        detailProgressBar.setProgress(t.getProgress() / 100.0);
        detailDescription.setText(t.getDescription());
    }

    private void clearDetails() {
        detailTitle.clear();
        detailStatus.clear();
        detailPriority.clear();
        detailCategory.clear();
        detailDue.clear();
        detailAssigned.clear();
        detailProgressBar.setProgress(0);
        detailDescription.clear();
    }

    private EventHandler<KeyEvent> onTableKey() {
        return event -> {
            if (event.getCode() == KeyCode.DELETE) {
                onDeleteTask();
                event.consume();
            }
            if (event.getCode() == KeyCode.ENTER) {
                onEditTask();
                event.consume();
            }
        };
    }

    @FXML
    private void onSearchKey() {
        onFilterChange();
    }

    @FXML
    private void onFilterChange() {
        applyFilters();
    }

    private void applyFilters() {
        TaskFilter f = TaskFilter.alwaysTrue();

        String search = searchField.getText();
        if (search != null && !search.trim().isEmpty()) {
            final String q = search.trim().toUpperCase();
            f = f.and(t -> (t.getTitle() != null && t.getTitle().toUpperCase().contains(q))
                    || (t.getAssignedTo() != null && t.getAssignedTo().toUpperCase().contains(q)));
        }
        TaskStatus st = statusFilter.getValue();
        if (st != null) f = f.and(t -> t.getStatus() == st);
        TaskCategory cat = categoryFilter.getValue();
        if (cat != null) f = f.and(t -> t.getCategory() == cat);
        TaskPriority pr = priorityFilter.getValue();
        if (pr != null) f = f.and(t -> t.getPriority() == pr);
        if (hideDoneCheck.isSelected()) f = f.and(t -> !t.getStatus().isTerminal());
        if (overdueOnlyCheck.isSelected()) f = f.and(Task::isOverdue);

        List<Task> filtered = service.findFiltered(f);
        tasks.setAll(filtered);
        updateStatusBar("Filtered: %d of %d tasks shown.".formatted(filtered.size(), service.findAll().size()));
    }

    private void reloadTasks() {
        tasks.setAll(service.findAll());
        applyFilters();
    }

    @FXML
    private void onRefresh() {
        reloadTasks();
        updateStatusBar("Refreshed " + tasks.size() + " tasks.");
    }

    @FXML
    private void onNewTask() {
        openEditDialog(null);
    }

    @FXML
    private void onMenuNew() {
        onNewTask();
    }

    @FXML
    private void onEditTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a task to edit.");
            return;
        }
        openEditDialog(selected);
    }

    @FXML
    private void onDeleteTask() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a task to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete task \"%s\"?".formatted(selected.getTitle()),
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.YES) {
            try {
                service.deleteTask(selected.getId());
                tasks.remove(selected);
                updateStatusBar("Deleted task id=" + selected.getId());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Delete Failed", e.getMessage());
            }
        }
    }

    @FXML
    private void onMenuSeedSample() {
        int added = service.addSampleData();
        reloadTasks();
        showAlert(Alert.AlertType.INFORMATION, "Sample Data",
                added > 0 ? "Added " + added + " sample tasks." : "No new sample tasks to add (they already exist in the database).");
    }

    @FXML
    private void onMenuExit() {
        shutdownApp();
    }

    @FXML
    private void onMenuAbout() {
        String msg = "JavaFX Multithreaded Task Manager\n" +
                "Java 21 / JavaFX 21 / SQLite\n\n" +
                "Demonstrates Java Foundations, Concurrency, JavaFX UI, SQLite DAO, FXML MVC.";
        showAlert(Alert.AlertType.INFORMATION, "About", msg);
    }

    @FXML
    private void onMenuShowStats() {
        openStatsScene();
    }

    public void setStatsScene(Scene scene, StatsController controller) {
        this.statsScene = scene;
        this.statsController = controller;
    }

    private void openStatsScene() {
        if (statsScene == null || statsController == null || primaryStage == null) return;
        statsController.setMainScene(mainScene, this);
        primaryStage.setScene(statsScene);
        primaryStage.setTitle("Task Scheduler — Statistics");
        statsController.refresh();
    }

    public void returnFromStats() {
        if (primaryStage != null && mainScene != null) {
            primaryStage.setScene(mainScene);
            primaryStage.setTitle("Task Scheduler — Desktop Manager");
            reloadTasks();
        }
    }

    @FXML
    private void onExecuteSelected() {
        Task selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a task to execute asynchronously.");
            return;
        }
        trackerThread.incrementTotal();
        updateStatusBar("Submitting task id=" + selected.getId() + " for async execution...");
        service.executeTaskAsync(selected, req -> Platform.runLater(() -> {
            trackerThread.incrementCompleted();
            String result = req.getFinalStatus() == TaskStatus.COMPLETED ? "succeeded" : "failed";
            updateStatusBar("Task id=%d async execution %s in %dms"
                    .formatted(req.getTask().getId(), result, req.millisTaken()));
        }));
    }

    @FXML
    private void onRunPipeline() {
        if (tasks.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Tasks", "Seed or create tasks first.");
            return;
        }
        List<Task> seed = List.copyOf(tasks);
        for (int i = 0; i < seed.size(); i++) trackerThread.incrementTotal();
        pipelineProgress.setProgress(0);
        pipelineLabel.setText("Running pipeline...");
        updateStatusBar("Starting producer-consumer pipeline with " + seed.size() + " tasks.");
        service.startPipeline(seed, req -> Platform.runLater(() -> {
            trackerThread.incrementCompleted();
        }));
    }

    private void updatePipelineBarFromTracker() {
        long done = trackerThread.getCompletedCount();
        long total = trackerThread.getTotalCount();
        double p = total == 0 ? 0 : (double) done / total;
        pipelineProgress.setProgress(p);
        pipelineLabel.setText("%d / %d (%.0f%%)".formatted(done, total, p * 100));
    }

    private Parent loadFxmlSafe(String resourcePath) throws IOException {
        URL location = getClass().getResource(resourcePath);
        if (location == null) {
            throw new IOException("Resource not found on classpath: " + resourcePath);
        }
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(location);
        loader.setClassLoader(getClass().getClassLoader());
        loader.setResources(null);
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Could not open InputStream for: " + resourcePath);
            }
            return loader.load(in);
        }
    }

    private String stylesheetUrlSafe(String resourcePath) {
        URL url = getClass().getResource(resourcePath);
        if (url == null) return null;
        return url.toExternalForm();
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
        }
        return sw.toString();
    }

    private void openEditDialog(Task task) {
        String titleText = task == null ? "New Task" : "Edit Task — #" + task.getId();
        String fxmlPath = "/com/taskscheduler/view/task-edit-dialog.fxml";
        try {
            URL location = getClass().getResource(fxmlPath);
            if (location == null) {
                throw new IOException("FXML resource not found on classpath: " + fxmlPath);
            }
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(location);
            loader.setClassLoader(getClass().getClassLoader());
            Parent root;
            try (InputStream in = getClass().getResourceAsStream(fxmlPath)) {
                if (in == null) {
                    throw new IOException("Could not open InputStream for FXML: " + fxmlPath);
                }
                root = loader.load(in);
            }
            TaskEditController ctrl = loader.getController();
            if (ctrl == null) {
                throw new IOException("FXMLLoader did not produce a controller instance for: " + fxmlPath);
            }
            ctrl.setService(service);
            ctrl.setTask(task == null ? null : task.copy());

            Stage dialog = new Stage();
            dialog.setTitle(titleText);
            dialog.initModality(Modality.WINDOW_MODAL);
            Window owner = taskTable.getScene() == null ? primaryStage : taskTable.getScene().getWindow();
            dialog.initOwner(owner);
            Scene scene = new Scene(root);
            String css = stylesheetUrlSafe("/com/taskscheduler/style.css");
            if (css != null) {
                scene.getStylesheets().add(css);
            }
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            if (ctrl.isSaved()) {
                reloadTasks();
                if (task == null) {
                    updateStatusBar("Created task: " + ctrl.getSavedTask().getTitle());
                } else {
                    updateStatusBar("Updated task id=" + ctrl.getSavedTask().getId());
                }
                Task saved = ctrl.getSavedTask();
                taskTable.getSelectionModel().select(saved);
                taskTable.scrollTo(saved);
            }
        } catch (Throwable t) {
            String stack = stackTraceToString(t);
            System.err.println("=== openEditDialog FAILED ===");
            System.err.println(stack);
            String detail = t.getMessage() == null ? t.toString() : t.getMessage();
            Throwable cause = t.getCause();
            while (cause != null) {
                if (cause.getMessage() != null) {
                    detail = detail + "\nCaused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
                }
                cause = cause.getCause();
            }
            detail = detail + "\n\n(Full stack trace printed to stderr/console — see IDE Run window.)";
            showAlert(Alert.AlertType.ERROR, "Open Failed", "Could not open edit dialog:\n" + detail);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void updateStatusBar(String text) {
        if (statusBar != null) statusBar.setText(text);
    }

    public void shutdownApp() {
        if (trackerThread != null) trackerThread.stopTracking();
        service.shutdownExecutor();
        Platform.exit();
    }

    public TaskService getService() {
        return service;
    }

    private static class EnumStringConverter<E extends Enum<E>> extends javafx.util.StringConverter<E> {
        private final java.util.function.Function<String, E> fromDisplay;
        private final java.util.function.Function<E, String> toDisplay;

        EnumStringConverter(java.util.function.Function<String, E> fromDisplay,
                            java.util.function.Function<E, String> toDisplay) {
            this.fromDisplay = fromDisplay;
            this.toDisplay = toDisplay;
        }

        @Override
        public String toString(E e) {
            return e == null ? "(All)" : toDisplay.apply(e);
        }

        @Override
        public E fromString(String s) {
            return s == null || s.isEmpty() || "(All)".equals(s) ? null : fromDisplay.apply(s);
        }
    }

    private static class StatusListCell extends ListCell<TaskStatus> {
        @Override
        protected void updateItem(TaskStatus item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText("(All)");
            } else {
                setText(item.getDisplayName());
            }
        }
    }

    private static class DateCell extends TableCell<Task, LocalDate> {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        protected void updateItem(LocalDate item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }
            setText(item.format(FMT));
            Task t = getTableView().getItems().get(getIndex());
            if (t.isOverdue()) {
                setStyle("-fx-text-fill: #c41e3a; -fx-font-weight: bold;");
            } else if (item.equals(LocalDate.now())) {
                setStyle("-fx-text-fill: #d97706;");
            } else {
                setStyle("");
            }
        }
    }

    private static class PriorityColorCell extends TableCell<Task, TaskPriority> {
        @Override
        protected void updateItem(TaskPriority item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }
            setText(item.getDisplayName());
            String color = switch (item) {
                case URGENT -> "-fx-text-fill: #991b1b; -fx-font-weight: bold;";
                case HIGH   -> "-fx-text-fill: #c2410c;";
                case MEDIUM -> "-fx-text-fill: #a16207;";
                case LOW    -> "-fx-text-fill: #15803d;";
            };
            setStyle(color);
        }
    }

    private static class StatusColorCell extends TableCell<Task, TaskStatus> {
        @Override
        protected void updateItem(TaskStatus item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }
            setText(item.getDisplayName());
            String color = switch (item) {
                case COMPLETED   -> "-fx-text-fill: #15803d;";
                case IN_PROGRESS -> "-fx-text-fill: #1d4ed8;";
                case CANCELLED   -> "-fx-text-fill: #6b7280;";
                case PENDING     -> "-fx-text-fill: #78350f;";
            };
            setStyle(color);
        }
    }

    private static class TitleTextCell extends TableCell<Task, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }
            setText(item);
            Task t = getTableView().getItems().get(getIndex());
            if (t.isOverdue()) {
                setStyle("-fx-font-weight: bold; -fx-text-fill: #c41e3a;");
            } else {
                setStyle("");
            }
        }
    }

    private static class TaskRowFactory implements Callback<TableView<Task>, TableRow<Task>> {
        @Override
        public TableRow<Task> call(TableView<Task> view) {
            return new TableRow<>() {
                @Override
                protected void updateItem(Task t, boolean empty) {
                    super.updateItem(t, empty);
                    if (empty || t == null) {
                        setStyle("");
                        return;
                    }
                    StringBuilder style = new StringBuilder(80);
                    if (t.isOverdue()) {
                        style.append("-fx-background-color: #fff1f2;");
                    } else if (t.getStatus() == TaskStatus.COMPLETED) {
                        style.append("-fx-background-color: #f0fdf4;");
                    } else if (t.getPriority() == TaskPriority.URGENT) {
                        style.append("-fx-background-color: #fff7ed;");
                    }
                    setStyle(style.toString());
                }
            };
        }
    }

    @SuppressWarnings("unused")
    private void demonstrateInputValidationSkeleton() {
        TextField sample = new TextField();
        try {
            int val = Integer.parseInt(sample.getText().trim());
            if (val < 0 || val > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sample.setStyle("-fx-border-color: #c41e3a;");
            sample.selectAll();
            sample.requestFocus();
        }
    }

    @SuppressWarnings("unused")
    private void demonstrateExplicitEventHandler() {
        Button b = new Button("Click");
        EventHandler<javafx.event.ActionEvent> handler = new EventHandler<>() {
            @Override
            public void handle(javafx.event.ActionEvent event) {
                System.out.println("Explicit handler fired.");
            }
        };
        b.addEventHandler(javafx.event.ActionEvent.ACTION, handler);
    }
}
