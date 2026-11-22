package org.example.happy_number;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HappyNumberApplicationTest {
    private HelloApplication application;
    private Stage stage;
    private TextField number;
    private TextField threads;
    private Button start;
    private Button stop;
    private Label error;
    private TilePane tiles;
    private TextArea results;
    private Label summary;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void openWindow() throws Exception {
        onFx(() -> {
            application = new HelloApplication();
            stage = new Stage();
            stage.setOpacity(0);
            application.start(stage);
            number = (TextField) stage.getScene().lookup("#numberField");
            threads = (TextField) stage.getScene().lookup("#threadField");
            start = (Button) stage.getScene().lookup("#startButton");
            stop = (Button) stage.getScene().lookup("#stopButton");
            error = (Label) stage.getScene().lookup("#errorLabel");
            tiles = (TilePane) stage.getScene().lookup("#progressPane");
            results = (TextArea) stage.getScene().lookup("#resultsArea");
            summary = (Label) stage.getScene().lookup("#summaryLabel");
            return null;
        });
    }

    @AfterEach
    void closeWindow() throws Exception {
        onFx(() -> {
            application.stop();
            stage.close();
            return null;
        });
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @Test
    void matchesSampleAtDefaultAndMinimumWindowSizes() throws Exception {
        onFx(() -> {
            assertEquals("Happy Number", stage.getTitle());
            assertEquals(760, stage.getScene().getWidth(), 2);
            assertEquals(640, stage.getScene().getHeight(), 2);
            assertEquals("10000", number.getText());
            assertEquals("4", threads.getText());
            assertTrue(stop.isDisabled());
            assertFalse(results.isEditable());
            startRun("22", "4");
            return null;
        });
        awaitFx(() -> !start.isDisabled());
        double originalWidth = onFx(() -> {
            assertEquals("1, 7, 10, 13, 19", results.getText());
            assertEquals("Happy numbers: 5\nUnhappy numbers: 17\nTotal checked: 22", summary.getText());
            List<String> ranges = List.of("1 to 3", "4 to 8", "9 to 14", "15 to 22");
            assertEquals(ranges.size(), tiles.getChildren().size());
            for (int i = 0; i < ranges.size(); i++) {
                Node tile = tiles.getChildren().get(i);
                List<String> labels = tile.lookupAll(".label").stream()
                        .map(Label.class::cast).map(Label::getText).toList();
                assertTrue(labels.contains("Thread " + (i + 1)));
                assertTrue(labels.contains(ranges.get(i)));
                assertEquals(1.0, ((ProgressIndicator) tile.lookup(".progress-indicator")).getProgress());
            }
            double width = number.getWidth();
            stage.setWidth(stage.getMinWidth());
            stage.setHeight(stage.getMinHeight());
            return width;
        });
        awaitFx(() -> number.getWidth() < originalWidth);
        onFx(() -> {
            assertTrue(summary.localToScene(summary.getBoundsInLocal()).getMaxY() <= stage.getScene().getHeight());
            double buttonY = stop.localToScene(stop.getBoundsInLocal()).getMaxY();
            for (Node control : List.of(number, threads, start)) {
                assertEquals(buttonY, control.localToScene(control.getBoundsInLocal()).getMaxY(), 2);
            }
            return null;
        });
    }

    @Test
    void invalidInputsLeaveTheFormIdle() throws Exception {
        onFx(() -> {
            for (String[] inputs : List.of(new String[]{"", "1"}, new String[]{"2147483648", "1"},
                    new String[]{"2.5", "1"}, new String[]{"-10", "1"}, new String[]{"2", "0"},
                    new String[]{"3", "4"}, new String[]{"100", "65"})) {
                startRun(inputs[0], inputs[1]);
                assertTrue(error.isVisible());
                assertFalse(error.getText().isBlank());
                assertTrue(tiles.getChildren().isEmpty());
                assertFalse(start.isDisabled());
                assertTrue(stop.isDisabled());
            }
            startRun("1", "1");
            assertFalse(error.isVisible());
            return null;
        });
        awaitFx(() -> results.getText().equals("1"));
    }

    @Test
    void stopAndImmediateRestartShowOnlyTheNewRun() throws Exception {
        onFx(() -> {
            startRun("2147483647", "2");
            assertTrue(number.isDisabled() && threads.isDisabled() && start.isDisabled());
            assertFalse(stop.isDisabled());
            return null;
        });
        awaitFx(() -> tiles.lookupAll(".progress-indicator").stream()
                .map(ProgressIndicator.class::cast).anyMatch(indicator ->
                        indicator.getProgress() > 0 && indicator.getProgress() < 1));
        onFx(() -> {
            stop.fire();
            assertTrue(results.getText().isEmpty());
            assertFalse(number.isDisabled() || threads.isDisabled() || start.isDisabled());
            assertTrue(stop.isDisabled());
            startRun("7", "1");
            return null;
        });
        awaitFx(() -> !start.isDisabled());
        onFx(() -> {
            assertEquals("1, 7", results.getText());
            assertEquals("Happy numbers: 2\nUnhappy numbers: 5\nTotal checked: 7", summary.getText());
            assertEquals(1, tiles.getChildren().size());
            return null;
        });
    }

    @Test
    void closingTheWindowStopsActiveWorkers() throws Exception {
        onFx(() -> {
            startRun("2147483647", "2");
            return null;
        });
        awaitFx(() -> workerCount() == 2);
        onFx(() -> {
            Event.fireEvent(stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            return null;
        });
        awaitFx(() -> !stage.isShowing() && workerCount() == 0);
    }

    private void startRun(String upperLimit, String threadCount) {
        number.setText(upperLimit);
        threads.setText(threadCount);
        start.fire();
    }

    private static long workerCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("happy-number-") && thread.isAlive()).count();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void awaitFx(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!onFx(condition)) {
            if (System.nanoTime() >= deadline) {
                fail("Timed out waiting for the JavaFX state change");
            }
            Thread.sleep(20);
        }
    }
}
