package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class KafkaRunner(
    private val kafkaConsumerFactory: KafkaFactory,
    private val listener: KafkaKonsumentListener,
) {
    private val kafkaConsumer by lazy {
        kafkaConsumerFactory.createConsumer(listener.topic).apply {
            register(listener)
        }
    }

    fun start() {
        logger.info { "Starter Kafka-consumer for topic: ${listener.topic}" }
        try {
            kafkaConsumer.start()
        } catch (e: Exception) {
            logger.error(e) { "En feil oppstod i Kafka-consumer for topic: ${listener.topic}" }
            stop()
        }
    }

    fun stop() {
        logger.info { "Stopper Kafka-consumer for topic: ${listener.topic}" }
        runCatching { kafkaConsumer.stop() }
            .onFailure { logger.error(it) { "En feil oppstod under lukking av Kafka-consumer for topic: ${listener.topic}" } }
    }
}
