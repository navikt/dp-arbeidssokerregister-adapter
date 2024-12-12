package no.nav.dagpenger.arbeidssokerregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.engine.embeddedServer
import no.nav.helse.rapids_rivers.RapidApplication
import io.ktor.server.cio.CIO as CIOEngine

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : RapidsConnection.StatusListener {

    private val rapidsConnection = RapidApplication.create(
            env = configuration,
            builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
        ) { engine, _ ->
            with(engine.application) {
                internalApi()
            }
        }

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
    }

}
