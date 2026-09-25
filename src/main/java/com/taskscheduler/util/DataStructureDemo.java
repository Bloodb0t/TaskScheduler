package com.taskscheduler.util;

import com.taskscheduler.model.TaskCategory;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.model.Task;

import java.time.LocalDate;
import java.util.*;

public final class DataStructureDemo {

    private DataStructureDemo() {}

    public static void demonstrateArraysVsLists() {
        Task[] taskArray = new Task[3];
        taskArray[0] = new Task(1, "A", "", TaskPriority.LOW, TaskStatus.PENDING,
                TaskCategory.WORK, LocalDate.now(), 0, "", null);
        taskArray[1] = new Task(2, "B", "", TaskPriority.HIGH, TaskStatus.PENDING,
                TaskCategory.PERSONAL, LocalDate.now(), 0, "", null);
        taskArray[2] = new Task(3, "C", "", TaskPriority.MEDIUM, TaskStatus.PENDING,
                TaskCategory.STUDY, LocalDate.now(), 0, "", null);
        Arrays.sort(taskArray, Comparator.comparing(Task::getTitle));

        ArrayList<Task> arrayList = new ArrayList<>(List.of(taskArray));
        arrayList.add(new Task(4, "D", "", TaskPriority.URGENT, TaskStatus.IN_PROGRESS,
                TaskCategory.HEALTH, LocalDate.now(), 50, "", null));
        Task first = arrayList.get(0);

        Deque<Task> taskQueue = new LinkedList<>();
        taskQueue.addLast(taskArray[0]);
        taskQueue.addLast(taskArray[1]);
        taskQueue.addLast(taskArray[2]);
        Task consumed = taskQueue.removeFirst();
    }

    public static <T> List<T> copyUpperBounded(List<? extends T> source) {
        return new ArrayList<>(source);
    }

    public static <T> void fillLowerBounded(List<? super T> dest, Collection<T> items) {
        dest.addAll(items);
    }
}
