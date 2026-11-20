package com.motuslab.service;

import com.motuslab.exception.ExportException;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.SimulationRun;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class CsvExportService {

    public void exportRunToCsv(
            Path targetPath,
            String experimentTitle,
            SimulationRun run,
            List<RunMetric> metrics,
            List<RunSample> samples
    ) {
        try (BufferedWriter writer = Files.newBufferedWriter(targetPath, StandardCharsets.UTF_8)) {
            writer.write("# Experiment: " + sanitize(experimentTitle));
            writer.newLine();
            writer.write("# Run ID: " + run.getId() + " | Status: " + run.getStatus());
            writer.newLine();

            for (RunMetric m : metrics) {
                writer.write(String.format(Locale.US, "# Metric: %s = %.6f %s",
                        m.getMetricCode(), m.getMetricValue(), m.getUnit()));
                writer.newLine();
            }

            writer.write("sample_index,time_seconds,position_x,position_y,velocity_x,velocity_y");
            writer.newLine();

            for (RunSample s : samples) {
                String posY = s.getPositionY() != null ? String.format(Locale.US, "%.6f", s.getPositionY()) : "";
                String velY = s.getVelocityY() != null ? String.format(Locale.US, "%.6f", s.getVelocityY()) : "";

                writer.write(String.format(Locale.US, "%d,%.6f,%.6f,%s,%.6f,%s",
                        s.getSampleIndex(),
                        s.getTimeSeconds(),
                        s.getPositionX(),
                        posY,
                        s.getVelocityX(),
                        velY
                ));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new ExportException("CSV dosyası yazılırken hata oluştu: " + e.getMessage(), e);
        }
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ");
    }
}