package com.maxdemarzi.results;

import java.util.Collections;
import java.util.Map;

public class MapResult {
    private static final MapResult EMPTY = new MapResult(Collections.emptyMap());
    public final Map<String, Object> value;

    public static MapResult empty() {
        return EMPTY;
    }
    public  boolean isEmpty() { return value.isEmpty(); }

    public MapResult(Map<String, Object> value) {
        this.value = value;
    }
}