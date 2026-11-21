package com.motuslab.model;

public class RunParameterSnapshot {

    private int id;
    private int runId;
    private int parameterDefinitionId;
    private String parameterCode;
    private String parameterName;
    private String unit;
    private double value;

    public RunParameterSnapshot() {
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

    public int getParameterDefinitionId() {
        return parameterDefinitionId;
    }

    public void setParameterDefinitionId(int parameterDefinitionId) {
        this.parameterDefinitionId = parameterDefinitionId;
    }

    public String getParameterCode() {
        return parameterCode;
    }

    public void setParameterCode(String parameterCode) {
        this.parameterCode = parameterCode;
    }

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}