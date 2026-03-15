---
title: "X-Wing"
difficulty: "Advanced"
slug: "x-wing"
---

# X-Wing

The **X-Wing** is a classic advanced pattern. It happens when a specific candidate is restricted to exactly **two cells** in two different rows, and those cells share the **exact same columns** (forming a rectangle or "X" shape).



## How it works
Let's track the number 7. 
* In Row 2, the number 7 can only go in Column 3 or Column 8.
* In Row 6, the number 7 can *also* only go in Column 3 or Column 8.

Because a 7 must be placed in both rows, there are only two valid diagonal combinations (like the points of an X). Either the 7s are at (Row 2, Col 3) and (Row 6, Col 8), OR they are at (Row 2, Col 8) and (Row 6, Col 3).

In both scenarios, Columns 3 and 8 will both receive exactly one 7 from those rows. Therefore, **no other cell in Column 3 or Column 8 can be a 7**.

## The Action
1. Find the 4 corners of the X-Wing pattern for a single number.
2. If the pattern is locked by Rows, **erase** that candidate from the rest of the connecting Columns. (And vice-versa if locked by Columns).