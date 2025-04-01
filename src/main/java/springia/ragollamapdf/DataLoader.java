package springia.ragollamapdf;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);
    private final VectorStore vectorStore;

    @Value("classpath:/cv-hadil-hammami.pdf")
    private Resource pdfResource;

    public DataLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void init() {
        try {
            if (!pdfResource.exists()) {
                logger.warn("Default PDF not found: {}. Initial vector store will be empty.", pdfResource.getFilename());
                return;
            }

            // Debug PDF content
            logger.info("Loading default PDF from: {}", pdfResource.getURL());

            // Read PDF with proper config
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();

            PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource, config);

            // Split text
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> documents = splitter.apply(reader.get());

            // Add metadata and debug content
            documents.forEach(doc -> {
                doc.getMetadata().put("source", "default-cv");
                logger.info("Content length: {}", doc.getText().length());
            });

            // Save to vector store
            vectorStore.accept(documents);
            logger.info("Loaded {} documents from default PDF", documents.size());
        } catch (Exception e) {
            logger.error("Failed to load default PDF", e);
            // Ne pas lancer d'exception pour permettre à l'application de démarrer même sans PDF par défaut
            logger.warn("Application will start with empty vector store");
        }
    }
}