package com.sigepes.backend.education;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.sigepes.backend.education.EducationDtos.CourseInput;
import com.sigepes.backend.education.EducationDtos.LessonInput;
import com.sigepes.backend.education.EducationDtos.QuestionInput;

/**
 * HU-19 · Cursos iniciales. Es contenido real, no de demostración: se carga una sola vez, cuando aún
 * no hay cursos, y desde ahí el ADMIN lo edita, publica o retira sin desplegar código (AC-19.4). El
 * lenguaje es sencillo y las lecciones son cortas a propósito (AC-19.5).
 */
@Component
@Profile("!test")
public class EducationContentSeeder {

    private static final Logger log = LoggerFactory.getLogger(EducationContentSeeder.class);

    private final CourseRepository courseRepository;
    private final EducationService service;

    public EducationContentSeeder(CourseRepository courseRepository, EducationService service) {
        this.courseRepository = courseRepository;
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (courseRepository.count() > 0) {
            return;
        }
        for (CourseInput course : courses()) {
            service.setPublished(service.create(course).id(), true);
        }
        log.info("Cursos iniciales de pesca responsable cargados.");
    }

    static List<CourseInput> courses() {
        return List.of(
                new CourseInput("Tallas mínimas de captura",
                        "Por qué cada especie tiene una talla mínima, cómo medir bien el pescado y qué hacer con un ejemplar pequeño.",
                        "ruler", 70,
                        List.of(
                                new LessonInput("¿Por qué existe la talla mínima?",
                                        "Un pez pequeño todavía no se ha reproducido. Si lo pescamos antes de tiempo, el año "
                                                + "siguiente habrá menos peces para todos.\n\nLa talla mínima es el tamaño a "
                                                + "partir del cual el pez ya tuvo al menos una oportunidad de reproducirse. "
                                                + "Respetarla es cuidar la pesca de mañana.",
                                        null, null, 2),
                                new LessonInput("Cómo medir el pescado",
                                        "Ponga el pez de lado, sobre una superficie plana y con la boca cerrada.\n\nMida en "
                                                + "línea recta desde la punta de la boca hasta el final de la cola. Esa es la "
                                                + "longitud total.\n\nUse una regla o una tabla de medir: a ojo es fácil "
                                                + "equivocarse.",
                                        null, null, 2),
                                new LessonInput("Si el pez es más pequeño",
                                        "Devuélvalo al agua lo antes posible, con cuidado y sin golpearlo.\n\nNo lo guarde "
                                                + "ni lo venda: un ejemplar por debajo de la talla mínima es un hallazgo en una "
                                                + "inspección y puede abrir un proceso sancionatorio.\n\nEn SIGEPES, al "
                                                + "registrar una captura, el sistema le avisa si la talla está por debajo de la "
                                                + "mínima de esa especie.",
                                        null, null, 2)),
                        List.of(
                                new QuestionInput("¿Para qué sirve la talla mínima?",
                                        List.of("Para que el pez alcance a reproducirse", "Para vender más caro",
                                                "Para pescar solo peces grandes y bonitos"),
                                        0, "La talla mínima protege a los peces que aún no se han reproducido."),
                                new QuestionInput("¿Cómo se mide la longitud total de un pez?",
                                        List.of("Desde la boca hasta el final de la cola, en línea recta",
                                                "Solo el cuerpo, sin la cola", "Por su peso en kilos"),
                                        0, "La longitud total va de la punta de la boca al final de la cola."),
                                new QuestionInput("Pesca un pez más pequeño que la talla mínima. ¿Qué hace?",
                                        List.of("Lo devuelvo al agua con cuidado", "Lo guardo para la casa",
                                                "Lo vendo más barato"),
                                        0, "Hay que devolverlo vivo al agua lo antes posible."),
                                new QuestionInput("¿Qué puede pasar si una inspección encuentra peces bajo la talla mínima?",
                                        List.of("Es un hallazgo y puede abrir un proceso sancionatorio", "Nada",
                                                "Le regalan un permiso"),
                                        0, "Es una infracción a la normatividad pesquera."))),
                new CourseInput("Vedas y temporadas de pesca",
                        "Qué es una veda, cómo saber si una especie está en veda y qué pasa si se pesca durante ella.",
                        "calendar", 70,
                        List.of(
                                new LessonInput("¿Qué es una veda?",
                                        "Una veda es un tiempo en el que no se puede pescar una especie, o no se puede "
                                                + "pescar en una zona.\n\nSe declara cuando la especie se está reproduciendo o "
                                                + "cuando hay pocos ejemplares. Dura unas semanas o unos meses.",
                                        null, null, 2),
                                new LessonInput("Cómo saber si hay veda",
                                        "En SIGEPES, el menú «Vedas» muestra las vedas vigentes y las próximas, con la "
                                                + "especie, la zona y las fechas.\n\nAdemás, el sistema le envía una alerta "
                                                + "cuando empieza una veda que le afecta y no le deja registrar capturas de esa "
                                                + "especie en la zona mientras dure.",
                                        null, null, 2),
                                new LessonInput("Si se pesca en veda",
                                        "Pescar una especie en veda es una infracción. La autoridad puede decomisar la "
                                                + "captura, imponer una multa y suspender el permiso.\n\nEl producto pescado en "
                                                + "veda no puede venderse como de origen legal en el marketplace.",
                                        null, null, 2)),
                        List.of(
                                new QuestionInput("¿Qué es una veda?",
                                        List.of("Un tiempo en el que no se puede pescar una especie o en una zona",
                                                "Un permiso especial para pescar más", "Una feria de pescado"),
                                        0, null),
                                new QuestionInput("¿Dónde consulta las vedas vigentes en SIGEPES?",
                                        List.of("En el menú «Vedas»", "En «Embarcaciones»", "No se pueden consultar"),
                                        0, null),
                                new QuestionInput("¿Qué puede pasar si pesca una especie en veda?",
                                        List.of("Decomiso, multa y suspensión del permiso", "Nada si nadie lo ve",
                                                "Le dan un premio"),
                                        0, null),
                                new QuestionInput("¿Por qué se declara una veda?",
                                        List.of("Porque la especie se reproduce o hay pocos ejemplares",
                                                "Para que suba el precio", "Porque llueve"),
                                        0, null))),
                new CourseInput("Áreas Marinas Protegidas",
                        "Qué es un Área Marina Protegida, qué se puede hacer en cada zona y cómo consultarlas antes de salir.",
                        "park", 70,
                        List.of(
                                new LessonInput("¿Qué es un Área Marina Protegida?",
                                        "Es un lugar del mar que se protege por su importancia para la vida marina: "
                                                + "arrecifes, manglares, zonas de cría.\n\nProteger estas áreas ayuda a que haya "
                                                + "más peces también por fuera de ellas.",
                                        null, null, 2),
                                new LessonInput("Las zonas de un área protegida",
                                        "Zona intangible: no se puede pescar nada.\n\nZona de uso sostenible: se puede "
                                                + "pescar solo con las artes y las especies autorizadas.\n\nZona de "
                                                + "amortiguación: es la transición hacia el mar abierto y no tiene "
                                                + "restricción de arte ni especie.",
                                        null, null, 2),
                                new LessonInput("Consúltelas antes de salir",
                                        "En SIGEPES puede ver el mapa de las áreas protegidas y sus reglas, sin iniciar "
                                                + "sesión, en «Áreas Marinas Protegidas».\n\nSi durante la faena su posición "
                                                + "GPS entra en una zona intangible, recibe una alerta para que salga de ella.",
                                        null, null, 2)),
                        List.of(
                                new QuestionInput("En la zona intangible de un área protegida...",
                                        List.of("No se puede pescar nada", "Se pesca con cualquier arte",
                                                "Solo se pesca de noche"),
                                        0, null),
                                new QuestionInput("En la zona de uso sostenible...",
                                        List.of("Se pesca solo con las artes y especies autorizadas", "No hay ninguna regla",
                                                "Está prohibido navegar"),
                                        0, null),
                                new QuestionInput("¿Dónde ve el mapa de las áreas protegidas?",
                                        List.of("En «Áreas Marinas Protegidas» de SIGEPES", "En ninguna parte",
                                                "Solo en la oficina de la autoridad"),
                                        0, null),
                                new QuestionInput("¿Qué pasa si su GPS entra en una zona intangible durante la faena?",
                                        List.of("Recibe una alerta para salir de la zona", "Nada", "Se cierra la faena sola"),
                                        0, null))));
    }
}
