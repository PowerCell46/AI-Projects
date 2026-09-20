package com.peter_gerdzhikov.url_shortener_backend.utilities;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Resolves {@code host} via DNS and checks every returned address against loopback, link-local,
 * site-local (RFC1918) and unique-local (fc00::/7) ranges — this is what catches a hostname that
 * merely points at an internal address, not just an internal-looking literal. An unresolvable
 * host is treated as internal (fail closed).
 *
 * <p>Two literal encodings need handling before/alongside plain range checks, because Java's own
 * parsing of them diverges from another party's:
 * <ul>
 *     <li>The deprecated IPv4-compatible IPv6 form ({@code ::a.b.c.d}) resolves to an
 *     {@link Inet6Address} that none of the standard range checks recognise — unlike the IPv4-
 *     <i>mapped</i> form ({@code ::ffff:a.b.c.d}), which Java collapses to a plain
 *     {@link java.net.Inet4Address} on its own.
 *     <li>A dotted-decimal octet with a leading zero (e.g. {@code 0177.0.0.1}) is read as decimal
 *     by Java but as octal by browsers (WHATWG URL spec), so the two sides can resolve the same
 *     literal to different addresses. Such hosts are rejected outright rather than resolved.
 * </ul>
 */
public final class InternalHostGuard {

    private InternalHostGuard() {
    }

    public static boolean isInternal(String host) {
        if (hasAmbiguousOctalOctet(host)) {
            return true;
        }

        InetAddress[] resolvedAddresses;
        try {
            resolvedAddresses = InetAddress.getAllByName(host);

        } catch (UnknownHostException e) {
            return true;
        }

        for (InetAddress address : resolvedAddresses) {
            if (isInternalAddress(address)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInternalAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || isUniqueLocalIpv6(address)
                || isInternalIpv4CompatibleIpv6(address);
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] addressBytes = address.getAddress();
        return addressBytes.length == 16 && (addressBytes[0] & 0xfe) == 0xfc;
    }

    private static boolean isInternalIpv4CompatibleIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }

        byte[] addressBytes = address.getAddress();
        if (!isIpv4CompatibleForm(addressBytes)) {
            return false;
        }

        byte[] embeddedIpv4 = Arrays.copyOfRange(addressBytes, 12, 16);
        try {
            return isInternalAddress(InetAddress.getByAddress(embeddedIpv4));

        } catch (UnknownHostException e) {
            return true;
        }
    }

    private static boolean isIpv4CompatibleForm(byte[] addressBytes) {
        for (int i = 0; i < 12; i++) {
            if (addressBytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAmbiguousOctalOctet(String host) {
        String[] labels = host.split("\\.");
        boolean allLabelsNumeric = Arrays.stream(labels)
                .allMatch(label -> !label.isEmpty() && label.chars().allMatch(Character::isDigit));
        if (!allLabelsNumeric) {
            return false;
        }

        return Arrays.stream(labels).anyMatch(label -> label.length() > 1 && label.charAt(0) == '0');
    }
}
