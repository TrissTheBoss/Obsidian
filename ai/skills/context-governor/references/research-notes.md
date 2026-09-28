# Research Notes and Design Inspirations

The Context Governor intentionally combines ideas from several existing systems without copying any one implementation.

## Tiered / virtual context

**MemGPT: Towards LLMs as Operating Systems** describes virtual context management inspired by hierarchical memory systems: keep a small working set while moving less-active information to external tiers and paging it back when needed.

Source: https://arxiv.org/abs/2310.08560

Adopted: tiered active/cold memory and explicit rehydration.

Not adopted: treating model-generated memory as authority. In Obsidian, repository/API source truth remains authoritative.

## Context editing, compaction, and memory

Anthropic documents three complementary mechanisms: compaction (summarize), tool-result clearing (remove stale re-fetchable results), and structured memory. Their context editing supports clearing old tool results while preserving recent items and excluding important tools from clearing.

Sources:
- https://platform.claude.com/docs/en/build-with-claude/context-editing
- https://platform.claude.com/cookbook/tool-use-context-engineering-context-engineering-tools

Adopted: separate "summarize" from "clear", keep recent/critical context, and treat re-fetchable tool output as disposable after a recovery pointer exists.

## OAuth least privilege and incremental authorization

OAuth 2.0 Security Best Current Practice (RFC 9700) recommends restricting token privileges to the minimum required for the application/use case, including audience/resource/action restriction. Google OAuth documentation recommends requesting scopes incrementally in context as capabilities are needed.

Sources:
- https://www.rfc-editor.org/rfc/rfc9700
- https://developers.google.com/identity/protocols/oauth2/web-server

Adopted: minimum action/resource scopes, incremental step-up where supported, and no use of OAuth parameters as a data-transfer channel.

## MCP authorization boundaries

Current MCP authorization guidance supports per-tool authorization and requires tokens to be validated for the intended resource. The 2026 MCP direction also emphasizes stateless/self-describing requests and authorization hardening.

Sources:
- https://apps.extensions.modelcontextprotocol.io/api/documents/authorization.html
- https://blog.modelcontextprotocol.io/posts/2026-07-28/

Adopted: call-specific authorization and request self-containment without attaching unrelated conversational state.

## API field projection

Microsoft Graph explicitly recommends retrieving only the data an app needs and supports `$select`, filters, paging, and minimal response representations.

Sources:
- https://learn.microsoft.com/en-us/graph/best-practices-concept
- https://learn.microsoft.com/en-us/graph/query-parameters

Adopted: project fields/rows server-side and retrieve narrow ranges instead of full objects.

## General least privilege

AWS IAM defines least privilege as granting only permissions required to perform a task and no additional permissions.

Source: https://docs.aws.amazon.com/IAM/latest/UserGuide/getting-started-reduce-permissions.html

Adopted: the same necessity principle for both authorization and data payloads.
