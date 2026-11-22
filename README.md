# Happy Number

Happy Number is a JavaFX desktop app that finds every happy number from 1 through an inclusive upper limit. A number is happy when repeatedly summing the squares of its decimal digits reaches 1. For example, 19 follows the sequence `19 → 82 → 68 → 100 → 1`. A sequence that repeats a value before reaching 1 is unhappy.

The upper limit defaults to 10,000 and accepts values from 1 through 2,147,483,647. The thread count defaults to 4 and ranges from 1 through the smaller of the upper limit and 64.

**Start** divides the interval into contiguous ranges and assigns one worker to each range. Each worker tile shows its assigned interval, number count, progress, and state. **Stop** cancels the active run and clears partial results. A completed run displays the happy numbers in ascending order and the totals for happy, unhappy, and checked numbers.

## Example

With an upper limit of 22 and 4 threads, the workers cover 1–3, 4–8, 9–14, and 15–22. The happy numbers are **1, 7, 10, 13, and 19**. Of the 22 numbers checked, 5 are happy and 17 are unhappy.
