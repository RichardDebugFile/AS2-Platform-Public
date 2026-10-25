package edi.partners.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Carpeta del socio dentro del recurso compartido del ERP (un nombre, no una ruta). */
public record SharedFolderRequest(@NotBlank @Size(max = 255) String sharedFolderName) {
}
