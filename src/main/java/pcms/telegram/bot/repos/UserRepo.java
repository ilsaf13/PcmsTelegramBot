package pcms.telegram.bot.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pcms.telegram.bot.domain.User;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
}
