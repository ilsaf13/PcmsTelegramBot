package pcms.telegram.bot.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pcms.telegram.bot.domain.User;

import javax.transaction.Transactional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    @Transactional
    void deleteByChatId(long chatId);

    @Transactional
    void deleteByChatIdAndLoginAndPass(long chatId, String login, String pass);
}
