package springia.ragollamapdf.service;


import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class MyJiraClient {

    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.username}")
    private String username;

    @Value("${jira.password}")
    private String password;

    private JiraRestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = getJiraRestClient();
    }

    private JiraRestClient getJiraRestClient() {
        return new AsynchronousJiraRestClientFactory()
                .createWithBasicHttpAuthentication(getJiraUri(), this.username, this.password);
    }

    private URI getJiraUri() {
        return URI.create(this.jiraUrl);
    }

    public String createIssue(String projectKey, Long issueType, String issueSummary) {
        IssueRestClient issueClient = restClient.getIssueClient();

        // Créer le builder avec le projet et le résumé
        IssueInputBuilder issueBuilder = new IssueInputBuilder();
        issueBuilder.setProjectKey(projectKey)
                .setSummary(issueSummary);

        // Définir le type de problème correctement
        // Note: Il est préférable d'utiliser une valeur numérique pour issueType
        // Par exemple: 10001 pour "Story", 10002 pour "Task", etc.
        issueBuilder.setIssueTypeId(issueType);

        IssueInput newIssue = issueBuilder.build();
        return issueClient.createIssue(newIssue).claim().getKey();
    }
    public void updateIssueDescription(String issueKey, String newDescription) {
        IssueInput input = new IssueInputBuilder()
                .setDescription(newDescription)
                .build();
        restClient.getIssueClient()
                .updateIssue(issueKey, input)
                .claim();
    }

    /**
     * Récupère tous les tickets associés à une version Jira
     *
     * @param versionId L'identifiant ou le nom de la version Jira
     * @return Liste des tickets inclus dans cette version
     */
    public List<Issue> getTicketsByVersion(String versionId) {
        List<Issue> tickets = new ArrayList<>();

        try {
            // Requête JQL pour récupérer les tickets de cette version
            String jql = "fixVersion = '" + versionId + "'";

            // Exécuter la recherche (max 500 tickets)
            SearchResult result = restClient.getSearchClient()
                    .searchJql(jql, 500, 0, null)
                    .claim();

            // Ajouter les résultats à notre liste
            result.getIssues().forEach(tickets::add);

            return tickets;
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la récupération des tickets pour la version: " + versionId, e);
        }
    }

    public Issue getIssue(String issueKey) {
        return restClient.getIssueClient()
                .getIssue(issueKey)
                .claim();
    }

    public void deleteIssue(String issueKey, boolean deleteSubtasks) {
        restClient.getIssueClient()
                .deleteIssue(issueKey, deleteSubtasks)
                .claim();
    }

    public void addComment(Issue issue, String commentBody) {
        restClient.getIssueClient()
                .addComment(issue.getCommentsUri(), Comment.valueOf(commentBody));
    }

    public List<Comment> getAllComments(String issueKey) {
        return StreamSupport.stream(getIssue(issueKey).getComments().spliterator(), false)
                .collect(Collectors.toList());
    }
}