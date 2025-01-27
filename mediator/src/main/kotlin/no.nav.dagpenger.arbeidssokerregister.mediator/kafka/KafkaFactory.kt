package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import com.github.navikt.tbd_libs.kafka.Config
import com.github.navikt.tbd_libs.kafka.ConsumerProducerFactory
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.APP_NAME
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

class KafkaFactory(
    private val kafkaConfig: Config,
) {
    fun createConsumer(topic: String): KafkaKonsument {
        val kafkaConsumer = ConsumerProducerFactory(kafkaConfig).createConsumer(APP_NAME)
        return KafkaKonsument(kafkaConsumer, topic)
    }

    fun <T> createProducer(topic: String): KafkaProdusent<T> {
        val kafkaProducer = createProducer(kafkaConfig)
        return KafkaProdusentImpl(kafkaProducer, topic)
    }

    private fun createProducer(
        config: Config,
        properties: Properties = Properties(),
        withShutdownHook: Boolean = true,
    ): KafkaProducer<Long, String> =
        KafkaProducer(config.producerConfig(properties), LongSerializer(), StringSerializer()).also {
            if (withShutdownHook) {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        it.close()
                    },
                )
            }
        }
}
