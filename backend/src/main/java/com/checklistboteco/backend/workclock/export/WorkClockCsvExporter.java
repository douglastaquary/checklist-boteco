package com.checklistboteco.backend.workclock.export;

import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorkClockMonthlyExportRow;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class WorkClockCsvExporter {
    public byte[] export(List<WorkClockMonthlyExportRow> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("Colaborador;Horas trabalhadas;Horas extras;Horas faltantes;Almoço;Descanso;Faltas (dias);Dias de falta\n");
        for (WorkClockMonthlyExportRow row : rows) {
            csv.append(escape(row.name)).append(';')
                .append(format(row.workedHours)).append(';')
                .append(format(row.overtimeHours)).append(';')
                .append(format(row.missingHours)).append(';')
                .append(format(row.lunchHours)).append(';')
                .append(format(row.restHours)).append(';')
                .append(row.absenceDays).append(';')
                .append(escape(String.join(", ", row.absenceDates == null ? List.of() : row.absenceDates))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        return String.format("%.2f", value).replace('.', ',');
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace(";", ",");
    }
}
