package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.sendDeferred
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerstatusBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.Behovmelding
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BekreftelseBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.OvertaBekreftelseBehov
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType.SYSTEM
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import no.nav.paw.bekreftelse.melding.v1.vo.Svar
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit.DAYS

class BehovløserMediator(
    private val rapidsConnection: RapidsConnection,
    private val overtaBekreftelseKafkaProdusent: Producer<Long, PaaVegneAv>,
    private val bekreftelseKafkaProdusent: Producer<Long, Bekreftelse>,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val overtaBekreftelseTopic: String,
    private val bekreftelseTopic: String,
) {
    fun behandle(behov: ArbeidssøkerstatusBehov) {
        sikkerlogg.info { "Behandler arbeidssøkerbehov $behov" }
        try {
            val arbeidssøkerperiode =
                runBlocking {
                    arbeidssøkerConnector.hentSisteArbeidssøkerperiode(behov.ident)
                }.firstOrNull()
                    ?.let {
                        ArbeidssøkerperiodeHendelse(
                            ident = behov.ident,
                            periodeId = it.periodeId,
                            startDato = it.startet.tidspunkt,
                            sluttDato = it.avsluttet?.tidspunkt,
                        )
                    }
            sikkerlogg.info { "Fant $arbeidssøkerperiode." }
            publiserLøsning(behov, arbeidssøkerperiode)
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Kunne ikke hente siste arbeidssøkerperiode for ident ${behov.ident}" }
            publiserLøsning(behovmelding = behov, svarPåBehov = null, feil = e)
        }
    }

    fun behandle(behov: OvertaBekreftelseBehov) =
        runBlocking {
            sikkerlogg.info { "Behandler overtagelse av bekreftelse-behov $behov" }
            try {
                val recordKeyResponse = runBlocking { arbeidssøkerConnector.hentRecordKey(behov.ident) }
                val record =
                    ProducerRecord(
                        overtaBekreftelseTopic,
                        recordKeyResponse.key,
                        OvertaArbeidssøkerBekreftelseMelding(UUID.fromString(behov.periodeId)).tilPaaVegneAv(),
                    )
                val metadata =
                    overtaBekreftelseKafkaProdusent
                        .sendDeferred(record)
                        .await()

                sikkerlogg.info {
                    "Sendt overtagelse av bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret. " +
                        "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
                }
                publiserLøsning(behov, "OK")
            } catch (e: Exception) {
                sikkerlogg.error(e) { "Kunne ikke overta bekreftelse for ident ${behov.ident}" }
                publiserLøsning(behovmelding = behov, svarPåBehov = null, feil = e)
            }
        }

    fun behandle(behov: BekreftelseBehov) =
        runBlocking {
            sikkerlogg.info { "Behandler bekreftelsesbehov $behov" }
            try {
                val recordKeyResponse = runBlocking { arbeidssøkerConnector.hentRecordKey(behov.ident) }
                val record =
                    ProducerRecord(
                        bekreftelseTopic,
                        recordKeyResponse.key,
                        behov.tilBekreftelse(),
                    )
                val metadata =
                    bekreftelseKafkaProdusent
                        .sendDeferred(record)
                        .await()

                sikkerlogg.info {
                    "Sendt bekreftelse for periodeId ${behov.periodeId} til arbeidssøkerregisteret. " +
                        "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
                }
                publiserLøsning(behov, "OK")
            } catch (e: Exception) {
                sikkerlogg.error(e) { "Kunne ikke sende bekreftelse for ident ${behov.ident}" }
                publiserLøsning(behovmelding = behov, svarPåBehov = null, feil = e)
            }
        }

    private fun publiserLøsning(
        behovmelding: Behovmelding,
        svarPåBehov: Any?,
        feil: Exception? = null,
    ) {
        leggLøsningPåBehovsmelding(behovmelding, svarPåBehov)
        leggFeilPåBehovsmelding(behovmelding, feil)
        sikkerlogg.info { "Publiserer løsning: ${behovmelding.innkommendePacket.toJson()}" }
        rapidsConnection.publish(behovmelding.ident, behovmelding.innkommendePacket.toJson())
        sikkerlogg.info { "Svarte på behov ${behovmelding.behovType} med løsning: $svarPåBehov og feil $feil" }
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

    private fun leggFeilPåBehovsmelding(
        behovmelding: Behovmelding,
        feilmelding: Exception?,
    ) {
        behovmelding.innkommendePacket["@feil"] =
            mapOf(
                behovmelding.behovType.toString() to
                    mapOf(
                        "verdi" to feilmelding?.message,
                    ),
            )
    }

    companion object {
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.BehovløserMediator")
    }
}

data class OvertaArbeidssøkerBekreftelseMelding(
    val periodeId: UUID,
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

private fun OvertaArbeidssøkerBekreftelseMelding.tilPaaVegneAv(): PaaVegneAv =
    PaaVegneAv(this.periodeId, DAGPENGER, Start(this.start.intervalMS, this.start.graceMS))

private fun BekreftelseBehov.tilBekreftelse(): Bekreftelse =
    Bekreftelse(
        UUID.fromString(this.periodeId),
        Bekreftelsesloesning.DAGPENGER,
        UUID.randomUUID(),
        Svar(
            Metadata(
                Instant.now(),
                Bruker(SYSTEM, BekreftelsesLøsning.DAGPENGER.name),
                BekreftelsesLøsning.DAGPENGER.name,
                "Bruker sendte inn dagpengermeldekort",
            ),
            this.meldeperiode.fraOgMed.toInstant(ZoneOffset.UTC),
            this.meldeperiode.tilOgMed.toInstant(ZoneOffset.UTC),
            this.arbeidet,
            this.arbeidssøkerNestePeriode,
        ),
    )
