package za.co.solar.fleet.integration.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Guards operator-configured vendor URLs against server-side request forgery: only https (http opt-in), no embedded
 * credentials, and no loopback / link-local / private / metadata addresses unless explicitly allowed.
 * Note: this checks the addresses a name resolves to at validation time; callers must also disable redirects.
 */
public final class OutboundUrlValidator {
    private OutboundUrlValidator() {}

    public static URI validate(String url, boolean allowHttp, boolean allowPrivateNetworks) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Base URL is required.");
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base URL is not a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
            throw new IllegalArgumentException("Base URL must use https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("Base URL has no host.");
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("Base URL must not contain credentials.");
        if (uri.getPort() == 0 || uri.getPort() > 65535) throw new IllegalArgumentException("Base URL has an invalid port.");
        if (!allowPrivateNetworks) {
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(uri.getHost());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Base URL host could not be resolved.");
            }
            for (InetAddress address : addresses) {
                if (isInternal(address)) {
                    throw new IllegalArgumentException("Base URL points to a private or local network address.");
                }
            }
        }
        return uri;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) return true;
        byte[] b = a.getAddress();
        if (b.length == 4) { // 100.64.0.0/10 carrier-grade NAT
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
        }
        return b.length == 16 && (b[0] & 0xFE) == 0xFC; // fc00::/7 unique-local IPv6
    }
}
