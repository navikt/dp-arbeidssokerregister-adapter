package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.kafka

import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaProdusent

class MockKafkaProducer<T> : KafkaProdusent<T>() {
    private var isClosed = false
    private val _meldinger = mutableMapOf<String, T>() // mutableListOf<Pair<String, T>>()

    val meldinger: Map<String, T> get() = _meldinger

    override fun send(
        key: String,
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
