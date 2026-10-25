package edi.partners.web;

import edi.partners.dto.AlgorithmCatalog;
import edi.partners.svc.AlgorithmPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogs")
public class CatalogController {

    private final AlgorithmPolicy algorithms;

    public CatalogController(AlgorithmPolicy algorithms) {
        this.algorithms = algorithms;
    }

    @GetMapping("/algorithms")
    public AlgorithmCatalog algorithms() {
        return algorithms.catalog();
    }
}
