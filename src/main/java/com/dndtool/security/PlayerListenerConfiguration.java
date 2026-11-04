package com.dndtool.security;

import jakarta.servlet.ServletContext;
import java.net.InetAddress;
import java.net.URI;

/** Explicit address of the separate TLS connector. This class never creates a listener. */
public record PlayerListenerConfiguration(String origin, String address, String authority) {
    public static final String ORIGIN_PARAMETER = "DND_PLAYER_ORIGIN";
    public static final String ADDRESS_PARAMETER = "DND_PLAYER_BIND_ADDRESS";

    public static PlayerListenerConfiguration from(ServletContext context) {
        String origin = configured(context, ORIGIN_PARAMETER);
        String address = configured(context, ADDRESS_PARAMETER);
        if (origin == null && address == null) return null;
        return parse(origin, address);
    }

    public static PlayerListenerConfiguration parse(String origin, String address) {
        try {
            if (origin == null || address == null || !address.matches("(?:0|[1-9][0-9]{0,2})(?:[.](?:0|[1-9][0-9]{0,2})){3}")) throw new IllegalArgumentException();
            InetAddress selected = InetAddress.getByName(address);
            if (!selected.getHostAddress().equals(address) || !(selected.isLoopbackAddress() || selected.isSiteLocalAddress() || selected.isLinkLocalAddress())) throw new IllegalArgumentException();
            URI uri = new URI(origin);
            String host = uri.getHost();
            if (host != null && host.startsWith("[")) host = host.substring(1, host.length() - 1);
            String authority = address + ":8443";
            if (!"https".equals(uri.getScheme()) || uri.getPort() != 8443 || !address.equals(host)
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !"".equals(uri.getRawPath()) || !origin.equals("https://" + authority)) throw new IllegalArgumentException();
            return new PlayerListenerConfiguration(origin, address, authority);
        } catch (Exception invalid) { throw new IllegalArgumentException("INVALID_PLAYER_CONFIGURATION"); }
    }

    private static String configured(ServletContext context, String name) {
        String value = context == null ? null : context.getInitParameter(name);
        return value == null ? System.getenv(name) : value;
    }
}
