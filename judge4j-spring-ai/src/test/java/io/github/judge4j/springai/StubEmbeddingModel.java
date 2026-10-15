package io.github.judge4j.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Test double: maps known texts to fixed vectors and counts how many times it was called. */
final class StubEmbeddingModel implements EmbeddingModel {

    int calls;
    private final Map<String, float[]> vectors;

    StubEmbeddingModel(Map<String, float[]> vectors) {
        this.vectors = vectors;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls++;
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            float[] vector = vectors.get(text);
            if (vector == null) {
                throw new IllegalArgumentException("no stub vector for '" + text + "'");
            }
            embeddings.add(new Embedding(vector, index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        throw new UnsupportedOperationException();
    }
}
