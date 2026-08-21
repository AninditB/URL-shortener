package com.aninditb.shortlink.validation;

import com.aninditb.shortlink.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class UrlSafetyValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{3,32}");

    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL: " + rawUrl);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("Unsupported scheme in URL: " + rawUrl);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL is missing a host: " + rawUrl);
        }

        if (host.equalsIgnoreCase("localhost")) {
            throw new InvalidUrlException("URL host is not allowed: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("Unable to resolve URL host: " + host);
        }

        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                throw new InvalidUrlException("URL host resolves to a disallowed address range: " + host);
            }
        }
    }

    public void validateAlias(String alias) {
        if (alias == null || !ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidUrlException("Custom alias must match [A-Za-z0-9_-]{3,32}: " + alias);
        }
    }
}
