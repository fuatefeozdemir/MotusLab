package com.motuslab.model;

public class ExperimentParameter {

    private int id;
    private int experimentId;
    private int parameterDefinitionId;
    private String parameterCode;
    private String parameterName;
    private String unit;
    private double value;
    private Double minValue;
    private Double maxValue;

    public ExperimentParameter() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(int experimentId) {
        this.experimentId = experimentId;
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

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }
}