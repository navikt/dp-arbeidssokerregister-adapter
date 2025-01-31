package no.nav.dagpenger.arbeidssokerregister.mediator.serializers

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv

class PaaVegneAvAvroSerializer : SpecificAvroSerializer<PaaVegneAv>()

class BekreftelseAvroSerializer : SpecificAvroSerializer<Bekreftelse>()
