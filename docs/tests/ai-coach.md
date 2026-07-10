# AI Coach Manual Tests

## Attempt 1 - basic conversation

**AI Coach conversation**
```
User: I'm stuck
AI:   No worries — let's find an easy win! A 'Full House' means a row, column, or box has only one empty cell left. Scan through the rows, columns, and boxes on your board and ask yourself: which one has just a single blank? Once you spot it, only one digit can possibly go there. Give it a try!
User: is that right?
AI:   Great question! Before we check, let me ask: which row, column, or box did you think had only one empty cell? Point me to it, and we can verify together.
User: column 7 row 7
AI:   Let me check that with you. Row 7, Column 7 already has a 3 in it, so that cell is already filled! Look for an empty cell (shown as _) instead. Scan Row 7 again — which cells are actually blank?
```

**Grid:**
![Grid](images/grid-1.png)

**Web Console Local Storage content**
| Key | Value |
|-----|-------|
| sudoku_candidateGrid | [[[],[1,7],[1,2,3],[1,2,3,7],[1,2,3,5],[1,2,3],[],[],[1,2,5,9]],[[1,3,7],[],[1,2,3,6],[1,2,3,6,7],[1,2,3,5,6],[],[],[1,2],[1,2,5]],[[],[1,6],[],[],[1,2,6],[],[],[],[1,2]],[[],[],[1,3,5,9],[1,3,4],[1,3,9],[1,3,9],[],[],[]],[[1],[],[1,9],[1,2,6,8],[],[1,2,6,9],[],[],[]],[[1,3],[],[],[1,2,3,4],[1,2,3],[],[],[],[]],[[],[1,6,7],[1,6],[1,2,3,6],[1,2,3,6,9],[1,2,3,6,9],[],[],[]],[[],[],[1,6],[],[],[],[],[2,6],[2,8]],[[],[5,6,7],[],[3,6],[],[3,6],[],[],[]]] |
| sudoku_currentGrid | [[4,0,0,0,0,0,6,8,0],[0,9,0,0,0,8,4,0,0],[5,0,8,9,0,4,7,3,0],[6,2,0,0,0,0,8,7,1],[0,4,0,0,7,0,5,2,3],[0,8,7,0,0,5,9,4,6],[8,0,0,0,0,0,3,5,4],[9,3,0,5,4,7,2,0,0],[2,0,4,0,8,0,1,9,7]] |
| sudoku_difficulty | easy |
| sudoku_elapsedSeconds | 614 |
| sudoku_gameId | 9c37eacb-8f3d-44f8-a791-d3eff79629ca |
| sudoku_hintsUsed | 0 |

**Web Console Grid as JSON**
```json
[
    [4,0,0,0,0,0,6,8,0],
    [0,9,0,0,0,8,4,0,0],
    [5,0,8,9,0,4,7,3,0],
    [6,2,0,0,0,0,8,7,1],
    [0,4,0,0,7,0,5,2,3],
    [0,8,7,0,0,5,9,4,6],
    [8,0,0,0,0,0,3,5,4],
    [9,3,0,5,4,7,2,0,0],
    [2,0,4,0,8,0,1,9,7]
]
```

**Coach logs**

```bash
AWS_PROFILE=sandbox bash scripts/logs/download-coach-logs.sh --hours 1
```

```terminaloutput
Fetching coach logs from /aws/lambda/sudoku-rc-ai-coach-improvements-1 (last 1h)...
Found 6 log event(s). Extracting JSON lines...
```

```json
{"type":"COACH_REQUEST","cid":"d884e08d-5ba2-4412-bfc0-26081848698d","modelId":"eu.anthropic.claude-haiku-4-5-20251001-v1:0","technique":"Full House","historyLen":1,"userMsgLen":9,"ts":1783671560228,"userMessage":"I'm stuck","board":[[4,0,0,0,0,0,6,8,0],[0,9,0,0,0,8,4,0,0],[5,0,8,9,0,4,7,3,0],[6,2,0,0,0,0,8,7,1],[0,4,0,0,7,0,5,2,3],[0,8,7,0,0,5,9,4,6],[8,0,0,0,0,0,0,5,4],[9,3,0,5,4,7,2,0,0],[2,0,4,0,8,0,1,9,7]],"candidatesGrid":{"rows":[[[],[1,7],[1,2,3],[1,2,3,7],[1,2,3,5],[1,2,3],[],[],[2,5,9]],[[1,3,7],[],[1,2,3,6],[1,2,3,6,7],[1,2,3,5,6],[],[],[1],[2,5]],[[],[1,6],[],[],[1,2,6],[],[],[],[2]],[[],[],[3,5,9],[3,4],[3,9],[3,9],[],[],[]],[[1],[],[1,9],[1,6,8],[],[1,6,9],[],[],[]],[[1,3],[],[],[1,2,3],[1,2,3],[],[],[],[]],[[],[1,6,7],[1,6],[1,2,3,6],[1,2,3,6,9],[1,2,3,6,9],[3],[],[]],[[],[],[1,6],[],[],[],[],[6],[8]],[[],[5,6],[],[3,6],[],[3,6],[],[],[]]]}}
{"type":"COACH_RESPONSE","cid":"d884e08d-5ba2-4412-bfc0-26081848698d","revealHint":false,"inputTokens":2376,"outputTokens":91,"cacheReadTokens":0,"cacheWriteTokens":0,"latencyMs":2499,"fallback":false,"aiMessage":"No worries — let's find an easy win! A 'Full House' means a row, column, or box has only one empty cell left. Scan through the rows, columns, and boxes on your board and ask yourself: which one has just a single blank? Once you spot it, only one digit can possibly go there. Give it a try!"}
{"type":"COACH_REQUEST","cid":"68d19672-515f-430c-95ea-1035c9fa38fb","modelId":"eu.anthropic.claude-haiku-4-5-20251001-v1:0","technique":"Naked Single","historyLen":3,"userMsgLen":14,"ts":1783671597905,"userMessage":"is that right?","board":[[4,0,0,0,0,0,6,8,0],[0,9,0,0,0,8,4,0,0],[5,0,8,9,0,4,7,3,0],[6,2,0,0,0,0,8,7,1],[0,4,0,0,7,0,5,2,3],[0,8,7,0,0,5,9,4,6],[8,0,0,0,0,0,3,5,4],[9,3,0,5,4,7,2,0,0],[2,0,4,0,8,0,1,9,7]],"candidatesGrid":{"rows":[[[],[1,7],[1,2,3],[1,2,3,7],[1,2,3,5],[1,2,3],[],[],[2,5,9]],[[1,3,7],[],[1,2,3,6],[1,2,3,6,7],[1,2,3,5,6],[],[],[1],[2,5]],[[],[1,6],[],[],[1,2,6],[],[],[],[2]],[[],[],[3,5,9],[3,4],[3,9],[3,9],[],[],[]],[[1],[],[1,9],[1,6,8],[],[1,6,9],[],[],[]],[[1,3],[],[],[1,2,3],[1,2,3],[],[],[],[]],[[],[1,6,7],[1,6],[1,2,6],[1,2,6,9],[1,2,6,9],[],[],[]],[[],[],[1,6],[],[],[],[],[6],[8]],[[],[5,6],[],[3,6],[],[3,6],[],[],[]]]}}
{"type":"COACH_RESPONSE","cid":"68d19672-515f-430c-95ea-1035c9fa38fb","revealHint":false,"inputTokens":2464,"outputTokens":54,"cacheReadTokens":0,"cacheWriteTokens":0,"latencyMs":1565,"fallback":false,"aiMessage":"Great question! Before we check, let me ask: which row, column, or box did you think had only one empty cell? Point me to it, and we can verify together."}
{"type":"COACH_REQUEST","cid":"9a37cacb-aee0-43e7-9ee1-45fd746179a5","modelId":"eu.anthropic.claude-haiku-4-5-20251001-v1:0","technique":"Naked Single","historyLen":5,"userMsgLen":14,"ts":1783671635546,"userMessage":"column 7 row 7","board":[[4,0,0,0,0,0,6,8,0],[0,9,0,0,0,8,4,0,0],[5,0,8,9,0,4,7,3,0],[6,2,0,0,0,0,8,7,1],[0,4,0,0,7,0,5,2,3],[0,8,7,0,0,5,9,4,6],[8,0,0,0,0,0,3,5,4],[9,3,0,5,4,7,2,0,0],[2,0,4,0,8,0,1,9,7]],"candidatesGrid":{"rows":[[[],[1,7],[1,2,3],[1,2,3,7],[1,2,3,5],[1,2,3],[],[],[2,5,9]],[[1,3,7],[],[1,2,3,6],[1,2,3,6,7],[1,2,3,5,6],[],[],[1],[2,5]],[[],[1,6],[],[],[1,2,6],[],[],[],[2]],[[],[],[3,5,9],[3,4],[3,9],[3,9],[],[],[]],[[1],[],[1,9],[1,6,8],[],[1,6,9],[],[],[]],[[1,3],[],[],[1,2,3],[1,2,3],[],[],[],[]],[[],[1,6,7],[1,6],[1,2,6],[1,2,6,9],[1,2,6,9],[],[],[]],[[],[],[1,6],[],[],[],[],[6],[8]],[[],[5,6],[],[3,6],[],[3,6],[],[],[]]]}}
{"type":"COACH_RESPONSE","cid":"9a37cacb-aee0-43e7-9ee1-45fd746179a5","revealHint":false,"inputTokens":2515,"outputTokens":74,"cacheReadTokens":0,"cacheWriteTokens":0,"latencyMs":1577,"fallback":false,"aiMessage":"Let me check that with you. Row 7, Column 7 already has a 3 in it, so that cell is already filled! Look for an empty cell (shown as _) instead. Scan Row 7 again — which cells are actually blank?"}
```

Reformatted to conversation to make reading easier:

```bash
pbpaste | jq -s 'group_by(.cid)[] | {cid: .[0].cid, userMessage: .[0].userMessage, aiMessage: (map(select(.type=="COACH_RESPONSE"))[0].aiMessage // "TIMED OUT")}'
```

```json
{
  "cid": "68d19672-515f-430c-95ea-1035c9fa38fb",
  "userMessage": "is that right?",
  "aiMessage": "Great question! Before we check, let me ask: which row, column, or box did you think had only one empty cell? Point me to it, and we can verify together."
}
{
  "cid": "9a37cacb-aee0-43e7-9ee1-45fd746179a5",
  "userMessage": "column 7 row 7",
  "aiMessage": "Let me check that with you. Row 7, Column 7 already has a 3 in it, so that cell is already filled! Look for an empty cell (shown as _) instead. Scan Row 7 again — which cells are actually blank?"
}
{
  "cid": "d884e08d-5ba2-4412-bfc0-26081848698d",
  "userMessage": "I'm stuck",
  "aiMessage": "No worries — let's find an easy win! A 'Full House' means a row, column, or box has only one empty cell left. Scan through the rows, columns, and boxes on your board and ask yourself: which one has just a single blank? Once you spot it, only one digit can possibly go there. Give it a try!"
}
```

<details>
<summary>Puzzle from data browser</sumamry>

```json
{
  "candidates": {
    "rows": [
      [
        [],
        [ 1, 7 ],
        [ 1, 2, 3 ],
        [ 1, 2, 3, 7 ],
        [ 1, 2, 3, 5 ],
        [ 1, 2, 3 ],
        [],
        [],
        [ 1, 2, 5, 9 ]
      ],
      [
        [ 1, 3, 7 ],
        [],
        [ 1, 2, 3, 6 ],
        [ 1, 2, 3, 6, 7 ],
        [ 1, 2, 3, 5, 6 ],
        [],
        [],
        [ 1, 2 ],
        [ 1, 2, 5 ]
      ],
      [
        [],
        [ 1, 6 ],
        [],
        [],
        [ 1, 2, 6 ],
        [],
        [],
        [],
        [ 1, 2 ]
      ],
      [
        [],
        [],
        [ 1, 3, 5, 9 ],
        [ 1, 3, 4 ],
        [ 1, 3, 9 ],
        [ 1, 3, 9 ],
        [],
        [],
        []
      ],
      [
        [ 1 ],
        [],
        [ 1, 9 ],
        [ 1, 2, 6, 8 ],
        [],
        [ 1, 2, 6, 9 ],
        [],
        [],
        []
      ],
      [
        [ 1, 3 ],
        [],
        [],
        [ 1, 2, 3, 4 ],
        [ 1, 2, 3 ],
        [],
        [],
        [],
        []
      ],
      [
        [],
        [ 1, 6, 7 ],
        [ 1, 6 ],
        [ 1, 2, 3, 6 ],
        [ 1, 2, 3, 6, 9 ],
        [ 1, 2, 3, 6, 9 ],
        [],
        [],
        []
      ],
      [
        [],
        [],
        [ 1, 6 ],
        [],
        [],
        [],
        [],
        [ 2, 6 ],
        [ 2, 8 ]
      ],
      [
        [],
        [ 5, 6, 7 ],
        [],
        [ 3, 6 ],
        [],
        [ 3, 6 ],
        [],
        [],
        []
      ]
    ]
  },
  "currentGrid": {
    "rows": [
      [ 4, 0, 0, 0, 0, 0, 6, 8, 0 ],
      [ 0, 9, 0, 0, 0, 8, 4, 0, 0 ],
      [ 5, 0, 8, 9, 0, 4, 7, 3, 0 ],
      [ 6, 2, 0, 0, 0, 0, 8, 7, 1 ],
      [ 0, 4, 0, 0, 7, 0, 5, 2, 3 ],
      [ 0, 8, 7, 0, 0, 5, 9, 4, 6 ],
      [ 8, 0, 0, 0, 0, 0, 3, 5, 4 ],
      [ 9, 3, 0, 5, 4, 7, 2, 0, 0 ],
      [ 2, 0, 4, 0, 8, 0, 1, 9, 7 ]
    ]
  },
  "difficulty": "easy",
  "endedAt": null,
  "gameId": "9c37eacb-8f3d-44f8-a791-d3eff79629ca",
  "hintsUsed": 0,
  "originalGrid": {
    "rows": [
      [ 4, 0, 0, 0, 0, 0, 6, 8, 0 ],
      [ 0, 9, 0, 0, 0, 8, 0, 0, 0 ],
      [ 5, 0, 8, 9, 0, 4, 7, 3, 0 ],
      [ 6, 2, 0, 0, 0, 0, 8, 0, 0 ],
      [ 0, 4, 0, 0, 7, 0, 5, 0, 3 ],
      [ 0, 8, 7, 0, 0, 5, 9, 0, 6 ],
      [ 8, 0, 0, 0, 0, 0, 0, 5, 4 ],
      [ 9, 3, 0, 5, 4, 7, 0, 0, 0 ],
      [ 2, 0, 4, 0, 8, 0, 1, 9, 0 ]
    ]
  },
  "score": 0,
  "solutionGrid": {
    "rows": [
      [ 4, 1, 3, 7, 5, 2, 6, 8, 9 ],
      [ 7, 9, 2, 3, 6, 8, 4, 1, 5 ],
      [ 5, 6, 8, 9, 1, 4, 7, 3, 2 ],
      [ 6, 2, 5, 4, 3, 9, 8, 7, 1 ],
      [ 1, 4, 9, 8, 7, 6, 5, 2, 3 ],
      [ 3, 8, 7, 1, 2, 5, 9, 4, 6 ],
      [ 8, 7, 6, 2, 9, 1, 3, 5, 4 ],
      [ 9, 3, 1, 5, 4, 7, 2, 6, 8 ],
      [ 2, 5, 4, 6, 8, 3, 1, 9, 7 ]
    ]
  },
  "startedAt": "2026-07-10T08:05:23.562288851Z",
  "status": "IN_PROGRESS",
  "timeSpentSeconds": 842,
  "userId": "46f212e4-3041-704f-05ee-5efdfdd881cf"
}
```

</details>
