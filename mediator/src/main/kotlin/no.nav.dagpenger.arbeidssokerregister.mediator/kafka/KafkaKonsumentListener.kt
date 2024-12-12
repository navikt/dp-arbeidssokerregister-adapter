package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

interface KafkaKonsumentListener {
    val topic: String

    fun onMessage(record: ConsumerRecord<String, String>)
}
