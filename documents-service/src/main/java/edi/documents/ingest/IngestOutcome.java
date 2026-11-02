package edi.documents.ingest;

/** CREATED: documento nuevo. DUPLICATE: el Message-ID ya estaba; se devuelve el existente sin crear otro. */
public enum IngestOutcome {
    CREATED,
    DUPLICATE
}
