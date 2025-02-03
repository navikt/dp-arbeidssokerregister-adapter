package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.kafka.AivenConfig
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection.StatusListener
import io.ktor.server.engine.embeddedServer
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.arbeidssokerregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaFactory
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaKonfigurasjon
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaRunner
import no.nav.dagpenger.arbeidssokerregister.mediator.serializers.BekreftelseAvroSerializer
import no.nav.dagpenger.arbeidssokerregister.mediator.serializers.PaaVegneAvAvroSerializer
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.ArbeidssøkerregisterMottak
import no.nav.dagpenger.arbeidssokerregister.mediator.tjenester.BehovMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import org.apache.kafka.common.serialization.LongSerializer
import io.ktor.server.cio.CIO as CIOEngine

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : StatusListener {
    private val arbeidssøkerConnector = ArbeidssøkerConnector()

    // Kafka
    private val overtaBekreftelseTopic = configuration.getValue("OVERTA_BEKREFTELSE_TOPIC")
    private val bekreftelseTopic = configuration.getValue("BEKREFTELSE_TOPIC")
    private val kafkaKonfigurasjon = KafkaKonfigurasjon(kafkaServerKonfigurasjon, kafkaSchemaRegistryConfig)
    private val kafkaFactory = KafkaFactory(AivenConfig.default, kafkaSchemaRegistryConfig, kafkaKonfigurasjon)
    private val overtaBekreftelseKafkaProdusent =
        kafkaFactory.createProducer<Long, PaaVegneAv>(
            clientId = "teamdagpenger-arbeidssokerregister-producer",
            keySerializer = LongSerializer::class,
            valueSerializer = PaaVegneAvAvroSerializer::class,
        )

        /*KafkaFactory(AivenConfig.default, kafkaSchemaRegistryConfig)
            .createProducer<OvertaArbeidssøkerBekreftelseMelding>(topic = overtaBekreftelseTopic)*/
    private val bekreftelseKafkaProdusent =
        kafkaFactory.createProducer<Long, Bekreftelse>(
            clientId = "teamdagpenger-arbeidssokerregister-producer",
            keySerializer = LongSerializer::class,
            valueSerializer = BekreftelseAvroSerializer::class,
        )
        /*KafkaFactory(AivenConfig.default)
            .createProducer<BekreftelseMelding>(topic = bekreftelseTopic)*/

    private val rapidsConnection =
        RapidApplication.create(
            env = configuration,
            builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
        ) { engine, rapids ->
            with(engine.application) {
                internalApi()
            }
            val behovløserMediator =
                BehovløserMediator(
                    rapids,
                    overtaBekreftelseKafkaProdusent,
                    bekreftelseKafkaProdusent,
                    arbeidssøkerConnector,
                    overtaBekreftelseTopic,
                    bekreftelseTopic,
                )
            BehovMottak(rapidsConnection = rapids, behovløserMediator = behovløserMediator)
        }

    private val arbeidssøkerperiodeConsumer =
        KafkaRunner(
            kafkaConsumerFactory = kafkaFactory,
            listener = ArbeidssøkerregisterMottak(ArbeidssokerregisterMediator(rapidsConnection), configuration),
        )

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
        arbeidssøkerperiodeConsumer.start()
    }
}
