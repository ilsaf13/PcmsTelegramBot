package pcms.telegram.bot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class LoginPassUpdater implements Runnable {
    //maps login -> password
    private Map<String, String> logins;
    private boolean updated = false;
    private final Object loginsFlag = new Object();
    File namesFile;
    long namesFileModified;
    final long timeout = 1 * 60 * 1000;
    String genxmlsCommand;
    List<String> dirs;

    LoginPassUpdater(File namesFile, String genxmlsCommand, List<String> dirs) {
        this.genxmlsCommand = genxmlsCommand;
        this.namesFile = namesFile;
        this.dirs = dirs;
        try {
            synchronized (loginsFlag) {
                logins = getLoginsFromFile();
                namesFileModified = namesFile.lastModified();
            }
        } catch (Exception e) {
            System.out.printf("ERROR: Couldn't get logins from '%s'\n", namesFile.getAbsolutePath());
        }
    }

    @Override
    public void run() {
        while (true) {
            if (updated) {
                synchronized (loginsFlag) {
                    printLoginsToFile();
                    //todo: run genxmls
                    for (String dir : dirs) {
                        System.out.println("DEBUG: " + dir);
                        String run = genxmlsCommand.replace("$dir$", dir);
                        try {
                            Process process = Runtime.getRuntime().exec(run);
                            System.out.println("DEBUG: Executing " + run);
                            process.waitFor();
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("ERROR: Error running '" + run + "'");
                        }
                    }
                    updated = false;
                }
                System.out.println("DEBUG: Updated names file");
            }
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    Map<String, String> getLoginsFromFile() throws Exception {
        Map<String, String> logins = new HashMap<>();
        FileInputStream fis = new FileInputStream(namesFile);
        InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        String s;
        while ((s = br.readLine()) != null) {
            String[] parts = s.split("\t");
            if (parts.length == 3) {
                //old format: name login pass
                logins.put(parts[1], parts[2]);
            } else if (parts.length == 4) {
                //new format: id name login pass
                logins.put(parts[2], parts[3]);
            } else {
                System.out.printf("Wrong format of row '%s' in user DB '%s'\n", s, namesFile.getAbsolutePath());
            }
        }
        br.close();
        return logins;
    }

    void printLoginsToFile() {
        try {
            File tmpFile = new File(namesFile.getAbsolutePath() + ".tmp");
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(tmpFile), StandardCharsets.UTF_8));
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(namesFile), StandardCharsets.UTF_8));

            String s;
            while ((s = br.readLine()) != null) {
                String[] parts = s.split("\t");
                if (parts.length == 3) {
                    //old format: name login pass
                    parts[2] = logins.getOrDefault(parts[1], parts[2]);
                    pw.println("\t" + parts[0] + "\t" + parts[1] + "\t" + parts[2]);
                } else if (parts.length == 4) {
                    //new format: id name login pass
                    parts[3] = logins.getOrDefault(parts[2], parts[3]);
                    pw.println(parts[0] + "\t" + parts[1] + "\t" + parts[2] + "\t" + parts[3]);
                } else {
                    System.out.printf("Wrong format of row '%s' in user DB '%s'\n", s, namesFile.getAbsolutePath());
                    pw.println(s);
                }
            }
            br.close();
            pw.close();
            Files.move(tmpFile.toPath(), namesFile.toPath(), REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR: Exception while writing logins to file");
        }
    }

    String getPassword(String login) {
        synchronized (loginsFlag) {
            return logins.get(login);
        }
    }

    void putPassword(String login, String pass) {
        synchronized (loginsFlag) {
            logins.put(login, pass);
            updated = true;
        }
    }

    boolean isNamesFileUpdated() {
        return namesFile.lastModified() > namesFileModified;
    }

    boolean updateLoginsIfModified() {
        if (isNamesFileUpdated()) {
            try {
                synchronized (loginsFlag) {
                    logins = getLoginsFromFile();
                    namesFileModified = namesFile.lastModified();
                }
                return true;
            } catch (Exception e) {
                System.out.printf("ERROR: Couldn't get logins from '%s'\n", namesFile.getAbsolutePath());
            }
        }
        return false;
    }
}
