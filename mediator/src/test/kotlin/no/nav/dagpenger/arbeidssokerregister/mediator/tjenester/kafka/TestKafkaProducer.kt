package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.kafka.TestKafkaContainer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class TestKafkaProducer<T>(
    private val topic: String,
    private val container: TestKafkaContainer,
) {
    private val producer: KafkaProducer<String, String> = container.createProducer()

    fun send(
        key: String,
        message: String,
    ) {
        producer.send(ProducerRecord(topic, key, message))
        producer.flush()
    }
}
