---
title: "Hidden Pair"
difficulty: "Intermediate"
slug: "hidden-pair"
---

# Hidden Pairs

A **Hidden Pair** occurs when two specific candidates only appear in exactly **two cells** within a single row, column, or block.



## How it works
This is the sneaky cousin of the Naked Pair. Imagine a row with several empty cells. Two of those cells contain the candidates 2 and 9 (among others like 4, 5, and 8).

If you look closely and realize that the 2 and 9 **do not appear as candidates in any other cell in that row**, they form a Hidden Pair. Because the 2 and 9 *must* go into those two cells, all other candidates in those two cells are impossible.

## The Action
1. Find two cells in a group that hold two unique candidates found nowhere else in that group.
2. **Erase** all *other* candidates from those two cells.
3. You have now turned a Hidden Pair into a Naked Pair!
