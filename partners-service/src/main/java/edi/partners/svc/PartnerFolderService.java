package edi.partners.svc;

import edi.partners.config.RequestIdentity;
import edi.partners.domain.FolderState;
import edi.partners.domain.Partner;
import edi.partners.dto.FolderPrecheckResponse;
import edi.partners.dto.FolderStatusResponse;
import edi.partners.dto.FolderStatusResponse.Folder;
import edi.partners.dto.FolderStatusResponse.Health;
import edi.partners.dto.PartnerResponse;
import edi.partners.error.FolderPrecheckException;
import edi.partners.error.PartnerStateException;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditEvent.Change;
import edi.partners.svc.audit.AuditEventTypes;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.svc.folders.FilesystemPartnerFolderProvisioner;
import edi.partners.svc.folders.PartnerFolderException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carpetas del socio (HU-09): estado, reparacion, verificacion previa y enlace con la carpeta
 * compartida del ERP, y activacion de produccion. Regla de oro: nunca se borra un archivo; si algo
 * impide moverlo, la operacion se detiene y lo ya movido queda en la carpeta compartida.
 */
@Service
public class PartnerFolderService {

    private final PartnerService partners;
    private final PartnerRepository repo;
    private final StorageLayout layout;
    private final FilesystemPartnerFolderProvisioner provisioner;
    private final AuditLogPublisher audit;

    public PartnerFolderService(PartnerService partners, PartnerRepository repo, StorageLayout layout,
                                FilesystemPartnerFolderProvisioner provisioner, AuditLogPublisher audit) {
        this.partners = partners;
        this.repo = repo;
        this.layout = layout;
        this.provisioner = provisioner;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ estado

    @Transactional(readOnly = true)
    public FolderStatusResponse status(UUID partnerId) {
        return status(partners.active(partnerId));
    }

    /** Estado de todos los socios activos. Un socio con problemas no impide ver a los demas. */
    @Transactional(readOnly = true)
    public List<FolderStatusResponse> statuses() {
        return repo.findAllByActiveOrderByAs2NameAsc(true).stream().map(this::status).toList();
    }

    /** Nunca lanza: lo que no se pueda comprobar se informa como {@link Health#ERROR}. */
    public FolderStatusResponse status(Partner p) {
        List<Folder> folders = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Health health = Health.OK;
        try {
            Path base = layout.partnerRoot(p.getAs2Name());
            boolean linked = p.getFolderState() != FolderState.LOCAL;
            if (linked && !Files.isDirectory(layout.shared(p.getSharedFolderName()))) {
                problems.add("La carpeta compartida " + layout.shared(p.getSharedFolderName())
                        + " no esta disponible (¿recurso compartido desmontado?)");
                health = Health.ERROR;
            }
            for (String name : expected(p)) {
                FolderCheck c = inspect(base.resolve(name), name, linked);
                folders.add(c.folder());
                if (c.problem() != null) {
                    problems.add(c.problem());
                    health = worst(health, c.health());
                }
            }
        } catch (IOException | RuntimeException e) {
            problems.add("No se pudo comprobar las carpetas: " + e.getMessage());
            health = Health.ERROR;
        }
        return new FolderStatusResponse(p.getId(), p.getAs2Name(), p.getFolderState(), p.getSharedFolderName(),
                health, folders, problems);
    }

    /** Resultado de revisar una subcarpeta: su estado y, si lo hay, el problema. */
    private record FolderCheck(Folder folder, Health health, String problem) {
    }

    private static FolderCheck inspect(Path local, String name, boolean linked) throws IOException {
        boolean link = Files.isSymbolicLink(local);
        String target = link ? Files.readSymbolicLink(local).toString() : null;
        boolean exists = Files.isDirectory(local);
        boolean readable = exists && Files.isReadable(local);
        boolean writable = exists && Files.isWritable(local);
        Folder f = new Folder(name, local.toString(), exists, readable, writable, link, target);
        if (link && !exists) {
            return new FolderCheck(f, Health.ERROR, name + ": el enlace apunta a " + target + ", que no esta disponible");
        }
        if (linked && !link) {
            return new FolderCheck(f, Health.ERROR, name + ": deberia ser un enlace a la carpeta compartida");
        }
        if (!exists) {
            return new FolderCheck(f, Health.MISSING, name + ": no existe");
        }
        if (!readable || !writable) {
            return new FolderCheck(f, Health.NO_ACCESS,
                    name + ": el servicio no tiene permiso de lectura y escritura");
        }
        return new FolderCheck(f, Health.OK, null);
    }

    private static List<String> expected(Partner p) {
        if (p.getFolderState() != FolderState.PRODUCTION) {
            return StorageLayout.TREE;
        }
        List<String> all = new ArrayList<>(StorageLayout.TREE);
        all.add(StorageLayout.INCOMING_QUEUE);
        return all;
    }

    private static Health worst(Health a, Health b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** Vuelve a crear las carpetas locales que falten (solo con el socio sin enlazar). */
    @Transactional
    public FolderStatusResponse repair(UUID partnerId) {
        Partner p = partners.active(partnerId);
        if (p.getFolderState() != FolderState.LOCAL) {
            throw new PartnerStateException("Con el socio enlazado sus carpetas estan en el recurso compartido: "
                    + "revise el montaje de " + layout.shared(p.getSharedFolderName()));
        }
        List<Path> created = provisioner.create(p.getAs2Name());
        if (!created.isEmpty()) {
            publish(AuditEventTypes.FOLDERS_REPAIRED, p, "Carpetas creadas: "
                    + created.stream().map(c -> c.getFileName().toString()).toList(), Map.of());
        }
        return status(p);
    }

    // ------------------------------------------------------------------ enlace con la carpeta compartida

    @Transactional(readOnly = true)
    public FolderPrecheckResponse precheck(UUID partnerId, String sharedFolderName) {
        return precheck(partners.active(partnerId), sharedFolderName);
    }

    /** Verificacion previa: no modifica nada. */
    FolderPrecheckResponse precheck(Partner p, String raw) {
        String name = PartnerRules.sharedFolderName(raw);
        if (name == null) {
            throw new IllegalArgumentException("Indique la carpeta del socio dentro del recurso compartido");
        }
        List<String> problems = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        Path target = layout.shared(name);
        if (p.getFolderState() != FolderState.LOCAL) {
            problems.add("El socio ya esta enlazado con " + layout.shared(p.getSharedFolderName()));
        } else if (!Files.isDirectory(layout.sharedRoot())) {
            problems.add("El recurso compartido no esta montado en " + layout.sharedRoot());
        } else if (!Files.isDirectory(target)) {
            problems.add("La carpeta compartida '" + name + "' no existe en el montaje (" + layout.sharedRoot() + ")");
        } else if (!Files.isWritable(target)) {
            problems.add("El servicio no tiene permiso de escritura en " + target);
        } else {
            Path base = layout.partnerRoot(p.getAs2Name());
            for (String sub : StorageLayout.TREE) {
                checkSubfolder(base.resolve(sub), target.resolve(sub), sub, problems, actions);
            }
        }
        return new FolderPrecheckResponse(problems.isEmpty(), name, target.toString(), problems, actions);
    }

    private static void checkSubfolder(Path local, Path remote, String sub, List<String> problems,
                                       List<String> actions) {
        try {
            if (Files.isSymbolicLink(local)) {
                checkExistingLink(local, remote, sub, problems, actions);
                return;
            }
            if (Files.exists(remote) && !Files.isDirectory(remote)) {
                problems.add(sub + ": en la carpeta compartida existe " + remote + " y no es una carpeta");
                return;
            }
            if (Files.isDirectory(local)) {
                checkFilesToMove(local, remote, sub, problems, actions);
            } else if (Files.exists(local)) {
                problems.add(sub + ": " + local + " existe y no es una carpeta");
                return;
            }
            String created = Files.isDirectory(remote) ? "" : " (se creara)";
            actions.add(sub + ": se enlazara con " + remote + created);
        } catch (IOException e) {
            problems.add(sub + ": no se pudo revisar (" + e.getMessage() + ")");
        }
    }

    /** Subcarpeta que ya es un enlace: correcta si apunta a su destino en el recurso compartido. */
    private static void checkExistingLink(Path local, Path remote, String sub, List<String> problems,
                                          List<String> actions) throws IOException {
        Path target = Files.readSymbolicLink(local);
        if (target.equals(remote)) {
            actions.add(sub + ": ya enlazada");
        } else {
            problems.add(sub + ": ya es un enlace a otra carpeta (" + target + ")");
        }
    }

    /** Archivos que el enlace movera: no puede haber subcarpetas ni nombres que ya existan en el destino. */
    private static void checkFilesToMove(Path local, Path remote, String sub, List<String> problems,
                                         List<String> actions) throws IOException {
        List<Path> entries;
        try (Stream<Path> s = Files.list(local)) {
            entries = s.toList();
        }
        List<String> dirs = entries.stream().filter(Files::isDirectory).map(e -> e.getFileName().toString()).toList();
        if (!dirs.isEmpty()) {
            problems.add(sub + ": contiene subcarpetas " + dirs + "; muevalas a mano antes de enlazar");
        }
        List<Path> files = entries.stream().filter(e -> !Files.isDirectory(e)).toList();
        files.stream().filter(f -> Files.exists(remote.resolve(f.getFileName())))
                .forEach(f -> problems.add(sub + "/" + f.getFileName()
                        + " ya existe en la carpeta compartida: no se sobrescribe"));
        if (!files.isEmpty()) {
            actions.add(sub + ": se moveran " + files.size() + " archivo(s) a " + remote);
        }
    }

    /**
     * Enlaza cada subcarpeta con {@code <montaje>/<carpeta>/<subcarpeta>}, moviendo antes los archivos
     * que tuviera. Repite la verificacion previa y no hace nada si falla. Es reanudable: las
     * subcarpetas ya enlazadas se saltan.
     */
    @Transactional
    public FolderStatusResponse link(UUID partnerId, String sharedFolderName) {
        Partner p = partners.active(partnerId);
        FolderPrecheckResponse pre = precheck(p, sharedFolderName);
        if (!pre.ok()) {
            throw new FolderPrecheckException(pre.problems());
        }
        provisioner.create(p.getAs2Name());
        Path base = layout.partnerRoot(p.getAs2Name());
        Path target = layout.shared(pre.sharedFolderName());
        int moved = 0;
        for (String sub : StorageLayout.TREE) {
            Path local = base.resolve(sub);
            Path remote = target.resolve(sub);
            try {
                if (Files.isSymbolicLink(local)) {
                    continue;
                }
                Files.createDirectories(remote);
                moved += moveFiles(local, remote);
                Files.delete(local); // ya vacia; si no lo estuviera, falla y no se borra nada
                Files.createSymbolicLink(local, remote);
            } catch (IOException e) {
                throw new PartnerFolderException("El enlace se detuvo en " + sub + " (" + e.getMessage()
                        + "). Ningun archivo se borro: lo ya movido esta en " + target
                        + ". Corrija el problema y vuelva a enlazar.", e);
            }
        }
        String before = p.getSharedFolderName();
        p.setSharedFolderName(pre.sharedFolderName());
        p.setFolderState(FolderState.SAMBA_LINKED);
        repo.saveAndFlush(p);
        Map<String, Change> changes = new LinkedHashMap<>();
        changes.put("sharedFolderName", new Change(before, pre.sharedFolderName()));
        changes.put("folderState", new Change(FolderState.LOCAL.name(), FolderState.SAMBA_LINKED.name()));
        changes.put("movedFiles", new Change(null, moved));
        publish(AuditEventTypes.FOLDERS_LINKED, p, "Carpetas enlazadas con " + target, changes);
        return status(p);
    }

    private static int moveFiles(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return 0;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(from)) {
            files = s.filter(f -> !Files.isDirectory(f)).toList();
        }
        for (Path f : files) {
            Files.move(f, to.resolve(f.getFileName())); // sin REPLACE_EXISTING: nunca sobrescribe
        }
        return files.size();
    }

    // ------------------------------------------------------------------ produccion

    /**
     * Activa o desactiva produccion. Activar exige el socio enlazado y sus carpetas sanas, y crea la
     * cola {@code Incoming_queue} en la carpeta compartida. Desactivar apaga la cola (el motor AS2 deja
     * de dejar alli documentos nuevos) y conserva lo que ya hubiera.
     */
    @Transactional
    public PartnerResponse production(UUID partnerId, boolean enabled, String confirm) {
        Partner p = partners.active(partnerId);
        if (confirm == null || !confirm.strip().equals(p.getAs2Name())) {
            throw new IllegalArgumentException(
                    "Para confirmar escriba el identificador AS2 del socio: " + p.getAs2Name());
        }
        FolderState before = p.getFolderState();
        if (enabled && before != FolderState.PRODUCTION) {
            enable(p);
        } else if (!enabled && before == FolderState.PRODUCTION) {
            p.setEnableIncomingQueue(false);
            p.setFolderState(FolderState.SAMBA_LINKED);
        } else {
            return PartnerResponse.of(p); // ya estaba asi
        }
        repo.saveAndFlush(p);
        publish(enabled ? AuditEventTypes.PRODUCTION_ENABLED : AuditEventTypes.PRODUCTION_DISABLED, p,
                (enabled ? "Produccion activada para " : "Produccion desactivada para ") + p.getAs2Name(),
                Map.of("folderState", new Change(before.name(), p.getFolderState().name()),
                        "enableIncomingQueue", new Change(!enabled, enabled)));
        return PartnerResponse.of(p);
    }

    private void enable(Partner p) {
        if (p.getFolderState() != FolderState.SAMBA_LINKED) {
            throw new PartnerStateException("Primero enlace las carpetas del socio con la carpeta compartida");
        }
        FolderStatusResponse st = status(p);
        if (st.health() != Health.OK) {
            throw new PartnerStateException("Las carpetas del socio tienen problemas: " + String.join("; ",
                    st.problems()));
        }
        Path remote = layout.shared(p.getSharedFolderName()).resolve(StorageLayout.INCOMING_QUEUE);
        Path local = layout.partnerRoot(p.getAs2Name()).resolve(StorageLayout.INCOMING_QUEUE);
        try {
            Files.createDirectories(remote);
            if (!Files.isSymbolicLink(local)) {
                if (Files.exists(local)) {
                    throw new PartnerStateException(local + " ya existe y no es un enlace: revise su contenido");
                }
                Files.createSymbolicLink(local, remote);
            }
        } catch (IOException e) {
            throw new PartnerFolderException("No se pudo crear la cola de entrada " + remote, e);
        }
        p.setEnableIncomingQueue(true);
        p.setFolderState(FolderState.PRODUCTION);
    }

    private void publish(String type, Partner p, String detail, Map<String, Change> changes) {
        audit.publish(AuditEvent.of(type, p.getId(), p.getAs2Name(), RequestIdentity.currentUser(), detail, changes));
    }
}
