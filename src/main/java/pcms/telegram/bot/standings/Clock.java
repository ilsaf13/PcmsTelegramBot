package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import java.util.ResourceBundle;

public class Clock {
    long startTime;
    String status;
    String time;
    String length;
    boolean virtual;

    public Clock(JsonObject object) {
        startTime = object.getJsonNumber("startTime").longValue();
        status = object.getString("status");
        time = object.getString("time");
        length = object.getString("length");
        virtual = object.getBoolean("virtual");
    }

    public String getUpdates(Clock old, ResourceBundle standingsMessages) {
        if (old == null) return null;
//        System.out.printf("DEBUG: old status '%s' new status '%s'\n", old.status, status);
        if (!status.equals(old.status)) {
            if (status.equalsIgnoreCase("running"))
                return standingsMessages.getString("contestStarted") + "\n";
            else if (status.equalsIgnoreCase("over"))
                return standingsMessages.getString("contestOver") + "\n";
        }

        return null;
    }
}
