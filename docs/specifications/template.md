### TVL template specifications
TVL has some pre-defined protocol-independent properties. User can enable them in `specs` block using reserved keywords, e.g.
```
specs {
    FinishingProperty;
    MsgDeliveredProperty;
    <some other properties>
}
```

All properties at once are enabled by **`AllTemplateProperties`** keyword.

##### Finishing property
Exact name: **`FinishingProperty`**.

Essentially it's a progress for finishing protocols.

Temporal formula: conjunction of `F(actor_N_finished == TRUE)` for all actors.

##### Validity property
Exact name: **`ValidityProperty`**.

If the protocol terminates, all channels must be empty.

Temporal formula: conjunction of `F(actor_i_finished -> channel_k_empty)` for all actors.

##### Message delivered property
Exact name: **`MsgDeliveredProperty`**.

Message delivery guarantee.

Temporal formula: conjunction of `G(sent_i -> F rcvd_i)` for all messages.

