package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import com.dndtool.persistence.storage.LocalRuleLibraryLedger;
import com.dndtool.persistence.storage.LocalRuleLibraryLedger.Intent;
import com.dndtool.persistence.storage.LocalRuleLibraryLedger.Kind;
import com.dndtool.persistence.storage.LocalRuleLibraryLedger.Operation;
import com.dndtool.persistence.storage.LocalRuleLibraryLedger.Phase;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.dndtool.module.composition.Schema2CompositionKernel.MAX_PACKAGES;

/** Independent local custody and explicit acceptance of exact, immutable author packages. */
public final class LocalRuleLibrary {
    public static final String REVIEW_PROTOCOL = "DND_LOCAL_RULE_REVIEW_V1";
    private static final Set<Path> JVM_OWNERS = ConcurrentHashMap.newKeySet();

    public record ImportRecord(StoredMember member, String canonicalProfile, String operationId) { }
    public record AcceptanceRecord(StoredMember member, String reviewProtocol, String acceptedBy, String operationId) { }
    public record ImportReceipt(String operationId, ImportRecord imported) { }
    public record AcceptanceReceipt(String operationId, AcceptanceRecord acceptance) { }
    public record Review(ImportRecord imported, AcceptanceRecord acceptance, ProductionRuleArtifact artifact) { }
    public record InventoryEntry(ImportRecord imported, AcceptanceRecord acceptance) { }
    public enum OperationState { NOT_FOUND, PENDING, RETRYABLE, COMMITTED }
    public record OperationStatus(String operationId, String kind, OperationState state, StoredMember member,
                                  ImportReceipt importReceipt, AcceptanceReceipt acceptanceReceipt) { }

    enum Boundary { AFTER_LEDGER_FILE_CREATE, AFTER_LEDGER_SCHEMA_COMMIT, AFTER_ARTIFACT_PUBLISH, AFTER_BUSINESS_COMMIT }
    @FunctionalInterface interface FaultInjector { void at(Boundary boundary); }
    @FunctionalInterface private interface Work<T> { T run() throws IOException, SQLException; }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws IOException, SQLException; }

    private final Path root, database, originals, initialized, lockFile;
    private final LocalRuleLibraryLedger ledger = new LocalRuleLibraryLedger();
    private final FaultInjector faults;

    public LocalRuleLibrary(Path storageRoot) { this(storageRoot, boundary -> { }); }

    LocalRuleLibrary(Path storageRoot, FaultInjector faults) {
        if (storageRoot == null || !storageRoot.isAbsolute()) throw new IllegalArgumentException("Storage root must be absolute");
        this.root = storageRoot.normalize().resolve("rule-library");
        database = root.resolve("ledger.db");
        originals = root.resolve("originals");
        initialized = root.resolve("initialized");
        lockFile = root.resolve("write.lock");
        this.faults = Objects.requireNonNull(faults);
    }

    public ImportReceipt importArtifact(String operationId, byte[] zip) {
        String id = operationId(operationId, "IMPORT");
        if (zip == null || zip.length == 0 || zip.length > ProductionRuleArtifact.MAX_ZIP_BYTES)
            throw failure("RULE_PACKAGE_INVALID", "IMPORT", null, "Rule ZIP size is outside the allowed budget", null);
        byte[] supplied = zip.clone();
        ProductionRuleArtifact artifact;
        try { artifact = ProductionRuleArtifact.read(supplied); }
        catch (IllegalArgumentException invalid) {
            throw failure("RULE_PACKAGE_INVALID", "IMPORT", null, "Rule ZIP is invalid", invalid);
        }
        Intent intent = new Intent(id, Kind.IMPORT, member(artifact), artifact.canonicalProfile(), null, null);
        return mutate(intent, supplied).importReceipt();
    }

    public AcceptanceReceipt accept(String operationId, StoredMember reviewed, String reviewProtocol, String acceptedBy) {
        requireMember(reviewed, "ACCEPT");
        if (!REVIEW_PROTOCOL.equals(reviewProtocol) || !validActor(acceptedBy))
            throw failure("RULE_REVIEW_INVALID", "ACCEPT", reviewed, "Review protocol or accepting identity is invalid", null);
        Intent intent = new Intent(operationId(operationId, "ACCEPT"), Kind.ACCEPT, reviewed,
                ProductionRuleArtifact.CANONICAL_PROFILE, reviewProtocol, acceptedBy);
        return mutate(intent, null).acceptanceReceipt();
    }

    public Review review(String moduleKey, String releaseVersion) {
        requireKey(moduleKey, releaseVersion, "REVIEW");
        StoredMember expected = new StoredMember(moduleKey, releaseVersion, null, null, null);
        return locked("REVIEW", expected, false, () -> { throw missing("REVIEW", expected); }, () ->
            query(connection -> {
                requireNoPending(connection, "REVIEW");
                ImportRecord imported = requireImport(connection, expected, "REVIEW", false);
                return new Review(imported, acceptance(connection, imported, null, "REVIEW"),
                        verifyOriginal(imported.member(), "REVIEW"));
            }));
    }

    public List<ImportRecord> listImports() {
        return inventory().stream().map(InventoryEntry::imported).toList();
    }

    public List<InventoryEntry> inventory() {
        return locked("INVENTORY", null, false, List::of, () ->
            query(connection -> {
                requireNoPending(connection, "INVENTORY");
                List<InventoryEntry> result = new ArrayList<>();
                for (ImportRecord imported : ledger.imports(connection)) {
                    verifyImport(connection, imported, null, "INVENTORY");
                    result.add(new InventoryEntry(imported, acceptance(connection, imported, null, "INVENTORY")));
                }
                return List.copyOf(result);
            }));
    }

    public List<ProductionRuleArtifact> readAccepted(List<StoredMember> ordered) {
        if (ordered == null || ordered.isEmpty() || ordered.size() > MAX_PACKAGES)
            throw failure("RULE_BINDING_INVALID", "BIND", null, "Select an ordered base and supported extensions", null);
        List<StoredMember> members = new ArrayList<>(ordered);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            StoredMember member = members.get(i);
            requireMember(member, "BIND");
            if (!keys.add(member.moduleKey()) || (i == 0) != (member.role() == Role.BASE))
                throw failure("RULE_BINDING_INVALID", "BIND", member, "Rule member order or identity is invalid", null);
        }
        return locked("BIND", members.getFirst(), false, () -> { throw missing("BIND", members.getFirst()); }, () ->
            query(connection -> {
                requireNoPending(connection, "BIND");
                List<ProductionRuleArtifact> result = new ArrayList<>();
                for (StoredMember member : members) {
                    ImportRecord imported = requireImport(connection, member, "BIND", true);
                    ProductionRuleArtifact artifact = verifyOriginal(member, "BIND");
                    if (acceptance(connection, imported, null, "BIND") == null)
                        throw failure("RULE_PACKAGE_NOT_ACCEPTED", "BIND", member, "Exact rule package has no local acceptance", null);
                    result.add(artifact);
                }
                return List.copyOf(result);
            }));
    }

    public OperationStatus inspect(String operationId) {
        String id = operationId(operationId, "INSPECT");
        return locked("INSPECT", null, false, () -> notFound(id), () ->
            query(connection -> {
                Operation operation = operation(connection, id, "INSPECT");
                if (operation == null) return notFound(id);
                return operation.phase() == Phase.COMMITTED ? proveReceipt(connection, operation, "INSPECT") : status(operation);
            }));
    }

    public OperationStatus recover(String operationId) {
        String id = operationId(operationId, "RECOVER");
        return locked("RECOVER", null, false, () -> notFound(id), () ->
                transact("RECOVER", null, connection -> recover(connection, id, "RECOVER")));
    }

    private OperationStatus mutate(Intent intent, byte[] zip) {
        String stage = intent.kind().name();
        StoredMember member = intent.member();
        return locked(stage, member, intent.kind() == Kind.IMPORT, () -> { throw missing(stage, member); }, () -> {
            Operation claimed = transact(stage, member, connection -> claim(connection, intent, stage));
            if (claimed.resultOperationId() != null)
                return transact(stage, member, connection -> recover(connection, intent.operationId(), stage));
            if (intent.kind() == Kind.IMPORT) {
                publish(intent, zip);
                faults.at(Boundary.AFTER_ARTIFACT_PUBLISH);
            }
            transact(stage, member, connection -> {
                Operation active = operation(connection, intent.operationId(), stage);
                if (active == null || !active.intent().equals(intent) || active.phase() != Phase.PENDING
                        || active.resultOperationId() != null) throw inconsistent(stage, member);
                String origin = apply(connection, intent, stage);
                ledger.recordResult(connection, intent.operationId(), origin);
                return null;
            });
            try { faults.at(Boundary.AFTER_BUSINESS_COMMIT); }
            catch (RuntimeException failure) { throw unknown(stage, member, failure); }
            return transact(stage, member, connection -> recover(connection, intent.operationId(), stage));
        });
    }

    private Operation claim(Connection connection, Intent intent, String stage) throws IOException, SQLException {
        Operation prior = operation(connection, intent.operationId(), stage);
        if (prior != null && !prior.intent().equals(intent))
            throw failure("IDEMPOTENCY_CONFLICT", stage, intent.member(), "Operation key already has a different exact intent", null);
        if (prior != null && prior.resultOperationId() != null) return prior;
        Operation pending = pending(connection, stage);
        if (pending != null && !pending.intent().operationId().equals(intent.operationId())) throw blocked(stage, pending);

        StoredMember member = intent.member();
        ImportRecord imported = ledger.imported(connection, member.moduleKey(), member.releaseVersion());
        if (imported != null) {
            verifyImport(connection, imported, null, stage);
            if (!member.equals(imported.member())) throw identityConflict(stage, member);
            verifyOriginal(member, stage);
        } else if (intent.kind() == Kind.ACCEPT) {
            throw missing(stage, member);
        } else {
            Operation owner = checkedOperation(ledger.unregisteredZip(connection, member.rawZipSha256()), stage);
            if (owner != null && !owner.intent().equals(intent)) throw blocked(stage, owner);
            if (owner == null && exists(original(member))) throw inconsistent(stage, member);
        }
        if (prior == null) ledger.claim(connection, intent);
        else if (prior.phase() == Phase.RETRYABLE) ledger.retry(connection, intent.operationId());
        return new Operation(intent, Phase.PENDING, null);
    }

    private String apply(Connection connection, Intent intent, String stage) throws SQLException {
        StoredMember member = intent.member();
        String id = intent.operationId();
        ImportRecord imported = ledger.imported(connection, member.moduleKey(), member.releaseVersion());
        if (imported != null) {
            verifyImport(connection, imported, null, stage);
            if (!member.equals(imported.member())) throw identityConflict(stage, member);
        }
        if (intent.kind() == Kind.IMPORT) {
            if (imported != null) return imported.operationId();
            ledger.register(connection, new ImportRecord(member, intent.canonicalProfile(), id));
            return id;
        }
        if (imported == null) throw missing(stage, member);
        AcceptanceRecord accepted = acceptance(connection, imported, null, stage);
        if (accepted != null) return accepted.operationId();
        ledger.accept(connection, new AcceptanceRecord(member, intent.reviewProtocol(), intent.acceptedBy(), id),
                imported.operationId());
        return id;
    }

    private OperationStatus recover(Connection connection, String id, String stage) throws IOException, SQLException {
        Operation operation = operation(connection, id, stage);
        if (operation == null) return notFound(id);
        if (operation.resultOperationId() != null) {
            OperationStatus proven = proveReceipt(connection, operation, stage);
            if (operation.phase() == Phase.PENDING) ledger.resolve(connection, id, Phase.COMMITTED);
            return proven;
        }
        if (operation.phase() == Phase.PENDING) {
            proveUncommitted(connection, operation, stage);
            ledger.resolve(connection, id, Phase.RETRYABLE);
            return status(new Operation(operation.intent(), Phase.RETRYABLE, null));
        }
        return status(operation);
    }

    private void proveUncommitted(Connection connection, Operation operation, String stage) throws IOException, SQLException {
        Intent intent = operation.intent();
        StoredMember member = intent.member();
        ImportRecord imported = ledger.imported(connection, member.moduleKey(), member.releaseVersion());
        if (imported == null) {
            if (intent.kind() == Kind.ACCEPT) throw inconsistent(stage, member);
            if (exists(original(member))) verifyOriginal(member, stage);
            return;
        }
        verifyImport(connection, imported, null, stage);
        if (!member.equals(imported.member())) throw identityConflict(stage, member);
        if (intent.operationId().equals(imported.operationId())) throw inconsistent(stage, member);
        AcceptanceRecord accepted = acceptance(connection, imported, null, stage);
        if (accepted != null && intent.operationId().equals(accepted.operationId())) throw inconsistent(stage, member);
        verifyOriginal(member, stage);
    }

    private OperationStatus proveReceipt(Connection connection, Operation operation, String stage) throws IOException, SQLException {
        Intent intent = operation.intent();
        StoredMember member = intent.member();
        String id = intent.operationId();
        ImportRecord imported = requireImport(connection, member, stage, true, operation);
        verifyOriginal(member, stage);
        if (intent.kind() == Kind.IMPORT) {
            if (!imported.operationId().equals(operation.resultOperationId())) throw inconsistent(stage, member);
            return new OperationStatus(id, Kind.IMPORT.name(), OperationState.COMMITTED, member,
                    new ImportReceipt(id, imported), null);
        }
        AcceptanceRecord accepted = acceptance(connection, imported, operation, stage);
        if (accepted == null || !accepted.operationId().equals(operation.resultOperationId())) throw inconsistent(stage, member);
        return new OperationStatus(id, Kind.ACCEPT.name(), OperationState.COMMITTED, member, null,
                new AcceptanceReceipt(id, accepted));
    }

    private ImportRecord requireImport(Connection connection, StoredMember expected, String stage, boolean exact)
            throws SQLException {
        return requireImport(connection, expected, stage, exact, null);
    }

    private ImportRecord requireImport(Connection connection, StoredMember expected, String stage, boolean exact, Operation current)
            throws SQLException {
        ImportRecord row = ledger.imported(connection, expected.moduleKey(), expected.releaseVersion());
        if (row == null) throw missing(stage, expected);
        if (exact && !expected.equals(row.member())) throw identityConflict(stage, expected);
        verifyImport(connection, row, current, stage);
        return row;
    }

    private void verifyImport(Connection connection, ImportRecord imported, Operation current, String stage) throws SQLException {
        StoredMember member = imported.member();
        Operation origin = origin(connection, imported.operationId(), current, stage);
        if (origin == null || origin.intent().kind() != Kind.IMPORT || !origin.intent().member().equals(member)
                || !origin.intent().canonicalProfile().equals(imported.canonicalProfile()))
            throw inconsistent(stage, member);
    }

    private AcceptanceRecord acceptance(Connection connection, ImportRecord imported, Operation current, String stage)
            throws SQLException {
        var row = ledger.acceptance(connection, imported.member());
        if (row == null) return null;
        var accepted = row.record();
        Operation origin = origin(connection, accepted.operationId(), current, stage);
        if (!imported.operationId().equals(row.importOperationId()) || origin == null || origin.intent().kind() != Kind.ACCEPT
                || !origin.intent().member().equals(imported.member()) || !origin.intent().reviewProtocol().equals(accepted.reviewProtocol())
                || !origin.intent().acceptedBy().equals(accepted.acceptedBy())) throw inconsistent(stage, imported.member());
        return accepted;
    }

    private Operation origin(Connection connection, String id, Operation current, String stage) throws SQLException {
        if (current != null && current.intent().operationId().equals(id))
            return id.equals(current.resultOperationId()) ? current : null;
        Operation origin = operation(connection, id, stage);
        return origin != null && origin.phase() == Phase.COMMITTED && id.equals(origin.resultOperationId()) ? origin : null;
    }

    private Operation operation(Connection connection, String id, String stage) throws SQLException {
        return checkedOperation(ledger.operation(connection, id), stage);
    }

    private Operation pending(Connection connection, String stage) throws SQLException {
        return checkedOperation(ledger.pending(connection), stage);
    }

    private static Operation checkedOperation(Operation operation, String stage) {
        if (operation == null) return null;
        Intent intent = operation.intent();
        if (!validMember(intent.member()) || !ProductionRuleArtifact.CANONICAL_PROFILE.equals(intent.canonicalProfile())
                || !validOperationId(intent.operationId())
                || (operation.resultOperationId() != null && !validOperationId(operation.resultOperationId()))
                || (intent.kind() == Kind.ACCEPT && (!REVIEW_PROTOCOL.equals(intent.reviewProtocol()) || !validActor(intent.acceptedBy()))))
            throw inconsistent(stage, null);
        return operation;
    }

    private void requireNoPending(Connection connection, String stage) throws SQLException {
        Operation pending = pending(connection, stage);
        if (pending != null) throw blocked(stage, pending);
    }

    private void publish(Intent intent, byte[] supplied) throws IOException {
        StoredMember member = intent.member();
        Path target = original(member);
        Path part = originals.resolve("." + intent.operationId() + ".part");
        if (!exists(target)) {
            if (exists(part)) {
                byte[] staged = readZip(part);
                if (!Arrays.equals(staged, supplied)) {
                    if (staged.length > supplied.length
                            || !Arrays.equals(staged, 0, staged.length, supplied, 0, staged.length))
                        throw inconsistent("PUBLISH", member);
                    Files.delete(part);
                }
            }
            if (!exists(part)) writeNew(part, supplied);
            else try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                channel.force(true);
            }
            if (!Arrays.equals(readZip(part), supplied)) throw inconsistent("PUBLISH", member);
            Files.createLink(target, part);
            ManagedCampaignFiles.forceDirectory(originals);
        }
        ProductionRuleArtifact stored = verifyOriginal(member, "PUBLISH");
        if (!Arrays.equals(stored.originalZip(), supplied)) throw inconsistent("PUBLISH", member);
        if (exists(part)) {
            if (!Arrays.equals(readZip(part), supplied)) throw inconsistent("PUBLISH", member);
            Files.delete(part);
            ManagedCampaignFiles.forceDirectory(originals);
        }
    }

    private ProductionRuleArtifact verifyOriginal(StoredMember expected, String stage) throws IOException {
        Path path = original(expected);
        if (!exists(path)) throw missing(stage, expected);
        try {
            byte[] bytes = readZip(path);
            if (!CampaignJson.sha256(bytes).equals(expected.rawZipSha256()))
                throw new IOException("Rule ZIP byte digest differs");
            ProductionRuleArtifact artifact = ProductionRuleArtifact.read(bytes);
            if (!member(artifact).equals(expected) || !ProductionRuleArtifact.CANONICAL_PROFILE.equals(artifact.canonicalProfile()))
                throw new IOException("Rule ZIP identity differs");
            return artifact;
        } catch (IOException | IllegalArgumentException invalid) {
            throw failure("RULE_PACKAGE_CORRUPT", stage, expected, "Original rule package cannot be verified", invalid);
        }
    }

    private Path original(StoredMember member) {
        return originals.resolve(member.rawZipSha256() + ".zip");
    }

    private static byte[] readZip(Path path) throws IOException {
        ManagedCampaignFiles.file(path, false);
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(ProductionRuleArtifact.MAX_ZIP_BYTES + 1);
            if (bytes.length > ProductionRuleArtifact.MAX_ZIP_BYTES) throw new IOException("Rule ZIP exceeds budget");
            return bytes;
        }
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                ManagedCampaignFiles.privateAttribute(path, false))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private <T> T locked(String stage, StoredMember member, boolean create, Supplier<T> absent, Work<T> work) {
        if (!JVM_OWNERS.add(root))
            throw failure("RULE_LIBRARY_BUSY", stage, member, "Rule library is busy", null);
        try {
            if (!exists(root) && !create) return absent.get();
            if (create) {
                ManagedCampaignFiles.privateDirectory(root.getParent());
                try { ManagedCampaignFiles.ensureDirectory(root); }
                catch (FileAlreadyExistsException concurrent) { ManagedCampaignFiles.directory(root); }
            }
            ManagedCampaignFiles.privateDirectory(root);
            if (!exists(lockFile)) {
                if (exists(database) || exists(initialized) || exists(originals))
                    throw inconsistent(stage, member);
                if (!create) return absent.get();
            }
            ManagedCampaignFiles.file(lockFile, create);
            Set<OpenOption> options = create
                    ? Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, LinkOption.NOFOLLOW_LINKS)
                    : Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try (FileChannel channel = FileChannel.open(lockFile, options, ManagedCampaignFiles.privateAttribute(lockFile, false));
                 var lock = channel.tryLock()) {
                if (lock == null) throw failure("RULE_LIBRARY_BUSY", stage, member, "Rule library is busy", null);
                ManagedCampaignFiles.file(lockFile, false);
                if (create) bootstrap(member);
                if (!exists(initialized)) {
                    if (exists(database)) throw inconsistent(stage, member);
                    return absent.get();
                }
                ManagedCampaignFiles.privateDirectory(initialized);
                ManagedCampaignFiles.privateDirectory(originals);
                if (!exists(database)) throw inconsistent(stage, member);
                return work.run();
            } catch (OverlappingFileLockException busy) {
                throw failure("RULE_LIBRARY_BUSY", stage, member, "Rule library is busy", busy);
            }
        } catch (IOException | SQLException | IllegalArgumentException | UnsupportedOperationException unavailable) {
            throw failure("RULE_LIBRARY_UNAVAILABLE", stage, member, "Rule library cannot be accessed safely", unavailable);
        } finally {
            JVM_OWNERS.remove(root);
        }
    }

    private void bootstrap(StoredMember member) throws IOException, SQLException {
        if (exists(initialized)) {
            if (!exists(database)) throw inconsistent("BOOTSTRAP", member);
            return;
        }
        if (exists(originals)) {
            ManagedCampaignFiles.privateDirectory(originals);
            try (var entries = Files.list(originals)) {
                if (entries.findAny().isPresent()) throw inconsistent("BOOTSTRAP", member);
            }
        }
        if (!exists(database)) {
            writeNew(database, new byte[0]);
            ManagedCampaignFiles.forceDirectory(root);
            faults.at(Boundary.AFTER_LEDGER_FILE_CREATE);
        }
        transact("BOOTSTRAP", member, connection -> {
            if (ledger.emptySchema(connection)) ledger.initialize(connection);
            else ledger.verify(connection);
            return null;
        }, false);
        faults.at(Boundary.AFTER_LEDGER_SCHEMA_COMMIT);
        try (Connection connection = connect(true, false)) { ledger.verify(connection); }
        ManagedCampaignFiles.ensureDirectory(originals);
        ManagedCampaignFiles.createDirectory(initialized);
        ManagedCampaignFiles.forceDirectory(root);
    }

    private Connection connect(boolean readOnly) throws IOException, SQLException {
        return connect(readOnly, true);
    }

    private <T> T query(SqlWork<T> work) throws IOException, SQLException {
        try (Connection connection = connect(true)) {
            ledger.beginRead(connection);
            T result = work.run(connection);
            ledger.commit(connection);
            return result;
        }
    }

    private Connection connect(boolean readOnly, boolean verify) throws IOException, SQLException {
        ManagedCampaignFiles.file(database, false);
        ManagedCampaignFiles.file(root.resolve("ledger.db-journal"), true);
        if (exists(root.resolve("ledger.db-wal")) || exists(root.resolve("ledger.db-shm")))
            throw new IOException("Unexpected rule-library SQLite sidecar");
        Connection connection = ledger.connect(database, readOnly);
        try {
            if (verify) ledger.verify(connection);
            return connection;
        } catch (SQLException | RuntimeException failure) {
            try { connection.close(); }
            catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    private <T> T transact(String stage, StoredMember member, SqlWork<T> work) throws IOException, SQLException {
        return transact(stage, member, work, true);
    }

    private <T> T transact(String stage, StoredMember member, SqlWork<T> work, boolean verify) throws IOException, SQLException {
        boolean committing = false;
        try (Connection connection = connect(false, verify)) {
            ledger.begin(connection);
            try {
                T result = work.run(connection);
                committing = true;
                ledger.commit(connection);
                return result;
            } catch (IOException | SQLException | RuntimeException failure) {
                if (committing) throw unknown(stage, member, failure);
                try { ledger.rollback(connection); }
                catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                    throw unknown(stage, member, failure);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            if (committing) throw unknown(stage, member, failure);
            throw failure;
        }
    }

    private static StoredMember member(ProductionRuleArtifact artifact) {
        return new StoredMember(artifact.moduleKey(), artifact.releaseVersion(), artifact.role(),
                artifact.rawZipSha256(), artifact.logicalSha256());
    }

    private static void requireMember(StoredMember member, String stage) {
        if (!validMember(member))
            throw failure("RULE_BINDING_INVALID", stage, null, "Exact rule member is invalid", null);
    }

    private static boolean validMember(StoredMember member) {
        return member != null && validKey(member.moduleKey(), member.releaseVersion()) && member.role() != null
                && digest(member.rawZipSha256()) && digest(member.logicalSha256());
    }

    private static void requireKey(String key, String version, String stage) {
        if (!validKey(key, version)) throw failure("RULE_BINDING_INVALID", stage, null, "Rule identity is invalid", null);
    }

    private static boolean validKey(String key, String version) {
        return key != null && key.matches("[a-z][a-z0-9_]{0,63}") && version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private static boolean validActor(String actor) { return actor != null && actor.matches("[a-z][a-z0-9_-]{0,63}"); }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }

    private static String operationId(String id, String stage) {
        if (!validOperationId(id)) throw failure("RULE_OPERATION_INVALID", stage, null, "Operation ID must be a UUIDv4", null);
        return id;
    }

    private static boolean validOperationId(String id) {
        try { CampaignJson.uuid(id); return true; }
        catch (StorageException invalid) { return false; }
    }

    private static OperationStatus notFound(String id) {
        return new OperationStatus(id, null, OperationState.NOT_FOUND, null, null, null);
    }

    private static OperationStatus status(Operation operation) {
        return new OperationStatus(operation.intent().operationId(), operation.intent().kind().name(),
                OperationState.valueOf(operation.phase().name()), operation.intent().member(), null, null);
    }

    private static LocalRuleLibraryException blocked(String stage, Operation pending) {
        return new LocalRuleLibraryException("RULE_LIBRARY_BLOCKED", "Continue the original rule-library operation",
                issue(stage, pending.intent().member()), null, pending.intent().operationId());
    }

    private static LocalRuleLibraryException missing(String stage, StoredMember member) {
        return failure("RULE_PACKAGE_MISSING", stage, member, "Exact original rule package is missing", null);
    }

    private static LocalRuleLibraryException identityConflict(String stage, StoredMember member) {
        return failure("RULE_PACKAGE_IDENTITY_CONFLICT", stage, member, "Rule version is already bound to different material", null);
    }

    private static LocalRuleLibraryException inconsistent(String stage, StoredMember member) {
        return failure("RULE_LIBRARY_INTEGRITY", stage, member, "Rule-library evidence is inconsistent", null);
    }

    private static LocalRuleLibraryException unknown(String stage, StoredMember member, Throwable cause) {
        return failure("RULE_LIBRARY_COMMIT_UNKNOWN", stage, member, "Verify the original rule-library operation", cause);
    }

    private static LocalRuleLibraryException failure(String code, String stage, StoredMember member, String message, Throwable cause) {
        return new LocalRuleLibraryException(code, message, issue(stage, member), cause);
    }

    private static LocalRuleLibraryException.Issue issue(String stage, StoredMember member) {
        return new LocalRuleLibraryException.Issue(stage,
                member == null ? null : member.moduleKey(), member == null ? null : member.releaseVersion(),
                member == null ? null : member.rawZipSha256(), member == null ? null : member.logicalSha256());
    }
}
