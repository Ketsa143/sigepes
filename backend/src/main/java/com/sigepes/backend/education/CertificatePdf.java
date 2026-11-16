package com.sigepes.backend.education;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.education.EducationEntities.CertificateEntity;

/** HU-19 · AC-19.3 · Constancia de aprobación en PDF, verificable por su código. */
@Component
public class CertificatePdf {

    private static final Color NAVY = new Color(0x0A, 0x25, 0x40);
    private static final Color TEAL = new Color(0x00, 0x8C, 0x99);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-CO")).withZone(AppClock.ZONE);

    private final String publicUrl;

    public CertificatePdf(@Value("${sigepes.public-url:https://project-sigepes.vercel.app}") String publicUrl) {
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    byte[] render(CertificateEntity certificate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 60, 60, 60, 60);
        PdfWriter.getInstance(document, out);
        document.addTitle("Constancia " + certificate.getCode());
        document.addCreator("SIGEPES");
        document.open();
        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEAL);
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 30, NAVY);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 14, Color.DARK_GRAY);
        Font name = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, NAVY);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

        center(document, "SIGEPES · Sistema de Gestión de Pesca Sostenible", brand, 30);
        center(document, "Constancia de aprobación", title, 30);
        center(document, "Se hace constar que", body, 8);
        center(document, certificate.getHolderName(), name, 8);
        center(document, "aprobó el curso de pesca responsable", body, 8);
        center(document, "«" + certificate.getCourseTitle() + "»", name, 8);
        center(document, "con una calificación de " + certificate.getScore() + " % el "
                + DATE.format(certificate.getIssuedAt()) + ".", body, 40);
        center(document, "Código de verificación: " + certificate.getCode(), brand, 4);
        center(document, "Verifique en " + publicUrl + "/verificar-constancia/" + certificate.getCode(), small, 0);
        document.close();
        return out.toByteArray();
    }

    private static void center(Document document, String text, Font font, float after) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(after);
        document.add(paragraph);
    }
}
