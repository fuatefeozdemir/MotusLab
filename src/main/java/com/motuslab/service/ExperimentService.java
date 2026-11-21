package com.motuslab.service;

import com.motuslab.dao.ExperimentDao;
import com.motuslab.dao.ExperimentMemberDao;
import com.motuslab.dao.SimulationTypeDao;
import com.motuslab.exception.AuthorizationException;
import com.motuslab.exception.NotFoundException;
import com.motuslab.exception.ValidationException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.ParameterDefinition;
import com.motuslab.model.SimulationType;
import com.motuslab.model.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExperimentService {

    private final ExperimentDao experimentDao;
    private final SimulationTypeDao simulationTypeDao;
    private final ExperimentMemberDao experimentMemberDao;

    public ExperimentService() {
        this(new ExperimentDao(), new SimulationTypeDao(), new ExperimentMemberDao());
    }

    public ExperimentService(
            ExperimentDao experimentDao,
            SimulationTypeDao simulationTypeDao,
            ExperimentMemberDao experimentMemberDao
    ) {
        this.experimentDao = experimentDao;
        this.simulationTypeDao = simulationTypeDao;
        this.experimentMemberDao = experimentMemberDao;
    }

    public List<SimulationType> getAllSimulationTypes() {
        return simulationTypeDao.findAll();
    }

    public List<ParameterDefinition> getParameterDefinitions(int simulationTypeId) {
        return simulationTypeDao.findParametersBySimulationTypeId(simulationTypeId);
    }

    public List<Experiment> getMyExperiments() {
        User currentUser = SessionManager.requireCurrentUser();
        return experimentDao.findAccessibleByUser(currentUser.getId());
    }

    public List<ExperimentParameter> getExperimentParameters(int experimentId) {
        SessionManager.requireCurrentUser();
        return experimentDao.findParametersByExperimentId(experimentId);
    }

    public Experiment createExperiment(int simulationTypeId, String title, String description, Map<Integer, Double> customValues) {
        User currentUser = SessionManager.requireCurrentUser();

        if (title == null || title.isBlank()) {
            throw new ValidationException("Deney başlığı boş bırakılamaz.");
        }
        String cleanTitle = title.trim();
        if (cleanTitle.length() > 150) {
            throw new ValidationException("Deney başlığı en fazla 150 karakter olabilir.");
        }

        SimulationType simType = simulationTypeDao.findById(simulationTypeId)
                .orElseThrow(() -> new NotFoundException("Seçilen simülasyon türü bulunamadı."));

        List<ParameterDefinition> definitions = simulationTypeDao.findParametersBySimulationTypeId(simulationTypeId);
        Map<Integer, Double> validatedValues = new LinkedHashMap<>();

        for (ParameterDefinition def : definitions) {
            double val = (customValues != null && customValues.containsKey(def.getId()))
                    ? customValues.get(def.getId())
                    : def.getDefaultValue();
            validateParameterValue(def, val);
            validatedValues.put(def.getId(), val);
        }

        Experiment experiment = new Experiment();
        experiment.setOwnerId(currentUser.getId());
        experiment.setOwnerUsername(currentUser.getUsername());
        experiment.setSimulationTypeId(simType.getId());
        experiment.setSimulationTypeCode(simType.getCode());
        experiment.setSimulationTypeName(simType.getName());
        experiment.setTitle(cleanTitle);
        experiment.setDescription(description == null || description.isBlank() ? null : description.trim());

        return experimentDao.create(experiment, validatedValues);
    }

    public void updateExperimentParameters(int experimentId, Map<Integer, Double> updatedValues) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        if (experiment.isArchived()) {
            throw new ValidationException("Arşivlenmiş bir deney düzenlenemez.");
        }

        requireEditPermission(experiment, currentUser);

        if (updatedValues == null || updatedValues.isEmpty()) {
            throw new ValidationException("Güncellenecek parametre bulunamadı.");
        }

        List<ParameterDefinition> definitions = simulationTypeDao.findParametersBySimulationTypeId(experiment.getSimulationTypeId());
        Map<Integer, Double> validatedValues = new LinkedHashMap<>();

        for (ParameterDefinition def : definitions) {
            if (!updatedValues.containsKey(def.getId())) {
                continue;
            }
            double val = updatedValues.get(def.getId());
            validateParameterValue(def, val);
            validatedValues.put(def.getId(), val);
        }

        experimentDao.updateParameters(experimentId, experiment.getSimulationTypeId(), validatedValues);
    }

    public void updateExperimentDetails(int experimentId, String title, String description) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        if (experiment.isArchived()) {
            throw new ValidationException("Arşivlenmiş bir deney düzenlenemez.");
        }

        requireEditPermission(experiment, currentUser);

        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            throw new ValidationException("Deney başlığı boş olamaz.");
        }
        if (cleanTitle.length() > 150) {
            throw new ValidationException("Deney başlığı en fazla 150 karakter olabilir.");
        }

        String cleanDesc = (description == null || description.isBlank()) ? null : description.trim();
        experimentDao.updateDetails(experimentId, cleanTitle, cleanDesc);
    }

    public void setExperimentArchived(int experimentId, boolean archived) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        requireOwnerOrAdmin(experiment, currentUser);
        experimentDao.updateArchivedStatus(experimentId, archived);
    }

    public void deleteExperiment(int experimentId) {
        User currentUser = SessionManager.requireCurrentUser();
        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        requireOwnerOrAdmin(experiment, currentUser);
        experimentDao.delete(experimentId);
    }

    private void requireOwnerOrAdmin(Experiment experiment, User user) {
        if (experiment.getOwnerId() != user.getId() && !user.isAdmin()) {
            throw new AuthorizationException("Bu deney üzerinde işlem yapma yetkiniz yok.");
        }
    }

    private void requireEditPermission(Experiment experiment, User user) {
        if (experiment.getOwnerId() == user.getId() || user.isAdmin()) {
            return;
        }
        String perm = experimentMemberDao.findPermission(experiment.getId(), user.getId()).orElse("NONE");
        if (!"EDIT".equals(perm)) {
            throw new AuthorizationException("Bu deneyi düzenleme yetkiniz yok.");
        }
    }

    private void validateParameterValue(ParameterDefinition def, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValidationException(def.getName() + " için geçerli bir sayı girin.");
        }
        if (def.getMinValue() != null && value < def.getMinValue()) {
            throw new ValidationException(def.getName() + " en az " + def.getMinValue() + " " + def.getUnit() + " olabilir.");
        }
        if (def.getMaxValue() != null && value > def.getMaxValue()) {
            throw new ValidationException(def.getName() + " en fazla " + def.getMaxValue() + " " + def.getUnit() + " olabilir.");
        }
    }
}