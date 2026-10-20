package com.github.blade.hvac.model;

import java.util.Objects;

/** A dehumidifier's own airflow zone, named by its iron block. */
public record ZoneId(BlockKey unit) implements AirflowTarget {
    public ZoneId {
        Objects.requireNonNull(unit, "unit");
    }

    public static ZoneId of(Dehumidifier dehumidifier) {
        return new ZoneId(dehumidifier.unit());
    }
}
