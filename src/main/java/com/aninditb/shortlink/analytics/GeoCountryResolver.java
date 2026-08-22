package com.aninditb.shortlink.analytics;

import com.maxmind.geoip2.GeoIp2Provider;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;

@Component
public class GeoCountryResolver {

    private static final String UNKNOWN = "UNKNOWN";

    // Typed as the GeoIp2Provider interface (DatabaseReader's own declared contract for
    // country()), not the concrete DatabaseReader class: @Lazy here needs Spring to build a
    // proxy that defers opening the database file until first real use (see GeoIpConfig), and
    // DatabaseReader has no visible constructor for CGLIB to subclass - an interface-typed
    // injection point lets Spring use a JDK dynamic proxy instead, which has no such
    // restriction.
    private final GeoIp2Provider databaseReader;

    public GeoCountryResolver(@Lazy GeoIp2Provider databaseReader) {
        this.databaseReader = databaseReader;
    }

    public String resolve(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return UNKNOWN;
        }

        try {
            InetAddress address = InetAddress.getByName(clientIp);
            CountryResponse response = databaseReader.country(address);
            String isoCode = response.getCountry().getIsoCode();
            return isoCode != null ? isoCode : UNKNOWN;
        } catch (GeoIp2Exception | IOException e) {
            return UNKNOWN;
        }
    }
}
