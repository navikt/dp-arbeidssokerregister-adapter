package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.kafka

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

class TestKafkaContainer {
    private val kafkaContainer: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

    init {
        kafkaContainer.start()
    }

    fun createProducer(): KafkaProducer<String, String> {
        val producerConfig =
            mapOf(
                "bootstrap.servers" to kafkaContainer.bootstrapServers,
                "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
            )
        return KafkaProducer(producerConfig)
    }

    fun createConsumer(groupId: String = "test-group"): KafkaConsumer<String, String> {
        val consumerConfig =
            mapOf(
                "bootstrap.servers" to kafkaContainer.bootstrapServers,
                "group.id" to groupId,
                "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                "auto.offset.reset" to "earliest",
            )
        return KafkaConsumer(consumerConfig)
    }

    fun stop() {
        kafkaContainer.stop()
    }
}
