package no.nav.dagpenger.arbeidssokerregister.mediator.kafka

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import io.confluent.kafka.serializers.subject.TopicNameStrategy
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SslConfigs

data class KafkaKonfigurasjon(
    val serverKonfigurasjon: KafkaServerKonfigurasjon,
    val schemaRegistryKonfigurasjon: KafkaSchemaRegistryConfig,
) {
    val properties: Map<String, Any?> =
        listOfNotNull(
            baseProperties,
            kafkaSecutiryProperties,
            schemaRegCredentialsProperties,
        ).reduce { acc, map -> acc + map }

    private val baseProperties: Map<String, Any?> get() =
        mapOf(
            "bootstrap.servers" to serverKonfigurasjon.kafkaBrokers,
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryKonfigurasjon.url,
            KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS to schemaRegistryKonfigurasjon.autoRegisterSchema,
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
            KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY to TopicNameStrategy::class.java.name,
        )

    private val kafkaSecutiryProperties: Map<String, Any?>? get() =
        if (serverKonfigurasjon.autentisering.equals("SSL", true)) {
            mapOf(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to serverKonfigurasjon.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to serverKonfigurasjon.credstorePassword,
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to serverKonfigurasjon.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to serverKonfigurasjon.credstorePassword,
            )
        } else {
            null
        }

    private val schemaRegCredentialsProperties: Map<String, Any?>? get() =
        schemaRegistryKonfigurasjon.username?.let {
            mapOf(
                SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                SchemaRegistryClientConfig.USER_INFO_CONFIG to
                    "${schemaRegistryKonfigurasjon.username}:${schemaRegistryKonfigurasjon.password}",
            )
        }
}

data class KafkaServerKonfigurasjon(
    val autentisering: String,
    val kafkaBrokers: String,
    val keystorePath: String?,
    val credstorePassword: String?,
    val truststorePath: String?,
)
