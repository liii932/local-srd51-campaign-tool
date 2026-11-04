package com.dndtool.web;

import com.dndtool.security.PlayerListenerConfiguration;
import com.dndtool.service.storage.PlayerGatewayService;
import jakarta.servlet.ServletContext;

/** Lazy construction avoids listener-order coupling and never enables a connector. */
public final class PlayerApplicationAccess {
    public static final String SERVICE_ATTRIBUTE = "com.dndtool.player.gateway";
    private PlayerApplicationAccess() { }
    public static PlayerGatewayService service(ServletContext context) {
        if (PlayerListenerConfiguration.from(context) == null || !StorageApplicationListener.enabled(context)) throw new IllegalStateException("PLAYER_DISABLED");
        synchronized (context) {
            Object existing = context.getAttribute(SERVICE_ATTRIBUTE);
            if (existing instanceof PlayerGatewayService service) return service;
            PlayerGatewayService service = new PlayerGatewayService(StorageApplicationListener.service(context));
            context.setAttribute(SERVICE_ATTRIBUTE, service); return service;
        }
    }
}
