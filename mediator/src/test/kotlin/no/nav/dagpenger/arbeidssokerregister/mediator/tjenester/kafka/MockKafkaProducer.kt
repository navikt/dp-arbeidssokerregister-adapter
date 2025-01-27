package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.kafka

import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaProdusent

class MockKafkaProducer<T> : KafkaProdusent<T>() {
    private var isClosed = false
    private val _meldinger = mutableMapOf<Long, T>()

    val meldinger: Map<Long, T> get() = _meldinger

    override fun send(
        key: Long,
        value: T,
    ) {
        check(!isClosed) { "Cannot send message. Producer is closed." }
        _meldinger[key] = value
    }

    override fun close() {
        isClosed = true
    }

    fun reset() {
        _meldinger.clear()
    }
}
