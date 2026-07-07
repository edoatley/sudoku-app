---
title: "Pointing Pair"
difficulty: "Intermediate"
slug: "pointing-pair"
---

# Pointing Pairs (Intersection Removal)

A **Pointing Pair** (or Pointing Triple) occurs when all the possible locations for a specific candidate within a 3x3 block fall into the **exact same row or column**.



## How it works
Imagine you are looking at the top-left 3x3 block. You notice that the number 4 can only go into two empty cells, and both of those cells happen to be in Row 2.

We don't know which of those two cells will eventually be the 4. However, because they are both in Row 2, the 4 for that *entire block* is strictly locked to Row 2. Therefore, the 4 for Row 2 **cannot appear anywhere else outside of that block**.

## The Action
1. Find a candidate in a 3x3 block that is restricted to a single row or column.
2. Look at the rest of that row or column outside the block.
3. **Erase** that candidate from all other cells in that line.
