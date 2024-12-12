package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class KafkaKonsument(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    val topic: String,
    private val pollTimeoutInSeconds: Duration = Duration.ofSeconds(10),
) {
    private val listeners = mutableListOf<KafkaKonsumentListener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register(listener: KafkaKonsumentListener): KafkaKonsument {
        listeners.add(listener)
        return this
    }

    fun start() {
        scope.launch {
            kafkaConsumer.use { consumer ->
                consumer.subscribe(listOf(topic))
                try {
                    while (isActive) {
                        val records = consumer.pollSafely(pollTimeoutInSeconds) ?: continue
                        records.forEach { record ->
                            listeners.forEach { listener ->
                                runCatching { listener.onMessage(record) }.onFailure {
                                    logger.error {
                                        "Kunne ikke behandle melding: ${record.value()}, feilmelding: ${it.message}"
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    logger.info { "Stopper Kafka-consumer for topic: $topic" }
                }
            }
        }
    }

    fun stop() {
        scope.cancel("Stopper Kafka-consumer for topic: $topic")
    }

    private fun KafkaConsumer<String, String>.pollSafely(duration: Duration) =
        runCatching { poll(duration) }
            .onFailure {
                logger.error { "Kunne ikke konsumere meldinger: ${it.message}" }
            }.getOrNull()

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
