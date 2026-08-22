package com.aninditb.shortlink.analytics;

import com.maxmind.geoip2.GeoIp2Provider;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeoCountryResolverTest {

    private final GeoIp2Provider databaseReader = mock(GeoIp2Provider.class);
    private final GeoCountryResolver resolver = new GeoCountryResolver(databaseReader);

    @Test
    void resolvesKnownIpToCountryIsoCode() throws Exception {
        CountryResponse response = countryResponse("US");
        when(databaseReader.country(any(InetAddress.class))).thenReturn(response);

        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("US");
    }

    @Test
    void returnsUnknownWhenAddressNotFound() throws Exception {
        when(databaseReader.country(any(InetAddress.class))).thenThrow(new AddressNotFoundException("not found"));

        assertThat(resolver.resolve("127.0.0.1")).isEqualTo("UNKNOWN");
    }

    @Test
    void returnsUnknownOnIOException() throws Exception {
        when(databaseReader.country(any(InetAddress.class))).thenThrow(new IOException("db read error"));

        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("UNKNOWN");
    }

    @Test
    void returnsUnknownOnUnexpectedRuntimeException() throws Exception {
        // Simulates a misconfigured/missing GeoIP database file: Spring's @Lazy proxy throws an
        // unchecked BeanCreationException on first real use, not the checked IOException a bad
        // *open* database would throw.
        when(databaseReader.country(any(InetAddress.class))).thenThrow(new RuntimeException("bean creation failed"));

        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("UNKNOWN");
    }

    @Test
    void returnsUnknownWhenCountryHasNoIsoCode() throws Exception {
        CountryResponse response = countryResponse(null);
        when(databaseReader.country(any(InetAddress.class))).thenReturn(response);

        assertThat(resolver.resolve("8.8.8.8")).isEqualTo("UNKNOWN");
    }

    @Test
    void returnsUnknownForNullClientIpWithoutTouchingDatabaseReader() {
        assertThat(resolver.resolve(null)).isEqualTo("UNKNOWN");

        verifyNoInteractions(databaseReader);
    }

    @Test
    void returnsUnknownForBlankClientIp() {
        assertThat(resolver.resolve("   ")).isEqualTo("UNKNOWN");

        verifyNoInteractions(databaseReader);
    }

    private CountryResponse countryResponse(String isoCode) {
        Country country = mock(Country.class);
        when(country.getIsoCode()).thenReturn(isoCode);
        CountryResponse response = mock(CountryResponse.class);
        when(response.getCountry()).thenReturn(country);
        return response;
    }
}
