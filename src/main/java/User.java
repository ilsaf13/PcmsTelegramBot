import java.util.HashSet;
import java.util.Set;

public class User {
    String login, pass, url;
    long chatId;
    Set<String> failedJobs = new HashSet<String>();
    Set<String> undefinedJobs = new HashSet<String>();
}
