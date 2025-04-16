package springia.ragollamapdf.controllers;

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
import springia.ragollamapdf.service.JiraIntegrationService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class PdfVectorController {
    private static final Logger logger = LoggerFactory.getLogger(PdfVectorController.class);

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final PdfService pdfService;
    private final JiraIntegrationService jiraService;

    public PdfVectorController(
            VectorStore vectorStore,
            ChatModel chatModel,
            PdfService pdfService,
            JiraIntegrationService jiraService
    ) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.pdfService = pdfService;
        this.jiraService = jiraService;
    }

    @GetMapping("/")
    public String simplify(@RequestParam(value = "question", defaultValue = "Summarize the document") String question) {
        try {
            logger.info("Processing question: {}", question);

            // First, check if this is a simple chat message or greeting
            if (isSimpleInteraction(question)) {
                logger.info("Processing as simple interaction");
                return chatModel.call(question);
            }

            // Next, check if this might be a Jira-related question
            if (mightBeJiraQuery(question)) {
                logger.info("Processing as potential Jira query");
                return jiraService.processQuery(question);
            }

            // Otherwise, treat as a document query
            logger.info("Processing as document query");
            List<Document> documents = vectorStore.similaritySearch(question);

            // If we have no documents, just use Ollama directly
            if (documents == null || documents.isEmpty()) {
                logger.info("No relevant documents found, using direct LLM response");
                return chatModel.call(question);
            }

            // Extract document text
            String documentContext = documents.stream()
                    .map(this::extractDocumentText)
                    .collect(Collectors.joining("\n\n"));

            // Log the context for debugging
            logger.info("Document Context length: {}", documentContext.length());

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

            logger.info("Generated Response length: {}", response.length());
            return response;

        } catch (Exception e) {
            logger.error("Error processing request", e);
            // Forward the error to Ollama for appropriate response
            String errorPrompt = String.format(
                    "There was an error processing the request: %s. Please provide a helpful response to the user's question: %s",
                    e.getMessage(),
                    question
            );
            return chatModel.call(errorPrompt);
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
            // Forward the error to Ollama for appropriate response
            String errorPrompt = String.format(
                    "There was an error processing the PDF upload: %s. Please provide a helpful response to the user's question: %s",
                    e.getMessage(),
                    question
            );
            return chatModel.call(errorPrompt);
        }
    }

    @GetMapping("/jira")
    public String jiraQuery(@RequestParam("question") String question) {
        try {
            logger.info("Processing direct Jira query: {}", question);
            return jiraService.processQuery(question);
        } catch (Exception e) {
            logger.error("Error processing Jira query", e);
            // Forward the error to Ollama for appropriate response
            String errorPrompt = String.format(
                    "There was an error processing the Jira query: %s. Please provide a helpful response to the user's question: %s",
                    e.getMessage(),
                    question
            );
            return chatModel.call(errorPrompt);
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

    private boolean isSimpleInteraction(String question) {
        String normalized = question.toLowerCase().trim();
        return normalized.equals("hello") ||
                normalized.equals("hi") ||
                normalized.equals("hey") ||
                normalized.startsWith("hello ") ||
                normalized.startsWith("hi ") ||
                normalized.length() < 10; // Very short messages likely simple interactions
    }

    private boolean mightBeJiraQuery(String question) {
        String normalized = question.toLowerCase();
        return normalized.contains("jira") ||
                normalized.contains("issue") ||
                normalized.contains("bug") ||
                normalized.contains("task") ||
                normalized.contains("story") ||
                normalized.contains("ticket") ||
                normalized.contains("version") ||
                normalized.matches(".*\\b(v\\d+\\.\\d+\\.\\d+\\.\\d+)\\b.*");
    }
}