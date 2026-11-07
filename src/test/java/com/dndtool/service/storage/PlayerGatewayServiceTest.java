package com.dndtool.service.storage;

import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlayerGatewayServiceTest {
    @TempDir Path temporary;
    @Test void eachApplicationRequiresExplicitApprovalAndRotatesCredentialsWithoutExposingPendingData() throws Exception {
        try (CampaignStorageService storage = storage()) {
            PlayerGatewayService gateway = new PlayerGatewayService(storage);
            JsonObject invitation = host(gateway,storage,"invite",object());
            PlayerGatewayService.Reply first = apply(gateway,invitation,"同名");
            PlayerGatewayService.Reply second = apply(gateway,invitation,"同名");
            assertEquals("WAITING",first.body().getAsJsonObject("session").get("status").getAsString());
            assertFalse(first.body().has("view"));
            assertEquals(2,gateway.hostState().getAsJsonArray("applications").size());
            String application = csrf(first);
            host(gateway,storage,"approve",object("applicationId",application,"memberId",""));
            assertEquals("PLAYER_SESSION_EXPIRED",assertThrows(PlayerGatewayException.class,()->gateway.post(first.sessionToken(),csrf(first),null,"takeover",object(),null)).code());
            PlayerGatewayService.Reply approved = gateway.read(first.sessionToken(),null,false);
            assertEquals("APPROVED",approved.body().getAsJsonObject("session").get("status").getAsString());
            assertNotNull(approved.deviceToken()); assertNotEquals(first.sessionToken(),approved.sessionToken());
            assertNotEquals(csrf(first),csrf(approved));
            assertEquals(401,assertThrows(PlayerGatewayException.class,()->gateway.read(first.sessionToken(),null,false)).status());
            assertEquals("WAITING",gateway.read(second.sessionToken(),null,false).body().getAsJsonObject("session").get("status").getAsString());
            host(gateway,storage,"close",object());
            assertEquals("APPROVED",gateway.read(approved.sessionToken(),null,false).body().getAsJsonObject("session").get("status").getAsString());
            assertEquals(1,storage.accessSnapshot().snapshot().domains().participation().members().size());
        }
    }
    @Test void authenticatedTabsAndDevicesHaveOneExplicitWriterAndRecoveryDoesNotRestoreWritePermission() throws Exception {
        try (CampaignStorageService storage = storage()) {
            PlayerGatewayService gateway = new PlayerGatewayService(storage); JsonObject invite=host(gateway,storage,"invite",object());
            var first=approve(gateway,storage,apply(gateway,invite,"甲"),"");
            String member=first.body().getAsJsonObject("session").get("playerKey").getAsString();
            var second=approve(gateway,storage,apply(gateway,invite,"另一个设备"),member);
            var firstWriter=gateway.post(first.sessionToken(),csrf(first),null,"takeover",object(),null);
            String old=firstWriter.body().get("connectionId").getAsString();
            assertTrue(gateway.read(first.sessionToken(),old,false).body().getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            var secondWriter=gateway.post(second.sessionToken(),csrf(second),null,"takeover",object(),null);
            String current=secondWriter.body().get("connectionId").getAsString();
            assertFalse(gateway.read(first.sessionToken(),old,false).body().getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            assertFalse(gateway.read(first.sessionToken(),current,false).body().getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            PlayerGatewayService restarted=new PlayerGatewayService(storage);
            var anonymous=restarted.read(null,null,true);
            assertEquals("PLAYER_HOST_REOPEN_REQUIRED",assertThrows(PlayerGatewayException.class,()->restarted.post(anonymous.sessionToken(),csrf(anonymous),null,"resume",object(),first.deviceToken())).code());
            host(restarted,storage,"pause",object("paused","false"));
            var restored=restarted.post(anonymous.sessionToken(),csrf(anonymous),null,"resume",object(),first.deviceToken());
            assertEquals(member,restored.body().getAsJsonObject("session").get("playerKey").getAsString());
            assertFalse(restored.body().getAsJsonObject("session").get("writeEnabled").getAsBoolean());
        }
    }
    @Test void revokedMemberCannotReadOrVerifyAndSecurityMaterialNeverEntersPortableFile() throws Exception {
        try (CampaignStorageService storage=storage()) {
            PlayerGatewayService gateway=new PlayerGatewayService(storage); JsonObject invite=host(gateway,storage,"invite",object());
            var approved=approve(gateway,storage,apply(gateway,invite,"甲"),"");
            String member=approved.body().getAsJsonObject("session").get("playerKey").getAsString();
            var missing=gateway.post(approved.sessionToken(),csrf(approved),null,"receipt",object("requestId",UUID.randomUUID().toString(),"candidateId","missing","precondition","missing"),null);
            assertEquals("UNVERIFIED",missing.body().getAsJsonObject("receipt").get("state").getAsString());
            var view=storage.current(); String file=storage.save(UUID.randomUUID().toString(),view.epoch(),view.revision()).fileKey();
            String bytes=Files.readString(storage.savedFile(file));
            for(String secret:List.of(approved.sessionToken(),approved.deviceToken(),csrf(approved),invite.get("invitation").getAsString(),"bindings","attempts"))assertFalse(bytes.contains(secret),secret);
            JsonObject revoke=object("memberId",member,"memberVersion","1","name","甲");revoke.addProperty("active",false);
            host(gateway,storage,"member",object("payload",new Gson().toJson(revoke)));
            assertEquals("PLAYER_SESSION_REQUIRED",assertThrows(PlayerGatewayException.class,()->gateway.read(approved.sessionToken(),null,false)).code());
            assertEquals("PLAYER_SESSION_REQUIRED",assertThrows(PlayerGatewayException.class,()->gateway.post(approved.sessionToken(),csrf(approved),null,"receipt",object("requestId",UUID.randomUUID().toString(),"candidateId","missing","precondition","missing"),null)).code());
            revoke.addProperty("memberVersion","2");revoke.addProperty("active",true);var current=storage.current();storage.domainCommand(UUID.randomUUID().toString(),current.epoch(),current.revision(),"member-update",revoke);
            var anonymous=gateway.read(null,null,true);
            assertEquals("PLAYER_DEVICE_REAPPROVAL_REQUIRED",assertThrows(PlayerGatewayException.class,()->gateway.post(anonymous.sessionToken(),csrf(anonymous),null,"resume",object(),approved.deviceToken())).code());
        }
    }
    @Test void malformedJoinAndWrongCsrfDoNotCreateAnApplication() throws Exception {
        try(CampaignStorageService storage=storage()) {
            PlayerGatewayService gateway=new PlayerGatewayService(storage); JsonObject invite=host(gateway,storage,"invite",object());var anonymous=gateway.read(null,null,true);
            JsonObject input=object("invitation",invite.get("invitation").getAsString(),"nickname","甲");
            assertEquals(403,assertThrows(PlayerGatewayException.class,()->gateway.post(anonymous.sessionToken(),"wrong",null,"join",input,null)).status());
            input.addProperty("extra","untrusted");assertEquals(400,assertThrows(PlayerGatewayException.class,()->gateway.post(anonymous.sessionToken(),csrf(anonymous),null,"join",input,null)).status());
            input.remove("extra");input.addProperty("nickname","甲\u0085");
            assertEquals("PLAYER_INVALID_NICKNAME",assertThrows(PlayerGatewayException.class,()->gateway.post(anonymous.sessionToken(),csrf(anonymous),null,"join",input,null)).code());
            assertEquals(0,gateway.hostState().getAsJsonArray("applications").size());
        }
    }
    @Test void malformedPrivateControlFailsClosedAndDirectCoreMemberVersionsInvalidateOldCredentials() throws Exception {
        try(CampaignStorageService storage=storage()) {
            PlayerGatewayService gateway=new PlayerGatewayService(storage);JsonObject invite=host(gateway,storage,"invite",object());var approved=approve(gateway,storage,apply(gateway,invite,"甲"),"");
            String member=approved.body().getAsJsonObject("session").get("playerKey").getAsString();
            JsonObject edit=object("memberId",member,"memberVersion","1","name","甲");edit.addProperty("active",false);var view=storage.current();storage.domainCommand(UUID.randomUUID().toString(),view.epoch(),view.revision(),"member-update",edit);
            edit.addProperty("memberVersion","2");edit.addProperty("active",true);view=storage.current();storage.domainCommand(UUID.randomUUID().toString(),view.epoch(),view.revision(),"member-update",edit);
            assertEquals("PLAYER_DEVICE_REAPPROVAL_REQUIRED",assertThrows(PlayerGatewayException.class,()->gateway.read(approved.sessionToken(),null,false)).code());
            JsonObject local=storage.localPlayerControl();local.addProperty("unexpected","tampered");storage.updateLocalPlayerControl(local);
            assertEquals("PLAYER_SECURITY_UNAVAILABLE",assertThrows(PlayerGatewayException.class,gateway::hostState).code());
        }
    }
    @Test void repeatedInvalidJoinAndPollingConsumeBoundedSessionBudgets() throws Exception {
        try(CampaignStorageService storage=storage()) {
            PlayerGatewayService gateway=new PlayerGatewayService(storage,Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"),ZoneOffset.UTC));host(gateway,storage,"invite",object());
            var session=gateway.read(null,null,true);
            for(int i=0;i<5;i++)assertEquals("PLAYER_INVITATION_INVALID",assertThrows(PlayerGatewayException.class,()->gateway.post(session.sessionToken(),csrf(session),null,"join",object("invitation","wrong","nickname","甲"),null)).code());
            assertEquals(429,assertThrows(PlayerGatewayException.class,()->gateway.post(session.sessionToken(),csrf(session),null,"join",object("invitation","wrong","nickname","甲"),null)).status());
            for(int i=0;i<7;i++)gateway.read(session.sessionToken(),null,false);
            assertEquals(429,assertThrows(PlayerGatewayException.class,()->gateway.read(session.sessionToken(),null,false)).status());assertEquals(0,gateway.hostState().getAsJsonArray("applications").size());
        }
    }
    private CampaignStorageService storage() throws Exception {
        CampaignStorageService storage=new CampaignStorageService(temporary.resolve(UUID.randomUUID().toString()));
        CampaignRuleFixtures.create(storage, "玩家安全");return storage;
    }
    private static PlayerGatewayService.Reply apply(PlayerGatewayService gateway,JsonObject invite,String name) {
        var anonymous=gateway.read(null,null,true);
        gateway.post(anonymous.sessionToken(),csrf(anonymous),null,"join",object("invitation",invite.get("invitation").getAsString(),"nickname",name),null);return anonymousWithBody(anonymous,gateway.read(anonymous.sessionToken(),null,false));
    }
    private static PlayerGatewayService.Reply anonymousWithBody(PlayerGatewayService.Reply original,PlayerGatewayService.Reply read) { return new PlayerGatewayService.Reply(read.body(),original.sessionToken(),null); }
    private static PlayerGatewayService.Reply approve(PlayerGatewayService gateway,CampaignStorageService storage,PlayerGatewayService.Reply pending,String member) {
        String app=pending.body().getAsJsonObject("session").get("csrf").getAsString();host(gateway,storage,"approve",object("applicationId",app,"memberId",member));return gateway.read(pending.sessionToken(),null,false);
    }
    private static JsonObject host(PlayerGatewayService gateway,CampaignStorageService storage,String action,JsonObject input) { var view=storage.current();return gateway.hostCommand(action,input,UUID.randomUUID().toString(),view.epoch(),view.revision()); }
    private static String csrf(PlayerGatewayService.Reply reply) { return reply.body().getAsJsonObject("session").get("csrf").getAsString(); }
    private static JsonObject object(String... values) { JsonObject result=new JsonObject();for(int i=0;i<values.length;i+=2)result.addProperty(values[i],values[i+1]);return result; }
}
