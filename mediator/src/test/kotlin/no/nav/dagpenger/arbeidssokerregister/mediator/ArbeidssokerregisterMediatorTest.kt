package no.nav.dagpenger.arbeidssokerregister.mediator

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.ArbeidssøkerperiodeHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssokerregisterMediatorTest {
    private lateinit var testRapid: TestRapid
    private lateinit var arbeidssokerregisterMediator: ArbeidssokerregisterMediator

    @BeforeEach
    fun setup() {
        testRapid = TestRapid()
        arbeidssokerregisterMediator = ArbeidssokerregisterMediator(testRapid)
    }

    @Test
    fun `kan behandle arbeidssøkerperiode hendelse`() {
        val arbeidssøkerperiodeHendelse =
            ArbeidssøkerperiodeHendelse(
                ident = "12345678910",
                periodeId = UUID.randomUUID(),
                startDato = LocalDateTime.of(2024, 9, 1, 0, 0, 0),
            )

        arbeidssokerregisterMediator.behandle(arbeidssøkerperiodeHendelse)

        with(testRapid.inspektør) {
            size shouldBe 1
            message(0) shouldEqual arbeidssøkerperiodeHendelse
        }
    }

    @Test
    fun `kan behandle avsluttet arbeidssøkerperiode hendelse`() {
        val arbeidssøkerperiodeHendelse =
            ArbeidssøkerperiodeHendelse(
                ident = "12345678910",
                periodeId = UUID.randomUUID(),
                startDato = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                sluttDato = LocalDateTime.of(2024, 1, 14, 0, 0, 0),
            )

        arbeidssokerregisterMediator.behandle(arbeidssøkerperiodeHendelse)

        with(testRapid.inspektør) {
            size shouldBe 1
            message(0) shouldEqual arbeidssøkerperiodeHendelse
        }
    }
}

infix fun JsonNode.shouldEqual(event: ArbeidssøkerperiodeHendelse) {
    this["ident"].asText() shouldBe event.ident
    this["periodeId"].asText() shouldBe event.periodeId.toString()
    this["startDato"].asText() shouldBe event.startDato.toString()
    this["sluttDato"]?.asText() shouldBe event.sluttDato?.toString()
}
