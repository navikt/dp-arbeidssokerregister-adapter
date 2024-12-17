package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerstatusBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.Behovmelding
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BekreftelseBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.OvertaBekreftelseBehov

class BehovløserMediator(
    private val rapidsConnection: RapidsConnection,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    fun behandle(behov: ArbeidssøkerstatusBehov) {
        sikkerlogg.info { "Behandler arbeidssøkerbehov $behov" }
        val arbeidssøkerperiodeResponse =
            runBlocking { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(behov.ident) }.firstOrNull()

        publiserLøsning(behov, arbeidssøkerperiodeResponse)
    }

    fun behandle(behov: OvertaBekreftelseBehov) {
        sikkerlogg.info { "Behandler overtagelse av bekreftelse-behov $behov" }
    }

    fun behandle(behov: BekreftelseBehov) {
        sikkerlogg.info { "Behandler bekreftelsesbehov $behov" }
    }

    private fun publiserLøsning(
        behovmelding: Behovmelding,
        svarPåBehov: Any?,
    ) {
        leggLøsningPåBehovsmeling(behovmelding, svarPåBehov)
        rapidsConnection.publish(behovmelding.ident, behovmelding.innkommendePacket.toJson())
        sikkerlogg.info { "Løste behov ${behovmelding.behovType} med løsning: $svarPåBehov" }
    }

    private fun leggLøsningPåBehovsmeling(
        behovmelding: Behovmelding,
        svarPåBehov: Any?,
    ) {
        behovmelding.innkommendePacket["@løsning"] =
            mapOf(
                behovmelding.behovType.toString() to
                    mapOf(
                        "verdi" to svarPåBehov,
                    ),
            )
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall.BehovløserMediator")
    }
}
