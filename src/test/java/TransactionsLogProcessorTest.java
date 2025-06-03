import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionsLogProcessorTest {

    private Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("logtest");

        String content1 = "[2025-05-10 09:00:22] user001 balance inquiry 1000.00\n" +
                "[2025-05-10 09:05:44] user001 transferred 100.00 to user002\n" +
                "[2025-05-10 09:06:00] user001 transferred 120.00 to user002\n" +
                "[2025-05-10 10:30:55] user005 transferred 10.00 to user003\n" +
                "[2025-05-10 11:09:01] user001 transferred 235.54 to user004\n" +
                "[2025-05-10 12:38:31] user003 transferred 150.00 to user002\n" +
                "[2025-05-11 10:00:31] user002 balance inquiry 210.00";

        String content2 = "[2025-05-10 10:03:23] user002 transferred 990.00 to user001\n" +
                "[2025-05-10 10:15:56] user002 balance inquiry 110.00\n" +
                "[2025-05-10 10:25:43] user003 transferred 120.00 to user002\n" +
                "[2025-05-10 11:00:03] user001 balance inquiry 1770\n" +
                "[2025-05-10 11:01:12] user001 transferred 102.00 to user003\n" +
                "[2025-05-10 17:04:09] user001 transferred 235.54 to user004\n" +
                "[2025-05-10 23:45:32] user003 transferred 150.00 to user002\n" +
                "[2025-05-10 23:55:32] user002 withdrew 50";

        Files.write(tempDir.resolve("log1.log"), content1.getBytes());
        Files.write(tempDir.resolve("log2.log"), content2.getBytes());
    }

    @Test
    public void testLogProcessing() throws IOException {
        TransactionsLogProcessor.processLogs(tempDir);
        Path user1Log = tempDir.resolve("transactions_by_users").resolve("user001.log");
        assertTrue(Files.exists(user1Log));

        String log = Files.readString(user1Log);
        assertTrue(log.contains("user001 balance inquiry 1000.00"));
        assertTrue(log.contains("user001 received 990.00 from user002"));
        assertTrue(log.contains("final balance"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
