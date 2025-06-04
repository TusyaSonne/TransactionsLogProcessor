import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Главный класс приложения.
 * <p>
 * Генерирует тестовые лог-файлы с транзакциями между пользователями, сохраняет их в директорию,
 * запускает обработку логов и выводит результат (для user001) в консоль.
 * <p>
 * Пример логов включает транзакции перевода, снятия и запроса баланса.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        Path logsDir = Paths.get("logs");
        Files.createDirectories(logsDir);

        // Здесь логическая ошибка в примере из ТЗ - несоответствие реального баланса запрошенному -
        // user002 balance inquiry (в обоих логах)

        // Содержимое первого лога
        String log1 = String.join("\n",
                "[2025-05-10 09:00:22] user001 balance inquiry 1000.00",
                "[2025-05-10 09:05:44] user001 transferred 100.00 to user002",
                "[2025-05-10 09:06:00] user001 transferred 120.00 to user002",
                "[2025-05-10 10:30:55] user005 transferred 10.00 to user003",
                "[2025-05-10 11:09:01] user001 transferred 235.54 to user004",
                "[2025-05-10 12:38:31] user003 transferred 150.00 to user002",
                "[2025-05-11 10:00:31] user002 balance inquiry 210.00"
        );

        // Содержимое второго лога
        String log2 = String.join("\n",
                "[2025-05-10 10:03:23] user002 transferred 990.00 to user001",
                "[2025-05-10 10:15:56] user002 balance inquiry 110.00",
                "[2025-05-10 10:25:43] user003 transferred 120.00 to user002",
                "[2025-05-10 11:00:03] user001 balance inquiry 1770",
                "[2025-05-10 11:01:12] user001 transferred 102.00 to user003",
                "[2025-05-10 17:04:09] user001 transferred 235.54 to user004",
                "[2025-05-10 23:45:32] user003 transferred 150.00 to user002",
                "[2025-05-10 23:55:32] user002 withdrew 50"
        );

        // Запись логов в директорию
        Files.write(logsDir.resolve("log1.log"), log1.getBytes());
        Files.write(logsDir.resolve("log2.log"), log2.getBytes());

        // Обработка логов
        TransactionsLogProcessor.processLogs(logsDir);

        // Печатаем содержимое user001.log
        Path resultFile = logsDir.resolve("transactions_by_users").resolve("user001.log");

        System.out.println("Содержимое user001.log:");
        List<String> lines = Files.readAllLines(resultFile);
        for (String line : lines) {
            System.out.println(line);
        }
    }
}
