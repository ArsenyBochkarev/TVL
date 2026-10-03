# CEGAR

When the full model is slow to verify, `./translate` can run a counterexample-guided abstraction refinement loop instead of a one-shot check:

```bash
./translate <model.tvl> <tla|spin> --cegar [--iterations=N]
```

With this flag, the translator will try to abstract the model with over-approximations, verify the abstract model with the target checker, and validate every found counterexample against the concrete model with
[Curtis](https://github.com/ArsenyBochkarev/Curtis).

A spurious counterexample refines the abstraction that caused it, a real one is the answer. A verified abstract model implies the concrete one is verified too.

If the loop does not converge within `--iterations` (default 20), it falls back to verifying the concrete model directly.

## Provided abstractions

- `loop-unroll` - `repeat N` becomes a nondeterministic loop, the iteration counter disappears from the state;
- `slice-actor` - an actor the specification does not observe is removed, together with every send to it;
- `collapse-messages` - messages with the same behavioral fate (the same queues, the same receive points) merge into one class message.

This abstractions applied on an IR, so there may be some overhead if discovered counterexamples are not applicable to concrete model.
