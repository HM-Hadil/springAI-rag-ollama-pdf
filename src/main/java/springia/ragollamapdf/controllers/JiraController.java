package springia.ragollamapdf.controllers;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springia.ragollamapdf.service.MyJiraClient;

import java.util.List;

@RestController
@RequestMapping("/jira")
public class JiraController {

    private final MyJiraClient myJiraClient;

    @Autowired
    public JiraController(MyJiraClient myJiraClient){
        this.myJiraClient = myJiraClient;
    }

    @PostMapping("/createIssue")
    public ResponseEntity<String> createIssue(@RequestParam String projectKey,
                                              @RequestParam Long issueType,
                                              @RequestParam String issueSummary) {
        String issueKey = myJiraClient.createIssue(projectKey, issueType, issueSummary);
        return ResponseEntity.ok(issueKey);
    }

    @PutMapping("/updateIssueDescription")
    public ResponseEntity<Void> updateIssueDescription(@RequestParam String issueKey,
                                                       @RequestParam String newDescription) {
        myJiraClient.updateIssueDescription(issueKey, newDescription);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/issue/{issueKey}")
    public ResponseEntity<Issue> getIssue(@PathVariable String issueKey) {
        Issue issue = myJiraClient.getIssue(issueKey);
        return ResponseEntity.ok(issue);
    }

    @DeleteMapping("/issue/{issueKey}")
    public ResponseEntity<Void> deleteIssue(@PathVariable String issueKey,
                                            @RequestParam(defaultValue = "false") boolean deleteSubtasks) {
        myJiraClient.deleteIssue(issueKey, deleteSubtasks);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/addComment")
    public ResponseEntity<Void> addComment(@RequestParam String issueKey,
                                           @RequestParam String commentBody) {
        Issue issue = myJiraClient.getIssue(issueKey);
        myJiraClient.addComment(issue, commentBody);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/comments/{issueKey}")
    public ResponseEntity<List<Comment>> getAllComments(@PathVariable String issueKey) {
        List<Comment> comments = myJiraClient.getAllComments(issueKey);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/versions/{versionId}/tickets")
    public List<Issue> getTicketsByVersion(@PathVariable String versionId) {
        return myJiraClient.getTicketsByVersion(versionId);
    }
}