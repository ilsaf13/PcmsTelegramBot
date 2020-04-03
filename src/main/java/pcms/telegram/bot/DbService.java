package pcms.telegram.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pcms.telegram.bot.domain.User;
import pcms.telegram.bot.repos.UserRepo;

@Service
public class DbService {
    @Autowired
    UserRepo userRepo;

    public DbService() {
        Main.dbService = this;
    }

    public Iterable<User> findUsers() {
        return userRepo.findAll();
    }

    public User saveUser(User user) {
        return userRepo.save(user);
    }

    public void deleteUserByChatId(long chatId) {
        userRepo.deleteByChatId(chatId);
    }

    public void deleteUserByChatIdAndLoginAndPass(long chatId, String login, String pass) {
        userRepo.deleteByChatIdAndLoginAndPass(chatId, login, pass);
    }

}
