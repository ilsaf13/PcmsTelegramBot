package pcms.telegram.bot.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pcms.telegram.bot.domain.User;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    @Transactional
    void deleteByBotIdAndChatId(long botId, long chatId);

    @Transactional
    void deleteByBotIdAndChatIdAndLoginAndPass(long botId, long chatId, String login, String pass);

    List<User> findByBotId(long botId);

    List<User> findUsersByLoginAndPass(String login, String pass);
}
