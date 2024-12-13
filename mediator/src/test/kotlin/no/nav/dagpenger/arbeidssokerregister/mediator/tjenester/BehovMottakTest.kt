package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.arbeidssokerregister.mediator.BehovløserMediator
import org.junit.jupiter.api.Test

class BehovMottakTest {
    private val testRapid = TestRapid()
    private val behovløserMediator = mockk<BehovløserMediator>(relaxed = true)

    init {
        BehovMottak(rapidsConnection = testRapid, behovløserMediator)
    }

    @Test
    fun `kan motta behov`() {
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
}
