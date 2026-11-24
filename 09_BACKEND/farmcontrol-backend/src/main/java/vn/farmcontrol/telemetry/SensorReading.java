package vn.farmcontrol.telemetry;

import jakarta.persistence.*;
import java.time.Instant;
import vn.farmcontrol.state.FarmSnapshot.SensorValues;

@Entity
@Table(name = "sensor_reading")
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "coop_temp")
    private Double coopTemp;

    @Column(name = "air_humidity")
    private Double airHumidity;

    @Column(name = "water_temp")
    private Double waterTemp;

    @Column(name = "water_level")
    private Double waterLevel;

    @Column(name = "soil_humidity")
    private Double soilHumidity;

    @Column(name = "light_lux")
    private Integer lightLux;

    @Column(name = "co2_ppm")
    private Integer co2Ppm;

    @Column(name = "nh3_ppm")
    private Integer nh3Ppm;

    @Column(name = "data_valid", nullable = false)
    private boolean dataValid;

    protected SensorReading() {
    }

    public SensorReading(Instant recordedAt, SensorValues s) {
        this.recordedAt = recordedAt;
        this.coopTemp = s.coopTemp();
        this.airHumidity = s.airHumidity();
        this.waterTemp = s.waterTemp();
        this.waterLevel = s.waterLevel();
        this.soilHumidity = s.soilHumidity();
        this.lightLux = s.lightLux();
        this.co2Ppm = s.co2Ppm();
        this.nh3Ppm = s.nh3Ppm();
        this.dataValid = s.dataValid() && s.tempSensorOk();
    }

    public Long getId() { return id; }
    public Instant getRecordedAt() { return recordedAt; }
    public Double getCoopTemp() { return coopTemp; }
    public Double getAirHumidity() { return airHumidity; }
    public Double getWaterTemp() { return waterTemp; }
    public Double getWaterLevel() { return waterLevel; }
    public Double getSoilHumidity() { return soilHumidity; }
    public Integer getLightLux() { return lightLux; }
    public Integer getCo2Ppm() { return co2Ppm; }
    public Integer getNh3Ppm() { return nh3Ppm; }
    public boolean isDataValid() { return dataValid; }
}
