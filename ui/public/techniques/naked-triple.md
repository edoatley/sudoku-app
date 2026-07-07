---
title: "Naked Triple"
difficulty: "Intermediate"
slug: "naked-triple"
---

# Naked Triples

A **Naked Triple** is the natural extension of a Naked Pair. It occurs when exactly three cells in the same group (row, column, or block) contain *only* the same three candidates (or a subset of those three).

## How it works
Let's say in a single 3x3 block, you have three empty cells with the following pencil marks:
* Cell 1: **[1, 5]**
* Cell 2: **[1, 8]**
* Cell 3: **[1, 5, 8]**

Notice that across these three cells, the only possible numbers are 1, 5, and 8. Because these three numbers are locked into these three specific cells, no other cell in that 3x3 block can be a 1, a 5, or an 8.

## The Action
1. Find three cells in a group that share exactly three unique candidates (even if not every cell has all three).
2. Look at all the *other* empty cells in that exact same group.
3. **Erase** those three numbers from the pencil marks of all other cells in the group.
