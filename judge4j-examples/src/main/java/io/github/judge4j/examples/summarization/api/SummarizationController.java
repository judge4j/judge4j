package io.github.judge4j.examples.summarization.api;

import io.github.judge4j.examples.summarization.service.SummarizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for the document summarization service.
 *
 * <pre>
 * POST /api/v1/summaries        summarize a document
 * GET  /api/v1/summaries/styles list the supported summary styles
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/v1/summaries", produces = MediaType.APPLICATION_JSON_VALUE)
public class SummarizationController {

    private final SummarizationService summarizationService;

    public SummarizationController(SummarizationService summarizationService) {
        this.summarizationService = summarizationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public SummaryResponse summarize(@Valid @RequestBody SummarizeRequest request) {
        return summarizationService.summarize(request);
    }

    @GetMapping("/styles")
    public List<SummaryStyle> styles() {
        return List.of(SummaryStyle.values());
    }
}
