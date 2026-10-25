package edi.partners.dto;

import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MicAlgorithm;
import edi.partners.domain.SigningAlgorithm;

public record AlgorithmSuggestion(SigningAlgorithm signing, EncryptionAlgorithm encryption, MicAlgorithm mic,
                                  String reason) {
}
