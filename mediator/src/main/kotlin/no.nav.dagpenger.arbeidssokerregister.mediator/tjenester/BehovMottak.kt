package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.River.PacketListener
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.BehovløserMediator
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BehovType.Arbeidssøkerstatus
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BehovType.Bekreftelse
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BehovType.OvertaBekreftelse
import java.time.LocalDateTime

class BehovMottak(
    val rapidsConnection: RapidsConnection,
    val behovløserMediator: BehovløserMediator,
) : PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov_arbeissokerstatus")
                    it.requireAllOrAny("@behov", BehovType.entries.map { behov -> behov.toString() })
                    it.requireKey("ident")
                    it.interestedIn("periodeId", "arbeidssøkerNestePeriode", "arbeidet", "meldeperiode")
                    it.forbid("@løsning")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        withMDC(
            mapOf("ident" to packet["ident"].asText()),
        ) {
            sikkerlogg.info { "Mottok behov: ${packet.toJson()}" }
            logger.info { "Mottok behov: ${packet.toJson()}" }
            try {
                val behov = BehovType.valueOf(packet.get("@behov")[0].asText())
                when (behov) {
                    Arbeidssøkerstatus -> behovløserMediator.behandle(packet.tilArbeidssøkerstatusBehov())
                    OvertaBekreftelse -> behovløserMediator.behandle(packet.tilOvertaBekreftelseBehov())
                    Bekreftelse -> behovløserMediator.behandle(packet.tilBekreftelseBehov())
                }
            } catch (e: Exception) {
                sikkerlogg.error(e) { "Mottak av behov feilet" }
                logger.error(e) { "Mottak av behov feilet" }
            }
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.BehovMottak")
    }
}

sealed class Behovmelding(
    open val ident: String,
    open val innkommendePacket: JsonMessage,
    val behovType: BehovType,
)

data class ArbeidssøkerstatusBehov(
    override val ident: String,
    override val innkommendePacket: JsonMessage,
) : Behovmelding(ident, innkommendePacket, Arbeidssøkerstatus)

data class OvertaBekreftelseBehov(
    override val ident: String,
    val periodeId: String,
    override val innkommendePacket: JsonMessage,
) : Behovmelding(ident, innkommendePacket, OvertaBekreftelse)

data class BekreftelseBehov(
    override val ident: String,
    val periodeId: String,
    val meldeperiode: Meldeperiode,
    val arbeidssøkerNestePeriode: Boolean,
    val arbeidet: Boolean,
    override val innkommendePacket: JsonMessage,
) : Behovmelding(ident, innkommendePacket, Bekreftelse) {
    data class Meldeperiode(
        val fraOgMed: LocalDateTime,
        val tilOgMed: LocalDateTime,
    )
}

fun JsonMessage.tilArbeidssøkerstatusBehov() = ArbeidssøkerstatusBehov(ident = this["ident"].asText(), innkommendePacket = this)

fun JsonMessage.tilOvertaBekreftelseBehov() =
    OvertaBekreftelseBehov(
        ident = this["ident"].asText(),
        periodeId = this["periodeId"].asText(),
        innkommendePacket = this,
    )

fun JsonMessage.tilBekreftelseBehov() =
    BekreftelseBehov(
        ident = this["ident"].asText(),
        periodeId = this["periodeId"].asText(),
        meldeperiode =
            BekreftelseBehov.Meldeperiode(
                fraOgMed = this["meldeperiode"]["fraOgMed"].asLocalDateTime(),
                tilOgMed = this["meldeperiode"]["tilOgMed"].asLocalDateTime(),
            ),
        arbeidssøkerNestePeriode = this["arbeidssøkerNestePeriode"].asBoolean(),
        arbeidet = this["arbeidet"].asBoolean(),
        innkommendePacket = this,
    )

enum class BehovType {
    Arbeidssøkerstatus,
    OvertaBekreftelse,
    Bekreftelse,
}
