package com.github.blade.hvac.model;

/**
 * Something the airflow walker maps: a system's vents ({@link GroupId}) or a
 * standalone dehumidifier's own zone ({@link ZoneId}).
 */
public sealed interface AirflowTarget permits GroupId, ZoneId {}
