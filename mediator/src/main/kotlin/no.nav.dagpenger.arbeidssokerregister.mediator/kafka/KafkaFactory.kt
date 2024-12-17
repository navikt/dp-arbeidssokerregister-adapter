package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.APP_NAME

class KafkaFactory(
    private val kafkaConfig: Config,
) {
    fun createConsumer(topic: String): KafkaKonsument {
        val kafkaConsumer = ConsumerProducerFactory(kafkaConfig).createConsumer(APP_NAME)
        return KafkaKonsument(kafkaConsumer, topic)
    }

    fun <T> createProducer(topic: String): KafkaProdusent<T> {
        val kafkaProducer = ConsumerProducerFactory(kafkaConfig).createProducer()
        return KafkaProdusentImpl(kafkaProducer, topic)
    }
}
