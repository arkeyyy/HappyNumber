package org.example.happy_number;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class HelloController {
    @FXML private TextField numberField;
    @FXML private TextField threadField;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;
    @FXML private TilePane progressPane;
    @FXML private TextArea resultsArea;
    @FXML private Label summaryLabel;

    private record Worker(Task<HappyNumbers.Result> task, Label state) {}

    private final List<Worker> workers = new ArrayList<>();
    private long runId;
    private int finished;

    @FXML
    private void onStart() {
        if (!workers.isEmpty()) {
            return;
        }
        List<HappyNumbers.Range> ranges;
        try {
            int num = positiveInteger(numberField, "Upper limit");
            int cnt = positiveInteger(threadField, "Threads");
            ranges = HappyNumbers.partition(num, cnt);
        } catch (IllegalArgumentException error) {
            showError(error.getMessage());
            return;
        }

        long currentRun = ++runId;
        finished = 0;
        showError("");
        resultsArea.clear();
        summaryLabel.setText("Calculating...");
        progressPane.getChildren().clear();
        setRunning(true);
        reportFinished(ranges.size());
        try {
            for (HappyNumbers.Range range : ranges) {
                int index = workers.size() + 1;
                Worker worker = createWorker(range, index, currentRun);
                workers.add(worker);
                Thread thread = new Thread(worker.task(), "happy-number-" + currentRun + "-" + index);
                thread.setDaemon(true);
                thread.start();
            }
        } catch (RuntimeException | OutOfMemoryError error) {
            failRun();
        }
    }

    private Worker createWorker(HappyNumbers.Range range, int index, long currentRun) {
        ProgressIndicator indicator = new ProgressIndicator(0);
        indicator.setPrefSize(64, 64);
        indicator.setAccessibleText("Thread " + index + " progress");
        Label assignment = new Label(range.start() + " to " + range.end());
        assignment.setWrapText(true);
        assignment.setMaxWidth(150);
        Label state = new Label("Waiting");
        long size = range.size();
        Label count = new Label(size == 1 ? "1 number" : size + " numbers");
        VBox tile = new VBox(6, new Label("Thread " + index), indicator, assignment, count, state);
        tile.setAlignment(Pos.CENTER);
        tile.setPrefWidth(150);
        progressPane.getChildren().add(tile);

        Task<HappyNumbers.Result> task = new Task<>() {
            private long lastUpdate;

            @Override
            protected HappyNumbers.Result call() throws InterruptedException {
                return HappyNumbers.calculate(range, checked -> {
                    if (checked == size || checked % 256 == 0) {
                        long now = System.nanoTime();
                        if (checked == size || now - lastUpdate >= 50_000_000L) {
                            updateProgress(checked, size);
                            lastUpdate = now;
                        }
                    }
                });
            }
        };
        task.progressProperty().addListener((property, before, progress) -> {
            if (runId == currentRun) {
                indicator.setProgress(progress.doubleValue());
            }
        });
        task.setOnRunning(event -> {
            if (runId == currentRun) {
                state.setText("Running");
            }
        });
        task.setOnSucceeded(event -> {
            if (runId == currentRun) {
                indicator.setProgress(1);
                state.setText("Done");
                finished++;
                if (finished == workers.size()) {
                    finish();
                } else {
                    reportFinished(workers.size());
                }
            }
        });
        task.setOnFailed(event -> {
            if (runId == currentRun) {
                state.setText("Failed");
                failRun();
            }
        });
        return new Worker(task, state);
    }

    private void finish() {
        StringJoiner matches = new StringJoiner(", ");
        long happy = 0;
        long checked = 0;
        try {
            for (Worker worker : workers) {
                HappyNumbers.Result result = worker.task().getValue();
                if (!result.matches().isEmpty()) {
                    matches.add(result.matches());
                }
                happy += result.happyCount();
                checked += result.checked();
            }
            resultsArea.setText(matches.toString());
        } catch (OutOfMemoryError error) {
            failRun();
            return;
        }
        summaryLabel.setText("Happy numbers: " + happy + "\nUnhappy numbers: " + (checked - happy)
                + "\nTotal checked: " + checked);
        statusLabel.setText("Complete. Checked all " + checked + " numbers from 1 to " + checked + ".");
        workers.clear();
        setRunning(false);
    }

    @FXML
    private void onStop() {
        cancelRun();
        statusLabel.setText("Stopped.");
    }

    public void shutdown() {
        if (!workers.isEmpty()) {
            cancelRun();
        }
    }

    private void failRun() {
        cancelRun();
        statusLabel.setText("Calculation failed.");
        showError("The calculation could not finish.");
    }

    private void cancelRun() {
        ++runId;
        for (Worker worker : workers) {
            worker.task().cancel(true);
            switch (worker.task().getState()) {
                case SUCCEEDED -> worker.state().setText("Done");
                case FAILED -> worker.state().setText("Failed");
                default -> worker.state().setText("Stopped");
            }
        }
        workers.clear();
        resultsArea.clear();
        summaryLabel.setText("No complete results.");
        setRunning(false);
    }

    private void reportFinished(int total) {
        statusLabel.setText("Running: " + finished + " of " + total + " threads finished.");
    }

    private void setRunning(boolean running) {
        numberField.setDisable(running);
        threadField.setDisable(running);
        startButton.setDisable(running);
        stopButton.setDisable(!running);
    }

    private int positiveInteger(TextField field, String label) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Malformed and overflowing inputs share the same field error.
        }
        field.requestFocus();
        throw new IllegalArgumentException(label + " must be a whole number from 1 to 2,147,483,647.");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(!message.isEmpty());
        errorLabel.setManaged(!message.isEmpty());
    }
}
