# dp-arbeidssokerregister-adapter
Adapter som henter data fra Arbeidssokerregisteret og tilgjengeliggjør meldinger for dagpengetjenester på rapids and rivers.

## Funksjonalitet
Adapteret tilbyr følgende funksjonalitet:
- Videreformidling av meldinger fra arbeidssøkerregisterets kafka-topic `paw.arbeidssokerperioder-v1`. Alle endringer i arbeidssøkerperioder tilgjengeligjøres her.
- Behovløsere for:
  - Hente siste arbeidssøkerperiode for en gitt person
  - Overta/frasi ansvar for bekreftelse av periode
  - Bekrefte periode

## Komme i gang

Gradle brukes som byggverktøy og er bundlet inn.

`./gradlew build`

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
