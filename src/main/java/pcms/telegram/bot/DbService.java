package pcms.telegram.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pcms.telegram.bot.domain.StandingsFilter;
import pcms.telegram.bot.domain.User;
import pcms.telegram.bot.repos.StandingsFilterRepo;
import pcms.telegram.bot.repos.UserRepo;

import java.util.List;

@Service
public class DbService {
    @Autowired
    UserRepo userRepo;
    @Autowired
    StandingsFilterRepo standingsFilterRepo;

    public DbService() {
        Main.dbService = this;
    }

    public Iterable<User> findUsers() {
        return userRepo.findAll();
    }

    public Iterable<User> findUsersByBotId(long botId){
        return userRepo.findByBotId(botId);
    }

    public List<User> findUsersByLoginAndPass(String login, String pass) {
        return userRepo.findUsersByLoginAndPass(login, pass);
    }

    public User saveUser(User user) {
        return userRepo.save(user);
    }

    public void deleteUser(long botId, long chatId) {
        userRepo.deleteByBotIdAndChatId(botId, chatId);
    }

    public void deleteUser(long botId, long chatId, String login, String pass) {
        userRepo.deleteByBotIdAndChatIdAndLoginAndPass(botId, chatId, login, pass);
    }

    public Iterable<StandingsFilter> findStandingsFiltersByBotId(long botId) {
        return standingsFilterRepo.findByBotId(botId);
    }

    public StandingsFilter saveStandingsFilter(StandingsFilter standingsFilter) {
        return standingsFilterRepo.save(standingsFilter);
    }

    public void deleteStandingsFilters(long botId, long chatId) {
        standingsFilterRepo.deleteByBotIdAndChatId(botId, chatId);
    }

}
