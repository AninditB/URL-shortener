package com.aninditb.shortlink.analytics;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClickEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ClickEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final GeoCountryResolver geoCountryResolver = mock(GeoCountryResolver.class);
    private final ClickEventPublisher publisher = new ClickEventPublisher(kafkaTemplate, geoCountryResolver);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishesEventBuiltFromTheCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0.0.0");
        request.addHeader("Referer", "https://example.com/referring-page");
        request.setRemoteAddr("8.8.8.8");
        bindRequest(request);
        when(geoCountryResolver.resolve("8.8.8.8")).thenReturn("US");
        when(kafkaTemplate.send(eq("url.clicks.v1"), eq("java"), any(ClickEvent.class)))
                .thenReturn(new CompletableFuture<>());

        publisher.publish("java");

        var captor = org.mockito.ArgumentCaptor.forClass(ClickEvent.class);
        verify(kafkaTemplate).send(eq("url.clicks.v1"), eq("java"), captor.capture());
        ClickEvent event = captor.getValue();
        assertThat(event.shortCode()).isEqualTo("java");
        assertThat(event.userAgent()).contains("Chrome");
        assertThat(event.referrer()).isEqualTo("https://example.com/referring-page");
        assertThat(event.country()).isEqualTo("US");
        assertThat(event.deviceType()).isEqualTo("DESKTOP");
        assertThat(event.eventId()).isNotBlank();
    }

    @Test
    void doesNothingWhenNoRequestIsBound() {
        assertThatCode(() -> publisher.publish("java")).doesNotThrowAnyException();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void neverThrowsWhenKafkaSendFailsAsynchronously() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        bindRequest(request);
        when(geoCountryResolver.resolve(any())).thenReturn("UNKNOWN");
        CompletableFuture<SendResult<String, ClickEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq("url.clicks.v1"), eq("java"), any(ClickEvent.class))).thenReturn(failed);

        assertThatCode(() -> publisher.publish("java")).doesNotThrowAnyException();
    }

    private void bindRequest(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
