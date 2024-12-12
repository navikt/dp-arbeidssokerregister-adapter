package no.nav.dagpenger.arbeidssokerregister.mediator.hendelser

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import java.time.LocalDateTime
import java.util.UUID

data class ArbeidssøkerperiodeHendelse(
    val ident: String,
    val periodeId: UUID,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime? = null,
) {
    fun asMessage(): JsonMessage {
        val map =
            mutableMapOf(
                "ident" to ident,
                "periodeId" to periodeId.toString(),
                "startDato" to startDato.toString(),
            )
        sluttDato?.let { map["sluttDato"] = it.toString() }
        return JsonMessage.newMessage("arbeidssøkerperiode_event", map)
    }
}
