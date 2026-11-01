package edi.documents.web;

import edi.documents.query.ConsistencyService;
import edi.documents.query.ConsistencyService.Report;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Control de consistencia entre la base y el repositorio en disco (HU-14). */
@RestController
@RequestMapping("/api/consistency")
public class ConsistencyController {

    private final ConsistencyService consistency;

    public ConsistencyController(ConsistencyService consistency) {
        this.consistency = consistency;
    }

    @GetMapping("/documents")
    public Report documents() {
        return consistency.documents();
    }
}
