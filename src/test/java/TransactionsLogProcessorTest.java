import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Набор юнит-тестов для проверки работы класса {@link TransactionsLogProcessor}.
 * <p>
 * Тестируются различные сценарии обработки логов:
 * <ul>
 *     <li>Передача средств между пользователями</li>
 *     <li>Снятие средств</li>
 *     <li>Операции balance inquiry</li>
 *     <li>Корректный расчёт баланса</li>
 *     <li>Сортировка логов по времени</li>
 * </ul>
 * Каждый тест использует временную директорию и очищает её перед запуском.
 */
public class TransactionsLogProcessorTest {

    private static Path tempDir;

    @BeforeAll
    static void setup() throws IOException {
        tempDir = Files.createTempDirectory("logtest");
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {}
                    });
        }
    }

    @BeforeEach
    void cleanBeforeTest() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(tempDir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {}
                    });
        }
    }


    @Test
    void testTransactionProcessing() throws IOException {
        String logContent = String.join("\n",
                "[2025-05-10 09:20:00] userB transferred 50.00 to userA",
                "[2025-05-10 09:00:00] userA balance inquiry 1000.00",
                "[2025-05-10 09:40:00] userB balance inquiry 150.00",
                "[2025-05-10 09:30:00] userA withdrew 100.00",
                "[2025-05-10 09:10:00] userA transferred 200.00 to userB"
        );

        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile, logContent);

        TransactionsLogProcessor.processLogs(tempDir);

        Path userAFile = tempDir.resolve("transactions_by_users").resolve("userA.log");
        Path userBFile = tempDir.resolve("transactions_by_users").resolve("userB.log");

        assertTrue(Files.exists(userAFile));
        assertTrue(Files.exists(userBFile));

        List<String> userALines = Files.readAllLines(userAFile);
        List<String> userBLines = Files.readAllLines(userBFile);

        // Проверка сортировки userA
        List<String> timestamps = userALines.stream()
                .filter(line -> line.matches("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}].*"))
                .map(line -> line.substring(1, 20)) // [2025-05-10 09:00:00]
                .toList();

        List<String> sorted = new ArrayList<>(timestamps);
        sorted.sort(String::compareTo);
        assertEquals(sorted, timestamps, "Записи в userA.log не отсортированы по дате");

        // Проверка: userB получил received
        assertTrue(userBLines.stream().anyMatch(line -> line.contains("received 200.00 from userA")));

        // Финальные балансы
        String finalA = userALines.get(userALines.size() - 1);
        String finalB = userBLines.get(userBLines.size() - 1);

        System.out.println("Финальный баланс A: " + finalA);
        System.out.println("Финальный баланс B: " + finalB);

        assertTrue(finalA.matches(".*userA final balance 750\\.00"));
        assertTrue(finalB.matches(".*userB final balance 150\\.00"));
    }

    @Test
    void testOnlyBalanceInquiry() throws IOException {
        String logContent = "[2025-01-01 00:00:00] userX balance inquiry 1234.56\n";

        Path logFile = tempDir.resolve("balance_only.log");
        Files.writeString(logFile, logContent);

        TransactionsLogProcessor.processLogs(tempDir);

        Path userXFile = tempDir.resolve("transactions_by_users").resolve("userX.log");
        assertTrue(Files.exists(userXFile));

        List<String> lines = Files.readAllLines(userXFile);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("balance inquiry 1234.56"));
        assertTrue(lines.get(1).contains("userX final balance 1234.56"));
    }

    @Test
    void testTransferWithoutInquiry() throws IOException {
        String logContent = String.join("\n",
                "[2025-01-01 01:00:00] userA transferred 300.00 to userB"
        );

        Path logFile = tempDir.resolve("no_inquiry.log");
        Files.writeString(logFile, logContent);

        TransactionsLogProcessor.processLogs(tempDir);

        Path fileA = tempDir.resolve("transactions_by_users").resolve("userA.log");
        Path fileB = tempDir.resolve("transactions_by_users").resolve("userB.log");

        assertTrue(Files.exists(fileA));
        assertTrue(Files.exists(fileB));

        List<String> aLines = Files.readAllLines(fileA);
        List<String> bLines = Files.readAllLines(fileB);

        assertTrue(aLines.get(aLines.size() - 1).contains("userA final balance -300.00"));
        assertTrue(bLines.get(bLines.size() - 1).contains("userB final balance 300.00"));
    }
}
