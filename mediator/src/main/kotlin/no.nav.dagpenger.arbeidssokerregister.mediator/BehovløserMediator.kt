package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.OvertaArbeidssøkerBekreftelseMelding.BekreftelsesLøsning.DAGPENGER
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaProdusent
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerstatusBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.Behovmelding
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BekreftelseBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.OvertaBekreftelseBehov
import java.util.concurrent.TimeUnit.DAYS

class BehovløserMediator(
    private val rapidsConnection: RapidsConnection,
    private val kafkaProdusent: KafkaProdusent<OvertaArbeidssøkerBekreftelseMelding>,
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
        // TODO: Måtte vi hente partisjonsnøkkel her?
        kafkaProdusent.send(key = behov.periodeId, value = OvertaArbeidssøkerBekreftelseMelding(behov.periodeId))
        sikkerlogg.info { "Sendt overtagelse av bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret" }
        // TODO: Skal vi svare ut/løse behovet her?
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

data class OvertaArbeidssøkerBekreftelseMelding(
    val periodeId: String,
    val bekreftelsesLøsning: BekreftelsesLøsning = DAGPENGER,
    val start: Start = Start(),
) {
    enum class BekreftelsesLøsning {
        DAGPENGER,
    }

    data class Start(
        val intervalMS: Long = DAYS.toMillis(14),
        val graceMS: Long = DAYS.toMillis(8),
    )
}
