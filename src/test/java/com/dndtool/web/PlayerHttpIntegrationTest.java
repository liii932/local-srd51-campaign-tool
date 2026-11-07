package com.dndtool.web;

import com.dndtool.service.storage.*;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTPS and SQLite, with explicit certificate trust and the unchanged Host connector. */
class PlayerHttpIntegrationTest {
    @TempDir Path temporary;
    private static final String HOST = "http://127.0.0.1:8080", PLAYER = "https://127.0.0.1:8443";
    private final HttpClient dm = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();

    @Test void trustedHttpsApplicationApprovalSingleWriterAndExactReceiptUseTheRealBoundary() throws Exception {
        try (var launcher = PlayerLoopLauncher.start(temporary.resolve("store"))) {
            var storage = launcher.storage(); CampaignRuleFixtures.create(storage, "玩家原型");
            domain(storage,"character-create",json("name","自己的战士","kind","PC","classKey","fighter","backgroundKey","acolyte"));
            UUID pc = storage.accessSnapshot().snapshot().domains().characters().getFirst().id();
            domain(storage,"character-create",json("name","秘密守卫","kind","NPC","classKey","fighter","backgroundKey","acolyte"));
            var equip = new Command("ITEM_ACQUIRED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,
                    new EquipmentInput(UUID.randomUUID(),CampaignRuleFixtures.rules().rule("equipment.item","item.longsword"),"CARRIED","主持准备"),null);
            JsonObject payload=json("characterId",pc.toString(),"characterVersion","0"); payload.add("command",CampaignDomainCodec.toJson(equip)); domain(storage,"character-command",payload);
            UUID node = PlayerSpaceFixture.placeAtWorld(storage, pc);

            HttpClient trusted = client(launcher); HttpClient second = client(launcher);
            assertThrows(SSLException.class,()->HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(PLAYER+"/player")).GET().build(),HttpResponse.BodyHandlers.ofString()));
            assertEquals(200,get(trusted,PLAYER+"/player").statusCode());
            for(String path:List.of("/","/health","/host/storage","/api/host/storage/state","/api/host/storage/rule-library","/api/host/storage/rule-download","/host/assets/host-storage.js","/WEB-INF/player.html"))
                assertEquals(404,get(trusted,PLAYER+path).statusCode(),path);
            assertEquals(404,get(dm,HOST+"/player").statusCode());
            assertEquals(404,get(trusted,PLAYER+"/player?invitation=secret").statusCode());
            var bootstrap = get(trusted,PLAYER+"/api/player/session");
            String cookie=bootstrap.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(cookie.contains("DNDPLAYERSESSION=")); assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Strict"));
            JsonObject first=body(bootstrap); String csrf=csrf(first);
            JsonObject invite=host("invite",Map.of()); String code=invite.get("invitation").getAsString();
            assertEquals(403,post(trusted,"join",csrf,json("invitation",code,"nickname","同名"),null,"https://wrong.invalid").statusCode());
            first=ok(post(trusted,"join",csrf,json("invitation",code,"nickname","同名"),null,PLAYER)); assertFalse(first.has("view"));
            JsonObject other=ok(get(second,PLAYER+"/api/player/session")); other=ok(post(second,"join",csrf(other),json("invitation",code,"nickname","同名"),null,PLAYER));
            assertFalse(other.has("view"));
            JsonObject applications=ok(get(dm,HOST+"/api/host/storage/players/state")); assertEquals(2,applications.getAsJsonArray("applications").size());
            host("approve",Map.of("applicationId",csrf(first),"memberId",""));
            first=ok(get(trusted,PLAYER+"/api/player/state")); assertEquals("APPROVED",first.getAsJsonObject("session").get("status").getAsString());
            assertNotEquals(csrf,csrf(first)); assertFalse(ok(get(second,PLAYER+"/api/player/state")).has("view"));
            String member=first.getAsJsonObject("session").get("playerKey").getAsString();
            host("control",Map.of("payload",json("characterId",pc.toString(),"memberId",member,"controlVersion","0").toString()));
            JsonObject permission=json("characterId",pc.toString(),"permissionVersion","0");permission.addProperty("sceneId",node.toString());permission.addProperty("active",true);JsonArray operations=new JsonArray();operations.add("CHARACTER_COMMAND");permission.add("actions",operations);
            host("exploration",Map.of("payload",permission.toString()));
            first=ok(get(trusted,PLAYER+"/api/player/state")); assertFalse(first.toString().contains("秘密守卫")); assertFalse(first.toString().contains("epoch")); assertFalse(first.toString().contains("preparationOperationId"));
            first=ok(post(trusted,"takeover",csrf(first),new JsonObject(),null,PLAYER)); String oldWriter=first.get("connectionId").getAsString();
            first=ok(post(trusted,"takeover",csrf(first),new JsonObject(),oldWriter,PLAYER)); String writer=first.get("connectionId").getAsString(); assertNotEquals(oldWriter,writer);
            JsonObject option=first.getAsJsonObject("view").getAsJsonArray("candidates").get(0).getAsJsonObject();
            JsonObject command=json("connectionId",oldWriter,"candidateId",option.get("id").getAsString(),"precondition",option.get("precondition").getAsString(),"requestId",UUID.randomUUID().toString());
            long before=storage.current().revision(); assertEquals(409,post(trusted,"command",csrf(first),command,oldWriter,PLAYER).statusCode()); assertEquals(before,storage.current().revision());
            command.addProperty("connectionId",writer); command.addProperty("dice","20");assertEquals(400,post(trusted,"command",csrf(first),command,writer,PLAYER).statusCode()); command.remove("dice");
            JsonObject accepted=ok(post(trusted,"command",csrf(first),command,writer,PLAYER));assertEquals("ACCEPTED",accepted.getAsJsonObject("receipt").get("state").getAsString());assertEquals(before+1,storage.current().revision());
            JsonObject receipt=json("requestId",command.get("requestId").getAsString(),"candidateId",command.get("candidateId").getAsString(),"precondition",command.get("precondition").getAsString());
            JsonObject checked=ok(post(trusted,"receipt",csrf(accepted),receipt,writer,PLAYER));assertEquals(accepted.get("receipt"),checked.get("receipt"));assertEquals(before+1,storage.current().revision());
            receipt.addProperty("requestId",UUID.randomUUID().toString());assertEquals("UNVERIFIED",ok(post(trusted,"receipt",csrf(checked),receipt,writer,PLAYER)).getAsJsonObject("receipt").get("state").getAsString());
            host("close",Map.of());assertEquals("APPROVED",ok(get(trusted,PLAYER+"/api/player/state")).getAsJsonObject("session").get("status").getAsString());
            host("pause",Map.of("paused","true"));assertFalse(ok(get(trusted,PLAYER+"/api/player/state")).getAsJsonObject("session").get("writeEnabled").getAsBoolean());
        }
    }
    private static HttpClient client(PlayerLoopLauncher launcher) { return HttpClient.newBuilder().sslContext(launcher.clientTrust()).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build(); }
    private JsonObject host(String action,Map<String,String> fields) throws Exception {
        JsonObject state=ok(get(dm,HOST+"/api/host/storage/state"));String epoch=state.getAsJsonObject("storage").get("epoch").getAsString(),revision=state.getAsJsonObject("campaign").get("revision").getAsString();
        Map<String,List<String>> values=new LinkedHashMap<>();fields.forEach((k,v)->values.put(k,List.of(v)));
        String encoded=String.join("&",fields.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).toList());
        var request=HttpRequest.newBuilder(URI.create(HOST+"/api/host/storage/players/"+action)).header("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin",HOST).header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Mode","cors").header("Sec-Fetch-Dest","empty")
                .header("X-CSRF-Token",state.get("csrf").getAsString()).header("X-Host-State-Epoch",epoch).header("X-Object-Row-Version",revision)
                .header("X-Request-Id",UUID.randomUUID().toString()).header("X-Request-Digest",StorageRequestDigest.sha256("players/"+action,epoch,revision,values)).POST(HttpRequest.BodyPublishers.ofString(encoded)).build();
        return ok(dm.send(request,HttpResponse.BodyHandlers.ofString()));
    }
    private static HttpResponse<String> post(HttpClient client,String action,String csrf,JsonObject input,String connection,String origin) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(PLAYER+"/api/player/"+action)).header("Content-Type","application/json").header("Origin",origin).header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Mode","cors").header("Sec-Fetch-Dest","empty").header("X-CSRF-Token",csrf);
        if(connection!=null)request.header("X-Player-Connection",connection);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(input.toString())).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> get(HttpClient client,String url) throws Exception { return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    private static JsonObject body(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private static JsonObject ok(HttpResponse<String> response) { assertEquals(200,response.statusCode(),response.body());return body(response); }
    private static String csrf(JsonObject body) { return body.getAsJsonObject("session").get("csrf").getAsString(); }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private static JsonObject json(String...values) { JsonObject o=new JsonObject();for(int i=0;i<values.length;i+=2)o.addProperty(values[i],values[i+1]);return o; }
    private static void domain(CampaignStorageService storage,String action,JsonObject input) { PlayerSpaceFixture.send(storage, action, input); }
}
