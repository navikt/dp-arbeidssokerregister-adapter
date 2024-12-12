package no.nav.dagpenger.arbeidssokerregister.mediator.hendelser

import java.util.UUID

data class Periode(
    val id: UUID,
    val identitetsnummer: String,
    val startet: Metadata,
    val avsluttet: Metadata?,
)

data class Metadata(
    val tidspunkt: Long,
    val utfoertAv: Bruker,
    val kilde: String,
    val aarsak: String,
    val tidspunktFraKilde: TidspunktFraKilde?,
)

data class Bruker(
    val type: String,
    val id: String,
)

data class TidspunktFraKilde(
    val tidspunkt: Long,
    val avviksType: String,
)
