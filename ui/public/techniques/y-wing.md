---
title: "Y-Wing (XY-Wing)"
difficulty: "Advanced"
slug: "y-wing"
---

# Y-Wing (XY-Wing)

The **Y-Wing** (often called the XY-Wing) is a powerful pattern that uses three cells, each containing exactly two candidates (bivalue cells). It relies on a "Pivot" cell and two "Pincer" cells to eliminate a candidate.

## How it works
Imagine you have a **Pivot** cell with candidates **[A, B]**. 
This Pivot sees two other cells (the **Pincers**):
* Pincer 1 has candidates **[A, C]**.
* Pincer 2 has candidates **[B, C]**.

Because the Pivot *must* be either A or B:
* If the Pivot is **A**, Pincer 1 is forced to be **C**.
* If the Pivot is **B**, Pincer 2 is forced to be **C**.

In either scenario, one of the two Pincers will absolutely be **C**. Therefore, any cell on the board that "sees" *both* Pincer 1 and Pincer 2 can never be C!

## The Action
1. Find a Pivot cell with two candidates.
2. Find two Pincer cells that each share one candidate with the Pivot, and share a third candidate with each other.
3. Find the intersection—any cell that is seen by *both* Pincers.
4. **Erase** the shared candidate (C) from that intersecting cell.