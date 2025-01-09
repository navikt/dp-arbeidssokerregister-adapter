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
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.ArbeidssøkerperiodeHendelse

class BehovløserMediator(
    private val rapidsConnection: RapidsConnection,
    private val overtaBekreftelseKafkaProdusent: KafkaProdusent<OvertaArbeidssøkerBekreftelseMelding>,
    private val bekreftelseKafkaProdusent: KafkaProdusent<BekreftelseMelding>,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    fun behandle(behov: ArbeidssøkerstatusBehov) {
        sikkerlogg.info { "Behandler arbeidssøkerbehov $behov" }
        val arbeidssøkerperiode =
            runBlocking {
                arbeidssøkerConnector.hentSisteArbeidssøkerperiode(behov.ident) }
                .firstOrNull()
                ?.let {
                    ArbeidssøkerperiodeHendelse(
                        ident = behov.ident,
                        periodeId = it.periodeId,
                        startDato = it.startet.tidspunkt,
                        sluttDato = it.avsluttet?.tidspunkt
                    )
                }

        publiserLøsning(behov, arbeidssøkerperiode)
    }

    fun behandle(behov: OvertaBekreftelseBehov) {
        sikkerlogg.info { "Behandler overtagelse av bekreftelse-behov $behov" }
        try {
            val recordKeyResponse = runBlocking { arbeidssøkerConnector.hentRecordKey(behov.ident) }
            overtaBekreftelseKafkaProdusent.send(
                key = recordKeyResponse.key.toString(),
                value = OvertaArbeidssøkerBekreftelseMelding(behov.periodeId),
            )
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Kunne ikke overta bekreftelse for ident ${behov.ident}" }
            publiserFeil(behov, e)
            return
        }
        sikkerlogg.info { "Sendt overtagelse av bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret" }
        publiserLøsning(behov, "OK")
    }

    fun behandle(behov: BekreftelseBehov) {
        sikkerlogg.info { "Behandler bekreftelsesbehov $behov" }
        try {
            val recordKeyResponse = runBlocking { arbeidssøkerConnector.hentRecordKey(behov.ident) }
            bekreftelseKafkaProdusent.send(key = recordKeyResponse.key.toString(), value = behov.tilBekreftelsesMelding())
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Kunne ikke sende bekreftelse for ident ${behov.ident}" }
            publiserFeil(behov, e)
            return
        }

        sikkerlogg.info { "Sendt bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret." }
        publiserLøsning(behov, "OK")
    }

    private fun publiserLøsning(
        behovmelding: Behovmelding,
        svarPåBehov: Any?,
    ) {
        leggLøsningPåBehovsmelding(behovmelding, svarPåBehov)
        rapidsConnection.publish(behovmelding.ident, behovmelding.innkommendePacket.toJson())
        sikkerlogg.info { "Løste behov ${behovmelding.behovType} med løsning: $svarPåBehov" }
    }

    private fun leggLøsningPåBehovsmelding(
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

    private fun publiserFeil(
        behovmelding: Behovmelding,
        e: Exception,
    ) {
        sikkerlogg.error(e) { "Feil ved behandling av behov ${behovmelding.behovType}" }
        leggFeilPåBehovsmelding(behovmelding, e.message)
        rapidsConnection.publish(behovmelding.ident, behovmelding.innkommendePacket.toJson())
    }

    private fun leggFeilPåBehovsmelding(
        behovmelding: Behovmelding,
        feilmelding: String?,
    ) {
        behovmelding.innkommendePacket["@feil"] =
            mapOf(
                behovmelding.behovType.toString() to
                    mapOf(
                        "verdi" to feilmelding,
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
