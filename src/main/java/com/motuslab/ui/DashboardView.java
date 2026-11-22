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
import com.motuslab.ui.component.NavSidebar;
import com.motuslab.ui.view.ComparisonView;
import com.motuslab.ui.view.ExperimentLibraryView;
import com.motuslab.ui.view.OverviewView;
import com.motuslab.ui.view.ProfileAdminView;
import javafx.animation.FadeTransition;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

public class DashboardView extends BorderPane {

    private final ExperimentService experimentService;
    private final SimulationRunService simulationRunService;
    private final ExperimentMemberService memberService;

    private final NavSidebar navSidebar;
    private final OverviewView overviewView;
    private final ExperimentLibraryView experimentsView;
    private final ExperimentLibraryView archiveView;
    private final ComparisonView comparisonView;
    private final ProfileAdminView profileAdminView;
    private final ExperimentDetailPane detailPane;
    private final RunResultPane runResultPane;

    private List<Experiment> cachedExperiments = List.of();
    private Experiment selectedExperiment;
    private NavSidebar.Section currentSection = NavSidebar.Section.OVERVIEW;
    private NavSidebar.Section returnSection = NavSidebar.Section.EXPERIMENTS;

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

        this.navSidebar = new NavSidebar(currentUser, this::navigateTo, onLogout);

        this.overviewView = new OverviewView(
                currentUser,
                this::handleNewExperiment,
                () -> navigateTo(NavSidebar.Section.COMPARISON),
                this::openExperimentWorkspace
        );

        this.experimentsView = new ExperimentLibraryView(
                currentUser,
                false,
                this::handleNewExperiment,
                this::openExperimentWorkspace
        );

        this.archiveView = new ExperimentLibraryView(
                currentUser,
                true,
                null,
                this::openExperimentWorkspace
        );

        this.comparisonView = new ComparisonView(simulationRunService);
        this.profileAdminView = new ProfileAdminView(currentUser, new AdminService());

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

        ExperimentDetailPane.Actions actions = new ExperimentDetailPane.Actions(
                () -> navigateTo(returnSection),
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

        setLeft(navSidebar);

        refreshExperiments();
        navigateTo(NavSidebar.Section.OVERVIEW);
    }

    private void switchCenter(Node view) {
        if (getCenter() == view) {
            return;
        }
        view.setOpacity(0);
        setCenter(view);
        FadeTransition ft = new FadeTransition(Duration.millis(140), view);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void navigateTo(NavSidebar.Section section) {
        this.currentSection = section;
        navSidebar.setActiveSection(section);

        switch (section) {
            case OVERVIEW -> {
                refreshExperiments();
                switchCenter(overviewView);
            }
            case EXPERIMENTS -> {
                refreshExperiments();
                switchCenter(experimentsView);
            }
            case COMPARISON -> {
                refreshExperiments();
                comparisonView.setExperiments(cachedExperiments, selectedExperiment);
                switchCenter(comparisonView);
            }
            case ARCHIVE -> {
                refreshExperiments();
                switchCenter(archiveView);
            }
            case MANAGEMENT -> {
                profileAdminView.refresh();
                switchCenter(profileAdminView);
            }
        }
    }

    private void openExperimentWorkspace(Experiment experiment) {
        if (experiment == null) {
            return;
        }
        this.returnSection = (currentSection == NavSidebar.Section.ARCHIVE)
                ? NavSidebar.Section.ARCHIVE
                : (currentSection == NavSidebar.Section.OVERVIEW
                ? NavSidebar.Section.OVERVIEW
                : NavSidebar.Section.EXPERIMENTS);

        this.selectedExperiment = experiment;
        navSidebar.setActiveSection(experiment.isArchived()
                ? NavSidebar.Section.ARCHIVE
                : NavSidebar.Section.EXPERIMENTS);

        renderSelectedExperiment();
        switchCenter(detailPane);
    }

    private void renderSelectedExperiment() {
        if (selectedExperiment == null) {
            detailPane.renderEmpty();
            return;
        }

        String permission;
        try {
            permission = memberService.getEffectivePermission(selectedExperiment);
        } catch (MotusLabException ex) {
            permission = "VIEW";
        }

        try {
            List<ExperimentParameter> params = experimentService.getExperimentParameters(selectedExperiment.getId());
            detailPane.renderExperiment(selectedExperiment, permission, params);
            loadRuns(selectedExperiment.getId(), null);
        } catch (MotusLabException ex) {
            detailPane.renderExperiment(selectedExperiment, permission, List.of());
            detailPane.showFeedback(ex.getMessage(), true);
        }
    }

    private void refreshExperiments() {
        try {
            cachedExperiments = experimentService.getMyExperiments();
            overviewView.setExperiments(cachedExperiments);
            experimentsView.setExperiments(cachedExperiments);
            archiveView.setExperiments(cachedExperiments);
        } catch (MotusLabException ex) {
            UiHelper.showError("Deneyler yüklenemedi", ex.getMessage());
        }
    }

    private void handleNewExperiment() {
        NewExperimentDialog.show(experimentService).ifPresent(created -> {
            refreshExperiments();
            openExperimentWorkspace(created);
        });
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
            detailPane.markParametersSaved();
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
            int expId = selectedExperiment.getId();
            refreshExperiments();
            cachedExperiments.stream()
                    .filter(e -> e.getId() == expId)
                    .findFirst()
                    .ifPresent(updated -> {
                        selectedExperiment = updated;
                        renderSelectedExperiment();
                        detailPane.showFeedback("Deney bilgileri güncellendi.", false);
                    });
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
            refreshExperiments();
            cachedExperiments.stream()
                    .filter(e -> e.getId() == expId)
                    .findFirst()
                    .ifPresent(updated -> {
                        selectedExperiment = updated;
                        renderSelectedExperiment();
                        detailPane.showFeedback(archive ? "Deney arşivlendi." : "Deney arşivden çıkarıldı.", false);
                    });
        } catch (MotusLabException ex) {
            detailPane.showFeedback(ex.getMessage(), true);
        }
    }

    private void handleDeleteExperiment() {
        if (selectedExperiment == null) {
            return;
        }
        boolean confirmed = UiHelper.showConfirm(
                "Deneyi Sil",
                selectedExperiment.getTitle(),
                "Bu deney ve tüm çalıştırma geçmişi kalıcı olarak silinecek. Emin misiniz?"
        );
        if (confirmed) {
            try {
                experimentService.deleteExperiment(selectedExperiment.getId());
                selectedExperiment = null;
                navigateTo(returnSection);
            } catch (MotusLabException ex) {
                detailPane.showFeedback(ex.getMessage(), true);
            }
        }
    }

    private void handleCompareRuns() {
        if (selectedExperiment == null) {
            return;
        }
        navigateTo(NavSidebar.Section.COMPARISON);
    }

    private void showFeedback(String message, boolean isError) {
        detailPane.showFeedback(message, isError);
    }
}