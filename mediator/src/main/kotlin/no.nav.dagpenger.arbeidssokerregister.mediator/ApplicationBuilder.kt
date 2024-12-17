package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection.StatusListener
import io.ktor.server.engine.embeddedServer
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaFactory
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaRunner
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerregisterMottak
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BehovMottak
import no.nav.helse.rapids_rivers.RapidApplication
import io.ktor.server.cio.CIO as CIOEngine

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : StatusListener {
    private val arbeidssøkerConnector = ArbeidssøkerConnector()

    private val rapidsConnection =
        RapidApplication.create(
            env = configuration,
            builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
        ) { engine, rapids ->
            with(engine.application) {
                internalApi()
            }
            BehovMottak(rapidsConnection = rapids, behovløserMediator = BehovløserMediator(rapids, arbeidssøkerConnector))
        }

    private val arbeidssøkerperiodeConsumer =
        KafkaRunner(
            kafkaConsumerFactory = KafkaFactory(AivenConfig.default),
            listener = ArbeidssøkerregisterMottak(ArbeidssokerregisterMediator(rapidsConnection)),
        )

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
        arbeidssøkerperiodeConsumer.start()
    }
}