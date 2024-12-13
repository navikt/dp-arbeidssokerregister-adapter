package no.nav.dagpenger.arbeidssokerregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerstatusBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BekreftelseBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.OvertaBekreftelseBehov

class BehovløserMediator {
    fun behandle(behov: ArbeidssøkerstatusBehov) {
        sikkerlogg.info { "Behandler arbeidssøkerbehov $behov" }
    }

    fun behandle(behov: OvertaBekreftelseBehov) {
        sikkerlogg.info { "Behandler overtagelse av bekreftelse-behov $behov" }
    }

    fun behandle(behov: BekreftelseBehov) {
        sikkerlogg.info { "Behandler bekreftelsesbehov $behov" }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
