package com.taskscheduler.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class NumberUtils {

    private NumberUtils() {}

    public static <T extends Number> BigDecimal sum(Collection<T> numbers) {
        BigDecimal total = BigDecimal.ZERO;
        for (T n : numbers) {
            if (n != null) {
                total = total.add(new BigDecimal(n.toString()));
            }
        }
        return total;
    }

    public static <T extends Number> BigDecimal average(Collection<T> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = sum(numbers);
        return sum.divide(BigDecimal.valueOf(numbers.size()), 4, RoundingMode.HALF_UP);
    }

    public static <T extends Number & Comparable<T>> Optional<T> max(List<T> list) {
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    public static String formatCurrency(BigDecimal amount) {
        return java.text.NumberFormat.getCurrencyInstance().format(amount);
    }

    public static String formatPercent(BigDecimal value) {
        return java.text.NumberFormat.getPercentInstance().format(value);
    }

    public static String formatPercent(double value) {
        return java.text.NumberFormat.getPercentInstance().format(value / 100.0);
    }

    public static Integer parseIntOrNull(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDoubleOrNull(String text) {
        try {
            return Double.parseDouble(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String joinInts(int... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        return sb.toString();
    }

    public static void demonstrateAutoboxing() {
        int primitive = 42;
        Integer boxed = primitive;
        int unboxed = boxed;
        List<Integer> ints = List.of(1, 2, 3);
        int sum = 0;
        for (Integer wrapped : ints) {
            sum += wrapped;
        }
    }

    public static String classifyScore(int score) {
        return switch (score / 10) {
            case 10, 9 -> "Excellent";
            case 8 -> "Very Good";
            case 7 -> "Good";
            case 6 -> "Satisfactory";
            case 5 -> "Pass";
            case 4, 3, 2, 1, 0 -> "Fail";
            default -> score > 100 ? "Out of range (high)" : "Out of range (low)";
        };
    }

    public static void demonstrateControlFlow(String[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null) continue;
            if (arr[i].isEmpty()) break;
        }
        for (String s : arr) {
            String upper = s == null ? "" : s.toUpperCase().trim();
            switch (upper) {
                case "HELLO" -> System.out.println("Hi");
                case "BYE"   -> System.out.println("Goodbye");
                default      -> System.out.println(upper);
            }
        }
        int n = arr.length;
        while (n > 0) {
            n--;
        }
    }
}
