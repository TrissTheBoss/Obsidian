# Call Minimization Protocol

## Principle

Data availability is not data necessity. For every external call, send and retrieve the smallest data set that still makes the requested operation correct and safe.

## Call capsule

Create this ephemeral plan before calling a tool/API:

```text
operation: exact action
resource: exact target
required identifiers: ...
required request fields: ...
required response fields/range: ...
required auth scopes/actions: ...
explicitly excluded context: ...
```

Do not automatically serialize the plan into the request. Use it to construct the request.

## Request minimization

- Use exact resource IDs instead of broad searches when already known.
- Use endpoint/tool arguments, not copied prose, for structured data.
- Do not attach full messages/documents when an ID, range, field, or short excerpt is enough.
- Prefer PATCH-like minimal mutations over full-object replacement when supported and semantically safe.
- Do not send model reasoning, hidden scratch state, unrelated user memories, old tool outputs, or unrelated project files.
- Omit optional fields unless they affect the requested operation.
- If a provider accepts field masks/projections, specify them.

## Response minimization

Prefer, where the API supports it:

- field projection (`$select`, GraphQL field selection, field masks);
- server-side filters/search constraints;
- exact record/resource endpoints;
- bounded pages/limits;
- byte/line/range reads;
- minimal representations (`Prefer: return=minimal`) when the returned object is not needed;
- metadata first, full body only after it is selected as relevant.

## OAuth / authorization

Authorization is a privilege request, not a context-transfer channel.

- Request the minimum scope/action/resource set needed for the current capability.
- Prefer incremental/step-up authorization if the provider supports it.
- Keep access tokens audience/resource restricted when the platform supports that model.
- Do not put task content, document bodies, conversation summaries, or secrets in `state`, `scope`, redirect URIs, or other OAuth parameters.
- Use opaque random anti-CSRF `state`; do not encode user data into it.
- Use PKCE where required/recommended by the provider/OAuth profile.
- Treat refresh tokens and access tokens as credentials, never as context.
- A token with broad scopes does not authorize the model to retrieve broad data unnecessarily.

## Secret transport

Connectors/OAuth runtimes should inject credentials outside the model payload. For direct APIs, secrets may appear only in the required transport location and must be redacted from logs, checkpoints, error summaries, and audit output.

The deterministic minimizer blocks secret-like fields by default unless the field path is explicitly listed in `secret_transport_paths`.

## Necessity test

A request field is necessary only if at least one is true:

1. the endpoint/tool schema requires it;
2. it selects the exact resource/records needed;
3. it changes the intended operation/result;
4. it is required for safe concurrency/idempotency/version checks;
5. it is required authentication transport and is supplied through the secure auth layer.

If none apply, omit it.
