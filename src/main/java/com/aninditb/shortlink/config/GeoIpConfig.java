package com.aninditb.shortlink.config;

import com.maxmind.geoip2.DatabaseReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.File;
import java.io.IOException;

@Configuration
public class GeoIpConfig {

    // @Lazy: opening a GeoLite2 database is real file I/O (multi-MB, memory-mapped) that
    // no request path needs until GeoCountryResolver's first real lookup - deferring it
    // means the app can boot without app.geoip.database-path pointing at a real database
    // until that first lookup actually happens, rather than requiring one just to start up.
    @Bean
    @Lazy
    public DatabaseReader geoIpDatabaseReader(
            @Value("${app.geoip.database-path}") String databasePath
    ) throws IOException {
        return new DatabaseReader.Builder(new File(databasePath)).build();
    }
}
