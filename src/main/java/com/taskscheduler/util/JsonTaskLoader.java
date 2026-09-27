package com.taskscheduler.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskCategory;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;

public final class JsonTaskLoader {

    private JsonTaskLoader() {}

    public static List<Task> fetchFromUrl(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(6))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(12))
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "TaskScheduler-JsonTaskLoader/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " fetching " + url);
        }
        String body = resp.body() == null ? "" : resp.body();
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        System.out.println("[JsonTaskLoader] Fetched " + body.length() + " bytes from " + url
                + " (Content-Type=" + contentType + ")");
        if (!body.isBlank()) {
            String preview = body.replaceAll("\\s+", " ").trim();
            if (preview.length() > 160) preview = preview.substring(0, 160) + "…";
            System.out.println("[JsonTaskLoader] Body preview: " + preview);
        }
        return parse(body);
    }

    public static List<Task> loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream in = JsonTaskLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("Resource not found: " + resourcePath);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(json);
        }
    }

    // Tiny streaming JSON array-of-object parser for the fixed schema:
    // [ { title, description, priority, status, category, dueDate, progress, assignedTo }, ... ]
    // Deliberately small, no external deps; strict but forgiving about ordering and optional fields.
    public static List<Task> parse(String json) throws IOException {
        List<Task> out = new ArrayList<>();
        char[] s = json.toCharArray();
        int i = skipWs(s, 0);
        if (i >= s.length || s[i] != '[') throw new IOException("Expected '[' at top-level");
        i = skipWs(s, i + 1);
        if (s[i] == ']') return out;
        while (i < s.length) {
            if (s[i] != '{') throw new IOException("Expected '{' at index " + i);
            i = skipWs(s, i + 1);
            String title = "", description = "", priority = "MEDIUM", status = "PENDING",
                    category = "PERSONAL", dueDate = null, assignedTo = "";
            int progress = 0;
            while (s[i] != '}') {
                // key
                if (s[i] != '"') throw new IOException("Expected '\"' for key at index " + i);
                int[] pos = {i + 1};
                String key = readString(s, pos);
                i = skipWs(s, pos[0]);
                if (s[i] != ':') throw new IOException("Expected ':' at index " + i);
                i = skipWs(s, i + 1);
                // value
                if (s[i] == '"') {
                    pos[0] = i + 1;
                    String v = readString(s, pos);
                    i = skipWs(s, pos[0]);
                    switch (key) {
                        case "title" -> title = v;
                        case "description" -> description = v;
                        case "priority" -> priority = v;
                        case "status" -> status = v;
                        case "category" -> category = v;
                        case "dueDate" -> dueDate = v;
                        case "assignedTo" -> assignedTo = v;
                    }
                } else if (s[i] == 't' || s[i] == 'f' || s[i] == 'n') {
                    i = skipLiteral(s, i);
                } else if (s[i] == '-' || (s[i] >= '0' && s[i] <= '9')) {
                    pos[0] = i;
                    Number num = readNumber(s, pos);
                    i = skipWs(s, pos[0]);
                    if (key.equals("progress")) {
                        int val = num.intValue();
                        progress = Math.max(0, Math.min(100, val));
                    }
                } else {
                    throw new IOException("Unexpected char '" + s[i] + "' at index " + i);
                }
                if (s[i] == ',') i = skipWs(s, i + 1);
            }
            i = skipWs(s, i + 1); // skip '}'
            try {
                Task t = new Task(0,
                        title,
                        description,
                        asPriority(priority),
                        asStatus(status),
                        asCategory(category),
                        dueDate == null || dueDate.isEmpty() ? null : LocalDate.parse(dueDate),
                        progress,
                        assignedTo == null ? "" : assignedTo);
                out.add(t);
            } catch (Exception ex) {
                throw new IOException("Invalid task object (title='" + title + "'): " + ex.getMessage(), ex);
            }
            if (s[i] == ',') i = skipWs(s, i + 1);
            else if (s[i] == ']') break;
            else throw new IOException("Expected ',' or ']' at index " + i);
        }
        return out;
    }

    private static TaskPriority asPriority(String v) {
        if (v == null) return TaskPriority.MEDIUM;
        String s = v.trim().toUpperCase(Locale.ROOT).replaceAll("[-_ ]", "");
        // common aliases
        switch (s) {
            case "CRITICAL":
            case "URGENT":
            case "P0":
            case "SEVERE":
                return TaskPriority.URGENT;
            case "HIGH":
            case "P1":
                return TaskPriority.HIGH;
            case "MEDIUM":
            case "NORMAL":
            case "DEFAULT":
            case "P2":
                return TaskPriority.MEDIUM;
            case "LOW":
            case "MINOR":
            case "TRIVIAL":
            case "P3":
                return TaskPriority.LOW;
        }
        try {
            return TaskPriority.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            TaskPriority t = TaskPriority.fromDisplayName(v);
            if (t != null) return t;
            return TaskPriority.MEDIUM;
        }
    }

    private static TaskStatus asStatus(String v) {
        if (v == null) return TaskStatus.PENDING;
        String s = v.trim().toUpperCase(Locale.ROOT).replaceAll("[-_ ]", "");
        switch (s) {
            case "TODO":
            case "PENDING":
            case "OPEN":
            case "NEW":
                return TaskStatus.PENDING;
            case "INPROGRESS":
            case "PROGRESS":
            case "ACTIVE":
            case "RUNNING":
            case "WORKING":
                return TaskStatus.IN_PROGRESS;
            case "COMPLETED":
            case "DONE":
            case "FINISHED":
            case "CLOSED":
                return TaskStatus.COMPLETED;
            case "CANCELLED":
            case "CANCELED":
            case "ABORTED":
            case "CANCEL":
                return TaskStatus.CANCELLED;
        }
        try {
            return TaskStatus.valueOf(v.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            TaskStatus t = TaskStatus.fromDisplayName(v);
            if (t != null) return t;
            return TaskStatus.PENDING;
        }
    }

    private static TaskCategory asCategory(String v) {
        if (v == null) return TaskCategory.OTHER;
        String s = v.trim().toUpperCase(Locale.ROOT).replaceAll("[-_ ]", "");
        switch (s) {
            case "WORK":
            case "JOB":
            case "OFFICE":
                return TaskCategory.WORK;
            case "PERSONAL":
            case "HOME":
            case "LIFE":
                return TaskCategory.PERSONAL;
            case "STUDY":
            case "EDUCATION":
            case "LEARNING":
            case "SCHOOL":
            case "COLLEGE":
            case "UNIVERSITY":
                return TaskCategory.STUDY;
            case "HEALTH":
            case "FITNESS":
            case "MEDICAL":
            case "WELLNESS":
            case "DOCTOR":
                return TaskCategory.HEALTH;
            case "FINANCE":
            case "BILLS":
            case "MONEY":
            case "BANKING":
                return TaskCategory.FINANCE;
        }
        try {
            return TaskCategory.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            TaskCategory t = TaskCategory.fromDisplayName(v);
            if (t != null) return t;
            return TaskCategory.OTHER;
        }
    }

    private static String readString(char[] s, int[] pos) {
        StringBuilder sb = new StringBuilder();
        int i = pos[0];
        while (i < s.length && s[i] != '"') {
            if (s[i] == '\\' && i + 1 < s.length) {
                char n = s[i + 1];
                switch (n) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        String hex = new String(s, i + 2, 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> sb.append(n);
                }
                i += 2;
            } else {
                sb.append(s[i]);
                i++;
            }
        }
        pos[0] = i + 1;
        return sb.toString();
    }

    private static Number readNumber(char[] s, int[] pos) {
        int i = pos[0];
        int start = i;
        boolean fp = false;
        if (s[i] == '-') i++;
        while (i < s.length) {
            char c = s[i];
            if (c >= '0' && c <= '9') { i++; continue; }
            if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { fp = true; i++; continue; }
            break;
        }
        String tok = new String(s, start, i - start);
        pos[0] = i;
        if (fp) return Double.parseDouble(tok);
        return Long.parseLong(tok);
    }

    private static int skipLiteral(char[] s, int i) {
        while (i < s.length && s[i] != ',' && s[i] != '}' && s[i] != ']') i++;
        return i;
    }

    private static int skipWs(char[] s, int i) {
        while (i < s.length) {
            char c = s[i];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
        return i;
    }
}
