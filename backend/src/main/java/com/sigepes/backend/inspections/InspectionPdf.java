package com.sigepes.backend.inspections;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.inspections.InspectionParts.FindingEntity;
import com.sigepes.backend.inspections.InspectionParts.ItemEntity;

/** HU-22 · AC-22.3 · Acta de inspección en PDF con las dos firmas, el sello y el código de verificación. */
@Component
public class InspectionPdf {

    private static final Color NAVY = new Color(0x0A, 0x25, 0x40);
    private static final Color RED = new Color(0xB3, 0x26, 0x1E);
    private static final Color GREEN = new Color(0x1B, 0x6B, 0x3F);
    private static final Color LINE = new Color(0xD5, 0xDE, 0xE3);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a",
            Locale.forLanguageTag("es-CO")).withZone(AppClock.ZONE);

    private final InspectionService service;
    private final String publicUrl;

    public InspectionPdf(InspectionService service,
            @Value("${sigepes.public-url:https://project-sigepes.vercel.app}") String publicUrl) {
        this.service = service;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Transactional(readOnly = true)
    public byte[] render(String inspectionId) {
        InspectionEntity act = service.visible(inspectionId);
        List<ItemEntity> items = service.items(act.getId());
        List<FindingEntity> findings = service.findings(act.getId());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.addTitle("Acta de inspección " + act.getCode());
        document.addCreator("SIGEPES");
        document.open();

        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, NAVY);
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, NAVY);
        Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        boolean conform = act.getResult() == InspectionEntity.Result.COMPLIANT;
        Font result = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, conform ? GREEN : RED);

        document.add(new Paragraph("SIGEPES · Sistema de Gestión de Pesca Sostenible · Autoridad pesquera", brand));
        Paragraph heading = new Paragraph("Acta de inspección " + act.getCode(), title);
        heading.setSpacingAfter(2);
        document.add(heading);
        document.add(new Paragraph(conform ? "Resultado: CONFORME" : "Resultado: CON HALLAZGOS (" + findings.size()
                + ")", result));

        PdfPTable facts = new PdfPTable(new float[] { 1.2f, 2.8f });
        facts.setWidthPercentage(100);
        facts.setSpacingBefore(10);
        fact(facts, "Embarcación", act.getVessel().getName() + " · matrícula " + act.getVessel().getRegistration(),
                label, body);
        fact(facts, "Armador", act.getOwner().getFullName(), label, body);
        fact(facts, "Permiso vigente", act.getPermitNumber() == null ? "Ninguno en la fecha" : act.getPermitNumber(),
                label, body);
        fact(facts, "Fecha y hora", STAMP.format(act.getInspectedAt()), label, body);
        fact(facts, "Lugar", act.getLocationText() + (act.getLatitude() == null ? ""
                : String.format(Locale.ROOT, " (%.5f, %.5f)", act.getLatitude(), act.getLongitude())), label, body);
        fact(facts, "Inspector", act.getInspector().getFullName(), label, body);
        fact(facts, "Responsable a bordo", act.getResponsibleName()
                + (act.getResponsibleIdNumber() == null ? "" : " · C.C. " + act.getResponsibleIdNumber()), label, body);
        if (act.isSavedOffline()) {
            fact(facts, "Registro", "Levantada sin señal y sincronizada el " + STAMP.format(act.getCreatedAt()),
                    label, body);
        }
        document.add(facts);

        section(document, "Revisión a bordo", title);
        PdfPTable grid = new PdfPTable(new float[] { 0.8f, 2.4f, 1f, 1f, 1f });
        grid.setWidthPercentage(100);
        grid.setHeaderRows(1);
        for (String column : List.of("Tipo", "Especie o arte", "Talla (cm)", "Peso (kg)", "Cantidad")) {
            PdfPCell cell = new PdfPCell(new Phrase(column, header));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(5);
            grid.addCell(cell);
        }
        if (items.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No se registraron capturas ni artes.", body));
            empty.setColspan(5);
            empty.setPadding(5);
            grid.addCell(empty);
        }
        for (ItemEntity item : items) {
            cell(grid, item.getKind() == ItemEntity.Kind.CATCH ? "Captura" : "Arte", body, false);
            cell(grid, item.getLabel(), body, false);
            cell(grid, item.getSizeCm() == null ? "—" : item.getSizeCm().stripTrailingZeros().toPlainString(), body, true);
            cell(grid, item.getWeightKg() == null ? "—" : item.getWeightKg().stripTrailingZeros().toPlainString(), body,
                    true);
            cell(grid, item.getQuantity() == null ? "—" : String.valueOf(item.getQuantity()), body, true);
        }
        document.add(grid);

        section(document, "Hallazgos", title);
        if (findings.isEmpty()) {
            document.add(new Paragraph("Sin hallazgos: la embarcación cumple lo revisado.", body));
        }
        for (FindingEntity finding : findings) {
            document.add(new Paragraph("• " + finding.getCode().label() + ": " + finding.getDescription()
                    + (finding.isAutomatic() ? "" : " (anotado por el inspector)"), body));
        }
        if (act.getObservations() != null) {
            section(document, "Observaciones", title);
            document.add(new Paragraph(act.getObservations(), body));
        }

        section(document, "Firmas", title);
        PdfPTable signatures = new PdfPTable(2);
        signatures.setWidthPercentage(100);
        signatures.addCell(signature(service.read(act.getInspectorSignaturePath()),
                "Inspector: " + act.getInspector().getFullName(), small));
        signatures.addCell(signature(service.read(act.getResponsibleSignaturePath()),
                "Responsable: " + act.getResponsibleName(), small));
        document.add(signatures);

        Paragraph seal = new Paragraph("Sello de integridad (SHA-256): " + act.getContentHash(), small);
        seal.setSpacingBefore(12);
        document.add(seal);
        document.add(new Paragraph("Verifique la autenticidad en " + publicUrl + "/verificar-acta/" + act.getCode()
                + " · Código " + act.getCode(), small));
        document.add(new Paragraph("Acta firmada e inmutable: cualquier modificación invalida el sello.", small));
        document.close();
        return out.toByteArray();
    }

    private static void fact(PdfPTable table, String name, String value, Font label, Font body) {
        PdfPCell key = new PdfPCell(new Phrase(name, label));
        key.setBorderColor(LINE);
        key.setPadding(4);
        table.addCell(key);
        PdfPCell val = new PdfPCell(new Phrase(value, body));
        val.setBorderColor(LINE);
        val.setPadding(4);
        table.addCell(val);
    }

    private static void cell(PdfPTable table, String value, Font font, boolean right) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBorderColor(LINE);
        cell.setPadding(4);
        if (right) {
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        table.addCell(cell);
    }

    private static void section(Document document, String text, Font title) {
        Paragraph paragraph = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11,
                title.getColor()));
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private static PdfPCell signature(byte[] png, String caption, Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(LINE);
        cell.setPadding(6);
        try {
            Image image = Image.getInstance(png);
            image.scaleToFit(220, 90);
            cell.addElement(image);
        } catch (Exception e) {
            cell.addElement(new Paragraph("[firma no disponible]", font));
        }
        cell.addElement(new Paragraph(caption, font));
        return cell;
    }
}
