import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Класс для обработки логов транзакций между пользователями.
 * <p>
 * Обрабатывает текстовые файлы с операциями пользователей (переводы, снятия и запросы баланса),
 * вычисляет изменения баланса и формирует для каждого пользователя отдельный лог с итоговым балансом.
 * <p>
 * Формат строк в логах:
 * <pre>
 * [yyyy-MM-dd HH:mm:ss] userID операция сумма [to целевойПользователь]
 * </pre>
 * Поддерживаемые операции:
 * <ul>
 *   <li><b>balance inquiry</b> — пользователь сообщает свой текущий баланс (начальный, если первый раз).</li>
 *   <li><b>transferred</b> — перевод суммы от одного пользователя к другому.</li>
 *   <li><b>withdrew</b> — пользователь снимает средства со счёта.</li>
 * </ul>
 * <p>
 * Результат сохраняется в подкаталоге {@code transactions_by_users} в виде отдельных файлов по пользователям.
 */
public class TransactionsLogProcessor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern PATTERN = Pattern.compile("\\[(.*?)\\] (\\S+) (transferred|withdrew|balance inquiry) (\\S+)(?: to (\\S+))?");
    private static final String OUTPUT_DIR = "transactions_by_users";

    /**
     * Внутреннее представление записи лога.
     * Хранит данные, небходимые для обработки.
     */
    private static class Entry {
        LocalDateTime timestamp;
        String user;
        String op;
        double amount;
        String target;        // может быть null
        String originalLine;  // "[2025-05-10 09:05:44] user001 transferred 100.00 to user002"

        Entry(LocalDateTime timestamp, String user, String op, double amount, String target, String originalLine) {
            this.timestamp = timestamp;
            this.user = user;
            this.op = op;
            this.amount = amount;
            this.target = target;
            this.originalLine = originalLine;
        }
    }

    /**
     * Основной метод для обработки логов.
     * <p>
     * Считывает все .log-файлы из указанной директории, обрабатывает транзакции,
     * рассчитывает баланс каждого пользователя и сохраняет результат в отдельный файл.
     * @param logsDir путь к директории, содержащей файлы логов
     * @throws IOException если произошла ошибка при чтении или записи файлов
     * Основной метод:
     * 1) Сбор всех Entry
     * 2) Сортировка по timestamp
     * 3) Один проход по всем Entry в хронологическом порядке
     *    → обновляем balances и сразу пишем в userLogs нужные строки
     * 4) Записываем файлы
     */
    public static void processLogs(Path logsDir) throws IOException {
        List<Entry> allEntries = new ArrayList<>();

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

                    allEntries.add(new Entry(date, user, op, amount, target, line));
                }
            }
        }

        allEntries.sort(Comparator.comparing(e -> e.timestamp));

        Map<String, List<String>> userLogs = new HashMap<>();
        Map<String, Double> balances = new HashMap<>();

        for (Entry e : allEntries) {
            switch (e.op) {
                case "balance inquiry":
                    // Если пользователь впервые встретился, выставляем стартовый баланс
                    balances.putIfAbsent(e.user, e.amount);
                    // Саму строку кладём «как есть» в пользовательский лог
                    userLogs.computeIfAbsent(e.user, k -> new ArrayList<>())
                            .add(e.originalLine);
                    break;
                case "transferred":
                    // Отправитель
                    balances.put(e.user, balances.getOrDefault(e.user, 0.0) - e.amount);
                    userLogs.computeIfAbsent(e.user, k -> new ArrayList<>())
                            .add(e.originalLine);

                    // Получатель
                    balances.put(e.target, balances.getOrDefault(e.target, 0.0) + e.amount);
                    // Формируем строку «… received X from Y»
                    String receivedLine = String.format(
                            Locale.US,
                            "[%s] %s received %.2f from %s",
                            FORMATTER.format(e.timestamp),
                            e.target,
                            e.amount,
                            e.user
                    );
                    userLogs.computeIfAbsent(e.target, k -> new ArrayList<>())
                            .add(receivedLine);
                    break;
                case "withdrew":
                    balances.put(e.user, balances.getOrDefault(e.user, 0.0) - e.amount);
                    userLogs.computeIfAbsent(e.user, k -> new ArrayList<>())
                            .add(e.originalLine);
                    break;
            }

            // Записываем результат для каждого пользователя
            Files.createDirectories(logsDir.resolve(OUTPUT_DIR));
            for (Map.Entry<String, List<String>> entry : userLogs.entrySet()) {
                String user = entry.getKey();
                List<String> lines = entry.getValue();
                Path outFile = logsDir.resolve(OUTPUT_DIR).resolve(user + ".log");
                try (BufferedWriter writer = Files.newBufferedWriter(outFile)) {
                    // Пишем по порядку все накопленные строки
                    for (String ln : lines) {
                        writer.write(ln);
                        writer.newLine();
                    }
                    // И в конце — финальный баланс
                    String finalLine = String.format(Locale.US,
                            "[%s] %s final balance %.2f",
                            FORMATTER.format(LocalDateTime.now()),
                            user,
                            balances.getOrDefault(user, 0.0));
                    writer.write(finalLine);
                }
            }
        }

    }
}
