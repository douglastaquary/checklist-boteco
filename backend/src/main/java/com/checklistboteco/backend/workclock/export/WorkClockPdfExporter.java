package com.checklistboteco.backend.workclock.export;

import com.checklistboteco.backend.workclock.domain.WorkClockModels.WorkClockMonthlyExportRow;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.util.List;

@ApplicationScoped
public class WorkClockPdfExporter {
    public byte[] export(int year, int month, List<WorkClockMonthlyExportRow> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, output);
            document.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            document.add(new Paragraph("Relatório mensal de ponto — " + month + "/" + year, titleFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            addHeader(table, headerFont, "Colaborador", "Horas", "Extras", "Faltantes", "Almoço", "Descanso", "Faltas", "Dias");
            for (WorkClockMonthlyExportRow row : rows) {
                addRow(table, bodyFont,
                    row.name,
                    format(row.workedHours),
                    format(row.overtimeHours),
                    format(row.missingHours),
                    format(row.lunchHours),
                    format(row.restHours),
                    Integer.toString(row.absenceDays),
                    row.absenceDates == null || row.absenceDates.isEmpty() ? "—" : String.join(", ", row.absenceDates)
                );
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Falha ao gerar PDF de ponto", error);
        }
    }

    private static void addHeader(PdfPTable table, Font font, String... labels) {
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase(label, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private static void addRow(PdfPTable table, Font font, String... values) {
        for (String value : values) {
            table.addCell(new Phrase(value == null ? "" : value, font));
        }
    }

    private static String format(double value) {
        return String.format("%.2f h", value);
    }
}
