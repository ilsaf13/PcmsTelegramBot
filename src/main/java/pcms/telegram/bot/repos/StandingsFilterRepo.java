package pcms.telegram.bot.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import pcms.telegram.bot.domain.StandingsFilter;

import jakarta.transaction.Transactional;
import java.util.List;

public interface StandingsFilterRepo extends JpaRepository<StandingsFilter, Long> {

    @Transactional
    void deleteByBotIdAndChatId(long botId, long chatId);

    List<StandingsFilter> findByBotId(long botId);

}
