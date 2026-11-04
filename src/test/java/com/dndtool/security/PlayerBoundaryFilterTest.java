package com.dndtool.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerBoundaryFilterTest {
    private static final PlayerListenerConfiguration CONFIG = PlayerListenerConfiguration.parse("https://127.0.0.1:8443","127.0.0.1");
    @Test void separateTlsSurfaceIsDisabledUntilExplicitlyConfigured() throws Exception {
        Result denied = invoke(new PlayerBoundaryFilter(),"/api/player/session","GET",Map.of());
        assertFalse(denied.called); assertEquals(404,denied.status);
    }
    @Test void connectorCannotReadAnyHostRootDiagnosticOrUnlistedAsset() throws Exception {
        for (String path : List.of("/", "/index.jsp", "/host/storage", "/api/host/storage/state", "/api/host/storage/download", "/host/assets/host-storage.js", "/WEB-INF/web.xml", "/player/assets/other.js", "/api/player/anything", "/player;anything")) {
            Result result = invoke(new PlayerBoundaryFilter(CONFIG),path,"GET",Map.of());
            assertFalse(result.called,path); assertEquals(404,result.status,path);
        }
    }
    @Test void exactHttpsReadAndStaticAssetPassWithPrivateHeaders() throws Exception {
        for (String path : List.of("/player","/player/assets/player.js","/player/assets/player.css","/api/player/session","/api/player/state")) {
            Result result = invoke(new PlayerBoundaryFilter(CONFIG),path,"GET",Map.of());
            assertTrue(result.called,path); assertEquals("no-store",result.headers.get("Cache-Control"));
            assertEquals("no-referrer",result.headers.get("Referrer-Policy"));
        }
    }
    @Test void exactOriginFetchMetadataAndActualTlsAddressAreRequiredBeforePost() throws Exception {
        Map<String,String> headers = Map.of("Origin",CONFIG.origin(),"Sec-Fetch-Site","same-origin","Sec-Fetch-Mode","cors","Sec-Fetch-Dest","empty");
        assertTrue(invoke(new PlayerBoundaryFilter(CONFIG),"/api/player/join","POST",headers).called);
        for (String key : headers.keySet()) {
            Map<String,String> missing = new HashMap<>(headers); missing.remove(key);
            assertFalse(invoke(new PlayerBoundaryFilter(CONFIG),"/api/player/join","POST",missing).called,key);
        }
        for (var bad : Map.of("Origin","null","Sec-Fetch-Site","same-site","Host","localhost:8443","Forwarded","for=127.0.0.1","X-Forwarded-Host","127.0.0.1:8443","X-Real-IP","127.0.0.1").entrySet()) {
            Map<String,String> changed = new HashMap<>(headers); changed.put(bad.getKey(),bad.getValue());
            assertFalse(invoke(new PlayerBoundaryFilter(CONFIG),"/api/player/join","POST",changed).called,bad.getKey());
        }
        Map<String,String> changed = new HashMap<>(headers); changed.put("__local","192.168.1.2");
        assertFalse(invoke(new PlayerBoundaryFilter(CONFIG),"/api/player/join","POST",changed).called);
    }
    @Test void hostConnectorKeepsItsExistingSurfaceAndCannotBootstrapPlayerAuthority() throws Exception {
        assertTrue(invoke(new PlayerBoundaryFilter(CONFIG),"/host/storage","GET",Map.of("__host","true")).called);
        assertFalse(invoke(new PlayerBoundaryFilter(CONFIG),"/api/player/session","GET",Map.of("__host","true")).called);
    }
    @Test void invitationLinksCanNavigateToThePublicShellWithoutCrossSiteApiOrAssetAccess() throws Exception {
        Map<String,String> navigation=Map.of("Sec-Fetch-Site","cross-site","Sec-Fetch-Mode","navigate","Sec-Fetch-Dest","document");
        for(String method:List.of("GET","HEAD"))for(String path:List.of("/player","/player/"))
            assertTrue(invoke(new PlayerBoundaryFilter(CONFIG),path,method,navigation).called);
        for(String path:List.of("/api/player/session","/api/player/state","/player/assets/player.js","/player/assets/player.css"))
            assertFalse(invoke(new PlayerBoundaryFilter(CONFIG),path,"GET",navigation).called);
        Map<String,String> withOrigin=new HashMap<>(navigation);withOrigin.put("Origin","http://127.0.0.1:8080");
        assertFalse(invoke(new PlayerBoundaryFilter(CONFIG),"/player","GET",withOrigin).called);
    }
    @Test void invalidConfigurationCannotSelectWildcardDnsPlainHttpOrOtherPort() {
        for (String[] pair : List.of(new String[]{"https://0.0.0.0:8443","0.0.0.0"}, new String[]{"https://localhost:8443","localhost"},
                new String[]{"http://127.0.0.1:8443","127.0.0.1"},new String[]{"https://127.0.0.1:8080","127.0.0.1"},
                new String[]{"https://127.0.0.1:8443/","127.0.0.1"},new String[]{"https://192.168.1.2:8443","127.0.0.1"}))
            assertThrows(IllegalArgumentException.class,()->PlayerListenerConfiguration.parse(pair[0],pair[1]));
    }
    private static Result invoke(PlayerBoundaryFilter filter,String path,String method,Map<String,String> extra) throws Exception {
        Map<String,String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); headers.put("Host","127.0.0.1:8443"); headers.putAll(extra);
        boolean host = extra.containsKey("__host");
        HttpServletRequest request = (HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()) {
            case "getRequestURI" -> path; case "getContextPath" -> ""; case "getMethod" -> method;
            case "getLocalPort" -> host ? 8080 : 8443; case "getScheme" -> host ? "http" : "https"; case "isSecure" -> !host;
            case "getLocalAddr" -> extra.getOrDefault("__local","127.0.0.1"); case "getHeader" -> headers.get(a[0]);
            case "getHeaderNames" -> Collections.enumeration(headers.keySet());
            case "getHeaders" -> Collections.enumeration(headers.containsKey(a[0]) ? List.of(headers.get(a[0])) : List.of());
            default -> defaultValue(m.getReturnType());
        });
        Result result = new Result();
        HttpServletResponse response = (HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->switch(m.getName()) {
            case "setStatus" -> { result.status=(int)a[0]; yield null; } case "setHeader" -> { result.headers.put((String)a[0],(String)a[1]); yield null; }
            case "getWriter" -> new PrintWriter(new StringWriter()); default -> defaultValue(m.getReturnType());
        });
        filter.doFilter(request,response,(a,b)->result.called=true); return result;
    }
    private static final class Result { boolean called; int status=200; Map<String,String> headers=new HashMap<>(); }
    private static Object defaultValue(Class<?> type) { if (!type.isPrimitive()) return null; if(type==boolean.class)return false;if(type==long.class)return 0L;return 0; }
}
