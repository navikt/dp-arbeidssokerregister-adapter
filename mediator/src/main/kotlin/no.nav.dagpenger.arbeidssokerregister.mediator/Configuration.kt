package no.nav.dagpenger.arbeidssokerregister.mediator

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaSchemaRegistryConfig
import no.nav.dagpenger.arbeidssokerregister.mediator.kafka.KafkaServerKonfigurasjon
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config.AzureAd

internal object Configuration {
    const val APP_NAME = "dp-arbeidssokerregister-adapter"
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "RAPID_APP_NAME" to APP_NAME,
                "KAFKA_CONSUMER_GROUP_ID" to "dp-arbeidssokerregister-v1",
                "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
                "KAFKA_RESET_POLICY" to "LATEST",
            ),
        )

    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    val config: Map<String, String> =
        properties.list().reversed().fold(emptyMap()) { map, pair ->
            map + pair.second
        }

    val arbeidssokerregisterOppslagUrl by lazy {
        properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_HOST", stringType)].formatUrl()
    }

    val arbeidssokerregisterRecordKeyUrl by lazy {
        properties[Key("ARBEIDSSOKERREGISTER_RECORD_KEY_HOST", stringType)].formatUrl()
    }

    private val azureAdConfig by lazy { AzureAd(properties) }
    private val azureAdClient by lazy {
        CachedOauth2Client(
            tokenEndpointUrl = azureAdConfig.tokenEndpointUrl,
            authType = azureAdConfig.clientSecret(),
        )
    }
    val oppslagTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("ARBEIDSSOKERREGISTER_OPPSLAG_SCOPE", stringType)])
                    .accessToken ?: throw RuntimeException("Failed to get token")
            }
        }
    }
    val recordKeyTokenProvider: () -> String by lazy {
        {
            runBlocking {
                azureAdClient
                    .clientCredentials(properties[Key("ARBEIDSSOKERREGISTER_RECORD_KEY_SCOPE", stringType)])
                    .accessToken ?: throw RuntimeException("Failed to get token")
            }
        }
    }

    val kafkaSchemaRegistryConfig =
        KafkaSchemaRegistryConfig(
            url = properties[Key("KAFKA_SCHEMA_REGISTRY", stringType)],
            username = properties[Key("KAFKA_SCHEMA_REGISTRY_USER", stringType)],
            password = properties[Key("KAFKA_SCHEMA_REGISTRY_PASSWORD", stringType)],
            autoRegisterSchema = true,
            avroSpecificReaderConfig = true,
        )

    val kafkaServerKonfigurasjon =
        KafkaServerKonfigurasjon(
            autentisering = "SSL",
            kafkaBrokers = properties[Key("KAFKA_BROKERS", stringType)],
            keystorePath = properties.getOrNull(Key("KAFKA_KEYSTORE_PATH", stringType)),
            credstorePassword = properties.getOrNull(Key("KAFKA_CREDSTORE_PASSWORD", stringType)),
            truststorePath = properties.getOrNull(Key("KAFKA_TRUSTSTORE_PATH", stringType)),
        )

    val defaultObjectMapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun String.formatUrl(): String = if (this.startsWith("http")) this else "https://$this"
}
