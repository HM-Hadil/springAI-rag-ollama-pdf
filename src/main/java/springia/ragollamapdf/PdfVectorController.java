package springia.ragollamapdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;
import springia.ragollamapdf.service.PdfService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class PdfVectorController {
    private static final Logger logger = LoggerFactory.getLogger(PdfVectorController.class);

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final PdfService pdfService;

    public PdfVectorController(VectorStore vectorStore, ChatModel chatModel, PdfService pdfService) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.pdfService = pdfService;
    }

    @GetMapping("/")
    public String simplify(@RequestParam(value = "question", defaultValue = "Summarize the document") String question) {
        try {
            // Retrieve similar documents
            List<Document> documents = vectorStore.similaritySearch(question);

            // Extract document text
            String documentContext = documents.stream()
                    .map(this::extractDocumentText)
                    .collect(Collectors.joining("\n\n"));

            // Log the context for debugging
            logger.info("Document Context: {}", documentContext);

            // Prepare the full prompt
            String fullPrompt = String.format(
                    "Your task is to answer questions about the document, using the following document context:\n\n" +
                            "CONTEXT:\n%s\n\n" +
                            "QUESTION:\n%s",
                    documentContext,
                    question
            );

            // Call the chat model with the full prompt
            String response = chatModel.call(fullPrompt);

            logger.info("Generated Response: {}", response);
            return response;

        } catch (Exception e) {
            logger.error("Error processing request", e);
            return "Sorry, an error occurred while processing your request: " + e.getMessage();
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadAndQuery(
            @RequestParam("file") MultipartFile file,
            @RequestParam("question") String question) {
        try {
            // Process the uploaded PDF and store in vector database
            String response = pdfService.processPdfAndAnswerQuestion(file, question);
            return response;
        } catch (Exception e) {
            logger.error("Error processing PDF upload", e);
            return "Error processing your PDF: " + e.getMessage();
        }
    }

    private String extractDocumentText(Document document) {
        try {
            // Multiple strategies to extract text
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
}

