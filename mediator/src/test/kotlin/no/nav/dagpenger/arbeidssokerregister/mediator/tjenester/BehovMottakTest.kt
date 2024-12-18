package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.arbeidssokerregister.mediator.BehovløserMediator
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class BehovMottakTest {
    private val testRapid = TestRapid()
    private val behovløserMediator = mockk<BehovløserMediator>(relaxed = true)

    init {
        BehovMottak(rapidsConnection = testRapid, behovløserMediator)
    }

    @Test
    fun `kan motta behov for arbeidssøkerstatus`() {
        val json = // language=JSON
            """
            {
              "@event_name": "behov_arbeissokerstatus",
              "@behov": ["Arbeidssøkerstatus"],
              "ident": "12345678910"
            }
            """.trimIndent()

        testRapid.sendTestMessage(json)

        verify(exactly = 1) { behovløserMediator.behandle(any<ArbeidssøkerstatusBehov>()) }
    }

    @Test
    fun `kan motta behov for å overta bekreftelse`() {
        val json = // language=JSON
            """
            {
              "@event_name": "behov_arbeissokerstatus",
              "@behov": ["OvertaBekreftelse"],
              "ident": "12345678910",
              "periodeId": "${UUID.randomUUID()}"
            }
            """.trimIndent()

        testRapid.sendTestMessage(json)

        verify(exactly = 1) { behovløserMediator.behandle(any<OvertaBekreftelseBehov>()) }
    }

    @Test
    fun `kan motta behov for å bekreftelse arbedissøkerstatus`() {
        val json = // language=JSON
            """
            {
              "@event_name": "behov_arbeissokerstatus",
              "@behov": ["Bekreftelse"],
              "ident": "12345678910",
              "periodeId": "${UUID.randomUUID()}",
              "meldeperiode": {
                "fraOgMed": "${LocalDateTime.now().minusDays(13)}",
                "tilOgMed": "${LocalDateTime.now()}"
              },
              "arbeidssøkerNestePeriode": "true",
              "arbeidet": "false"
            }
            """.trimIndent()

        testRapid.sendTestMessage(json)

        verify(exactly = 1) { behovløserMediator.behandle(any<BekreftelseBehov>()) }
    }
}
