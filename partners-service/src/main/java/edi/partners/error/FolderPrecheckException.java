package edi.partners.error;

import java.util.List;

/** La verificacion previa encontro problemas: no se toca ninguna carpeta. */
public class FolderPrecheckException extends RuntimeException {

    private final transient List<String> problems;

    public FolderPrecheckException(List<String> problems) {
        super("No se puede enlazar: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
