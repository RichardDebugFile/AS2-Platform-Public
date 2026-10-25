package edi.partners.dto;

import java.util.List;

/** Algoritmos que la consola ofrece al configurar un socio, con su recomendacion. */
public record AlgorithmCatalog(List<Item> signing, List<Item> encryption, List<Item> mic) {

    /**
     * @param key    valor que se envia al API
     * @param label  nombre para mostrar
     * @param status {@code recommended}, {@code ok} o {@code legacy} (solo por compatibilidad)
     */
    public record Item(String key, String label, String status) {
    }
}
