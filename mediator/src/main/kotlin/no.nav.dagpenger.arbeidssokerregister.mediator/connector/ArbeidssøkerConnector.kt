package no.nav.dagpenger.arbeidssokerregister.mediator.connector

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration
import no.nav.dagpenger.arbeidssokerregister.mediator.Configuration.defaultObjectMapper
import java.net.URI

class ArbeidssøkerConnector(
    private val arbeidssøkerregisterOppslagUrl: String = Configuration.arbeidssokerregisterOppslagUrl,
    private val arbeidssokerregisterRecordKeyUrl: String = Configuration.arbeidssokerregisterRecordKeyUrl,
    private val tokenProvider: () -> String? = Configuration.oppslagTokenProvider,
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun hentSisteArbeidssøkerperiode(ident: String): List<ArbeidssøkerperiodeResponse> =
        withContext(Dispatchers.IO) {
            val result =
                httpClient
                    .post(URI("$arbeidssøkerregisterOppslagUrl/api/v1/veileder/arbeidssoekerperioder").toURL()) {
                        bearerAuth(hentToken())
                        contentType(ContentType.Application.Json)
                        parameter("siste", true)
                        setBody(defaultObjectMapper.writeValueAsString(ArbeidssøkerperiodeRequestBody(ident)))
                    }.also {
                        logger.info { "Kall til arbeidssøkerregister for å hente arbeidssøkerperiode ga status ${it.status}" }
                        sikkerlogg.info {
                            "Kall til arbeidssøkerregister for å hente arbeidssøkerperiode for $ident ga status ${it.status}"
                        }
                    }

            if (result.status != HttpStatusCode.OK) {
                val body = result.bodyAsText()
                logger.warn { "Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode" }
                sikkerlogg.warn {
                    "Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode for $ident. Response: $body"
                }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av arbeidssøkerperiode")
            }
            result.body()
        }

    suspend fun hentRecordKey(ident: String): RecordKeyResponse =
        withContext(Dispatchers.IO) {
            val result =
                httpClient
                    .post(URI("$arbeidssokerregisterRecordKeyUrl/api/v1/record-key").toURL()) {
                        bearerAuth(hentToken()) // TODO: Mulig denne ikke trengs
                        contentType(ContentType.Application.Json)
                        setBody(defaultObjectMapper.writeValueAsString(RecordKeyRequestBody(ident)))
                    }.also {
                        logger.info { "Kall til arbeidssøkerregister for å hente record key ga status ${it.status}" }
                        sikkerlogg.info {
                            "Kall til arbeidssøkerregister for å hente record key for $ident ga status ${it.status}"
                        }
                    }

            if (result.status != HttpStatusCode.OK) {
                val body = result.bodyAsText()
                logger.warn { "Uforventet status ${result.status.value} ved henting av record key" }
                sikkerlogg.warn {
                    "Uforventet status ${result.status.value} ved henting av record key for $ident. Response: $body"
                }
                throw RuntimeException("Uforventet status ${result.status.value} ved henting av record key")
            }
            result.body()
        }

    private fun hentToken(): String = tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
