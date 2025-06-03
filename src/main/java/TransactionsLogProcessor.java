import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionsLogProcessor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern PATTERN = Pattern.compile("\\[(.*?)\\] (\\S+) (transferred|withdrew|balance inquiry) (\\S+)(?: to (\\S+))?");
    private static final String OUTPUT_DIR = "transactions_by_users";

    private static class LogEntry {
        LocalDateTime timestamp;
        String content;
        double delta;

        public LogEntry(LocalDateTime timestamp, String content, double delta) {
            this.timestamp = timestamp;
            this.content = content;
            this.delta = delta;
        }
    }

    public static void addEntry(Map<String, List<LogEntry>> map, String user, LogEntry entry) {
        map.computeIfAbsent(user, k -> new ArrayList<>()).add(entry);
    }

    public static void processLogs(Path logsDir) throws IOException {
        Map<String, List<LogEntry>> userLogs = new HashMap<>();
        Map<String, Double> balances = new HashMap<>();

        Files.createDirectories(logsDir.resolve(OUTPUT_DIR));

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, "*.log")) {
            for (Path file : stream) {
                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {
                    Matcher matcher = PATTERN.matcher(line);
                    if (!matcher.matches()) {
                        continue;
                    }


                    LocalDateTime date = LocalDateTime.parse(matcher.group(1), FORMATTER);
                    String user = matcher.group(2);
                    String op = matcher.group(3);
                    double amount = Double.parseDouble(matcher.group(4));
                    String target = matcher.group(5);

                    switch (op) {
                        case "balance inquiry":
                            addEntry(userLogs, user, new LogEntry(date, line, 0));
                            balances.putIfAbsent(user, amount);
                            break;
                        case "transferred":
                            addEntry(userLogs, user, new LogEntry(date, line, -amount));
                            balances.put(user, balances.getOrDefault(user, 0.0) - amount);
                            addEntry(userLogs, target, new LogEntry(date, String.format(Locale.US,
                                    "[%s] %s received %.2f from %s", FORMATTER.format(date),
                                    target, amount, user), amount));
                            balances.put(target, balances.getOrDefault(target, 0.0) + amount);
                            break;
                        case "withdrew":
                            addEntry(userLogs, user, new LogEntry(date, line, -amount));
                            balances.put(user, balances.getOrDefault(user, 0.0) - amount);
                    }
                }
            }
        }
        for (Map.Entry<String, List<LogEntry>> entry : userLogs.entrySet()) {
            String user = entry.getKey();
            List<LogEntry> logs = entry.getValue();
            logs.sort(Comparator.comparing(e -> e.timestamp));

            Path userFile = logsDir.resolve(OUTPUT_DIR).resolve(user + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(userFile)) {
                for (LogEntry e : logs) {
                    writer.write(e.content);
                    writer.newLine();
                }
                writer.write(String.format(Locale.US, "[%s] %s final balance %.2f",
                        FORMATTER.format(LocalDateTime.now()), user, balances.getOrDefault(user, 0.0)));
            }
        }
    }
}
