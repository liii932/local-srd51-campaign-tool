package com.dndtool.web;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.storage.*;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual independent JVM restart, fixed loopback HTTP/TLS ports and disposable SQLite only. */
class PlayerProcessRecoveryIntegrationTest {
    private static final String HOST = "http://127.0.0.1:8080", PLAYER = "https://127.0.0.1:8443";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(120), START_TIMEOUT = Duration.ofSeconds(180);
    @TempDir Path temporary;
    private final List<ServerRun> runs = new ArrayList<>();
    private final Set<String> secrets = new HashSet<>();
    private String phase = "starting initial independent JVM";

    @Test @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void processRestartRequiresHostRecoveryAndReopenBeforeOriginalDeviceCanVerifyItsReceipt() throws Exception {
        Path root = temporary.resolve("private-store");
        try (ServerRun first = launch("initial", root)) {
            HttpClient dm = hostClient(); CookieManager deviceCookies = cookies();
            HttpClient device = playerClient(first, deviceCookies);
            phase = "creating disposable campaign and exact legal candidate through Host HTTP";
            Map<String,List<String>> create = form("name", "独立进程恢复验收");
            create.put("modules", List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME));
            hostPost(dm, "create", create, false);
            JsonObject created = hostPost(dm, "domain", form("action", "character-create", "payload",
                    object("name", "重连的战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte").toString()), false);
            JsonObject card = created.getAsJsonObject("campaign").getAsJsonObject("domain").getAsJsonArray("characters").get(0).getAsJsonObject();
            String pc = card.get("id").getAsString();
            var acquire = new Schema2CharacterRuntime.Command("ITEM_ACQUIRED", null, 0, null, null, null,
                    Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                    new Schema2CharacterRuntime.EquipmentInput(UUID.randomUUID(), BundledCampaignRules.rule("equipment.item", "item.longsword"), "CARRIED", "主持准备"), null);
            JsonObject equipment = object("characterId", pc, "characterVersion", card.get("version").getAsString());
            equipment.add("command", CampaignDomainCodec.toJson(acquire));
            hostPost(dm, "domain", form("action", "character-command", "payload", equipment.toString()), false);

            JsonObject anonymous = ok(get(device, PLAYER + "/api/player/session"), deviceCookies);
            String invitation = hostPost(dm, "invite", form(), true).get("invitation").getAsString(); secrets.add(invitation);
            JsonObject pending = playerPost(device, deviceCookies, "join", csrf(anonymous), object("invitation", invitation, "nickname", "原设备玩家"));
            assertFalse(pending.has("view"), "temporary application must not expose campaign data");
            hostPost(dm, "approve", form("applicationId", csrf(pending), "memberId", ""), true);
            JsonObject approved = ok(get(device, PLAYER + "/api/player/state"), deviceCookies);
            String member = approved.getAsJsonObject("session").get("playerKey").getAsString();
            hostPost(dm, "control", form("payload", object("characterId", pc, "memberId", member, "controlVersion", "0").toString()), true);
            JsonObject permission = object("characterId", pc, "permissionVersion", "0");
            permission.add("sceneId", JsonNull.INSTANCE); permission.addProperty("active", true);
            JsonArray actions = new JsonArray(); actions.add("CHARACTER_COMMAND"); permission.add("actions", actions);
            hostPost(dm, "exploration", form("payload", permission.toString()), true);
            JsonObject takeover = playerPost(device, deviceCookies, "takeover", csrf(approved), object());
            String writer = takeover.get("connectionId").getAsString(); secrets.add(writer);
            JsonObject option = takeover.getAsJsonObject("view").getAsJsonArray("candidates").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(candidate -> candidate.get("label").getAsString().startsWith("装备 ")).findFirst().orElseThrow(() -> new AssertionError("Expected current server-authored equipment candidate"));
            String requestId = UUID.randomUUID().toString();
            JsonObject command = object("connectionId", writer, "candidateId", option.get("id").getAsString(), "precondition", option.get("precondition").getAsString(), "requestId", requestId);
            secrets.add(command.get("candidateId").getAsString()); secrets.add(command.get("precondition").getAsString());
            JsonObject accepted = playerPost(device, deviceCookies, "command", csrf(takeover), command);
            JsonObject originalReceipt = accepted.getAsJsonObject("receipt").deepCopy();
            assertEquals("ACCEPTED", originalReceipt.get("state").getAsString());
            JsonObject exactIdentity = object("requestId", requestId, "candidateId", option.get("id").getAsString(), "precondition", option.get("precondition").getAsString());
            JsonObject acceptedState = ok(get(dm, HOST + "/api/host/storage/state"), null);
            Facts beforeRestart = facts(acceptedState); long oldEpoch = acceptedState.getAsJsonObject("storage").get("epoch").getAsLong();
            String oldSession = cookie(deviceCookies, "DNDPLAYERSESSION"), originalDevice = cookie(deviceCookies, "DNDPLAYERDEVICE");

            phase = "stopping only the initial child without END or file reopen";
            first.close(); assertFalse(first.process.isAlive());
            phase = "starting a replacement independent JVM on the same private root";
            try (ServerRun restarted = launch("restarted", root)) {
                assertTrue(first.process.pid() != ProcessHandle.current().pid() && restarted.process.pid() != ProcessHandle.current().pid(), "servers must run outside the test JVM");
                assertFalse(first.spki.equals(restarted.spki), "each disposable launch must use its own test certificate");
                HttpClient recoveredDm = hostClient(); HttpClient reconnected = playerClient(restarted, deviceCookies);
                phase = "rejecting the old Session and observing the exact residual run";
                HttpResponse<String> oldIdentity = get(reconnected, PLAYER + "/api/player/state");
                assertEquals(401, oldIdentity.statusCode()); assertEquals("PLAYER_SESSION_REQUIRED", body(oldIdentity).get("code").getAsString());
                JsonObject residual = ok(get(recoveredDm, HOST + "/api/host/storage/state"), null);
                assertEquals("RESIDUAL", residual.getAsJsonObject("storage").get("state").getAsString()); assertTrue(residual.get("campaign").isJsonNull());
                assertEquals(0, residual.getAsJsonObject("storage").get("epoch").getAsLong());
                assertTrue(residual.getAsJsonObject("storage").getAsJsonArray("residualRunIds").asList().stream().anyMatch(run -> run.getAsString().equals(beforeRestart.runId)));

                phase = "explicit Host recover preserves business facts and advances only the run epoch";
                JsonObject recovered = hostPost(recoveredDm, "recover", form("runId", beforeRestart.runId), false);
                assertTrue(beforeRestart.equals(facts(recovered)), "Host recovery must retain exact campaign domain, run identity and accepted revision");
                assertTrue(recovered.getAsJsonObject("storage").get("epoch").getAsLong() != oldEpoch, "recovery must invalidate the old epoch");
                JsonObject reopenedSession = ok(get(reconnected, PLAYER + "/api/player/session"), deviceCookies);
                assertEquals("ANONYMOUS", reopenedSession.getAsJsonObject("session").get("status").getAsString());
                assertFalse(oldSession.equals(cookie(deviceCookies, "DNDPLAYERSESSION")), "bootstrap must replace the dead process Session");
                HttpResponse<String> beforeHostReopen = playerRequest(reconnected, "resume", csrf(reopenedSession), object());
                assertEquals(409, beforeHostReopen.statusCode()); assertEquals("PLAYER_HOST_REOPEN_REQUIRED", body(beforeHostReopen).get("code").getAsString());
                assertTrue(beforeRestart.equals(facts(ok(get(recoveredDm, HOST + "/api/host/storage/state"), null))));

                phase = "Host explicitly reopens and original device restores identity without a writer";
                hostPost(recoveredDm, "pause", form("paused", "false"), true);
                String anonymousSession = cookie(deviceCookies, "DNDPLAYERSESSION");
                JsonObject resumed = playerPost(reconnected, deviceCookies, "resume", csrf(reopenedSession), object());
                assertEquals("APPROVED", resumed.getAsJsonObject("session").get("status").getAsString());
                assertEquals(member, resumed.getAsJsonObject("session").get("playerKey").getAsString());
                assertFalse(resumed.getAsJsonObject("session").get("writeEnabled").getAsBoolean());
                assertFalse(anonymousSession.equals(cookie(deviceCookies, "DNDPLAYERSESSION")), "device authentication must rotate the anonymous Session");
                assertTrue(originalDevice.equals(cookie(deviceCookies, "DNDPLAYERDEVICE")), "same-run authentication must use the original approved device binding");
                assertFalse(csrf(reopenedSession).equals(csrf(resumed)), "restored identity must receive a fresh CSRF credential");
                HttpResponse<String> staleWriterRead = reconnected.send(HttpRequest.newBuilder(URI.create(PLAYER + "/api/player/state")).timeout(HTTP_TIMEOUT).header("X-Player-Connection", writer).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertFalse(ok(staleWriterRead, deviceCookies).getAsJsonObject("session").get("writeEnabled").getAsBoolean());

                phase = "read-only exact receipt verification never replays or advances the game";
                Facts beforeReceipt = facts(ok(get(recoveredDm, HOST + "/api/host/storage/state"), null));
                assertTrue(beforeRestart.equals(beforeReceipt));
                JsonObject checked = playerPost(reconnected, deviceCookies, "receipt", csrf(resumed), exactIdentity);
                assertTrue(originalReceipt.equals(checked.get("receipt")), "same-run recovery must return the original receipt outcome");
                assertFalse(checked.getAsJsonObject("session").get("writeEnabled").getAsBoolean());
                assertTrue(beforeReceipt.equals(facts(ok(get(recoveredDm, HOST + "/api/host/storage/state"), null))), "identity recovery and receipt lookup must not consume RNG, append roots or modify any business fact");
                phase = "closing only the replacement child";
            }
        } catch (Exception | AssertionError failure) {
            AssertionError report = new AssertionError(failureEvidence(failure)); report.setStackTrace(failure.getStackTrace()); throw report;
        }
    }

    private ServerRun launch(String name, Path root) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path childTemporary = Files.createDirectory(temporary.resolve(name + "-tmp")); Path log = temporary.resolve(name + "-server.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String absoluteClasspath = String.join(File.pathSeparator, Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator))).map(entry -> Path.of(entry).toAbsolutePath().toString()).toList());
        Process process = new ProcessBuilder(java.toString(), "--enable-native-access=ALL-UNNAMED", "-Xmx512m", "-Djava.io.tmpdir=" + childTemporary,
                "-cp", absoluteClasspath, PlayerLoopLauncher.class.getName(), root.toString()).directory(Path.of("").toAbsolutePath().toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        ServerRun run = new ServerRun(process, log); runs.add(run);
        try {
            long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
            while (System.nanoTime() < deadline) {
                String output = Files.exists(log) ? Files.readString(log) : "";
                String certificate = line(output, "Temporary test certificate: "), spki = line(output, "Test-only certificate SPKI: ");
                if (output.contains("DM: " + HOST + "/host/storage") && output.contains("Player: " + PLAYER + "/player") && certificate != null && spki != null) {
                    Path pem = Path.of(certificate).toAbsolutePath().normalize();
                    assertTrue(pem.startsWith(childTemporary.toAbsolutePath().normalize()) && pem.getFileName().toString().equals("test-certificate.pem"), "certificate must belong to this child test directory");
                    assertTrue(Files.isRegularFile(pem, LinkOption.NOFOLLOW_LINKS), "ready child must expose its exact public test certificate");
                    java.security.cert.Certificate certificateObject;
                    try (InputStream input = Files.newInputStream(pem)) { certificateObject = CertificateFactory.getInstance("X.509").generateCertificate(input); }
                    String actualSpki = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(certificateObject.getPublicKey().getEncoded()));
                    assertTrue(actualSpki.equals(spki), "public certificate must match this launch's exact key");
                    KeyStore trusted = KeyStore.getInstance("PKCS12"); trusted.load(null, null); trusted.setCertificateEntry("owned-test-child", certificateObject);
                    TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(trusted);
                    SSLContext ssl = SSLContext.getInstance("TLS"); ssl.init(null, managers.getTrustManagers(), null);
                    run.trust = ssl; run.spki = spki; return run;
                }
                if (!process.isAlive()) throw new AssertionError("Owned child exited before application/storage/TLS readiness; exit=" + process.exitValue());
                Thread.sleep(100);
            }
            throw new AssertionError("Owned child did not reach application/storage/TLS readiness within the bounded startup deadline");
        } catch (Exception | AssertionError failure) {
            try { run.close(); } catch (Exception | AssertionError cleanup) { failure.addSuppressed(new AssertionError("Owned child shutdown also failed")); }
            throw failure;
        }
    }

    private final class ServerRun implements AutoCloseable {
        final Process process; final Path log; SSLContext trust; String spki;
        ServerRun(Process process, Path log) { this.process = process; this.log = log; }
        @Override public void close() throws Exception {
            if (!process.isAlive()) return;
            boolean interrupted = false, terminated = false, forced = false;
            process.destroy();
            try { terminated = process.waitFor(60, TimeUnit.SECONDS); }
            catch (InterruptedException stopInterrupted) { interrupted = true; }
            if (!terminated) {
                forced = true; process.destroyForcibly();
                try { terminated = process.waitFor(10, TimeUnit.SECONDS); }
                catch (InterruptedException forceInterrupted) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            assertTrue(terminated, "Owned child could not be stopped within the cleanup deadline");
            assertFalse(forced, "Owned child exceeded graceful shutdown deadline; it was forcibly stopped");
            if (interrupted) throw new InterruptedException("Owned child cleanup was interrupted");
        }
    }
    private JsonObject hostPost(HttpClient dm, String action, Map<String,List<String>> fields, boolean participation) throws Exception {
        JsonObject state = ok(get(dm, HOST + "/api/host/storage/state"), null);
        String epoch = state.getAsJsonObject("storage").get("epoch").getAsString();
        String revision = state.get("campaign").isJsonNull() ? "0" : state.getAsJsonObject("campaign").get("revision").getAsString();
        String name = (participation ? "players/" : "") + action;
        List<String> values = new ArrayList<>(); for (var field : new TreeMap<>(fields).entrySet()) for (String value : field.getValue()) values.add(encode(field.getKey()) + "=" + encode(value));
        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/api/host/storage/" + name)).timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").header("Origin", HOST).header("Sec-Fetch-Site", "same-origin").header("Sec-Fetch-Mode", "cors").header("Sec-Fetch-Dest", "empty")
                .header("X-CSRF-Token", state.get("csrf").getAsString()).header("X-Host-State-Epoch", epoch).header("X-Object-Row-Version", revision)
                .header("X-Request-Id", UUID.randomUUID().toString()).header("X-Request-Digest", StorageRequestDigest.sha256(name, epoch, revision, fields))
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", values))).build();
        return ok(dm.send(request, HttpResponse.BodyHandlers.ofString()), null);
    }
    private JsonObject playerPost(HttpClient client, CookieManager cookies, String action, String csrf, JsonObject input) throws Exception { return ok(playerRequest(client, action, csrf, input), cookies); }
    private HttpResponse<String> playerRequest(HttpClient client, String action, String csrf, JsonObject input) throws Exception {
        secrets.add(csrf);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(PLAYER + "/api/player/" + action)).timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json").header("Origin", PLAYER).header("Sec-Fetch-Site", "same-origin").header("Sec-Fetch-Mode", "cors").header("Sec-Fetch-Dest", "empty").header("X-CSRF-Token", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(input.toString()));
        if (action.equals("command")) {
            String connection = input.get("connectionId").getAsString();
            assertTrue(connection.matches("[A-Za-z0-9_-]{43}"), "Command must use the exact bounded writer issued to this test device");
            request.header("X-Player-Connection", connection);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonObject ok(HttpResponse<String> response, CookieManager cookies) {
        JsonObject result = body(response); remember(result, cookies);
        String code = result.has("code") && result.get("code").isJsonPrimitive() ? result.get("code").getAsString() : "NO_PUBLIC_CODE";
        assertTrue(response.statusCode() == 200 && !"ERROR".equals(result.has("status") ? result.get("status").getAsString() : ""), "Unexpected HTTP status=" + response.statusCode() + ", code=" + (code.matches("[A-Z_]{1,80}") ? code : "INVALID_PUBLIC_CODE"));
        return result;
    }
    private static JsonObject body(HttpResponse<String> response) {
        try { return JsonParser.parseString(response.body()).getAsJsonObject(); }
        catch (RuntimeException malformed) { throw new AssertionError("Response was not a JSON object; HTTP status=" + response.statusCode()); }
    }
    private void remember(JsonObject body, CookieManager cookies) {
        for (String key : List.of("csrf", "invitation", "connectionId")) if (body.has(key) && body.get(key).isJsonPrimitive()) secrets.add(body.get(key).getAsString());
        if (body.has("session") && body.get("session").isJsonObject() && body.getAsJsonObject("session").has("csrf")) secrets.add(body.getAsJsonObject("session").get("csrf").getAsString());
        if (cookies != null) for (HttpCookie cookie : cookies.getCookieStore().getCookies()) secrets.add(cookie.getValue());
    }
    private String cookie(CookieManager cookies, String name) { return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElseThrow(() -> new AssertionError("Expected named secure test cookie: " + name)); }
    private String failureEvidence(Throwable failure) {
        StringBuilder text = new StringBuilder("Independent player process recovery failed at phase: ").append(phase).append("\n").append(failure.getClass().getSimpleName()).append(": ").append(Objects.toString(failure.getMessage(), "No detail"));
        for (ServerRun run : runs) {
            text.append("\nOwned child log: ").append(run.log).append("\n");
            try (SeekableByteChannel channel = Files.newByteChannel(run.log)) {
                channel.position(Math.max(0, channel.size() - 8192)); ByteBuffer bytes = ByteBuffer.allocate(8192); while (channel.read(bytes) > 0 && bytes.hasRemaining()) { }
                bytes.flip(); text.append(StandardCharsets.UTF_8.decode(bytes));
            } catch (IOException unavailable) { text.append("Child log is unavailable"); }
        }
        String redacted = text.toString(); for (String secret : secrets) if (secret != null && !secret.isEmpty()) redacted = redacted.replace(secret, "[redacted]");
        return redacted.replaceAll("(?i)(DND(?:PLAYER(?:SESSION|DEVICE)|HOSTSESSION)=)[^;\\s]+", "$1[redacted]");
    }
    private record Facts(String runId, long revision, JsonObject domain) { }
    private static Facts facts(JsonObject body) { JsonObject campaign = body.getAsJsonObject("campaign"), storage = body.getAsJsonObject("storage"); return new Facts(storage.get("runId").getAsString(), campaign.get("revision").getAsLong(), campaign.getAsJsonObject("domain").deepCopy()); }
    private static String line(String output, String prefix) { return output.substring(0, output.lastIndexOf('\n') + 1).lines().filter(value -> value.startsWith(prefix)).map(value -> value.substring(prefix.length()).trim()).findFirst().orElse(null); }
    private static CookieManager cookies() { return new CookieManager(null, CookiePolicy.ACCEPT_ALL); }
    private static HttpClient hostClient() { return HttpClient.newBuilder().cookieHandler(cookies()).connectTimeout(HTTP_TIMEOUT).build(); }
    private static HttpClient playerClient(ServerRun run, CookieManager cookies) { return HttpClient.newBuilder().sslContext(run.trust).cookieHandler(cookies).connectTimeout(HTTP_TIMEOUT).build(); }
    private static HttpResponse<String> get(HttpClient client, String url) throws Exception { return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString()); }
    private static String csrf(JsonObject body) { return body.getAsJsonObject("session").get("csrf").getAsString(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static Map<String,List<String>> form(String... values) { Map<String,List<String>> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put(values[i], List.of(values[i + 1])); return result; }
    private static JsonObject object(String... values) { JsonObject result = new JsonObject(); for (int i = 0; i < values.length; i += 2) result.addProperty(values[i], values[i + 1]); return result; }
}
