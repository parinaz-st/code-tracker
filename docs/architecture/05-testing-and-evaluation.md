# 5. Testing and evaluation

## Test layers

| Layer | Where | Runs | What it proves |
|---|---|---|---|
| Locator tests | `util/CodeElementLocator*Test` | `mvn test -Dtest=…` | Finding the element on a line. |
| Published oracles | `util/{Method,Variable,Attribute,Block}OracleTest` | `mvn test -Dtest=…OracleTest` (slow) | The FSE 2022 / TSE 2024 histories: TP/FP/FN must equal the expected values. |
| Experiments | `experiment/*ExperimentStarter` | `main` | Precision and recall tables (`experiments/tracking-accuracy/**`). |

## How an oracle test works

```mermaid
flowchart LR
    O["oracle JSON<br/>src/main/resources/oracle/element/set/"] --> T["*TrackerWithLocalFiles<br/>(offline)"]
    CL["commit logs<br/>src/test/resources/element/"] --> T
    CC["commit cache<br/>oracle/commits"] --> T
    T --> K["detected change keys<br/>commitId + type"]
    O --> E["expected keys"]
    K --> M["TP = E∩D, FP = D−E, FN = E−D"]
    E --> M
    M --> X{"equal to the<br/>expected values?"}
```

- A change is matched by its key, **commit id + change type**. Element names are not compared.
- `OracleTest` skips some change types when it compares (for example `BlockBodyChange` subclasses); read `OracleTest` before you add a new change type, so you know whether it will be scored.
- The expected TP/FP/FN values are loaded by `OracleTest.loadExpected(...)` from a text file per set and stored in static maps keyed by history file name, so **oracle file names must be unique across all elements**.

## Practical notes

- Don't compile in a checkout while an oracle run uses its `target/`.
- Oracle tests need the `oracle` git submodule (the commit cache). Don't commit the submodule pointer.
- The oracle tests currently do not start on this branch (a JUnit version mismatch); see "Known test problem" in `AGENTS.md`.
- The published oracle files are ground truth. Don't edit them.
- Before and after a change to tracking logic, run the published oracle for the element you touched and compare the results with a baseline from the unchanged code.
