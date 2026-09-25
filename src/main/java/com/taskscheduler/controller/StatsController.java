package com.taskscheduler.controller;

import com.taskscheduler.concurrent.TaskConsumer;
import com.taskscheduler.concurrent.TaskExecutionRequest;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskCategory;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatistics;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.service.TaskService;
import com.taskscheduler.util.NumberUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxListCell;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class StatsController implements Initializable {
    private TaskService service;
    private Scene mainScene;
    private MainController mainController;

    @FXML private Label summaryLabel;
    @FXML private Label totalValue;
    @FXML private Label activeValue;
    @FXML private Label pendingValue;
    @FXML private Label inProgressValue;
    @FXML private Label completedValue;
    @FXML private Label cancelledValue;
    @FXML private Label overdueValue;
    @FXML private Label avgProgressValue;
    @FXML private ProgressBar completionBar;
    @FXML private ListView<String> priorityListView;
    @FXML private TreeView<String> breakdownTree;
    @FXML private TextArea logArea;
    @FXML private Button btnRefresh;
    @FXML private Button btnBack;

    private final ObservableList<String> priorityLines = FXCollections.observableArrayList();
    private final DateTimeFormatter logTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void setMainScene(Scene mainScene, MainController mainController) {
        this.mainScene = mainScene;
        this.mainController = mainController;
        this.service = mainController.getService();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        priorityListView.setItems(priorityLines);
        priorityListView.setCellFactory(ComboBoxListCell.forListView(priorityLines));
        logArea.setWrapText(true);
        log("Statistics view initialized.");
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onBack() {
        if (mainController != null) {
            mainController.returnFromStats();
        }
    }

    public void refresh() {
        if (service == null) return;
        log("Async computeStatistics requested...");
        Future<TaskStatistics> future = service.computeStatisticsAsync();

        javafx.concurrent.Task<TaskStatistics> jfxTask = new javafx.concurrent.Task<>() {
            @Override
            protected TaskStatistics call() throws Exception {
                while (!future.isDone()) {
                    Thread.onSpinWait();
                    Thread.sleep(25);
                }
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        };

        jfxTask.setOnSucceeded(e -> {
            TaskStatistics stats = jfxTask.getValue();
            applyStatistics(stats);
            log("Statistics refreshed successfully.");
        });
        jfxTask.setOnFailed(e -> {
            Throwable ex = jfxTask.getException();
            log("Statistics computation failed: " + ex.getMessage());
            summaryLabel.setText("Failed to load.");
        });

        new Thread(jfxTask, "StatsRefreshWorker").start();

        executeSampleRandomTaskAsync();
    }

    private void applyStatistics(TaskStatistics stats) {
        totalValue.setText(String.valueOf(stats.totalTasks()));
        activeValue.setText(String.valueOf(stats.activeTasks()));
        pendingValue.setText(String.valueOf(stats.pendingTasks()));
        inProgressValue.setText(String.valueOf(stats.inProgressTasks()));
        completedValue.setText(String.valueOf(stats.completedTasks()));
        cancelledValue.setText(String.valueOf(stats.cancelledTasks()));
        overdueValue.setText(String.valueOf(stats.overdueTasks()));
        avgProgressValue.setText(NumberUtils.formatPercent(stats.averageProgress()));
        completionBar.setProgress(stats.completionRate() / 100.0);

        priorityLines.clear();
        Map<TaskPriority, Long> byPriority = stats.tasksByPriority();
        for (TaskPriority p : TaskPriority.values()) {
            Long count = byPriority.getOrDefault(p, 0L);
            String line = "%-8s %s %d task(s)  (weight %d)"
                    .formatted(p.getDisplayName() + ":",
                            progressBarText(count, stats.totalTasks()),
                            count, p.getWeight());
            priorityLines.add(line);
        }

        buildBreakdownTree();
        summaryLabel.setText(stats.summary());
    }

    private void executeSampleRandomTaskAsync() {
        List<Task> all = service.findAll();
        if (all.isEmpty()) return;
        Task pick = all.get(new Random().nextInt(all.size()));
        log("Submitting random task (#%d) for background execution demo...".formatted(pick.getId()));
        service.executeTaskAsync(pick, new TaskConsumer.TaskCompletionCallback() {
            @Override
            public void onComplete(TaskExecutionRequest req) {
                Platform.runLater(() -> {
                    String status = req.getFinalStatus() == TaskStatus.COMPLETED ? "COMPLETED" : "FAILED";
                    log("Exec #%d => %s in %dms".formatted(
                            req.getTask().getId(), status, Math.max(0, req.millisTaken())));
                });
            }
        });
    }

    private String progressBarText(long part, long total) {
        if (total == 0) return "[        ]";
        int len = 10;
        int filled = (int) ((double) part / total * len);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < len; i++) sb.append(i < filled ? '#' : '.');
        sb.append(']');
        return sb.toString();
    }

    private void buildBreakdownTree() {
        TreeItem<String> root = new TreeItem<>("Task Breakdown (Status -> Category)");
        root.setExpanded(true);

        List<Task> all = service.findAll();
        Map<TaskStatus, Map<TaskCategory, List<Task>>> byStatusAndCategory = all.stream()
                .collect(Collectors.groupingBy(Task::getStatus,
                        Collectors.groupingBy(Task::getCategory)));

        for (TaskStatus s : TaskStatus.values()) {
            Map<TaskCategory, List<Task>> catMap = byStatusAndCategory.getOrDefault(s, Map.of());
            long total = catMap.values().stream().mapToLong(List::size).sum();
            TreeItem<String> statusNode = new TreeItem<>(
                    "%s (%d)".formatted(s.getDisplayName(), total));
            statusNode.setExpanded(total > 0 && total <= 10);

            for (TaskCategory c : TaskCategory.values()) {
                List<Task> list = catMap.getOrDefault(c, List.of());
                if (list.isEmpty()) continue;
                TreeItem<String> catNode = new TreeItem<>(
                        "  %s: %d".formatted(c.getDisplayName(), list.size()));
                catNode.setExpanded(false);
                for (Task t : list.stream().limit(25).toList()) {
                    TreeItem<String> leaf = new TreeItem<>(
                            "#%d %s  [due %s, %s, %d%%]".formatted(
                                    t.getId(), t.getTitle(), t.formattedDueDate(),
                                    t.getPriority().getDisplayName(), t.getProgress()));
                    catNode.getChildren().add(leaf);
                }
                if (list.size() > 25) {
                    catNode.getChildren().add(new TreeItem<>("... (" + (list.size() - 25) + " more)"));
                }
                statusNode.getChildren().add(catNode);
            }
            root.getChildren().add(statusNode);
        }

        breakdownTree.setRoot(root);
        breakdownTree.setShowRoot(true);
    }

    private void log(String message) {
        String ts = LocalDateTime.now().format(logTimeFmt);
        String line = "[%s] %s%n".formatted(ts, message);
        logArea.appendText(line);
    }
}
