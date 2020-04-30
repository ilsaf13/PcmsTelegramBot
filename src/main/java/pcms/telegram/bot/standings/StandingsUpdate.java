package pcms.telegram.bot.standings;

public class StandingsUpdate {
//    Session<Problem> session;
    Type type;
    String message;

    public String getMessage() {
        return message;
    }

    public StandingsUpdate(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public enum Type {
        SESSION, CLOCK;
    }
}