package com.aninditb.shortlink.validation;

import com.aninditb.shortlink.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validator = new UrlSafetyValidator();

    @Test
    void acceptsWellFormedPublicHttpsUrl() {
        // Uses a literal public IP (Google public DNS) so the test doesn't depend on live DNS resolution.
        assertThatCode(() -> validator.validate("https://8.8.8.8/products/java"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> validator.validate("not a url"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> validator.validate(" "))
                .isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/file", "file:///etc/passwd"})
    void rejectsNonHttpSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLocalhostByName() {
        assertThatThrownBy(() -> validator.validate("http://localhost:8080/admin"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLoopbackIp() {
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1/admin"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsPrivateRangeIp() {
        assertThatThrownBy(() -> validator.validate("http://10.0.0.5/internal"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsValidAlias() {
        assertThatCode(() -> validator.validateAlias("java-2026")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAliasWithDisallowedCharacters() {
        assertThatThrownBy(() -> validator.validateAlias("bad alias!"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsTooShortAlias() {
        assertThatThrownBy(() -> validator.validateAlias("ab"))
                .isInstanceOf(InvalidUrlException.class);
    }
}
