package com.aninditb.shortlink.config;

import com.aninditb.shortlink.analytics.ClickEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String CLICK_EVENTS_TOPIC = "url.clicks.v1";
    public static final String CLICK_EVENTS_DLQ_TOPIC = "url.clicks.v1.dlq";
    public static final String ANALYTICS_CONSUMER_GROUP = "analytics-consumer";

    @Bean
    public NewTopic clickEventsTopic() {
        return TopicBuilder.name(CLICK_EVENTS_TOPIC).build();
    }

    @Bean
    public NewTopic clickEventsDlqTopic() {
        return TopicBuilder.name(CLICK_EVENTS_DLQ_TOPIC).build();
    }

    @Bean
    public ProducerFactory<String, ClickEvent> clickEventProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, ClickEvent> clickEventKafkaTemplate(
            ProducerFactory<String, ClickEvent> clickEventProducerFactory
    ) {
        return new KafkaTemplate<>(clickEventProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, ClickEvent> clickEventConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aninditb.shortlink.analytics");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ClickEvent.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, ANALYTICS_CONSUMER_GROUP);
        // A fresh/reset consumer group should replay from the beginning rather than silently
        // skip any backlog - a missed click event is permanently lost analytics data with no
        // visible symptom, whereas reprocessing a small backlog is harmless given the
        // consumer's event-id dedup (EventDedupService).
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler clickEventErrorHandler(KafkaTemplate<String, ClickEvent> clickEventKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                clickEventKafkaTemplate,
                (record, ex) -> new TopicPartition(CLICK_EVENTS_DLQ_TOPIC, record.partition())
        );
        // 3 total delivery attempts (the initial call plus 2 retries), 1s apart, then DLQ.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClickEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, ClickEvent> clickEventConsumerFactory,
            DefaultErrorHandler clickEventErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, ClickEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(clickEventConsumerFactory);
        factory.setCommonErrorHandler(clickEventErrorHandler);
        return factory;
    }
}
