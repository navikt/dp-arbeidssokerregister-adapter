package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.BrukerResponse
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.MetadataResponse
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerstatusBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BehovType
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.OvertaBekreftelseBehov
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.kafka.MockKafkaProducer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

private val ident = "12345678910"

class BehovløserMediatorTest {
    private val rapidsConnection = TestRapid()
    private val kafkaProdusent = MockKafkaProducer<OvertaArbeidssøkerBekreftelseMelding>()
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()

    private val behovløserMediator = BehovløserMediator(rapidsConnection, kafkaProdusent, arbeidssøkerConnector)

    @BeforeEach
    fun reset() {
        rapidsConnection.reset()
        kafkaProdusent.reset()
    }

    @Test
    fun `kan løse ArbeidssøkerstatusBehov når bruker finnes i arbeidssøkerregisteret`() {
        val periodeId = UUID.randomUUID()
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any<String>()) } returns arbeidssøkerResponse(periodeId)

        val behov =
            ArbeidssøkerstatusBehov(
                ident = ident,
                innkommendePacket =
                    JsonMessage.newMessage(
                        eventName = "behov_arbeissokerstatus",
                        mapOf(
                            "@behov" to listOf(BehovType.Arbeidssøkerstatus.name),
                            "ident" to ident,
                        ),
                    ),
            )

        behovløserMediator.behandle(behov)
        with(rapidsConnection.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeissokerstatus"
            message(0)["@behov"][0].asText() shouldBe BehovType.Arbeidssøkerstatus.name
            message(0)["ident"].asText() shouldBe ident
            message(0)["@løsning"]["Arbeidssøkerstatus"]["verdi"]["periodeId"].asText() shouldBe periodeId.toString()
        }
    }

    @Test
    fun `kan løse ArbeidssøkerstatusBehov når bruker ikke finnes i arbeidssøkerregisteret`() {
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(any<String>()) } returns emptyList()

        val behov =
            ArbeidssøkerstatusBehov(
                ident = ident,
                innkommendePacket =
                    JsonMessage.newMessage(
                        eventName = "behov_arbeissokerstatus",
                        mapOf(
                            "@behov" to listOf(BehovType.Arbeidssøkerstatus.name),
                            "ident" to ident,
                        ),
                    ),
            )

        behovløserMediator.behandle(behov)
        with(rapidsConnection.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeissokerstatus"
            message(0)["@behov"][0].asText() shouldBe BehovType.Arbeidssøkerstatus.name
            message(0)["ident"].asText() shouldBe ident
            message(0)["@løsning"]["Arbeidssøkerstatus"]["verdi"].isEmpty shouldBe true
        }
    }

    @Test
    fun `skal overta bekreftelse av arbeidssøkerstatus for en periode`() {
        val periodeId = "9876543210"
        val overtaBekreftelseBehov =
            OvertaBekreftelseBehov(
                ident = ident,
                periodeId = periodeId,
                innkommendePacket =
                    JsonMessage.newMessage(
                        eventName = "behov_arbeissokerstatus",
                        mapOf(
                            "@behov" to listOf(BehovType.OvertaBekreftelse.name),
                            "ident" to ident,
                            "periodeId" to periodeId,
                        ),
                    ),
            )
        behovløserMediator.behandle(overtaBekreftelseBehov)

        val meldinger = kafkaProdusent.meldinger

        meldinger.size shouldBe 1
        with(meldinger.first()) {
            this.periodeId shouldBe periodeId
            bekreftelsesLøsning shouldBe OvertaArbeidssøkerBekreftelseMelding.BekreftelsesLøsning.DAGPENGER
            start.intervalMS shouldBe dagerTilMillisekunder(14)
            start.graceMS shouldBe dagerTilMillisekunder(8)
        }
    }
}

fun arbeidssøkerResponse(periodeId: UUID) =
    listOf(
        ArbeidssøkerperiodeResponse(
            periodeId = periodeId,
            startet =
                MetadataResponse(
                    tidspunkt = LocalDateTime.now().minusWeeks(3),
                    utfoertAv =
                        BrukerResponse(
                            type = "SLUTTBRUKER",
                            id = ident,
                        ),
                    kilde = "kilde",
                    aarsak = "aarsak",
                    tidspunktFraKilde = null,
                ),
            avsluttet = null,
        ),
    )

fun dagerTilMillisekunder(dager: Long): Long = dager * 24 * 60 * 60 * 1000
