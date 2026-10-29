package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Thermometer;
import com.github.blade.hvac.model.WeatherStation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiDevicesTest {
    @Test
    void weatherStationsStayOutOfApi15() {
        // Creating one with an addon listening said "not an EZvac device" to the admin (found on play263).
        BlockKey at = new BlockKey(UUID.randomUUID(), 0, 64, 0);
        assertFalse(EzvacApiImpl.inApi(new WeatherStation(at, "Harbour")));
        assertTrue(EzvacApiImpl.inApi(new Thermometer(at, "Hall")));
    }
}
