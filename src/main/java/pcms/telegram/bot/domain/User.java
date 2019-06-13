package pcms.telegram.bot.domain;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="user_chats")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    long id;

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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public static User get(List<User> userList, User user) {
        for (User u : userList) {
            if (user.equals(u)) {
                return u;
            }
        }
        return null;
    }

    public boolean equals(User user) {
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
}
