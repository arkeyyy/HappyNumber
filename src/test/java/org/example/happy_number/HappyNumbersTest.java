package org.example.happy_number;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class HappyNumbersTest {
    @Test
    void sampleRangesAndCountsMatchTheCompletedRun() throws InterruptedException {
        var ranges = HappyNumbers.partition(22, 4);
        assertEquals(List.of(new HappyNumbers.Range(1, 3), new HappyNumbers.Range(4, 8),
                new HappyNumbers.Range(9, 14), new HappyNumbers.Range(15, 22)), ranges);

        List<HappyNumbers.Result> results = new ArrayList<>();
        for (var range : ranges) {
            List<Long> progress = new ArrayList<>();
            var result = HappyNumbers.calculate(range, progress::add);
            results.add(result);
            assertEquals(range.size(), result.checked());
            assertEquals(range.size(), progress.size());
            for (int i = 0; i < progress.size(); i++) {
                assertEquals((long) i + 1, progress.get(i).longValue());
            }
        }
        String matches = results.stream().map(HappyNumbers.Result::matches)
                .filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
        long happy = results.stream().mapToLong(HappyNumbers.Result::happyCount).sum();
        long checked = results.stream().mapToLong(HappyNumbers.Result::checked).sum();
        assertEquals("1, 7, 10, 13, 19", matches);
        assertEquals(5, happy);
        assertEquals(22, checked);
        assertEquals(17, checked - happy);
    }

    @Test
    void singletonsHandleOneAndAnUnhappyCycle() throws InterruptedException {
        assertEquals(List.of(new HappyNumbers.Range(1, 1)), HappyNumbers.partition(1, 1));
        assertEquals(new HappyNumbers.Result("1", 1, 1),
                HappyNumbers.calculate(new HappyNumbers.Range(1, 1), ignored -> {}));
        assertEquals(new HappyNumbers.Result("", 0, 1),
                HappyNumbers.calculate(new HappyNumbers.Range(4, 4), ignored -> {}));
    }

    @Test
    void smallRangesAreContiguousAndUnequalWheneverPossible() {
        for (int num = 1; num <= 75; num++) {
            for (int cnt = 1; cnt <= Math.min(64, num); cnt++) {
                var ranges = HappyNumbers.partition(num, cnt);
                assertEquals(cnt, ranges.size());
                int next = 1;
                for (var range : ranges) {
                    assertEquals(next, range.start());
                    assertTrue(range.size() > 0);
                    next = range.end() + 1;
                }
                assertEquals(num + 1, next);
                if (cnt > 1 && num > cnt) {
                    assertTrue(ranges.stream().map(HappyNumbers.Range::size).distinct().count() > 1);
                }
            }
        }
    }

    @Test
    void rejectsInvalidLimitsAndThreadCounts() {
        assertThrows(IllegalArgumentException.class, () -> HappyNumbers.partition(0, 1));
        assertThrows(IllegalArgumentException.class, () -> HappyNumbers.partition(1, 0));
        assertThrows(IllegalArgumentException.class, () -> HappyNumbers.partition(2, 3));
        assertThrows(IllegalArgumentException.class, () -> HappyNumbers.partition(100, 65));
        assertThrows(IllegalArgumentException.class, () -> new HappyNumbers.Range(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new HappyNumbers.Range(2, 1));
    }

    @Test
    void maximumIntegerHasNoOverflowInAllocationOrIteration() throws InterruptedException {
        var ranges = HappyNumbers.partition(Integer.MAX_VALUE, 64);
        assertEquals(64, ranges.size());
        long next = 1;
        for (var range : ranges) {
            assertEquals(next, range.start());
            next = (long) range.end() + 1;
        }
        assertEquals((long) Integer.MAX_VALUE + 1, next);
        var lastEleven = new HappyNumbers.Range(Integer.MAX_VALUE - 10, Integer.MAX_VALUE);
        assertEquals(new HappyNumbers.Result("2147483645", 1, 11),
                HappyNumbers.calculate(lastEleven, ignored -> {}));
    }

    @Test
    void cancellationInterruptsScanningBeforeTheNextNumber() {
        AtomicLong progress = new AtomicLong();
        try {
            assertThrows(InterruptedException.class, () -> HappyNumbers.calculate(
                    new HappyNumbers.Range(1, 1000), checked -> {
                        progress.set(checked);
                        if (checked == 3) {
                            Thread.currentThread().interrupt();
                        }
                    }));
            assertEquals(3, progress.get());
        } finally {
            Thread.interrupted();
        }
    }
}
