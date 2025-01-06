package no.nav.dagpenger.arbeidssokerregister.mediator.tjenester

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.arbeidssokerregister.mediator.ArbeidssokerregisterMediator
import no.nav.dagpenger.arbeidssokerregister.mediator.hendelser.ArbeidssøkerperiodeHendelse
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaKonsument
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.kafka.TestKafkaContainer
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.TestKafkaProducer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class ArbeidssøkerregisterMottakTest {
    private val topic = "paw.arbeidssokerperioder-v1"
    private lateinit var testKafkaContainer: TestKafkaContainer

    private val arbeidssøkerregisterMediator: ArbeidssokerregisterMediator = mockk(relaxed = true)
    private lateinit var testProducer: TestKafkaProducer<String>
    private lateinit var testConsumer: KafkaConsumer<String, String>
    private lateinit var kafkaKonsument: KafkaKonsument

    @BeforeEach
    fun setup() {
        testKafkaContainer = TestKafkaContainer()
        testProducer = TestKafkaProducer(topic, testKafkaContainer)
        testConsumer = testKafkaContainer.createConsumer(topic)
        kafkaKonsument =
            KafkaKonsument(
                kafkaConsumer = testConsumer,
                topic = topic,
                pollTimeoutInSeconds = Duration.ofSeconds(1),
            ).apply {
                register(
                    ArbeidssøkerregisterMottak(
                        arbeidssøkerregisterMediator,
                        mapOf(
                            "ARBEIDSSOKERPERIODER_TOPIC" to topic,
                        ),
                    ),
                )
            }
    }

    @AfterEach
    fun tearDown() {
        kafkaKonsument.stop()
        testKafkaContainer.stop()
    }

    @Test
    fun `skal håndtere arbeidssøkerperiode-melding`() =
        runBlocking {
            kafkaKonsument.start()

            testProducer.send("key1", gyldigArbeidssøkerperiode)

            delay(2000)
            verify(exactly = 1) { arbeidssøkerregisterMediator.behandle(ofType<ArbeidssøkerperiodeHendelse>()) }

            kafkaKonsument.stop()
        }

    @Test
    fun `skal håndtere ugyldig arbeidssøkerperiode-melding`() =
        runBlocking {
            kafkaKonsument.start()

            testProducer.send("key1", ugylidaAbeidssøkerperiode)

            delay(2000)
            verify(exactly = 0) { arbeidssøkerregisterMediator.behandle(ofType<ArbeidssøkerperiodeHendelse>()) }

            kafkaKonsument.stop()
        }
}

val gyldigArbeidssøkerperiode =
    """
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "identitetsnummer": "12345678910",
      "startet": {
        "tidspunkt": 1672531200000,
        "utfoertAv": {
          "id": "system-user",
          "type": "SYSTEM"
        },
        "kilde": "system-api",
        "aarsak": "INITIAL_REGISTRATION",
        "tidspunktFraKilde": {
          "tidspunkt": 1672531200000,
          "avviksType": "EPOCH_MILLIS"
        }
      },
      "avsluttet": null
    }
    """.trimIndent()

val ugylidaAbeidssøkerperiode =
    """
    {
      "id": 123e4567, // Invalid JSON
      "identitetsnummer": "missing-brace"
    """.trimIndent()
