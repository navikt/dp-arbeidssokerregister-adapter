package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.ArbeidssokerregisterMediator
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.Periode
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaKonsumentListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ArbeidssøkerregisterMottak(
    private val arbeidssokerRegisterMediator: ArbeidssokerregisterMediator,
    private val configuration: Map<String, String>,
) : KafkaKonsumentListener {
    override val topic: String = configuration.getValue("ARBEIDSSOKERPERIODER_TOPIC")

    override fun onMessage(record: ConsumerRecord<String, String>) {
        logger.info { "Mottok melding om endring i arbeidssøkerperiode: ${record.value()}" }
        try {
            val hendelse = record.tilHendelse()
            arbeidssokerRegisterMediator.behandle(hendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun ConsumerRecord<String, String>.tilHendelse(): ArbeidssøkerperiodeHendelse =
    defaultObjectMapper.readValue(value(), Periode::class.java).let { periode ->
        ArbeidssøkerperiodeHendelse(
            ident = periode.identitetsnummer,
            periodeId = periode.id,
            startDato = periode.startet.tidspunkt.toLocalDateTime(),
            sluttDato = periode.avsluttet?.tidspunkt?.toLocalDateTime(),
        )
    }

private fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
