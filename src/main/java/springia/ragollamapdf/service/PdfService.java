package springia.ragollamapdf.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;;

@Service
public
class PdfService {
    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    public PdfService(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    public String processPdfAndAnswerQuestion(MultipartFile file, String question) throws IOException {
        // Convertir le MultipartFile en fichier temporaire
        File tempFile = convertMultiPartToFile(file);

        try {
            // Configurer le lecteur PDF
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();

            // Créer le lecteur PDF avec le fichier temporaire
            PagePdfDocumentReader reader = new PagePdfDocumentReader(new FileSystemResource(tempFile), config);

            // Diviser le texte en morceaux
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> documents = splitter.apply(reader.get());

            // Ajouter des métadonnées et enregistrer le contenu pour débogage
            documents.forEach(doc -> {
                doc.getMetadata().put("source", "uploaded-pdf");
                logger.info("Content length: {}", doc.getText().length());
            });

            // Sauvegarder dans le vectorStore
            vectorStore.accept(documents);
            logger.info("Loaded {} documents from uploaded PDF", documents.size());

            // Récupérer les documents similaires
            List<Document> similarDocuments = vectorStore.similaritySearch(question);

            // Extraire le texte du document
            String documentContext = similarDocuments.stream()
                    .map(this::extractDocumentText)
                    .collect(Collectors.joining("\n\n"));

            // Préparer la requête complète
            String fullPrompt = String.format(
                    "Your task is to answer questions about the uploaded document, using the following document context:\n\n" +
                            "CONTEXT:\n%s\n\n" +
                            "QUESTION:\n%s",
                    documentContext,
                    question
            );

            // Appeler le modèle de chat avec la requête complète
            String response = chatModel.call(fullPrompt);

            logger.info("Generated Response for uploaded PDF: {}", response);
            return response;
        } finally {
            // Supprimer le fichier temporaire après utilisation
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String extractDocumentText(Document document) {
        try {
            // Plusieurs stratégies pour extraire le texte
            if (document.getMetadata() != null) {
                String[] possibleKeys = {"page_content", "text", "content", "document"};

                for (String key : possibleKeys) {
                    Object content = document.getMetadata().get(key);
                    if (content != null) {
                        return content.toString();
                    }
                }
            }

            return document.toString();
        } catch (Exception e) {
            logger.warn("Could not extract document text", e);
            return "Unable to extract document text";
        }
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        File convFile = new File(System.getProperty("java.io.tmpdir"), fileName);
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }
}