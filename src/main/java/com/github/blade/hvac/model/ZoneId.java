package com.github.blade.hvac.model;

import java.util.Objects;

/** A standalone humidity unit's own airflow zone, named by its iron block. */
public record ZoneId(BlockKey unit) implements AirflowTarget {
    public ZoneId {
        Objects.requireNonNull(unit, "unit");
    }

    public static ZoneId of(ClimateUnit unit) {
        return new ZoneId(unit.unit());
    }
}
