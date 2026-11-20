package com.motuslab.model;

public class RunMetric {

    private int id;
    private int runId;
    private String metricCode;
    private double metricValue;
    private String unit;

    public RunMetric() {
    }

    public RunMetric(int runId, String metricCode, double metricValue, String unit) {
        this.runId = runId;
        this.metricCode = metricCode;
        this.metricValue = metricValue;
        this.unit = unit;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRunId() {
        return runId;
    }

    public void setRunId(int runId) {
        this.runId = runId;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public void setMetricCode(String metricCode) {
        this.metricCode = metricCode;
    }

    public double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(double metricValue) {
        this.metricValue = metricValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}