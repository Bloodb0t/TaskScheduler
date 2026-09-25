package com.taskscheduler.util;

public class GenericPair<K, V> {
    private final K key;
    private final V value;

    public GenericPair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() { return key; }
    public V getValue() { return value; }

    @Override
    public String toString() {
        return "(%s, %s)".formatted(key, value);
    }

    public static <K, V> GenericPair<K, V> of(K key, V value) {
        return new GenericPair<>(key, value);
    }
}
