package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaProdusent
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerstatusBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.Behovmelding
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BekreftelseBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.OvertaBekreftelseBehov
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit.DAYS

class BehovløserMediator(
    private val rapidsConnection: RapidsConnection,
    private val overtaBekreftelseKafkaProdusent: KafkaProdusent<OvertaArbeidssøkerBekreftelseMelding>,
    private val bekreftelseKafkaProdusent: KafkaProdusent<BekreftelseMelding>,
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
        overtaBekreftelseKafkaProdusent.send(key = behov.periodeId, value = OvertaArbeidssøkerBekreftelseMelding(behov.periodeId))
        sikkerlogg.info { "Sendt overtagelse av bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret" }
        publiserLøsning(behov, "OK")
    }

    fun behandle(behov: BekreftelseBehov) {
        sikkerlogg.info { "Behandler bekreftelsesbehov $behov" }
        // TODO: Skal key være periodeId eller partisjonsnøkkel?
        bekreftelseKafkaProdusent.send(key = behov.periodeId, value = behov.tilBekreftelsesMelding())
        sikkerlogg.info { "Sendt bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret." }
        publiserLøsning(behov, "OK")
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
    val bekreftelsesLøsning: BekreftelsesLøsning = BekreftelsesLøsning.DAGPENGER,
    val start: Start = Start(),
) {
    data class Start(
        val intervalMS: Long = DAYS.toMillis(14),
        val graceMS: Long = DAYS.toMillis(8),
    )
}

data class BekreftelseMelding(
    val periodeId: String,
    val id: UUID,
    val bekreftelsesLøsning: BekreftelsesLøsning = BekreftelsesLøsning.DAGPENGER,
    val svar: Svar,
) {
    data class Svar(
        val sendtInnAv: Metadata = Metadata(),
        val gjelderFra: Long,
        val gjelderTil: Long,
        val harJobbetIDennePerioden: Boolean,
        val vilFortsetteSomArbeidssoeker: Boolean,
    )

    data class Metadata(
        val tidspunkt: Long = LocalDateTime.now().tilMillis(),
        val utfoertAv: Bruker = Bruker("SYSTEM", BekreftelsesLøsning.DAGPENGER.name),
        val kilde: String = BekreftelsesLøsning.DAGPENGER.name,
        val aarsak: String = "Bruker sendte inn dagpengermeldekort",
    )

    // Er det sluttbruker eller system som bekrefter?
    data class Bruker(
        val type: String = "SLUTTBRUKER",
        val id: String,
    )
}

enum class BekreftelsesLøsning {
    DAGPENGER,
}

private fun BekreftelseBehov.tilBekreftelsesMelding(): BekreftelseMelding =
    BekreftelseMelding(
        periodeId = this.periodeId,
        id = UUID.randomUUID(), // TODO: partisjonsnøkkel?
        svar =
            BekreftelseMelding.Svar(
                gjelderFra = this.meldeperiode.fraOgMed.tilMillis(),
                gjelderTil = this.meldeperiode.tilOgMed.tilMillis(),
                harJobbetIDennePerioden = this.arbeidet,
                vilFortsetteSomArbeidssoeker = this.arbeidssøkerNestePeriode,
            ),
    )

fun LocalDateTime.tilMillis(): Long = this.toInstant(ZoneOffset.UTC).toEpochMilli()
