package pcms.telegram.bot.domain;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="user_chats")
public class User {
    @Id
    long chatId;

    String login, pass, url;

    @Transient
    public Set<String> failedJobs = new HashSet<String>();
    @Transient
    public Set<String> undefinedJobs = new HashSet<String>();

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
}
