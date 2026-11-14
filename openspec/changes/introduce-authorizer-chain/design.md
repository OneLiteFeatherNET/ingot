# Design

## D1. Shape

```
interface Authorizer {
    fun decide(request: AuthorizationRequest): AuthorizerDecision   // Allow | Deny | NoOpinion, with trace
}

class AuthorizationChain(
    restrictions: List<Authorizer>,    // empty in phase A
    builtIn: Authorizer,               // PermissionEvaluator, unchanged
    bindings: List<Authorizer>,        // empty in phase A
    plugins: List<Authorizer>,         // empty in phase A
)
```

The chain returns the first Allow or Deny. When every stage answers NoOpinion, the result is Deny. This matches today's "no match, deny".

## D2. Order and the token ceiling

The design order lists a separate credential ceiling before restrictions. Today the token node list is checked inside `PermissionEvaluator.decideRequest`, and the instance-admin shortcut is allowed only when the token does not deny (`tokenDenies` in the same function). A separate ceiling step would therefore have to reproduce that shortcut exactly. Phase A keeps the token check inside the built-in authorizer. The closed-world ceiling of the design arrives in phase C, when it can be tested against new action names.

## D3. Trace

The `/api/identity/check` trace is built from the candidates the evaluator already collects (`Decision.trace`). The chain passes the trace through unchanged, so the JSON shape and the reasons stay the same.

## D4. Verification

- A contract test for the chain with fake authorizers: each mapping of Allow, Deny and NoOpinion, the empty slots, and the default deny.
- The corpus of `record-decision-corpus` is the equality proof. It must pass without any edit in this change.
- The trace of `/check` is compared with the trace of the same fixture before the change.
