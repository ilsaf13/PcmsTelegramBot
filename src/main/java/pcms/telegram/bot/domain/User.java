package pcms.telegram.bot.domain;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "user_chats")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;
    long chatId;
    String login, pass, url;
    boolean watchRuns, watchQuestions;

    @Transient
    public Set<String> failedJobs = new HashSet<String>();
    @Transient
    public Set<String> undefinedJobs = new HashSet<String>();
    @Transient
    public Set<Long> questions = new HashSet<Long>();

    public boolean equals(Object o) {
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return chatId == user.chatId && login.equals(user.login) && pass.equals(user.pass);
    }

    public static String getLoginList(List<User> userList) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (User u : userList) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(u.getLogin());
            first = false;
        }
        return sb.toString();
    }

    public String toString() {
        return String.format("User %s, %swatching failed and undefined runs, %swatching questions.",
                login, watchRuns ? "" : "not ", watchQuestions ? "" : "not ");
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isWatchRuns() {
        return watchRuns;
    }

    public void setWatchRuns(boolean watchRuns) {
        this.watchRuns = watchRuns;
    }

    public boolean isWatchQuestions() {
        return watchQuestions;
    }

    public void setWatchQuestions(boolean watchQuestions) {
        this.watchQuestions = watchQuestions;
    }
}
