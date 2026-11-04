package com.dndtool.service.storage;

import com.dndtool.persistence.storage.*;
import com.dndtool.persistence.storage.CampaignRepository.*;
import com.dndtool.service.Schema2CharacterHealthRules;
import com.google.gson.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.dndtool.service.storage.CampaignData.*;

/** One-process finite Host: one private SQLite authority, complete JSON checkpoints and explicit recovery. */
public final class CampaignStorageService implements AutoCloseable {
    /** Internal verified bridge. Never serialize this record into a player response. */
    public record AccessSnapshot(Snapshot snapshot, String runId, long epoch, long revision, JsonObject localControl) {
        public AccessSnapshot { localControl = localControl.deepCopy(); }
        @Override public JsonObject localControl() { return localControl.deepCopy(); }
    }
    @FunctionalInterface public interface CommandGuard { void validate(AccessSnapshot current); }
    public record Status(String state, long epoch, String runId, List<String> residualRunIds, List<String> saveFiles) { }
    public record CharacterView(String id, String name, long maximumHp, long currentHp, long version) { }
    public record EventView(long sequence, String type, String characterId, long beforeHp, long afterHp,
                            long amount, long applied, String name, long maximumHp) { }
    public record CommandResult(String requestId, boolean replayed, long acceptedRevision, long sequence,
                                String characterId, long beforeHp, long afterHp) { }
    public record CampaignView(String campaignId, String name, long revision, long epoch, List<String> rules,
                               List<CharacterView> characters, List<EventView> history, CommandResult lastCommand, JsonObject domain) { }
    public record SaveResult(String fileKey, long revision, String sha256) { }
    public record ImportResult(String fileKey, String name, long eventTail, String sha256) { }
    public record SavedCampaign(String fileKey, String name, long eventTail) { }
    public enum Boundary { AFTER_PREPARATION_DIRECTORY, AFTER_PREPARATION_LOCK, AFTER_INPUT_STAGING_CREATE, DURING_INPUT_WRITE, AFTER_INPUT_LINK, AFTER_INPUT_WRITE,
                           AFTER_ALLOCATION_STAGING_CREATE, DURING_ALLOCATION_WRITE, AFTER_ALLOCATION_LINK, AFTER_ALLOCATION_WRITE, AFTER_PREPARATION_PUBLISH, AFTER_DATABASE_PREPARATION, AFTER_DDL, AFTER_INIT_COMMIT, AFTER_CHARACTER_UPDATE, AFTER_BUSINESS_COMMIT,
                           AFTER_P0, AFTER_TEMP, AFTER_P2, BEFORE_P3_COMMIT, AFTER_P3_COMMIT,
                           AFTER_IMPORT_TEMP, AFTER_IMPORT_PUBLISH, AFTER_DISCARD_ISOLATION }
    @FunctionalInterface public interface FaultInjector { void at(Boundary boundary); }
    private static final FaultInjector NO_FAULTS = boundary -> { };
    private static final Set<String> RUN_FILES = Set.of("run.db", "run.db-journal", "run.lock", "allocation.json", "input.json", "initialized.json", "database-started.json", "cleanup.json");
    private static final Set<String> DISCARD_FILES = Set.of("run.db", "run.db-journal", "run.lock", "allocation.json", "input.json", "initialized.json", "database-started.json", "cleanup.json", "discard.json");
    private static final Set<Path> JVM_ROOT_OWNERS = ConcurrentHashMap.newKeySet();
    private record OwnedLock(FileChannel channel, FileLock lock) { }
    private final Path root, runs, saves;
    private final FileChannel instanceChannel;
    private final FileLock instanceLock;
    private final CampaignRepository repository = new CampaignRepository();
    private final CampaignCodec codec = new CampaignCodec();
    private final StorageSchemaCatalog catalog;
    private final FaultInjector faults;
    private Active active;
    private boolean closed;

    private static final class Active implements AutoCloseable {
        final String runId; final Path directory, database; final FileChannel lockChannel; final FileLock lock;
        StoragePlan plan; String blocked; Snapshot initialInput;
        Active(String id, Path directory, FileChannel channel, FileLock lock, StoragePlan plan) {
            runId = id; this.directory = directory; database = directory.resolve("run.db"); lockChannel = channel; this.lock = lock; this.plan = plan;
        }
        public void close() throws IOException { try { lock.release(); } finally { lockChannel.close(); } }
    }

    public CampaignStorageService(Path root) throws IOException { this(root, NO_FAULTS); }
    public CampaignStorageService(Path root, FaultInjector faults) throws IOException {
        if (root == null || !root.isAbsolute()) throw new IOException("Managed storage root must be absolute");
        if (ManagedCampaignFiles.windows() && root.getRoot().toString().startsWith("\\\\"))
            throw new IOException("Managed storage requires a local Windows drive; UNC roots are unsupported");
        for (Path ancestor = root.normalize(); ancestor != null; ancestor = ancestor.getParent()) {
            if (Files.exists(ancestor.resolve(".git"), LinkOption.NOFOLLOW_LINKS) || Files.exists(ancestor.resolve("WEB-INF"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Managed storage must be outside Git and deployed application trees: " + ancestor);
        }
        this.root = root.toAbsolutePath().normalize(); this.faults = Objects.requireNonNull(faults);
        ensureDirectory(this.root); ManagedCampaignFiles.privateDirectory(this.root); runs = this.root.resolve("runs"); saves = this.root.resolve("saves");
        ensureDirectory(runs); ensureDirectory(saves);
        for (Path privateDirectory : List.of(this.root, runs, saves)) ManagedCampaignFiles.privateDirectory(privateDirectory);
        OwnedLock ownership = acquireRootLock(this.root);
        instanceChannel = ownership.channel(); instanceLock = ownership.lock();
        try { catalog = StorageSchemaCatalog.bundled(); }
        catch (RuntimeException | Error e) {
            try { releaseRootLock(this.root, ownership); } catch (IOException | RuntimeException | Error release) { e.addSuppressed(release); }
            throw e;
        }
    }

    public synchronized List<CampaignRules.RuleModule> catalog() { ensureOpen(); return CampaignRules.CATALOG; }
    public synchronized List<SavedCampaign> savedCampaigns() {
        ensureOpen();
        try {
            List<SavedCampaign> result = new ArrayList<>();
            for (String key : saveFiles()) {
                Document document = codec.decode(readBounded(checkedSavePath(key)));
                if (!key.equals(document.fileKey())) throw new StorageException("STORAGE_INTEGRITY", "Saved campaign file identity differs");
                result.add(new SavedCampaign(key, document.snapshot().name(), document.snapshot().history().size()));
            }
            result.sort(Comparator.comparing(SavedCampaign::name).thenComparingLong(SavedCampaign::eventTail).thenComparing(SavedCampaign::fileKey));
            return List.copyOf(result);
        } catch (IOException e) { throw io("Cannot inspect saved campaigns", e); }
    }
    public synchronized Status status() {
        ensureOpen();
        try {
            List<String> residual = residualRuns(); String state = active == null ? (residual.isEmpty() ? "IDLE" : "RESIDUAL") : active.blocked == null ? "ACTIVE" : "BLOCKED";
            long epoch = 0; String id = active == null ? "" : active.runId;
            if (active != null) {
                try (Connection c = connect(active.database)) {
                    Control control = repository.control(c); epoch = control.epoch(); if (control.sealed()) state = "SEALED";
                } catch (SQLException | RuntimeException e) { active.blocked = "INTEGRITY"; state = "BLOCKED"; }
            }
            return new Status(state, epoch, id, residual, saveFiles());
        } catch (IOException e) { throw io("Cannot inspect managed storage", e); }
    }

    public synchronized CampaignView current() {
        ensureActive(false);
        try (Connection c = connect(active.database)) { return view(c); }
        catch (SQLException e) { active.blocked = "INTEGRITY"; throw database(e); }
        catch (RuntimeException e) { active.blocked = "INTEGRITY"; throw e; }
    }

    public synchronized CampaignView create(String name, List<String> ruleIds) {
        ensureIdle();
        List<String> selected = CampaignRules.validate(ruleIds);
        Snapshot input = CampaignData.validate(new Snapshot(newId(), CampaignJson.name(name), selected, List.of(), List.of(),
                selected.contains(CampaignRules.RUNTIME) ? CampaignDomainData.State.initial(BundledCampaignRules.projection()) : null));
        return initialize(input);
    }

    public synchronized CampaignView open(String fileKey) {
        ensureIdle(); CampaignJson.uuid(fileKey);
        try {
            Path file = checkedSavePath(fileKey); Document input = codec.decode(readBounded(file));
            if (!input.fileKey().equals(fileKey)) throw new StorageException("INVALID_FILE", "Filename and portable file identity differ");
            return initialize(input.snapshot());
        } catch (IOException e) { throw io("Cannot read the selected campaign file", e); }
    }

    /** Imports an independently verified portable file without allocating or activating a run. */
    public synchronized ImportResult importFile(byte[] supplied) {
        ensureIdle();
        if (supplied == null || supplied.length == 0 || supplied.length > CampaignJson.MAX_BYTES) throw CampaignJson.bad("Campaign file size is outside the supported budget");
        byte[] bytes = supplied.clone();
        Document document = codec.decode(bytes); // Complete validation precedes the first filesystem write.
        Path target = saves.resolve(document.fileKey() + ".json"), temporary = saves.resolve(document.fileKey() + ".tmp");
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (matchesImportedCandidate(target, bytes, document)) removeVerifiedImportStaging(temporary, bytes, document);
                // Cleanup acknowledges an existing artifact; it never claims a second import succeeded.
                throw new StorageException("FILE_CONFLICT", "A saved file already owns this portable file identity");
            }
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                verifyImportedCandidate(temporary, bytes, document);
            } else writeNewForced(temporary, bytes);
            verifyImportedCandidate(temporary, bytes, document); faults.at(Boundary.AFTER_IMPORT_TEMP);
            try { Files.createLink(target, temporary); }
            catch (FileAlreadyExistsException e) { throw new StorageException("FILE_CONFLICT", "A saved file already owns this portable file identity", e); }
            forceDirectory(saves);
            verifyImportedCandidate(target, bytes, document); faults.at(Boundary.AFTER_IMPORT_PUBLISH);
            // Only the exact, independently verified staging file belongs to this import.
            verifyImportedCandidate(temporary, bytes, document); Files.delete(temporary); forceDirectory(saves);
            return new ImportResult(document.fileKey(), document.snapshot().name(), document.snapshot().history().size(), CampaignJson.sha256(bytes));
        } catch (IOException e) { throw io("Import did not complete; any published file or exact staging material was retained", e); }
    }

    private void verifyImportedCandidate(Path path, byte[] bytes, Document expected) throws IOException {
        byte[] actual = readBounded(path);
        if (!Arrays.equals(bytes, actual) || !expected.equals(codec.decode(actual))) throw new StorageException("FILE_CONFLICT", "Existing import staging bytes belong to another file");
    }

    private boolean matchesImportedCandidate(Path path, byte[] bytes, Document expected) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.size() != bytes.length) return false;
        byte[] actual = readBounded(path);
        return Arrays.equals(bytes, actual) && expected.equals(codec.decode(actual));
    }

    private void removeVerifiedImportStaging(Path temporary, byte[] bytes, Document expected) throws IOException {
        if (matchesImportedCandidate(temporary, bytes, expected)) { Files.delete(temporary); forceDirectory(saves); }
    }

    /** Removes one managed save only after all local run references have been resolved. */
    public synchronized void deleteSavedFile(String fileKey) {
        ensureIdle(); CampaignJson.uuid(fileKey);
        try {
            Path file = checkedSavePath(fileKey);
            byte[] bytes = readBounded(file); Document document = codec.decode(bytes);
            if (!document.fileKey().equals(fileKey)) throw new StorageException("INVALID_FILE", "Filename differs from the portable file identity");
            // Remove the verified staging name first: interruption must retain the complete artifact.
            removeVerifiedImportStaging(saves.resolve(fileKey + ".tmp"), bytes, document);
            Files.delete(file); forceDirectory(saves);
        } catch (IOException e) { throw io("Cannot delete the exact saved file", e); }
    }

    /** Explicitly abandons only an identified, inactive local run; saved artifacts are retained. */
    public synchronized void discardResidual(String runId) {
        ensureOpen(); CampaignJson.uuid(runId);
        if (active != null && (!active.runId.equals(runId) || active.blocked == null)) throw new StorageException("RUN_EXISTS", "A healthy or different active run cannot be abandoned as a residual");
        Path original = runs.resolve(runId), isolated = runs.resolve(".discard-" + runId);
        Active locked = null;
        try {
            Preparation preparation = findPreparation(runId);
            if (preparation != null) { discardPreparation(preparation); return; }
            boolean originalExists = Files.exists(original, LinkOption.NOFOLLOW_LINKS), isolatedExists = Files.exists(isolated, LinkOption.NOFOLLOW_LINKS);
            if (originalExists && isolatedExists) throw new StorageException("CLEANUP_BLOCKED", "Both original and discard directories exist; neither was changed");
            if (!originalExists && !isolatedExists) throw new StorageException("RUN_NOT_FOUND", "The exact residual run does not exist");
            Path directory = originalExists ? original : isolated;
            verifyDiscardFiles(directory);
            if (originalExists) discardAuthorization(runId, directory); // Establish ownership before creating a missing lock.
            // An empty isolated tombstone is the final interrupted unlink, never a new allocation.
            if (isolatedExists && isEmptyDirectory(directory)) { Files.delete(directory); forceDirectory(runs); return; }
            if (active != null) { locked = active; active = null; }
            else locked = lockRun(runId, directory, null);
            verifyDiscardFiles(directory);
            Path markerPath = directory.resolve("discard.json");
            JsonObject marker;
            if (originalExists) {
                marker = discardAuthorization(runId, directory);
                if (Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Arrays.equals(readBounded(markerPath), CampaignJson.canonical(marker))) throw new StorageException("STORAGE_INTEGRITY", "Discard marker differs from the exact allocation");
                } else { writeNewForced(markerPath, CampaignJson.canonical(marker)); forceDirectory(directory); }
                locked.close(); locked = null;
                ManagedCampaignFiles.moveDirectory(directory, isolated); forceDirectory(runs); directory = isolated;
                locked = lockRun(runId, directory, null);
                faults.at(Boundary.AFTER_DISCARD_ISOLATION);
            } else {
                marker = CampaignJson.read(readBounded(markerPath));
                validateDiscardMarker(runId, marker);
                verifyDiscardProgress(runId, directory, marker);
            }
            verifyDiscardFiles(directory); // Inspect every entry before removing any run material.
            for (String name : List.of("run.db-journal", "run.db", "initialized.json", "database-started.json", "cleanup.json", "input.json", "allocation.json")) {
                Path file = directory.resolve(name);
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { rejectUnsafeFile(file, false); Files.delete(file); }
            }
            forceDirectory(directory); locked.close(); locked = null;
            Files.deleteIfExists(directory.resolve("run.lock"));
            Files.delete(directory.resolve("discard.json")); forceDirectory(directory);
            Files.delete(directory); forceDirectory(runs);
        } catch (IOException e) { throw new StorageException("CLEANUP_BLOCKED", "Exact residual abandonment remains pending; unrecognized material was retained", e); }
        finally {
            if (locked != null) try { locked.close(); }
            catch (IOException e) { throw io("Cannot release the residual discard lock", e); }
        }
    }

    private JsonObject discardAuthorization(String runId, Path directory) throws IOException {
        byte[] allocationBytes = readBounded(directory.resolve("allocation.json"));
        JsonObject allocation = CampaignJson.read(allocationBytes);
        CampaignJson.fields(allocation, "runId", "applicationModel", "storageModel", "planDigest", "inputDigest", "modules");
        if (!CampaignJson.string(allocation, "runId").equals(runId) || !CampaignJson.string(allocation, "applicationModel").equals(CampaignCodec.APPLICATION_MODEL)
                || !CampaignJson.string(allocation, "storageModel").equals(CampaignCodec.STORAGE_MODEL)) throw new StorageException("UNSUPPORTED_RUN", "Residual allocation does not belong to this application and run");
        byte[] input = readBounded(directory.resolve("input.json"));
        if (!CampaignJson.sha256(input).equals(CampaignJson.string(allocation, "inputDigest"))) throw new StorageException("STORAGE_INTEGRITY", "Residual input differs from its allocation");
        StoragePlan plan = catalog.plan(CampaignRules.roots(codec.decode(input).snapshot().rules()));
        JsonArray modules = new JsonArray(); plan.moduleIds().forEach(modules::add);
        if (!plan.digest().equals(CampaignJson.string(allocation, "planDigest")) || !modules.equals(CampaignJson.array(allocation, "modules"))) throw new StorageException("STORAGE_INTEGRITY", "Residual allocation plan differs");
        JsonObject marker = new JsonObject(); marker.addProperty("runId", runId); marker.addProperty("applicationModel", CampaignCodec.APPLICATION_MODEL);
        marker.addProperty("storageModel", CampaignCodec.STORAGE_MODEL); marker.addProperty("planDigest", plan.digest());
        marker.addProperty("allocationDigest", CampaignJson.sha256(allocationBytes)); marker.addProperty("inputDigest", CampaignJson.sha256(input));
        return marker;
    }

    private static void validateDiscardMarker(String runId, JsonObject marker) {
        CampaignJson.fields(marker, "runId", "applicationModel", "storageModel", "planDigest", "allocationDigest", "inputDigest");
        if (!CampaignJson.string(marker, "runId").equals(runId) || !CampaignJson.string(marker, "applicationModel").equals(CampaignCodec.APPLICATION_MODEL)
                || !CampaignJson.string(marker, "storageModel").equals(CampaignCodec.STORAGE_MODEL)) throw new StorageException("UNSUPPORTED_RUN", "Discard marker does not belong to this application and run");
        for (String field : List.of("planDigest", "allocationDigest", "inputDigest")) if (!CampaignJson.string(marker, field).matches("[0-9a-f]{64}")) throw new StorageException("STORAGE_INTEGRITY", "Malformed discard binding");
    }

    private void verifyDiscardProgress(String runId, Path directory, JsonObject marker) throws IOException {
        boolean allocated = Files.exists(directory.resolve("allocation.json"), LinkOption.NOFOLLOW_LINKS);
        boolean input = Files.exists(directory.resolve("input.json"), LinkOption.NOFOLLOW_LINKS);
        if (allocated && input) {
            if (!Arrays.equals(CampaignJson.canonical(discardAuthorization(runId, directory)), CampaignJson.canonical(marker))) throw new StorageException("STORAGE_INTEGRITY", "Discard authorization differs from the surviving preparation");
            return;
        }
        // The deletion order removes runtime material before input, and allocation last.
        // A marker on its own cannot authorize removal of an unrelated live database.
        for (String file : List.of("run.db-journal", "run.db", "initialized.json", "database-started.json", "cleanup.json"))
            if (Files.exists(directory.resolve(file), LinkOption.NOFOLLOW_LINKS)) throw new StorageException("STORAGE_INTEGRITY", "Discard progress does not match the authorized deletion order");
        if (input) throw new StorageException("STORAGE_INTEGRITY", "Discard allocation disappeared before its input");
        if (allocated) {
            byte[] bytes = readBounded(directory.resolve("allocation.json"));
            JsonObject allocation = CampaignJson.read(bytes);
            CampaignJson.fields(allocation, "runId", "applicationModel", "storageModel", "planDigest", "inputDigest", "modules");
            for (String field : List.of("runId", "applicationModel", "storageModel", "planDigest", "inputDigest"))
                if (!CampaignJson.string(allocation, field).equals(CampaignJson.string(marker, field))) throw new StorageException("STORAGE_INTEGRITY", "Discard allocation identity differs");
            if (!CampaignJson.sha256(bytes).equals(CampaignJson.string(marker, "allocationDigest"))) throw new StorageException("STORAGE_INTEGRITY", "Discard allocation digest differs");
        }
    }

    private static void verifyDiscardFiles(Path directory) throws IOException {
        rejectUnsafeDirectory(directory);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!DISCARD_FILES.contains(file.getFileName().toString())) throw new StorageException("CLEANUP_BLOCKED", "Unknown content prevents exact residual abandonment");
                rejectUnsafeFile(file, false);
            }
        }
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) { return !stream.iterator().hasNext(); }
    }

    private static final Set<String> PREPARATION_FILES = Set.of("run.lock", "input.part", "input.json", "allocation.part", "allocation.json");
    private record Preparation(String runId, String inputDigest, String planDigest, int inputLength, Path directory) { }

    private static Preparation preparation(Path path) {
        String[] pieces = path.getFileName().toString().substring(".prepare-".length()).split("_", -1);
        if (pieces.length != 4 || !pieces[1].matches("[0-9a-f]{64}") || !pieces[2].matches("[0-9a-f]{64}") || !pieces[3].matches("[1-9][0-9]{0,7}"))
            throw new StorageException("STORAGE_INTEGRITY", "Unknown preparation directory identity");
        int length = Integer.parseInt(pieces[3]);
        if (length > CampaignJson.MAX_BYTES) throw new StorageException("STORAGE_INTEGRITY", "Preparation length exceeds budget");
        return new Preparation(CampaignJson.uuid(pieces[0]), pieces[1], pieces[2], length, path);
    }

    private JsonObject allocation(String runId, StoragePlan plan, String inputDigest) {
        JsonObject marker = new JsonObject(); marker.addProperty("runId", runId); marker.addProperty("applicationModel", CampaignCodec.APPLICATION_MODEL);
        marker.addProperty("storageModel", CampaignCodec.STORAGE_MODEL); marker.addProperty("planDigest", plan.digest()); marker.addProperty("inputDigest", inputDigest);
        JsonArray modules = new JsonArray(); plan.moduleIds().forEach(modules::add); marker.add("modules", modules); return marker;
    }

    private CampaignView initialize(Snapshot input) {
        StoragePlan plan = catalog.plan(CampaignRules.roots(input.rules()));
        String runId = newId(); byte[] inputBytes = codec.encode(new Document(runId, input));
        Path directory = runs.resolve(".prepare-" + runId + "_" + CampaignJson.sha256(inputBytes) + "_" + plan.digest() + "_" + inputBytes.length);
        // Atomic mkdir publishes the complete ownership binding before any preparation byte or DB exists.
        Active preparing = null;
        try {
            createPrivateDirectory(directory); forceDirectory(runs); faults.at(Boundary.AFTER_PREPARATION_DIRECTORY);
            preparing = lockRun(runId, directory, plan); faults.at(Boundary.AFTER_PREPARATION_LOCK);
            writePreparation(directory, "input", inputBytes, Boundary.DURING_INPUT_WRITE); faults.at(Boundary.AFTER_INPUT_WRITE);
            writePreparation(directory, "allocation", CampaignJson.canonical(allocation(runId, plan, CampaignJson.sha256(inputBytes))), Boundary.DURING_ALLOCATION_WRITE);
            faults.at(Boundary.AFTER_ALLOCATION_WRITE);
            preparing.close(); preparing = null;
            publishPreparation(preparation(directory));
            active = lockRun(runId, plan); active.initialInput = input; active.blocked = "PREPARING";
            faults.at(Boundary.AFTER_PREPARATION_PUBLISH);
            initializeDatabase(input); faults.at(Boundary.AFTER_INIT_COMMIT);
            verifyActive(); saveInternal(newId(), 0, "INITIAL"); active.blocked = null; return current();
        } catch (IOException e) { if (active != null) active.blocked = "PREPARING"; throw io("Initialization remains available for explicit recovery or exact abandonment", e); }
        catch (RuntimeException e) { if (active != null) active.blocked = "PREPARING"; throw e; }
        finally { if (preparing != null) try { preparing.close(); } catch (IOException e) { throw io("Cannot release preparation lock", e); } }
    }

    private void writePreparation(Path directory, String name, byte[] bytes, Boundary during) throws IOException {
        Path partial = directory.resolve(name + ".part");
        try (FileChannel channel = FileChannel.open(partial, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), ManagedCampaignFiles.privateAttribute(partial, false))) {
            faults.at(name.equals("input") ? Boundary.AFTER_INPUT_STAGING_CREATE : Boundary.AFTER_ALLOCATION_STAGING_CREATE);
            int split = bytes.length / 2; ByteBuffer first = ByteBuffer.wrap(bytes, 0, split);
            while (first.hasRemaining()) channel.write(first); channel.force(true); faults.at(during);
            ByteBuffer remainder = ByteBuffer.wrap(bytes, split, bytes.length - split);
            while (remainder.hasRemaining()) channel.write(remainder); channel.force(true);
        }
        if (!Arrays.equals(readBounded(partial), bytes)) throw new IOException("Preparation write differs");
        Files.createLink(directory.resolve(name + ".json"), partial); forceDirectory(directory);
        faults.at(name.equals("input") ? Boundary.AFTER_INPUT_LINK : Boundary.AFTER_ALLOCATION_LINK);
        Files.delete(partial); forceDirectory(directory);
    }

    private void verifyPreparation(Preparation prep) throws IOException {
        ManagedCampaignFiles.privateDirectory(prep.directory());
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(prep.directory())) {
            for (Path entry : entries) {
                if (!PREPARATION_FILES.contains(entry.getFileName().toString())) throw new StorageException("CLEANUP_BLOCKED", "Unknown material in pre-database preparation");
                rejectUnsafeFile(entry, false);
                if (Files.size(entry) > CampaignJson.MAX_BYTES) throw new StorageException("STORAGE_INTEGRITY", "Preparation entry exceeds budget");
                if (entry.getFileName().toString().equals("run.lock") && Files.size(entry) != 0) throw new StorageException("STORAGE_INTEGRITY", "Preparation lock contains unknown material");
            }
        }
        byte[] full = null;
        Path input = prep.directory().resolve("input.json"), partial = prep.directory().resolve("input.part");
        if (Files.exists(input, LinkOption.NOFOLLOW_LINKS)) { full = readBounded(input); validatePreparationInput(prep, full); }
        if (Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = readPreparationPartial(partial);
            if (bytes.length > prep.inputLength()) throw new StorageException("STORAGE_INTEGRITY", "Preparation input is oversized");
            if (full != null && !prefix(bytes, full)) throw new StorageException("STORAGE_INTEGRITY", "Staged input differs from its complete input");
            if (bytes.length == prep.inputLength()) { validatePreparationInput(prep, bytes); full = bytes; }
            else {
                JsonObject complete = null;
                try { complete = CampaignJson.read(bytes); } catch (StorageException incomplete) { /* Reserved partial bytes are not an accepted document. */ }
                if (complete != null) validatePreparationInput(prep, bytes);
                byte[] header = ("{\"applicationModel\":\"" + CampaignCodec.APPLICATION_MODEL + "\",").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (!prefix(Arrays.copyOf(bytes, Math.min(bytes.length, header.length)), header)) throw new StorageException("STORAGE_INTEGRITY", "Staged input does not belong to this preparation protocol");
            }
        }
        for (String name : List.of("allocation.json", "allocation.part")) {
            Path path = prep.directory().resolve(name);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
            if (full == null) throw new StorageException("STORAGE_INTEGRITY", "Allocation exists before a complete bound input");
            Snapshot snapshot = codec.decode(full).snapshot(); StoragePlan plan = catalog.plan(CampaignRules.roots(snapshot.rules()));
            byte[] expected = CampaignJson.canonical(allocation(prep.runId(), plan, prep.inputDigest()));
            byte[] actual = readPreparationPartial(path);
            if (!(name.endsWith(".part") ? prefix(actual, expected) : Arrays.equals(actual, expected))) throw new StorageException("STORAGE_INTEGRITY", "Allocation differs from its preparation binding");
        }
    }

    private static byte[] readPreparationPartial(Path path) throws IOException {
        rejectUnsafeFile(path, false);
        if (Files.size(path) == 0) return new byte[0];
        return readBounded(path);
    }
    private static boolean prefix(byte[] candidate, byte[] expected) {
        return candidate.length <= expected.length && Arrays.equals(candidate, Arrays.copyOf(expected, candidate.length));
    }
    private Snapshot validatePreparationInput(Preparation prep, byte[] bytes) {
        if (bytes.length != prep.inputLength() || !CampaignJson.sha256(bytes).equals(prep.inputDigest())) throw new StorageException("STORAGE_INTEGRITY", "Preparation input digest differs");
        Document document = codec.decode(bytes);
        if (!document.fileKey().equals(prep.runId())) throw new StorageException("STORAGE_INTEGRITY", "Preparation input identity differs");
        StoragePlan plan = catalog.plan(CampaignRules.roots(document.snapshot().rules()));
        if (!plan.digest().equals(prep.planDigest())) throw new StorageException("STORAGE_INTEGRITY", "Preparation plan identity differs");
        return document.snapshot();
    }
    private void publishPreparation(Preparation prep) throws IOException {
        verifyPreparation(prep);
        Path input = prep.directory().resolve("input.json"), partial = prep.directory().resolve("input.part");
        if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.exists(partial, LinkOption.NOFOLLOW_LINKS) || Files.size(partial) != prep.inputLength())
                throw new StorageException("PREPARATION_INCOMPLETE", "Original input was interrupted; explicitly abandon this exact preparation");
            validatePreparationInput(prep, readBounded(partial)); Files.createLink(input, partial); forceDirectory(prep.directory());
        }
        Snapshot snapshot = validatePreparationInput(prep, readBounded(input));
        StoragePlan plan = catalog.plan(CampaignRules.roots(snapshot.rules()));
        Path marker = prep.directory().resolve("allocation.json"), markerPartial = prep.directory().resolve("allocation.part");
        byte[] expected = CampaignJson.canonical(allocation(prep.runId(), plan, prep.inputDigest()));
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(markerPartial, LinkOption.NOFOLLOW_LINKS)) { // Exact prefix was verified before any mutation.
                byte[] bytes = readPreparationPartial(markerPartial);
                try (FileChannel c = FileChannel.open(markerPartial, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    c.position(bytes.length); ByteBuffer rest = ByteBuffer.wrap(expected, bytes.length, expected.length - bytes.length);
                    while (rest.hasRemaining()) c.write(rest); c.force(true);
                }
                Files.createLink(marker, markerPartial); forceDirectory(prep.directory());
            } else writePreparation(prep.directory(), "allocation", expected, Boundary.DURING_ALLOCATION_WRITE);
        }
        verifyPreparation(prep);
        Files.deleteIfExists(partial); Files.deleteIfExists(markerPartial); forceDirectory(prep.directory());
        ManagedCampaignFiles.moveDirectory(prep.directory(), runs.resolve(prep.runId())); forceDirectory(runs);
    }

    private Preparation findPreparation(String runId) throws IOException {
        Preparation found = null;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(runs, ".prepare-" + runId + "_*")) {
            for (Path entry : entries) { if (found != null) throw new StorageException("STORAGE_INTEGRITY", "Multiple preparations claim one run"); found = preparation(entry); }
        }
        if (found != null) for (String name : List.of(runId, ".cleanup-" + runId, ".discard-" + runId))
            if (Files.exists(runs.resolve(name), LinkOption.NOFOLLOW_LINKS)) throw new StorageException("STORAGE_INTEGRITY", "Published, preparing or cleanup run identities conflict");
        return found;
    }

    private void discardPreparation(Preparation prep) throws IOException {
        verifyPreparation(prep);
        try (Active ownership = lockRun(prep.runId(), prep.directory(), null)) {
            verifyPreparation(prep);
            // Fixed pre-DB slots only. No recursion; the directory binding survives every interrupted unlink.
            for (String name : List.of("allocation.part", "allocation.json", "input.part", "input.json")) Files.deleteIfExists(prep.directory().resolve(name));
            forceDirectory(prep.directory());
        }
        Files.deleteIfExists(prep.directory().resolve("run.lock")); Files.delete(prep.directory()); forceDirectory(runs);
    }

    private void initializeDatabase(Snapshot input) {
        try {
            Path started = active.directory.resolve("database-started.json");
            if (!Files.exists(started, LinkOption.NOFOLLOW_LINKS)) {
                writeNewForced(started, CampaignJson.canonical(allocation(active.runId, active.plan, CampaignJson.sha256(readBounded(active.directory.resolve("input.json"))))));
                forceDirectory(active.directory); faults.at(Boundary.AFTER_DATABASE_PREPARATION);
            }
        } catch (IOException e) { throw io("Database preparation did not complete", e); }
        transaction(c -> {
            active.plan.create(c); faults.at(Boundary.AFTER_DDL);
            repository.initialize(c, new Control(active.runId, input.campaignId(), input.name(), 0, nextEpoch(0), false, false, active.plan.digest()), active.plan.moduleIds(), input);
            active.plan.verify(c); repository.snapshot(c); return null;
        }, true);
        try {
            Path completed = active.directory.resolve("initialized.json");
            JsonObject marker = new JsonObject(); marker.addProperty("runId", active.runId); marker.addProperty("planDigest", active.plan.digest());
            writeNewForced(completed, CampaignJson.canonical(marker)); forceDirectory(active.directory);
        } catch (IOException e) { active.blocked = "UNKNOWN"; throw io("Initialization committed; its completion marker requires explicit recovery", e); }
    }

    public synchronized CampaignView addCharacter(String requestId, long epoch, long expectedRevision, String name, long maximumHp, long currentHp) {
        ensureActive(true); CampaignJson.uuid(requestId); CampaignJson.name(name); CampaignRules.hp(maximumHp, currentHp);
        String intent = intent("ADD_CHARACTER", expectedRevision, name, maximumHp, currentHp);
        CommandResult command = transaction(c -> {
            Snapshot before = verifiedSnapshot(c); Control control = repository.control(c);
            if (before.domains() != null) throw new StorageException("DOMAIN_COMMAND_REQUIRED", "Use the complete character creation command");
            if (control.epoch() != epoch) throw new StorageException("STALE_EPOCH", "Refresh the current Host epoch");
            CommandReceipt replay = replay(c, requestId, intent, "ADD_CHARACTER");
            if (replay != null) return result(replay, true);
            requireGate(c, control, epoch, expectedRevision);
            if (before.characters().size() >= 10000 || before.history().size() >= 100000) throw new StorageException("CAPACITY", "Campaign fact budget reached");
            String id = newId(); long sequence = before.history().size() + 1L;
            repository.insertCharacter(c, new CharacterRow(new CharacterFact(id, name, maximumHp, currentHp), currentHp, 1, sequence));
            faults.at(Boundary.AFTER_CHARACTER_UPDATE);
            EventFact event = new EventFact(sequence, "CHARACTER_CREATED", id, currentHp, currentHp, 0, 0, name, maximumHp);
            repository.insertEvent(c, event);
            repository.acceptCommand(c, requestId, intent, "ADD_CHARACTER", expectedRevision, event);
            codec.encode(new Document(newId(), repository.snapshot(c)));
            return result(repository.command(c, requestId), false);
        });
        afterBusinessCommit(!command.replayed()); return current(command);
    }

    public synchronized CampaignView heal(String requestId, long epoch, long expectedRevision, String characterId, long expectedCharacterVersion, long amount) {
        ensureActive(true); CampaignJson.uuid(requestId); CampaignJson.uuid(characterId);
        if (amount < 0 || amount > 65535) throw new StorageException("INVALID_AMOUNT", "Healing amount must be an integer between 0 and 65535");
        String intent = intent("HEAL", expectedRevision, characterId, expectedCharacterVersion, amount);
        CommandResult command = transaction(c -> {
            Snapshot before = verifiedSnapshot(c); Control control = repository.control(c);
            if (before.domains() != null) throw new StorageException("DOMAIN_COMMAND_REQUIRED", "Use the complete character health command");
            if (control.epoch() != epoch) throw new StorageException("STALE_EPOCH", "Refresh the current Host epoch");
            CommandReceipt replay = replay(c, requestId, intent, "HEAL");
            if (replay != null) return result(replay, true);
            requireGate(c, control, epoch, expectedRevision);
            if (!before.rules().contains(CampaignRules.HEALING)) throw new StorageException("RULE_NOT_SELECTED", "The exact healing module is not selected");
            if (before.history().size() >= 100000) throw new StorageException("CAPACITY", "History budget reached");
            CharacterRow row = repository.characters(c).stream().filter(r -> r.fact().id().equals(characterId)).findFirst().orElseThrow(() -> new StorageException("CHARACTER_NOT_FOUND", "Character does not exist in this run"));
            if (row.version() != expectedCharacterVersion) throw new StorageException("STALE_CHARACTER", "Character version changed");
            var resolution = Schema2CharacterHealthRules.heal(row.fact().currentHp(), row.fact().maximumHp(), amount);
            repository.updateCharacterHp(c, characterId, expectedCharacterVersion, resolution.after()); faults.at(Boundary.AFTER_CHARACTER_UPDATE);
            EventFact event = new EventFact(before.history().size() + 1L, "HEALED", characterId, resolution.before(), resolution.after(), resolution.requested(), resolution.applied(), "", 0);
            repository.insertEvent(c, event);
            repository.acceptCommand(c, requestId, intent, "HEAL", expectedRevision, event);
            codec.encode(new Document(newId(), repository.snapshot(c)));
            return result(repository.command(c, requestId), false);
        });
        afterBusinessCommit(!command.replayed()); return current(command);
    }

    public synchronized AccessSnapshot accessSnapshot() {
        ensureActive(true);
        try (Connection c = connect(active.database)) { return accessSnapshot(c); }
        catch (SQLException e) { active.blocked = "INTEGRITY"; throw database(e); }
    }
    private AccessSnapshot accessSnapshot(Connection c) throws SQLException {
        Snapshot snapshot = verifiedSnapshot(c); Control control = repository.control(c);
        if (control.sealed()) throw new StorageException("RUN_BLOCKED", "The run is sealed");
        return new AccessSnapshot(snapshot, active.runId, control.epoch(), control.revision(), localPlayerControl(c));
    }
    public synchronized JsonObject localPlayerControl() {
        ensureActive(false);
        try (Connection c = connect(active.database)) { return localPlayerControl(c); }
        catch (SQLException e) { throw database(e); }
    }
    private JsonObject localPlayerControl(Connection c) throws SQLException {
        try (Statement statement = c.createStatement(); ResultSet result = statement.executeQuery("SELECT singleton,document FROM core_player_control")) {
            if (!result.next() || result.getLong(1) != 1) throw new StorageException("STORAGE_INTEGRITY", "Missing local player control");
            byte[] bytes = result.getBytes(2); JsonObject control = CampaignJson.read(bytes);
            if (!Arrays.equals(bytes, CampaignJson.canonical(control)) || result.next()) throw new StorageException("STORAGE_INTEGRITY", "Invalid local player control");
            return control;
        }
    }
    /** Run-local security state. This does not advance game facts or enter a portable file. */
    public synchronized void updateLocalPlayerControl(JsonObject control) {
        ensureActive(true); if (control == null) throw CampaignJson.bad("Missing local player control");
        byte[] bytes = CampaignJson.canonical(control); if (bytes.length > 1024 * 1024) throw CampaignJson.bad("Local player control capacity exceeded");
        transaction(c -> {
            verifiedSnapshot(c);
            try (PreparedStatement statement = c.prepareStatement("UPDATE core_player_control SET document=? WHERE singleton=1")) {
                statement.setString(1, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                if (statement.executeUpdate() != 1) throw new StorageException("STORAGE_INTEGRITY", "Missing local player control");
            }
            return null;
        });
    }
    public synchronized CampaignView domainCommand(String requestId, long epoch, long expectedRevision, String action, JsonObject input) {
        return domainCommand(requestId, epoch, expectedRevision, action, input, null, current -> { });
    }
    /** Authorization runs in the same serialized transaction, before receipt lookup and all randomness. */
    public synchronized CampaignView domainCommand(String requestId, long epoch, long expectedRevision, String action, JsonObject supplied,
            CampaignParticipationData.Actor requestedActor, CommandGuard guard) {
        ensureActive(true); CampaignJson.uuid(requestId);
        if (action == null || supplied == null || guard == null) throw CampaignJson.bad("Missing domain command or guard");
        JsonObject input = supplied.deepCopy();
        CommandResult accepted = transaction(c -> {
            AccessSnapshot access = accessSnapshot(c); Snapshot before = access.snapshot(); Control control = repository.control(c);
            if (control.epoch() != epoch) throw new StorageException("STALE_EPOCH", "Refresh the current Host epoch");
            guard.validate(access);
            if (before.domains() == null) throw new StorageException("RULE_NOT_SELECTED", "Select the current campaign runtime module");
            CampaignParticipationData.Actor actor = requestedActor == null ? CampaignParticipationCommands.actualActor(before.domains(), action, input) : requestedActor;
            CampaignParticipationCommands.validateActor(before.domains(), action, input, actor);
            String intent = domainIntent(expectedRevision, action, input, actor);
            CommandReceipt prior = replay(c, requestId, intent, "DOMAIN_COMMAND");
            if (prior != null) return result(prior, true);
            requireGate(c, control, epoch, expectedRevision);
            CampaignDomainData.State domains;
            try {
                domains = new CampaignDomainCommands().apply(before, action, input, UUID.fromString(requestId), intent,
                        sides -> new SecureRandom().nextInt(sides) + 1, actor);
            } catch (IllegalArgumentException invalid) {
                throw new StorageException("DOMAIN_REJECTED", invalid.getMessage() == null ? "Invalid domain request" : invalid.getMessage(), invalid);
            }
            Snapshot after = CampaignData.validate(new Snapshot(before.campaignId(), before.name(), before.rules(), List.of(),
                    domains.roots().stream().map(CampaignDomainValidation::summary).toList(), domains));
            codec.encode(new Document(newId(), after));
            new CampaignDomainRepository().write(c, domains); faults.at(Boundary.AFTER_CHARACTER_UPDATE);
            repository.acceptCommand(c, requestId, intent, "DOMAIN_COMMAND", expectedRevision, CampaignDomainValidation.summary(domains.roots().getLast()));
            if (!after.equals(repository.snapshot(c))) throw new StorageException("STORAGE_INTEGRITY", "Domain transaction readback differs");
            return result(repository.command(c, requestId), false);
        });
        afterBusinessCommit(!accepted.replayed()); return current(accepted);
    }
    /** Exact read-only result verification. An absent result never proves non-submission. */
    public synchronized Optional<CampaignDomainData.Root> exactDomainReceipt(String requestId, String runId, long originalRevision,
            String action, JsonObject supplied, CampaignParticipationData.Actor actor, CommandGuard guard) {
        ensureActive(true); CampaignJson.uuid(requestId); CampaignJson.uuid(runId);
        if (supplied == null || actor == null || guard == null || action == null) throw CampaignJson.bad("Incomplete exact receipt identity");
        JsonObject input = supplied.deepCopy();
        try (Connection c = connect(active.database)) {
            AccessSnapshot access = accessSnapshot(c); if (!active.runId.equals(runId)) throw new StorageException("STALE_RUN", "Receipt belongs to a different run");
            guard.validate(access);
            CommandReceipt receipt = repository.command(c, requestId);
            if (receipt == null) return Optional.empty();
            String expected = domainIntent(originalRevision, action, input, actor);
            if (!receipt.kind().equals("DOMAIN_COMMAND") || !receipt.intent().equals(expected)) throw new StorageException("IDEMPOTENCY_CONFLICT", "Receipt does not match the exact original actor and intent");
            var root = access.snapshot().domains().roots().stream().filter(r -> r.operationId().toString().equals(requestId)).findFirst().orElseThrow(() -> new StorageException("STORAGE_INTEGRITY", "Receipt root is missing"));
            if (!root.actor().equals(actor) || !root.kind().equals(action) || !root.intent().equals(expected)) throw new StorageException("STORAGE_INTEGRITY", "Receipt actor differs from root");
            return Optional.of(root);
        } catch (SQLException e) { throw database(e); }
    }
    private static String domainIntent(long revision, String action, JsonObject input, CampaignParticipationData.Actor actor) {
        return intent("DOMAIN_COMMAND", revision, action, new String(CampaignJson.canonical(input), java.nio.charset.StandardCharsets.UTF_8), CampaignDomainCodec.text(actor));
    }

    private void afterBusinessCommit(boolean changed) {
        if (changed) try { faults.at(Boundary.AFTER_BUSINESS_COMMIT); }
        catch (RuntimeException e) { active.blocked = "UNKNOWN"; throw new StorageException("COMMIT_UNKNOWN", "The committed command requires explicit recovery before further writes", e); }
    }

    public synchronized SaveResult save(String requestId, long epoch, long expectedRevision) {
        ensureActive(false); CampaignJson.uuid(requestId); requireSaveEpoch(epoch);
        if (active.blocked != null && !active.blocked.equals("SAVE")) throw new StorageException("RUN_BLOCKED", "The run requires explicit recovery");
        return saveInternal(requestId, expectedRevision, "MANUAL");
    }

    public synchronized SaveResult end(String requestId, long epoch, long expectedRevision) {
        ensureActive(false); CampaignJson.uuid(requestId); requireSaveEpoch(epoch);
        if (active.blocked != null && !active.blocked.equals("SAVE")) throw new StorageException("RUN_BLOCKED", "The run requires explicit recovery");
        SaveResult result = saveInternal(requestId, expectedRevision, "FINAL");
        cleanupSealed(); return result;
    }

    public synchronized Path savedFile(String fileKey) {
        ensureOpen(); CampaignJson.uuid(fileKey);
        try {
            if (active == null && !residualRuns().isEmpty()) throw new StorageException("RESIDUAL_RUN", "Recover residual runs before downloading files whose acceptance is unknown");
            Path path = checkedSavePath(fileKey); Document document = codec.decode(readBounded(path));
            if (!document.fileKey().equals(fileKey)) throw new StorageException("INVALID_FILE", "Filename differs from the campaign file identity");
            if (active != null) try (Connection c = connect(active.database)) {
                for (SaveReceipt save : repository.saves(c)) if (save.fileKey().equals(fileKey) && !save.accepted()) throw new StorageException("SAVE_NOT_ACCEPTED", "This file is pending exact save acceptance");
            }
            return path;
        } catch (IOException e) { throw io("Cannot read the selected save", e); }
        catch (SQLException e) { throw database(e); }
    }

    private CampaignView current(CommandResult command) {
        try (Connection c = connect(active.database)) { return view(c, command); }
        catch (SQLException e) { active.blocked = "INTEGRITY"; throw database(e); }
        catch (RuntimeException e) { active.blocked = "INTEGRITY"; throw e; }
    }
    private CampaignView view(Connection c) throws SQLException { return view(c, null); }
    private CampaignView view(Connection c, CommandResult command) throws SQLException {
        Snapshot snapshot = verifiedSnapshot(c); Control control = repository.control(c);
        List<CharacterView> characters = repository.characters(c).stream().map(r -> new CharacterView(r.fact().id(), r.fact().name(), r.fact().maximumHp(), r.fact().currentHp(), r.version())).toList();
        return new CampaignView(snapshot.campaignId(), snapshot.name(), control.revision(), control.epoch(), snapshot.rules(), characters,
                snapshot.history().stream().map(e -> new EventView(e.sequence(), e.type(), e.characterId(), e.beforeHp(), e.afterHp(), e.amount(), e.applied(), e.name(), e.maximumHp())).toList(), command,
                domainView(snapshot.domains()));
    }

    private static JsonObject domainView(CampaignDomainData.State state) {
        if (state == null) return null;
        Gson json = new GsonBuilder().serializeNulls().disableHtmlEscaping().create(); JsonObject result = new JsonObject();
        JsonArray characters = new JsonArray();
        for (var c : state.characters()) {
            var current = c.history().current(); JsonObject card = new JsonObject();
            card.addProperty("id", c.id().toString()); card.addProperty("name", c.name()); card.addProperty("kind", c.kind()); card.addProperty("active", c.active());
            card.addProperty("version", Long.toString(current.version())); card.addProperty("level", current.level());
            card.addProperty("hitPoints", current.hitPoints()); card.addProperty("maximumHitPoints", current.maximumHitPoints());
            card.add("state", json.toJsonTree(current)); card.add("initial", json.toJsonTree(c.initial())); card.add("history", json.toJsonTree(c.history()));
            characters.add(card);
        }
        result.add("characters", characters); result.add("combats", json.toJsonTree(state.combats())); result.add("nodes", json.toJsonTree(state.nodes()));
        result.add("positions", json.toJsonTree(state.positions())); result.add("items", json.toJsonTree(state.items())); result.add("roots", json.toJsonTree(state.roots()));
        JsonObject manual = json.toJsonTree(state.manual()).getAsJsonObject();
        manual.add("catalog", json.toJsonTree(CampaignManualData.catalog())); result.add("manual", manual);
        result.add("participation", json.toJsonTree(state.participation()));
        result.add("rules", json.toJsonTree(BundledCampaignRules.metadata())); return result;
    }

    private Snapshot verifiedSnapshot(Connection c) throws SQLException {
        Control control = repository.control(c);
        if (!control.runId().equals(active.runId) || !control.schemaDigest().equals(active.plan.digest()) || !repository.modules(c).equals(active.plan.moduleIds())) throw new StorageException("STORAGE_INTEGRITY", "Run identity or structure-plan binding differs");
        Snapshot snapshot = repository.snapshot(c);
        if (active.initialInput != null) verifyInitialBinding(control, snapshot, active.initialInput);
        return snapshot;
    }
    private static void verifyInitialBinding(Control control, Snapshot snapshot, Snapshot initial) {
        int prefix = initial.history().size();
        if (!snapshot.campaignId().equals(initial.campaignId()) || !snapshot.name().equals(initial.name()) || !snapshot.rules().equals(initial.rules())
                || snapshot.history().size() < prefix || control.revision() != snapshot.history().size() - (long) prefix
                || !snapshot.history().subList(0, prefix).equals(initial.history())) throw new StorageException("STORAGE_INTEGRITY", "Current run differs from its exact initialization identity, baseline or history prefix");
        if ((snapshot.domains() == null) != (initial.domains() == null)) throw new StorageException("STORAGE_INTEGRITY", "Domain initialization profile changed");
        if (initial.domains() != null) {
            var current = snapshot.domains(); var origin = initial.domains();
            if (!current.material().equals(origin.material()) || current.roots().size() < origin.roots().size()
                    || !current.roots().subList(0, origin.roots().size()).equals(origin.roots())) throw new StorageException("STORAGE_INTEGRITY", "Imported domain root prefix changed");
            for (var old : origin.characters()) {
                var now = current.characters().stream().filter(c -> c.id().equals(old.id())).findFirst().orElseThrow(() -> new StorageException("STORAGE_INTEGRITY", "Imported character disappeared"));
                if (!now.initial().equals(old.initial()) || now.history().events().size() < old.history().events().size()
                        || !now.history().events().subList(0, old.history().events().size()).equals(old.history().events())) throw new StorageException("STORAGE_INTEGRITY", "Imported character facts changed");
            }
            for (var old : origin.combats()) {
                var now = current.combats().stream().filter(c -> c.current().combatId().equals(old.current().combatId())).findFirst().orElseThrow(() -> new StorageException("STORAGE_INTEGRITY", "Imported scene disappeared"));
                if (!now.members().equals(old.members()) || now.events().size() < old.events().size()
                        || !now.events().subList(0, old.events().size()).equals(old.events())) throw new StorageException("STORAGE_INTEGRITY", "Imported scene facts changed");
            }
            for (var old : origin.manual().characters()) {
                var now = current.manual().characters().stream().filter(c -> c.current().id().equals(old.current().id())).findFirst()
                        .orElseThrow(() -> new StorageException("STORAGE_INTEGRITY", "Imported free character disappeared"));
                if (!now.initial().equals(old.initial())) throw new StorageException("STORAGE_INTEGRITY", "Imported free character initial facts changed");
            }
        }
    }

    private CommandReceipt replay(Connection c, String requestId, String intent, String kind) throws SQLException {
        CommandReceipt receipt = repository.command(c, requestId);
        if (receipt == null) {
            if (repository.saves(c).stream().anyMatch(s -> s.requestId().equals(requestId))) throw new StorageException("IDEMPOTENCY_CONFLICT", "Request UUID is already used by a save");
            return null;
        }
        if (!receipt.intent().equals(intent) || !receipt.kind().equals(kind)) throw new StorageException("IDEMPOTENCY_CONFLICT", "Request UUID is already bound to another intent");
        return receipt;
    }
    private static CommandResult result(CommandReceipt receipt, boolean replayed) {
        return new CommandResult(receipt.requestId(), replayed, receipt.resultRevision(), receipt.sequence(), receipt.characterId(), receipt.beforeHp(), receipt.afterHp());
    }
    private void requireGate(Connection c, Control control, long epoch, long revision) throws SQLException {
        if (control.sealed() || !control.firstSaved()) throw new StorageException("RUN_BLOCKED", "The run has no execution qualification");
        if (control.epoch() != epoch) throw new StorageException("STALE_EPOCH", "Refresh the current Host epoch");
        if (control.revision() != revision) throw new StorageException("STALE_REVISION", "Refresh the campaign revision");
        if (repository.saves(c).stream().anyMatch(s -> !s.accepted())) throw new StorageException("SAVE_PENDING", "Complete the exact pending save before changing game facts");
    }

    private static String intent(String action, long revision, Object... values) {
        if (revision < 0) throw new StorageException("STALE_REVISION", "Revision must be nonnegative");
        JsonArray a = new JsonArray(); a.add(action); a.add(Long.toString(revision));
        for (Object value : values) a.add(value.toString());
        return CampaignJson.digest("DND-VITALITY-COMMAND", CampaignJson.canonical(a));
    }

    // Saving, recovery and the filesystem/transaction boundary are below; no leaf commits.
    private SaveResult saveInternal(String requestId, long expectedRevision, String purpose) {
        String saveIntent = intent("SAVE_" + purpose, expectedRevision);
        SaveReceipt receipt = transaction(c -> {
            Snapshot snapshot = verifiedSnapshot(c); Control control = repository.control(c);
            if (repository.command(c, requestId) != null) throw new StorageException("IDEMPOTENCY_CONFLICT", "Request UUID is already a business command");
            List<SaveReceipt> receipts = repository.saves(c);
            SaveReceipt existing = receipts.stream().filter(s -> s.requestId().equals(requestId)).findFirst().orElse(null);
            if (existing != null) {
                if (!existing.intent().equals(saveIntent) || !existing.purpose().equals(purpose)) throw new StorageException("IDEMPOTENCY_CONFLICT", "Save request UUID is bound to another intent");
                return existing;
            }
            if (control.sealed()) throw new StorageException("RUN_SEALED", "The run is sealed");
            if (control.revision() != expectedRevision) throw new StorageException("STALE_REVISION", "Refresh the campaign revision");
            if (receipts.stream().anyMatch(s -> !s.accepted())) throw new StorageException("SAVE_PENDING", "Resume the exact pending save request UUID");
            String fileKey = newId(); byte[] bytes = codec.encode(new Document(fileKey, snapshot));
            SaveReceipt result = new SaveReceipt(requestId, saveIntent, fileKey, expectedRevision, CampaignJson.sha256(bytes), bytes.length, purpose, false);
            repository.registerSave(c, result); return result;
        });
        if (receipt.accepted()) { verifyActive(); active.blocked = null; return new SaveResult(receipt.fileKey(), receipt.revision(), receipt.rawDigest()); }
        active.blocked = "SAVE";
        try {
            faults.at(Boundary.AFTER_P0);
            Snapshot source;
            try (Connection c = connect(active.database)) {
                source = verifiedSnapshot(c);
                if (repository.control(c).revision() != receipt.revision()) throw new StorageException("STORAGE_INTEGRITY", "Pending save source revision changed");
            }
            byte[] bytes = codec.encode(new Document(receipt.fileKey(), source));
            if (bytes.length != receipt.byteSize() || !CampaignJson.sha256(bytes).equals(receipt.rawDigest())) throw new StorageException("STORAGE_INTEGRITY", "Pending save source differs from its exact P0 intent");
            Path target = saves.resolve(receipt.fileKey() + ".json"), temporary = saves.resolve(receipt.fileKey() + ".tmp");
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                    rejectUnsafeFile(temporary, false);
                    if (!Arrays.equals(readBounded(temporary), bytes)) { Files.delete(temporary); forceDirectory(saves); }
                }
                if (!Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) writeNewForced(temporary, bytes);
                verifyCandidate(temporary, receipt, source); faults.at(Boundary.AFTER_TEMP);
                // Creating a same-directory hard link is atomic and fails if any target already exists.
                // ATOMIC_MOVE is deliberately not used: POSIX providers may replace an existing target.
                Files.createLink(target, temporary); forceDirectory(saves);
            }
            verifyCandidate(target, receipt, source); faults.at(Boundary.AFTER_P2);
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                rejectUnsafeFile(temporary, false);
                if (!Files.isSameFile(target, temporary) && !Arrays.equals(readBounded(temporary), bytes)) throw new StorageException("STORAGE_INTEGRITY", "Save temporary file has another owner");
                Files.delete(temporary); forceDirectory(saves);
            }
            transaction(c -> {
                Snapshot current = verifiedSnapshot(c);
                if (repository.control(c).revision() != receipt.revision() || !source.equals(current)) throw new StorageException("STORAGE_INTEGRITY", "P3 source is not the exact P0 source");
                repository.acceptSave(c, requestId, purpose.equals("FINAL")); faults.at(Boundary.BEFORE_P3_COMMIT); return null;
            });
            faults.at(Boundary.AFTER_P3_COMMIT); active.blocked = null;
            return new SaveResult(receipt.fileKey(), receipt.revision(), receipt.rawDigest());
        } catch (IOException e) {
            throw new StorageException("SAVE_PENDING", "The exact save intent remains pending; its source run is retained", e);
        } catch (SQLException e) {
            active.blocked = "UNKNOWN"; throw database(e);
        } catch (RuntimeException e) {
            if (active.blocked == null) active.blocked = "UNKNOWN";
            throw e;
        }
    }

    private void verifyCandidate(Path path, SaveReceipt receipt, Snapshot source) throws IOException {
        byte[] bytes = readBounded(path);
        if (bytes.length != receipt.byteSize() || !CampaignJson.sha256(bytes).equals(receipt.rawDigest())) throw new StorageException("STORAGE_INTEGRITY", "Candidate bytes differ from the registered save");
        Document decoded = codec.decode(bytes);
        if (!decoded.fileKey().equals(receipt.fileKey()) || !decoded.snapshot().equals(source)) throw new StorageException("STORAGE_INTEGRITY", "Independent candidate decoding differs from the complete source");
    }

    public synchronized CampaignView recover(String runId) {
        ensureOpen(); CampaignJson.uuid(runId);
        if (active != null && (!active.runId.equals(runId) || active.blocked == null)) throw new StorageException("RUN_EXISTS", "Another run already owns this Host");
        try {
            Preparation preparation = findPreparation(runId);
            if (preparation != null) {
                verifyPreparation(preparation);
                try (Active ownership = lockRun(runId, preparation.directory(), null)) { verifyPreparation(preparation); }
                publishPreparation(preparation);
            }
            Path directory = runs.resolve(runId);
            if (Files.exists(runs.resolve(".discard-" + runId), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(directory.resolve("discard.json"), LinkOption.NOFOLLOW_LINKS)) throw new StorageException("RUN_DISCARDED", "Explicit abandonment is pending; continue the exact discard operation");
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) directory = runs.resolve(".cleanup-" + runId);
            rejectUnsafeDirectory(directory);
            if (directory.getFileName().toString().startsWith(".cleanup-") && !Files.exists(directory.resolve("cleanup.json"), LinkOption.NOFOLLOW_LINKS)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) { if (stream.iterator().hasNext()) throw new StorageException("CLEANUP_BLOCKED", "An incomplete cleanup tombstone contains unknown material"); }
                Files.delete(directory); forceDirectory(runs); return null;
            }
            if (Files.exists(directory.resolve("cleanup.json"), LinkOption.NOFOLLOW_LINKS)) return resumeCleanup(runId, directory);
            JsonObject marker = CampaignJson.read(readBounded(directory.resolve("allocation.json")));
            CampaignJson.fields(marker, "runId", "applicationModel", "storageModel", "planDigest", "inputDigest", "modules");
            if (!CampaignJson.string(marker, "runId").equals(runId) || !CampaignJson.string(marker, "applicationModel").equals(CampaignCodec.APPLICATION_MODEL) || !CampaignJson.string(marker, "storageModel").equals(CampaignCodec.STORAGE_MODEL)) throw new StorageException("UNSUPPORTED_RUN", "Residual run uses another application or storage model");
            byte[] inputBytes = readBounded(directory.resolve("input.json"));
            if (!CampaignJson.sha256(inputBytes).equals(CampaignJson.string(marker, "inputDigest"))) throw new StorageException("STORAGE_INTEGRITY", "Original initialization input differs from its allocation marker");
            Snapshot original = codec.decode(inputBytes).snapshot(); StoragePlan plan = catalog.plan(CampaignRules.roots(original.rules()));
            List<String> markedModules = new ArrayList<>();
            for (JsonElement e : CampaignJson.array(marker, "modules")) {
                if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new StorageException("STORAGE_INTEGRITY", "Invalid marked storage module");
                markedModules.add(e.getAsString());
            }
            if (!plan.digest().equals(CampaignJson.string(marker, "planDigest")) || !plan.moduleIds().equals(markedModules)) throw new StorageException("STORAGE_INTEGRITY", "Residual plan binding differs");
            if (active == null) active = lockRun(runId, plan);
            active.plan = plan; active.initialInput = original; active.blocked = "RESTART";
            if (!Files.exists(active.database, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(directory.resolve("database-started.json"), LinkOption.NOFOLLOW_LINKS) || Files.exists(directory.resolve("initialized.json"), LinkOption.NOFOLLOW_LINKS))
                    throw new StorageException("STORAGE_INTEGRITY", "Residual database is missing after database preparation; committed progress cannot be replaced");
                initializeDatabase(original);
            } else {
                rejectUnsafeFile(active.database, false);
                boolean empty;
                try (Connection c = connect(active.database); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%'")) { r.next(); empty = r.getLong(1) == 0; }
                if (empty) {
                    if (Files.exists(directory.resolve("initialized.json"), LinkOption.NOFOLLOW_LINKS)) throw new StorageException("STORAGE_INTEGRITY", "A committed initialization has lost its schema");
                    initializeDatabase(original); // exact marked original input only; partial schemas never enter this branch
                }
            }
            verifyActive();
            Control control;
            try (Connection c = connect(active.database)) { control = repository.control(c); }
            if (control.sealed()) {
                CampaignView sealed;
                try (Connection c = connect(active.database)) { sealed = view(c); }
                cleanupSealed(); return sealed;
            }
            long epoch = nextEpoch(control.epoch());
            transaction(c -> { repository.epoch(c, epoch); return null; });
            List<SaveReceipt> receipts;
            try (Connection c = connect(active.database)) { receipts = repository.saves(c); }
            SaveReceipt pending = receipts.stream().filter(s -> !s.accepted()).findFirst().orElse(null);
            if (pending != null) {
                saveInternal(pending.requestId(), pending.revision(), pending.purpose());
                if (pending.purpose().equals("FINAL")) {
                    CampaignView sealed;
                    try (Connection c = connect(active.database)) { sealed = view(c); }
                    cleanupSealed(); return sealed;
                }
            }
            try (Connection c = connect(active.database)) { control = repository.control(c); }
            if (!control.firstSaved()) saveInternal(newId(), control.revision(), "INITIAL");
            active.blocked = null; return current();
        } catch (IOException e) { if (active != null) active.blocked = "INTEGRITY"; throw io("Residual run could not be verified; it was retained", e); }
        catch (SQLException | RuntimeException e) {
            if (active != null) active.blocked = "INTEGRITY";
            if (e instanceof StorageException storage) throw storage;
            if (e instanceof SQLException sql) throw database(sql);
            throw (RuntimeException) e;
        }
    }

    private void verifyActive() {
        try (Connection c = connect(active.database)) {
            active.plan.verify(c); Snapshot snapshot = verifiedSnapshot(c); Control control = repository.control(c);
            verifyInitialBinding(control, snapshot, codec.decode(readBounded(active.directory.resolve("input.json"))).snapshot());
            boolean acceptedInitial = false, acceptedFinal = false;
            for (SaveReceipt receipt : repository.saves(c)) {
                if (receipt.accepted()) {
                    Path file = checkedSavePath(receipt.fileKey()); byte[] bytes = readBounded(file);
                    if (bytes.length != receipt.byteSize() || !CampaignJson.sha256(bytes).equals(receipt.rawDigest())) throw new StorageException("STORAGE_INTEGRITY", "Accepted save bytes are missing or changed");
                    Document document = codec.decode(bytes); Snapshot saved = document.snapshot();
                    long expectedEvents = snapshot.history().size() - control.revision() + receipt.revision();
                    if (!document.fileKey().equals(receipt.fileKey()) || !saved.campaignId().equals(snapshot.campaignId()) || !saved.rules().equals(snapshot.rules()) || !saved.name().equals(snapshot.name()) || saved.history().size() != expectedEvents || saved.history().size() > snapshot.history().size() || !snapshot.history().subList(0, saved.history().size()).equals(saved.history())) throw new StorageException("STORAGE_INTEGRITY", "Accepted checkpoint identity or history differs from its exact live-source revision");
                    if (receipt.purpose().equals("INITIAL")) acceptedInitial = true;
                    if (receipt.purpose().equals("FINAL")) acceptedFinal = true;
                }
            }
            if (control.firstSaved() != acceptedInitial || control.sealed() != acceptedFinal) throw new StorageException("STORAGE_INTEGRITY", "Execution/seal qualification differs from accepted checkpoint receipts");
        } catch (IOException e) { throw io("Post-commit full readback failed", e); }
        catch (SQLException e) { throw database(e); }
    }

    private void cleanupSealed() {
        Active sealed = active;
        try {
            SaveReceipt finalReceipt;
            try (Connection c = connect(sealed.database)) {
                if (!repository.control(c).sealed()) throw new StorageException("RUN_BLOCKED", "Only a verified sealed run may be cleaned");
                finalReceipt = repository.saves(c).stream().filter(s -> s.accepted() && s.purpose().equals("FINAL")).findFirst().orElseThrow(() -> new StorageException("STORAGE_INTEGRITY", "No accepted final save"));
                byte[] finalBytes = readBounded(checkedSavePath(finalReceipt.fileKey()));
                Document finalDocument = codec.decode(finalBytes);
                if (!CampaignJson.sha256(finalBytes).equals(finalReceipt.rawDigest()) || !finalDocument.fileKey().equals(finalReceipt.fileKey()) || !finalDocument.snapshot().equals(verifiedSnapshot(c))) throw new StorageException("STORAGE_INTEGRITY", "Accepted final save no longer equals the complete source");
            }
            JsonObject tombstone = new JsonObject(); tombstone.addProperty("runId", sealed.runId); tombstone.addProperty("fileKey", finalReceipt.fileKey()); tombstone.addProperty("rawDigest", finalReceipt.rawDigest()); tombstone.addProperty("revision", Long.toString(finalReceipt.revision()));
            Path cleanup = sealed.directory.resolve("cleanup.json");
            if (!Files.exists(cleanup, LinkOption.NOFOLLOW_LINKS)) { writeNewForced(cleanup, CampaignJson.canonical(tombstone)); forceDirectory(sealed.directory); }
            else if (!Arrays.equals(readBounded(cleanup), CampaignJson.canonical(tombstone))) throw new StorageException("STORAGE_INTEGRITY", "Cleanup marker differs from the exact final receipt");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sealed.directory)) {
                for (Path path : stream) {
                    if (!RUN_FILES.contains(path.getFileName().toString())) throw new StorageException("CLEANUP_BLOCKED", "Unknown file prevents exact sealed-run cleanup");
                    rejectUnsafeFile(path, false);
                }
            }
            for (String name : List.of("run.db-journal", "input.json", "allocation.json", "initialized.json", "database-started.json", "run.db")) {
                Path path = sealed.directory.resolve(name);
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { rejectUnsafeFile(path, false); Files.delete(path); }
            }
            forceDirectory(sealed.directory); sealed.close(); active = null;
            Files.delete(sealed.directory.resolve("run.lock"));
            Path tombstoneDirectory = runs.resolve(".cleanup-" + sealed.runId);
            if (Files.exists(tombstoneDirectory, LinkOption.NOFOLLOW_LINKS)) throw new StorageException("CLEANUP_BLOCKED", "Cleanup destination already exists");
            ManagedCampaignFiles.moveDirectory(sealed.directory, tombstoneDirectory); forceDirectory(runs);
            Files.delete(tombstoneDirectory.resolve("cleanup.json")); Files.delete(tombstoneDirectory); forceDirectory(runs);
        } catch (IOException | SQLException | RuntimeException e) {
            if (active != null) active.blocked = "CLEANUP";
            if (e instanceof StorageException storage) throw storage;
            throw new StorageException("CLEANUP_BLOCKED", "Final save is accepted; exact sealed-run cleanup remains pending", e);
        }
    }

    private CampaignView resumeCleanup(String runId, Path directory) throws IOException, SQLException {
        JsonObject marker = CampaignJson.read(readBounded(directory.resolve("cleanup.json")));
        CampaignJson.fields(marker, "runId", "fileKey", "rawDigest", "revision");
        if (!CampaignJson.string(marker, "runId").equals(runId)) throw new StorageException("STORAGE_INTEGRITY", "Cleanup run identity differs");
        String fileKey = CampaignJson.uuid(CampaignJson.string(marker, "fileKey"));
        byte[] bytes = readBounded(checkedSavePath(fileKey));
        if (!CampaignJson.sha256(bytes).equals(CampaignJson.string(marker, "rawDigest"))) throw new StorageException("STORAGE_INTEGRITY", "Cleanup's exact final artifact differs");
        Document finalDocument = codec.decode(bytes);
        if (!finalDocument.fileKey().equals(fileKey)) throw new StorageException("STORAGE_INTEGRITY", "Final artifact identity differs");
        StoragePlan plan = catalog.plan(CampaignRules.roots(finalDocument.snapshot().rules()));
        if (active == null) active = lockRun(runId, directory, plan);
        active.plan = plan;
        active.blocked = "CLEANUP";
        long revision = CampaignJson.integer(marker, "revision");
        if (revision > finalDocument.snapshot().history().size()) throw new StorageException("STORAGE_INTEGRITY", "Cleanup revision exceeds its complete final history");
        if (Files.exists(active.database, LinkOption.NOFOLLOW_LINKS)) {
            try (Connection c = connect(active.database)) {
                active.plan.verify(c); Snapshot current = verifiedSnapshot(c); Control control = repository.control(c);
                SaveReceipt receipt = repository.saves(c).stream().filter(s -> s.accepted() && s.purpose().equals("FINAL") && s.fileKey().equals(fileKey)).findFirst().orElseThrow(() -> new StorageException("STORAGE_INTEGRITY", "Cleanup marker has no exact accepted FINAL receipt"));
                if (!control.sealed() || control.revision() != revision || receipt.revision() != revision || !receipt.rawDigest().equals(CampaignJson.string(marker, "rawDigest")) || !current.equals(finalDocument.snapshot())) throw new StorageException("STORAGE_INTEGRITY", "Existing database does not authorize this cleanup marker");
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!RUN_FILES.contains(file.getFileName().toString())) throw new StorageException("CLEANUP_BLOCKED", "Unknown file prevents exact sealed-run cleanup");
                rejectUnsafeFile(file, false);
            }
        }
        // The forced cleanup marker is written only after the accepted FINAL receipt and independent artifact verification.
        for (String name : List.of("run.db-journal", "input.json", "allocation.json", "initialized.json", "database-started.json", "run.db")) {
            Path path = directory.resolve(name);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { rejectUnsafeFile(path, false); Files.delete(path); }
        }
        forceDirectory(directory); active.close(); active = null;
        Files.deleteIfExists(directory.resolve("run.lock"));
        Path tombstoneDirectory = runs.resolve(".cleanup-" + runId);
        if (!directory.equals(tombstoneDirectory)) {
            if (Files.exists(tombstoneDirectory, LinkOption.NOFOLLOW_LINKS)) throw new StorageException("CLEANUP_BLOCKED", "Cleanup destination already exists");
            ManagedCampaignFiles.moveDirectory(directory, tombstoneDirectory); forceDirectory(runs);
        }
        Files.delete(tombstoneDirectory.resolve("cleanup.json")); Files.delete(tombstoneDirectory); forceDirectory(runs);
        Snapshot s = finalDocument.snapshot(); Map<String, Long> versions = new HashMap<>();
        for (EventFact event : s.history()) versions.merge(event.characterId(), 1L, Long::sum);
        return new CampaignView(s.campaignId(), s.name(), revision, 0, s.rules(),
                s.characters().stream().map(c -> new CharacterView(c.id(), c.name(), c.maximumHp(), c.currentHp(), versions.get(c.id()))).toList(),
                s.history().stream().map(e -> new EventView(e.sequence(), e.type(), e.characterId(), e.beforeHp(), e.afterHp(), e.amount(), e.applied(), e.name(), e.maximumHp())).toList(), null, domainView(s.domains()));
    }

    @FunctionalInterface private interface Work<T> { T run(Connection connection) throws SQLException; }
    private <T> T transaction(Work<T> work) { return transaction(work, false); }
    private <T> T transaction(Work<T> work, boolean creating) {
        boolean committing = false, begun = false;
        try (Connection c = connect(active.database, creating)) {
            try {
                try (Statement s = c.createStatement()) { s.execute("BEGIN IMMEDIATE"); } begun = true;
                T result = work.run(c); committing = true;
                try (Statement s = c.createStatement()) { s.execute("COMMIT"); }
                begun = false; return result;
            } catch (SQLException | RuntimeException e) {
                if (committing) {
                    active.blocked = "UNKNOWN";
                    throw new StorageException("COMMIT_UNKNOWN", "Commit outcome requires exclusive explicit recovery", e);
                }
                if (begun) try (Statement s = c.createStatement()) { s.execute("ROLLBACK"); }
                catch (SQLException rollback) {
                    active.blocked = "UNKNOWN"; e.addSuppressed(rollback);
                    throw new StorageException("COMMIT_UNKNOWN", "Rollback could not be confirmed; explicit recovery is required", e);
                }
                if (e instanceof StorageException storage) throw storage;
                if (e instanceof SQLException sql) throw database(sql);
                throw e;
            }
        } catch (SQLException e) { active.blocked = "UNKNOWN"; throw database(e); }
    }

    private Connection connect(Path database) throws SQLException { return connect(database, false); }
    private Connection connect(Path database, boolean creating) throws SQLException {
        try {
            rejectUnsafeFile(database, creating);
            rejectUnsafeFile(database.resolveSibling(database.getFileName() + "-journal"), true);
            for (String unsupported : List.of("-wal", "-shm")) if (Files.exists(database.resolveSibling(database.getFileName() + unsupported), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Unexpected SQLite sidecar");
        }
        catch (IOException e) { throw new SQLException("Unsafe SQLite database path", e); }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + database.toUri() + "?mode=" + (creating ? "rwc" : "rw"));
        try (Statement s = c.createStatement()) {
            try { rejectUnsafeFile(database, false); } catch (IOException e) { throw new SQLException("Database privacy readback failed", e); }
            s.execute("PRAGMA busy_timeout=5000");
            try (ResultSet r = s.executeQuery("PRAGMA encoding")) { if (!r.next() || !"UTF-8".equals(r.getString(1))) throw new SQLException("SQLite UTF-8 encoding is required"); }
            try (ResultSet r = s.executeQuery("PRAGMA journal_mode=DELETE")) { if (!r.next() || !"delete".equalsIgnoreCase(r.getString(1))) throw new SQLException("SQLite rollback journal mode unavailable"); }
            s.execute("PRAGMA synchronous=EXTRA"); s.execute("PRAGMA foreign_keys=ON");
            try (ResultSet r = s.executeQuery("PRAGMA synchronous")) { if (!r.next() || r.getInt(1) != 3) throw new SQLException("SQLite EXTRA durability unavailable"); }
            try (ResultSet r = s.executeQuery("PRAGMA foreign_keys")) { if (!r.next() || r.getInt(1) != 1) throw new SQLException("SQLite foreign keys unavailable"); }
            return c;
        } catch (SQLException | RuntimeException | Error e) { try { c.close(); } catch (SQLException close) { e.addSuppressed(close); } throw e; }
    }

    private void requireSaveEpoch(long epoch) {
        try (Connection c = connect(active.database)) {
            if (repository.control(c).epoch() != epoch) throw new StorageException("STALE_EPOCH", "Refresh the current Host epoch");
        } catch (SQLException e) { throw database(e); }
    }
    private void ensureOpen() { if (closed) throw new StorageException("SERVICE_CLOSED", "Storage service is closed"); }
    private void ensureIdle() {
        ensureOpen(); if (active != null) throw new StorageException("RUN_EXISTS", "A run already owns this Host");
        try { if (!residualRuns().isEmpty()) throw new StorageException("RESIDUAL_RUN", "Explicitly recover the existing residual before opening or creating another run"); }
        catch (IOException e) { throw io("Cannot inspect residual runs", e); }
    }
    private void ensureActive(boolean writing) {
        ensureOpen(); if (active == null) throw new StorageException("NO_ACTIVE_RUN", "No run is active");
        if (writing && active.blocked != null) throw new StorageException("RUN_BLOCKED", "The run requires exact save completion or explicit recovery");
    }
    private Active lockRun(String runId, StoragePlan plan) throws IOException {
        return lockRun(runId, runs.resolve(runId), plan);
    }
    private Active lockRun(String runId, Path directory, StoragePlan plan) throws IOException {
        ManagedCampaignFiles.privateDirectory(directory);
        Path lockPath = directory.resolve("run.lock"); rejectUnsafeFile(lockPath, true);
        FileChannel channel = FileChannel.open(lockPath, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), ManagedCampaignFiles.privateAttribute(lockPath, false));
        try {
            rejectUnsafeFile(lockPath, false);
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException e) { throw new StorageException("RUN_LOCKED", "Residual run has another owner", e); }
            if (lock == null) throw new StorageException("RUN_LOCKED", "Residual run has another owner");
            return new Active(runId, directory, channel, lock, plan);
        } catch (IOException | RuntimeException | Error e) {
            try { channel.close(); } catch (IOException close) { e.addSuppressed(close); }
            throw e;
        }
    }
    private static OwnedLock acquireRootLock(Path root) throws IOException {
        // Do not open another channel for the same JVM-owned lock file: on some systems closing
        // that second channel releases the process's existing operating-system lock as well.
        if (!JVM_ROOT_OWNERS.add(root)) throw new StorageException("INSTANCE_LOCKED", "The managed storage root already has an owner in this JVM");
        FileChannel channel = null; FileLock lock = null;
        try {
            Path path = root.resolve("instance.lock"); rejectUnsafeFile(path, true);
            channel = FileChannel.open(path, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), ManagedCampaignFiles.privateAttribute(path, false));
            rejectUnsafeFile(path, false);
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException e) { throw new StorageException("INSTANCE_LOCKED", "The managed storage root already has an owner", e); }
            if (lock == null) throw new StorageException("INSTANCE_LOCKED", "The managed storage root already has an owner");
            return new OwnedLock(channel, lock);
        } catch (IOException | RuntimeException | Error e) {
            try {
                if (lock != null) try { lock.release(); } catch (IOException | RuntimeException | Error release) { e.addSuppressed(release); }
                if (channel != null) try { channel.close(); } catch (IOException | RuntimeException | Error close) { e.addSuppressed(close); }
            } finally { JVM_ROOT_OWNERS.remove(root); }
            throw e;
        }
    }
    private static void releaseRootLock(Path root, OwnedLock ownership) throws IOException {
        try { ownership.lock().release(); }
        finally { try { ownership.channel().close(); } finally { JVM_ROOT_OWNERS.remove(root); } }
    }
    private List<String> residualRuns() throws IOException {
        List<String> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(runs)) {
            for (Path path : stream) {
                rejectUnsafeDirectory(path); String name = path.getFileName().toString();
                String id = name.startsWith(".prepare-") ? preparation(path).runId() : name.startsWith(".cleanup-") ? name.substring(9) : name.startsWith(".discard-") ? name.substring(9) : name; CampaignJson.uuid(id);
                if (active == null || !active.runId.equals(id)) ids.add(id);
            }
        }
        if (new HashSet<>(ids).size() != ids.size()) throw new StorageException("STORAGE_INTEGRITY", "Multiple residual entries claim one run");
        Collections.sort(ids); return List.copyOf(ids);
    }
    private List<String> saveFiles() throws IOException {
        if (active == null && !residualRuns().isEmpty()) return List.of();
        Set<String> pending = new HashSet<>();
        if (active != null) try (Connection c = connect(active.database)) {
            repository.saves(c).stream().filter(s -> !s.accepted()).forEach(s -> pending.add(s.fileKey()));
        } catch (SQLException e) { return List.of(); }
        List<String> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(saves)) {
            for (Path path : stream) {
                rejectUnsafeFile(path, false); String name = path.getFileName().toString();
                if (name.endsWith(".json")) { String id = name.substring(0, name.length() - 5); CampaignJson.uuid(id); if (!pending.contains(id)) ids.add(id); }
                else if (name.endsWith(".tmp")) CampaignJson.uuid(name.substring(0, name.length() - 4));
                else throw new StorageException("STORAGE_INTEGRITY", "Unknown file in managed save directory");
            }
        }
        Collections.sort(ids); return List.copyOf(ids);
    }
    private Path checkedSavePath(String fileKey) throws IOException {
        Path path = saves.resolve(CampaignJson.uuid(fileKey) + ".json"); rejectUnsafeFile(path, false); return path;
    }
    private static byte[] readBounded(Path path) throws IOException {
        rejectUnsafeFile(path, false);
        try (FileChannel c = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = c.size();
            if (size < 1 || size > CampaignJson.MAX_BYTES) throw new StorageException("INVALID_FILE", "File size outside the supported budget");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) if (c.read(buffer) < 0) throw new EOFException("Truncated managed file");
            if (c.read(ByteBuffer.allocate(1)) != -1) throw new IOException("Managed file changed during reading");
            return buffer.array();
        }
    }
    private static void writeNewForced(Path path, byte[] bytes) throws IOException {
        rejectUnsafeFile(path, true);
        try (FileChannel c = FileChannel.open(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), ManagedCampaignFiles.privateAttribute(path, false))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) c.write(buffer); c.force(true);
        }
        rejectUnsafeFile(path, false);
    }
    private static void ensureDirectory(Path path) throws IOException { ManagedCampaignFiles.ensureDirectory(path); }
    private static void createPrivateDirectory(Path path) throws IOException { ManagedCampaignFiles.createDirectory(path); }
    private static void rejectUnsafeDirectory(Path path) throws IOException { ManagedCampaignFiles.directory(path); }
    private static void rejectUnsafeFile(Path path, boolean allowMissing) throws IOException { ManagedCampaignFiles.file(path, allowMissing); }
    private static void forceDirectory(Path directory) throws IOException { ManagedCampaignFiles.forceDirectory(directory); }
    private static String newId() { return UUID.randomUUID().toString(); }
    private static long nextEpoch(long prior) {
        long epoch; SecureRandom random = new SecureRandom();
        do { epoch = random.nextLong() & Long.MAX_VALUE; } while (epoch == 0 || epoch == prior); return epoch;
    }
    private static StorageException io(String message, IOException e) { return new StorageException("STORAGE_IO", message, e); }
    private static StorageException database(SQLException e) { return new StorageException("STORAGE_DATABASE", "SQLite operation failed; source material is retained", e); }

    /** Shutdown does not perform END, save, seal or data deletion. */
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        Throwable failure = null;
        if (active != null) try { active.close(); } catch (IOException | RuntimeException | Error e) { failure = e; }
        try { releaseRootLock(root, new OwnedLock(instanceChannel, instanceLock)); }
        catch (IOException | RuntimeException | Error e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        finally { closed = true; }
        if (failure instanceof IOException io) throw io;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }
}
