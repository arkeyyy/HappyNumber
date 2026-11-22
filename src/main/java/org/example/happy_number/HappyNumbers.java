package org.example.happy_number;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongConsumer;

final class HappyNumbers {
    private static final int MAX_THREADS = 64;
    // A positive int has at most ten digits. Its first digit-square sum is at most 810.
    private static final boolean[] HAPPY_SUMS = classifyDigitSums();

    private HappyNumbers() {}

    record Range(int start, int end) {
        Range {
            if (start < 1 || end < start) {
                throw new IllegalArgumentException("Invalid positive-integer range.");
            }
        }

        long size() {
            return (long) end - start + 1;
        }
    }

    record Result(String matches, long happyCount, long checked) {}

    static List<Range> partition(int num, int cnt) {
        if (num < 1) {
            throw new IllegalArgumentException("Upper limit must be positive.");
        }
        int maxThreads = Math.min(num, MAX_THREADS);
        if (cnt < 1 || cnt > maxThreads) {
            throw new IllegalArgumentException("Threads must be between 1 and " + maxThreads + ".");
        }

        // One number per worker, then proportional shares with weights 1 through cnt.
        long remaining = (long) num - cnt;
        long totalWeight = (long) cnt * (cnt + 1) / 2;
        long[] sizes = new long[cnt];
        long[] remainders = new long[cnt];
        List<Integer> order = new ArrayList<>(cnt);
        long allocated = 0;
        for (int i = 0; i < cnt; i++) {
            long weighted = remaining * (i + 1);
            sizes[i] = 1 + weighted / totalWeight;
            remainders[i] = weighted % totalWeight;
            allocated += sizes[i];
            order.add(i);
        }

        order.sort(Comparator.<Integer>comparingLong(i -> remainders[i]).reversed()
                .thenComparing(Comparator.reverseOrder()));
        for (int i = 0; i < num - allocated; i++) {
            sizes[order.get(i)]++;
        }
        if (cnt > 1 && sizes[0] > 1 && Arrays.stream(sizes).allMatch(size -> size == sizes[0])) {
            sizes[0]--;
            sizes[cnt - 1]++;
        }

        List<Range> ranges = new ArrayList<>(cnt);
        long start = 1;
        for (long size : sizes) {
            long end = start + size - 1;
            ranges.add(new Range((int) start, (int) end));
            start = end + 1;
        }
        return List.copyOf(ranges);
    }

    private static boolean[] classifyDigitSums() {
        boolean[] happy = new boolean[811];
        int[] seenFrom = new int[happy.length];
        for (int start = 1; start < happy.length; start++) {
            int current = start;
            while (current != 1 && seenFrom[current] != start) {
                seenFrom[current] = start;
                current = digitSquareSum(current);
            }
            happy[start] = current == 1;
        }
        return happy;
    }

    private static int digitSquareSum(int number) {
        int sum = 0;
        while (number > 0) {
            int digit = number % 10;
            sum += digit * digit;
            number /= 10;
        }
        return sum;
    }

    static Result calculate(Range range, LongConsumer progress) throws InterruptedException {
        StringBuilder matches = new StringBuilder();
        long happyCount = 0;
        long checked = 0;
        for (long number = range.start(); number <= range.end(); number++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Calculation interrupted.");
            }
            if (HAPPY_SUMS[digitSquareSum((int) number)]) {
                if (happyCount > 0) {
                    matches.append(", ");
                }
                matches.append((int) number);
                happyCount++;
            }
            progress.accept(++checked);
        }
        return new Result(matches.toString(), happyCount, checked);
    }
}
