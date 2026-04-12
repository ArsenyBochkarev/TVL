### TVL user-defined specifications
TVL allows users to define their own specifications based on labels set in actors. It is possible to use both LTL and CTL for this purpose, but only if target model checker supports it.
E.g.
```
...
specs {
    ltl R2EndsR3: "[] (R2.R2Receive -> <> (R3.R3Break))";
}
```

Here, `R2Receive` and `R3Break` are labels set on actors R2 and R3, respectively. This specification may be read as "Whenever actor `R2` passes through the `R2Receive` label, actor `R3` will subsequently pass through the `R3Break` label".

##### Single actor finishing property
Though it is possible to use user labels to mark end of an actor, it is highly recommended to use actor's `ACTOR_END` property. E.g.
```
...
specs {
    ltl Agreement: "G (N1.ACTOR_END -> F (N2.ACTOR_END && N3.ACTOR_END && N4.ACTOR_END))";
}
```

Notice the fact that there are no labels `ACTOR_END` in the code. The `N1.ACTOR_END` exists only in the `Agreement` specification.