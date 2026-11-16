package com.sigepes.backend.inspections;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.Codes;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.common.FileSignatures;
import com.sigepes.backend.common.FileStorageService;
import com.sigepes.backend.common.FileStorageService.StoredFile;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.gears.FishingGearEntity;
import com.sigepes.backend.gears.FishingGearRepository;
import com.sigepes.backend.inspections.InspectionDtos.CreateInspectionRequest;
import com.sigepes.backend.inspections.InspectionDtos.FindingResponse;
import com.sigepes.backend.inspections.InspectionDtos.InspectionResponse;
import com.sigepes.backend.inspections.InspectionDtos.InspectionSummary;
import com.sigepes.backend.inspections.InspectionDtos.ItemResponse;
import com.sigepes.backend.inspections.InspectionDtos.MeasuredCatch;
import com.sigepes.backend.inspections.InspectionDtos.PublicInspection;
import com.sigepes.backend.inspections.InspectionParts.FindingEntity;
import com.sigepes.backend.inspections.InspectionParts.ItemEntity;
import com.sigepes.backend.inspections.InspectionParts.PhotoEntity;
import com.sigepes.backend.permits.PermitEntity;
import com.sigepes.backend.permits.PermitRepository;
import com.sigepes.backend.reports.PhotoSanitizer;
import com.sigepes.backend.reports.PhotoSanitizer.CleanPhoto;
import com.sigepes.backend.security.AuthenticatedUser;
import com.sigepes.backend.security.CurrentUser;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.species.SpeciesRepository;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.users.UserRepository;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselRepository;
import com.sigepes.backend.vessels.VesselStatus;

/**
 * HU-22 · Inspecciones en campo con acta digital.
 *
 * <p>El acta se arma y se firma en el teléfono, con o sin señal, y llega completa: el servidor la
 * valida, calcula los hallazgos (AC-22.2), la sella con SHA-256 y la guarda inmutable (AC-22.5). El
 * {@code clientId} del dispositivo hace idempotente el envío desde la cola sin señal (AC-22.4).
 */
@Service
public class InspectionService {

    static final int MAX_PHOTOS = 4;
    /** Una firma en PNG ocupa unas decenas de KB; el tope evita usar el campo para subir otra cosa. */
    static final int MAX_SIGNATURE_BYTES = 300 * 1024;
    /** Un acta guardada sin señal puede tardar días en sincronizarse, pero no meses. */
    static final Duration MAX_OFFLINE_AGE = Duration.ofDays(30);

    private final InspectionRepository repository;
    private final InspectionItemRepository itemRepository;
    private final InspectionFindingRepository findingRepository;
    private final InspectionPhotoRepository photoRepository;
    private final VesselRepository vesselRepository;
    private final SpeciesRepository speciesRepository;
    private final FishingGearRepository gearRepository;
    private final PermitRepository permitRepository;
    private final UserRepository userRepository;
    private final PhotoSanitizer photoSanitizer;
    private final FileStorageService storage;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public InspectionService(InspectionRepository repository, InspectionItemRepository itemRepository,
            InspectionFindingRepository findingRepository, InspectionPhotoRepository photoRepository,
            VesselRepository vesselRepository, SpeciesRepository speciesRepository,
            FishingGearRepository gearRepository, PermitRepository permitRepository, UserRepository userRepository,
            PhotoSanitizer photoSanitizer, FileStorageService storage, CurrentUser currentUser,
            AuditService auditService, ApplicationEventPublisher events) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.findingRepository = findingRepository;
        this.photoRepository = photoRepository;
        this.vesselRepository = vesselRepository;
        this.speciesRepository = speciesRepository;
        this.gearRepository = gearRepository;
        this.permitRepository = permitRepository;
        this.userRepository = userRepository;
        this.photoSanitizer = photoSanitizer;
        this.storage = storage;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.events = events;
    }

    /** Resultado del registro: {@code created} es false si el acta ya había llegado (reenvío). */
    public record Registered(InspectionResponse inspection, boolean created) {
    }

    // ------------------------------------------------------------------
    // Registro (AC-22.1 a AC-22.4)
    // ------------------------------------------------------------------

    @Transactional
    public Registered register(CreateInspectionRequest request, List<MultipartFile> photos) {
        AuthenticatedUser actor = currentUser.require();
        Optional<InspectionEntity> existing = repository.findByClientId(request.clientId().trim());
        if (existing.isPresent()) {
            // D5 · El reenvío de la cola sin señal devuelve la misma acta, sin crear otra.
            return new Registered(toResponse(existing.get()), false);
        }

        Instant now = Instant.now();
        Instant inspectedAt = request.inspectedAt().truncatedTo(ChronoUnit.SECONDS);
        if (inspectedAt.isAfter(now.plus(10, ChronoUnit.MINUTES))) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR, "La fecha de la inspección no puede estar en el futuro.");
        }
        if (inspectedAt.isBefore(now.minus(MAX_OFFLINE_AGE))) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                    "El acta tiene más de 30 días; debe registrarse por el procedimiento manual.");
        }
        VesselEntity vessel = vesselRepository.findById(request.vesselId())
                .orElseThrow(() -> ApiException.of(ErrorCode.VESSEL_NOT_FOUND));
        UserEntity inspector = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.of(ErrorCode.UNAUTHORIZED));

        byte[] inspectorSignature = signature(request.inspectorSignature(), "del inspector");
        byte[] responsibleSignature = signature(request.responsibleSignature(), "del responsable");
        List<MultipartFile> files = photos == null ? List.of()
                : photos.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (files.size() > MAX_PHOTOS) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR, "Se admiten hasta " + MAX_PHOTOS + " fotos por acta.");
        }
        List<CleanPhoto> cleanPhotos = files.stream().map(photoSanitizer::sanitize).toList();

        // --- Lo revisado a bordo y los hallazgos automáticos (AC-22.2) ------
        LocalDate day = inspectedAt.atZone(AppClock.ZONE).toLocalDate();
        List<ItemDraft> items = new ArrayList<>();
        List<FindingDraft> findings = new ArrayList<>();
        Optional<PermitEntity> permit = permitRepository.findValidFor(vessel.getOwner().getId(), day).stream()
                .findFirst();
        if (permit.isEmpty()) {
            findings.add(new FindingDraft(FindingCode.NO_VALID_PERMIT,
                    "El armador no tiene un permiso de pesca aprobado y vigente el " + day + ".", true));
        }
        if (vessel.getStatus() != VesselStatus.ACTIVE) {
            findings.add(new FindingDraft(FindingCode.VESSEL_NOT_ACTIVE,
                    "La embarcación figura como " + vessel.getStatus() + " en el registro.", true));
        }
        for (MeasuredCatch measured : request.catches() == null ? List.<MeasuredCatch>of() : request.catches()) {
            SpeciesEntity species = speciesRepository.findById(measured.speciesId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.SPECIES_NOT_FOUND));
            BigDecimal size = measured.sizeCm().setScale(2, RoundingMode.HALF_UP);
            items.add(new ItemDraft(ItemEntity.Kind.CATCH, species.getId(), null, species.getCommonName(), size,
                    measured.weightKg() == null ? null : measured.weightKg().setScale(2, RoundingMode.HALF_UP),
                    measured.quantity()));
            BigDecimal min = species.getMinSizeCm();
            if (min != null && size.compareTo(min) < 0) {
                findings.add(new FindingDraft(FindingCode.SIZE_BELOW_MIN,
                        "Talla inferior a la mínima (" + plain(min) + " cm): " + species.getCommonName() + " de "
                                + plain(size) + " cm.", true));
            }
        }
        Set<String> gearIds = new LinkedHashSet<>(request.gearIds() == null ? List.of() : request.gearIds());
        Set<String> registered = vessel.getGears().stream().map(FishingGearEntity::getId).collect(Collectors.toSet());
        for (String gearId : gearIds) {
            FishingGearEntity gear = gearRepository.findById(gearId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.GEAR_NOT_FOUND));
            items.add(new ItemDraft(ItemEntity.Kind.GEAR, null, gear.getId(), gear.getName(), null, null, null));
            if (gear.isDestructive()) {
                findings.add(new FindingDraft(FindingCode.PROHIBITED_GEAR,
                        "Arte prohibido a bordo: " + gear.getName() + ".", true));
            } else if (!registered.contains(gear.getId())) {
                findings.add(new FindingDraft(FindingCode.UNREGISTERED_GEAR,
                        "Arte no registrado en el perfil de la embarcación: " + gear.getName() + ".", true));
            }
        }
        for (String other : request.otherFindings() == null ? List.<String>of() : request.otherFindings()) {
            findings.add(new FindingDraft(FindingCode.OTHER, other.trim(), false));
        }
        InspectionEntity.Result result = findings.isEmpty() ? InspectionEntity.Result.COMPLIANT
                : InspectionEntity.Result.WITH_FINDINGS;

        // --- Archivos y sello (AC-22.3, AC-22.5) -----------------------------
        String id = Ids.generate("act");
        String code = newCode();
        String dir = "inspecciones/" + id;
        StoredFile inspectorFile = storage.storeBytes(inspectorSignature, FileSignatures.PNG, dir, "firma-inspector");
        StoredFile responsibleFile = storage.storeBytes(responsibleSignature, FileSignatures.PNG, dir,
                "firma-responsable");
        List<PhotoEntity> photoEntities = new ArrayList<>();
        for (int i = 0; i < cleanPhotos.size(); i++) {
            CleanPhoto photo = cleanPhotos.get(i);
            String photoId = Ids.generate("fot");
            StoredFile stored = storage.storeBytes(photo.content(), photo.contentType(), dir, photoId);
            photoEntities.add(new PhotoEntity(photoId, id, i + 1, stored.storagePath(), photo.contentType(),
                    photo.content().length, InspectionSeal.sha256(photo.content())));
        }

        BigDecimal latitude = scale6(request.latitude());
        BigDecimal longitude = scale6(request.longitude());
        String observations = blankToNull(request.observations());
        String responsibleId = blankToNull(request.responsibleIdNumber());
        String hash = InspectionSeal.seal(new InspectionSeal.Content(code, request.clientId().trim(), vessel.getId(),
                vessel.getOwner().getId(), inspector.getId(), permit.map(PermitEntity::getNumber).orElse(null),
                latitude, longitude, request.locationText().trim(), inspectedAt, result.name(), observations,
                request.responsibleName().trim(), responsibleId,
                items.stream().map(ItemDraft::sealed).toList(),
                findings.stream().map(FindingDraft::sealed).toList(),
                InspectionSeal.sha256(inspectorSignature), InspectionSeal.sha256(responsibleSignature),
                photoEntities.stream().map(PhotoEntity::getSha256).toList()));

        InspectionEntity inspection = new InspectionEntity(id, request.clientId().trim(), code, vessel, inspector,
                permit.map(PermitEntity::getNumber).orElse(null), latitude, longitude, request.locationText().trim(),
                inspectedAt, result, observations, request.responsibleName().trim(), responsibleId,
                inspectorFile.storagePath(), responsibleFile.storagePath(), hash, request.savedOffline());
        // La restricción única de client_id es la última barrera: si dos reenvíos llegan a la vez, el
        // segundo falla y el dispositivo, al reintentar, recibe el acta ya guardada.
        repository.saveAndFlush(inspection);
        for (int i = 0; i < items.size(); i++) {
            ItemDraft item = items.get(i);
            itemRepository.save(new ItemEntity(Ids.generate("iti"), id, i + 1, item.kind(), item.speciesId(),
                    item.gearId(), item.label(), item.sizeCm(), item.weightKg(), item.quantity()));
        }
        for (int i = 0; i < findings.size(); i++) {
            FindingDraft finding = findings.get(i);
            findingRepository.save(new FindingEntity(Ids.generate("hal"), id, i + 1, finding.code(),
                    finding.description(), finding.automatic()));
        }
        photoRepository.saveAll(photoEntities);

        auditService.record(AuditActionType.CREATE, "INSPECTION_SIGNED", AuditService.MODULE_INSPECTIONS,
                "Acta de inspección", id, "Acta " + code + " de «" + vessel.getName() + "»: "
                        + (findings.isEmpty() ? "conforme" : findings.size() + " hallazgo(s)")
                        + (request.savedOffline() ? " (levantada sin señal)" : "") + ". SHA-256 " + hash);
        events.publishEvent(new InspectionSigned(id, code, vessel.getOwner().getId(), vessel.getName(),
                findings.size()));
        return new Registered(toResponse(repository.findById(id).orElseThrow()), true);
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InspectionSummary> list(String vesselId) {
        AuthenticatedUser actor = currentUser.require();
        List<InspectionEntity> inspections;
        if (actor.isAuthority()) {
            inspections = vesselId == null || vesselId.isBlank() ? repository.findTop200ByOrderByInspectedAtDesc()
                    : repository.findByVesselIdOrderByInspectedAtDesc(vesselId);
        } else {
            inspections = repository.findByOwnerIdOrderByInspectedAtDesc(actor.id()).stream()
                    .filter(inspection -> vesselId == null || vesselId.isBlank()
                            || inspection.getVessel().getId().equals(vesselId))
                    .toList();
        }
        Map<String, Long> findings = findingRepository
                .findByInspectionIdIn(inspections.stream().map(InspectionEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(FindingEntity::getInspectionId, Collectors.counting()));
        return inspections.stream()
                .map(inspection -> new InspectionSummary(inspection.getId(), inspection.getCode(),
                        inspection.getVessel().getName(), inspection.getVessel().getRegistration(),
                        inspection.getInspector().getFullName(), inspection.getLocationText(),
                        inspection.getInspectedAt(), inspection.getResult().name(),
                        findings.getOrDefault(inspection.getId(), 0L).intValue(), inspection.isSavedOffline()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InspectionResponse detail(String id) {
        return toResponse(visible(id));
    }

    /** Acta visible para quien consulta: la autoridad ve todas; el armador, solo las suyas. */
    @Transactional(readOnly = true)
    public InspectionEntity visible(String id) {
        InspectionEntity inspection = repository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.INSPECTION_NOT_FOUND));
        AuthenticatedUser actor = currentUser.require();
        if (!actor.isAuthority() && !inspection.getOwner().getId().equals(actor.id())) {
            throw ApiException.of(ErrorCode.INSPECTION_NOT_FOUND);
        }
        return inspection;
    }

    @Transactional(readOnly = true)
    public List<ItemEntity> items(String inspectionId) {
        return itemRepository.findByInspectionIdOrderByPositionAsc(inspectionId);
    }

    @Transactional(readOnly = true)
    public List<FindingEntity> findings(String inspectionId) {
        return findingRepository.findByInspectionIdOrderByPositionAsc(inspectionId);
    }

    /** AC-22.3 · Verificación pública por el código impreso en el PDF, con la comprobación del sello. */
    @Transactional(readOnly = true)
    public PublicInspection verify(String code) {
        InspectionEntity inspection = repository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> ApiException.of(ErrorCode.INSPECTION_NOT_FOUND));
        return new PublicInspection(inspection.getCode(), inspection.getVessel().getName(),
                inspection.getVessel().getRegistration(), inspection.getInspectedAt(), inspection.getLocationText(),
                inspection.getResult().name(),
                findings(inspection.getId()).stream().map(finding -> finding.getCode().label()).toList(),
                integrityOk(inspection));
    }

    /** Recalcula el sello con lo guardado en la base y en los archivos. */
    @Transactional(readOnly = true)
    public boolean integrityOk(InspectionEntity inspection) {
        try {
            List<ItemEntity> items = items(inspection.getId());
            List<FindingEntity> findings = findings(inspection.getId());
            List<PhotoEntity> photos = photoRepository.findByInspectionIdOrderByPositionAsc(inspection.getId());
            List<String> photoShas = new ArrayList<>();
            for (PhotoEntity photo : photos) {
                String actual = InspectionSeal.sha256(read(photo.getStoragePath()));
                if (!actual.equals(photo.getSha256())) {
                    return false;
                }
                photoShas.add(actual);
            }
            String expected = InspectionSeal.seal(new InspectionSeal.Content(inspection.getCode(),
                    inspection.getClientId(), inspection.getVessel().getId(), inspection.getOwner().getId(),
                    inspection.getInspector().getId(), inspection.getPermitNumber(), inspection.getLatitude(),
                    inspection.getLongitude(), inspection.getLocationText(), inspection.getInspectedAt(),
                    inspection.getResult().name(), inspection.getObservations(), inspection.getResponsibleName(),
                    inspection.getResponsibleIdNumber(),
                    items.stream().map(item -> InspectionSeal.item(item.getKind().name(), item.getSpeciesId(),
                            item.getGearId(), item.getLabel(), item.getSizeCm(), item.getWeightKg(),
                            item.getQuantity())).toList(),
                    findings.stream().map(f -> InspectionSeal.finding(f.getCode().name(), f.getDescription())).toList(),
                    InspectionSeal.sha256(read(inspection.getInspectorSignaturePath())),
                    InspectionSeal.sha256(read(inspection.getResponsibleSignaturePath())), photoShas));
            return expected.equals(inspection.getContentHash());
        } catch (RuntimeException e) {
            return false;
        }
    }

    byte[] read(String storagePath) {
        try (InputStream in = storage.load(storagePath).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible leer " + storagePath, e);
        }
    }

    // ------------------------------------------------------------------
    // AC-22.5 · S4: un acta firmada no se modifica
    // ------------------------------------------------------------------

    /**
     * Cualquier intento de modificar o borrar un acta firmada se rechaza y queda auditado en una
     * transacción propia (el rechazo revierte la de la petición). La autoridad recibe 409; quien no
     * tiene potestad sobre actas, 403.
     */
    public void rejectModification(String id, String operation) {
        AuthenticatedUser actor = currentUser.require();
        boolean exists = repository.existsById(id);
        auditService.recordIsolated(actor.id(), actor.fullName(), AuditActionType.SECURITY,
                "INSPECTION_TAMPER_ATTEMPT", AuditService.MODULE_INSPECTIONS, "Acta de inspección", id,
                "Intento de " + operation + " un acta " + (exists ? "firmada" : "inexistente") + " por "
                        + actor.email() + " (" + actor.role() + "). Rechazado: el acta es inmutable.");
        if (!actor.isAuthority()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "No tiene permiso para modificar actas de inspección.");
        }
        throw ApiException.of(exists ? ErrorCode.INSPECTION_IMMUTABLE : ErrorCode.INSPECTION_NOT_FOUND);
    }

    // ------------------------------------------------------------------

    private InspectionResponse toResponse(InspectionEntity inspection) {
        List<ItemEntity> items = items(inspection.getId());
        List<FindingEntity> findings = findings(inspection.getId());
        Map<String, SpeciesEntity> species = speciesRepository.findAllById(items.stream()
                .map(ItemEntity::getSpeciesId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(SpeciesEntity::getId, Function.identity()));
        Set<String> flaggedGears = findings.stream()
                .filter(f -> f.getCode() == FindingCode.PROHIBITED_GEAR || f.getCode() == FindingCode.UNREGISTERED_GEAR)
                .map(FindingEntity::getDescription).collect(Collectors.toSet());
        int photos = photoRepository.findByInspectionIdOrderByPositionAsc(inspection.getId()).size();
        return new InspectionResponse(inspection.getId(), inspection.getCode(), inspection.getClientId(),
                inspection.getVessel().getId(), inspection.getVessel().getName(),
                inspection.getVessel().getRegistration(), inspection.getOwner().getFullName(),
                inspection.getInspector().getFullName(), inspection.getPermitNumber(), inspection.getLatitude(),
                inspection.getLongitude(), inspection.getLocationText(), inspection.getInspectedAt(),
                inspection.getResult().name(), inspection.getObservations(), inspection.getResponsibleName(),
                items.stream().map(item -> {
                    SpeciesEntity sp = item.getSpeciesId() == null ? null : species.get(item.getSpeciesId());
                    BigDecimal min = sp == null ? null : sp.getMinSizeCm();
                    boolean finding = item.getKind() == ItemEntity.Kind.CATCH
                            ? min != null && item.getSizeCm() != null && item.getSizeCm().compareTo(min) < 0
                            : flaggedGears.stream().anyMatch(text -> text.endsWith(": " + item.getLabel() + "."));
                    return new ItemResponse(item.getKind().name(), item.getSpeciesId(), item.getGearId(),
                            item.getLabel(), item.getSizeCm(), min, item.getWeightKg(), item.getQuantity(), finding);
                }).toList(),
                findings.stream().map(f -> new FindingResponse(f.getId(), f.getCode().name(), f.getCode().label(),
                        f.getDescription(), f.isAutomatic())).toList(),
                photos, inspection.getContentHash(), inspection.isSavedOffline(), inspection.getCreatedAt());
    }

    /** Firma dibujada en pantalla, enviada como data URL PNG. */
    private static byte[] signature(String dataUrl, String whose) {
        String prefix = "data:image/png;base64,";
        if (dataUrl == null || !dataUrl.startsWith(prefix)) {
            throw ApiException.of(ErrorCode.INVALID_SIGNATURE, "La firma " + whose + " debe ser una imagen PNG.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw ApiException.of(ErrorCode.INVALID_SIGNATURE, "La firma " + whose + " está dañada.");
        }
        if (bytes.length > MAX_SIGNATURE_BYTES || !FileSignatures.PNG.equals(FileSignatures.detect(bytes))) {
            throw ApiException.of(ErrorCode.INVALID_SIGNATURE, "La firma " + whose + " no es una imagen PNG válida.");
        }
        return bytes;
    }

    private String newCode() {
        String code;
        do {
            code = Codes.random("ACT", 8);
        } while (repository.findByCodeIgnoreCase(code).isPresent());
        return code;
    }

    private static BigDecimal scale6(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ItemDraft(ItemEntity.Kind kind, String speciesId, String gearId, String label, BigDecimal sizeCm,
            BigDecimal weightKg, Integer quantity) {

        String sealed() {
            return InspectionSeal.item(kind.name(), speciesId, gearId, label, sizeCm, weightKg, quantity);
        }
    }

    private record FindingDraft(FindingCode code, String description, boolean automatic) {

        String sealed() {
            return InspectionSeal.finding(code.name(), description);
        }
    }
}
