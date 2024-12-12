package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.ArbeidssøkerperiodeHendelse

class ArbeidssokerregisterMediator(
    private val rapidConnection: RapidsConnection,
) {
    fun behandle(hendelse: ArbeidssøkerperiodeHendelse) {
        sikkerlogg.info { "Behandler hendelse: $hendelse" }
        rapidConnection.publish(hendelse.asMessage().toJson())

        sikkerlogg.info { "Publiserte hendelse: ${hendelse.asMessage().toJson()}" }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
