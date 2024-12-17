package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.defaultObjectMapper

abstract class KafkaProdusent<T> {
    protected fun serialize(value: T): String = defaultObjectMapper.writeValueAsString(value)

    abstract fun send(
        key: String,
        value: T,
    )

    abstract fun close()
}
