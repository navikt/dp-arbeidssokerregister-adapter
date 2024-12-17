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
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

private val ident = "12345678910"

class BehovløserMediatorTest {
    private val rapidsConnection = TestRapid()
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()

    private val behovløserMediator = BehovløserMediator(rapidsConnection, arbeidssøkerConnector)

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
                            "@behov" to listOf("Arbeidssøkerstatus"),
                            "ident" to ident,
                        ),
                    ),
            )

        behovløserMediator.behandle(behov)
        with(rapidsConnection.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeissokerstatus"
            message(0)["@behov"][0].asText() shouldBe "Arbeidssøkerstatus"
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
                            "@behov" to listOf("Arbeidssøkerstatus"),
                            "ident" to ident,
                        ),
                    ),
            )

        behovløserMediator.behandle(behov)
        with(rapidsConnection.inspektør) {
            size shouldBe 1
            message(0)["@event_name"].asText() shouldBe "behov_arbeissokerstatus"
            message(0)["@behov"][0].asText() shouldBe "Arbeidssøkerstatus"
            message(0)["ident"].asText() shouldBe ident
            message(0)["@løsning"]["Arbeidssøkerstatus"]["verdi"].isEmpty shouldBe true
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
