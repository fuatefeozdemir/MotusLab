package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.SimulationRun;
import com.motuslab.model.User;
import com.motuslab.service.AdminService;
import com.motuslab.service.CsvExportService;
import com.motuslab.service.ExperimentMemberService;
import com.motuslab.service.ExperimentService;
import com.motuslab.service.SimulationRunService;
import com.motuslab.ui.component.ExperimentDetailPane;
import com.motuslab.ui.component.ExperimentSidebar;
import com.motuslab.ui.component.TopBar;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;

import java.util.List;
import java.util.Map;

public class DashboardView extends BorderPane {

    private final ExperimentService experimentService;
    private final SimulationRunService simulationRunService;
    private final ExperimentMemberService memberService;

    private final ExperimentSidebar sidebar;
    private final ExperimentDetailPane detailPane;
    private final RunResultPane runResultPane;

    private Experiment selectedExperiment;

    public DashboardView(
            User currentUser,
            ExperimentService experimentService,
            SimulationRunService simulationRunService,
            ExperimentMemberService memberService,
            Runnable onLogout
    ) {
        this.experimentService = experimentService;
        this.simulationRunService = simulationRunService;
        this.memberService = memberService;

        this.runResultPane = new RunResultPane(
                simulationRunService,
                new CsvExportService(),
                this::showFeedback,
                selectRunId -> {
                    if (selectedExperiment != null) {
                        loadRuns(selectedExperiment.getId(), selectRunId);
                    }
                }
        );

        TopBar topBar = new TopBar(
                currentUser,
                () -> AdminPanelDialog.show(currentUser, new AdminService()),
                onLogout
        );

        this.sidebar = new ExperimentSidebar(
                currentUser,
                () -> NewExperimentDialog.show(experimentService).ifPresent(created -> loadExperiments(created.getId())),
                this::onExperimentSelected
        );

        ExperimentDetailPane.Actions actions = new ExperimentDetailPane.Actions(
                this::handleRunSimulation,
                this::handleSaveParameters,
                this::handleEditExperiment,
                this::handleShareExperiment,
                this::handleToggleArchive,
                this::handleDeleteExperiment,
                this::handleCompareRuns,
                run -> runResultPane.render(selectedExperiment, run)
        );
        this.detailPane = new ExperimentDetailPane(runResultPane, actions);

        ScrollPane scrollPane = new ScrollPane(detailPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        setTop(topBar);
        setLeft(sidebar);
        setCenter(scrollPane);

        loadExperiments(null);
    }

    private void loadExperiments(Integer selectId) {
        try {
            List<Experiment> experiments = experimentService.getMyExperiments();
            sidebar.setExperiments(experiments, selectId);
            if (experiments.isEmpty()) {
                selectedExperiment = null;
                detailPane.renderEmpty();
            }
        } catch (MotusLabException ex) {
            showErrorAlert("Deneyler yüklenemedi", ex.getMessage());
        }
    }

    private void onExperimentSelected(Experiment experiment) {
        this.selectedExperiment = experiment;
        if (experiment == null) {
            detailPane.renderEmpty();
            return;
        }

        String permission;
        try {
            permission = memberService.getEffectivePermission(experiment);
        } catch (MotusLabException ex) {
            permission = "VIEW";
        }

        try {
            List<ExperimentParameter> params = experimentService.getExperimentParameters(experiment.getId());
            detailPane.renderExperiment(experiment, permission, params);
            loadRuns(experiment.getId(), null);
        } catch (MotusLabException ex) {
            detailPane.renderExperiment(experiment, permission, List.of());
            detailPane.showFeedback(ex.getMessage(), true);
        }
    }

    private void loadRuns(int experimentId, Integer selectRunId) {
        try {
            List<SimulationRun> runs = simulationRunService.getRunsByExperiment(experimentId);
            detailPane.setRuns(runs, selectRunId);
            if (runs.isEmpty()) {
                runResultPane.render(selectedExperiment, null);
            }
        } catch (MotusLabException ex) {
            detailPane.showFeedback(ex.getMessage(), true);
        }
    }

    private void handleRunSimulation(boolean canEdit) {
        if (selectedExperiment == null) {
            return;
        }
        if (canEdit && !saveParametersSilently()) {
            return;
        }

        int targetExperimentId = selectedExperiment.getId();
        detailPane.setRunningState(true);
        detailPane.clearFeedback();

        Task<SimulationRun> task = new Task<>() {
            @Override
            protected SimulationRun call() {
                return simulationRunService.executeExperiment(targetExperimentId);
            }
        };

        task.setOnSucceeded(e -> {
            SimulationRun run = task.getValue();
            detailPane.setRunningState(false);
            if (selectedExperiment != null && selectedExperiment.getId() == targetExperimentId) {
                detailPane.showFeedback("Simülasyon tamamlandı (" + run.getDisplayName() + ").", false);
                loadRuns(targetExperimentId, run.getId());
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            detailPane.setRunningState(false);
            if (selectedExperiment != null && selectedExperiment.getId() == targetExperimentId) {
                String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "Simülasyon çalıştırılamadı.";
                detailPane.showFeedback(msg, true);
                loadRuns(targetExperimentId, null);
            }
        });

        Thread worker = new Thread(task, "motuslab-sim-runner");
        worker.setDaemon(true);
        worker.start();
    }

    private void handleSaveParameters() {
        if (saveParametersSilently()) {
            detailPane.showFeedback("Parametreler kaydedildi.", false);
        }
    }

    private boolean saveParametersSilently() {
        if (selectedExperiment == null) {
            return false;
        }
        detailPane.clearFeedback();
        try {
            Map<Integer, Double> values = detailPane.readParameterValues();
            experimentService.updateExperimentParameters(selectedExperiment.getId(), values);
            return true;
        } catch (NumberFormatException ex) {
            detailPane.showFeedback("Tüm parametreler geçerli bir sayı olmalıdır.", true);
            return false;
        } catch (MotusLabException ex) {
            detailPane.showFeedback(ex.getMessage(), true);
            return false;
        }
    }

    private void handleEditExperiment() {
        if (selectedExperiment != null && EditExperimentDialog.show(selectedExperiment, experimentService)) {
            loadExperiments(selectedExperiment.getId());
            detailPane.showFeedback("Deney bilgileri güncellendi.", false);
        }
    }

    private void handleShareExperiment() {
        if (selectedExperiment != null) {
            ShareExperimentDialog.show(selectedExperiment, memberService);
        }
    }

    private void handleToggleArchive(boolean archive) {
        if (selectedExperiment == null) {
            return;
        }
        try {
            int expId = selectedExperiment.getId();
            experimentService.setExperimentArchived(expId, archive);
            if (archive && !sidebar.isShowArchivedSelected()) {
                loadExperiments(null);
            } else {
                loadExperiments(expId);
            }
            detailPane.showFeedback(archive ? "Deney arşivlendi." : "Deney arşivden çıkarıldı.", false);
        } catch (MotusLabException ex) {
            detailPane.showFeedback(ex.getMessage(), true);
        }
    }

    private void handleDeleteExperiment() {
        if (selectedExperiment == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deneyi Sil");
        confirm.setHeaderText(selectedExperiment.getTitle());
        confirm.setContentText("Bu deney ve tüm çalıştırma geçmişi kalıcı olarak silinecek. Emin misiniz?");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    experimentService.deleteExperiment(selectedExperiment.getId());
                    loadExperiments(null);
                } catch (MotusLabException ex) {
                    detailPane.showFeedback(ex.getMessage(), true);
                }
            }
        });
    }

    private void handleCompareRuns() {
        if (selectedExperiment == null) {
            return;
        }
        List<SimulationRun> completed = detailPane.getRuns().stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .toList();

        if (completed.size() < 2) {
            detailPane.showFeedback("Karşılaştırma için en az 2 tamamlanmış çalıştırma gerekir.", true);
            return;
        }
        RunComparisonDialog.show(selectedExperiment, completed, simulationRunService);
    }

    private void showFeedback(String message, boolean isError) {
        detailPane.showFeedback(message, isError);
    }

    private void showErrorAlert(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Hata");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}