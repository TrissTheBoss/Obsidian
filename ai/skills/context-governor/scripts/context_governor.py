#!/usr/bin/env python3
import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path

CRITICAL_CATEGORIES = {
    "user_requirements",
    "current_state",
    "durable_decisions",
    "open_obligations",
    "exact_identifiers",
    "security_boundaries",
}
SECRET_KEY_RE = re.compile(r"(^|[._-])(access[_-]?token|refresh[_-]?token|id[_-]?token|password|client[_-]?secret|secret|cookie|authorization|private[_-]?key|api[_-]?key)([._-]|$)", re.I)


def load_json(path):
    return json.loads(Path(path).read_text())


def dump_json(obj):
    print(json.dumps(obj, indent=2, sort_keys=True))


def estimate_tokens(value):
    if isinstance(value, str):
        text = value
    else:
        text = json.dumps(value, sort_keys=True, separators=(",", ":"))
    return max(1, math.ceil(len(text) / 4))


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def leaf_paths(value, prefix=""):
    out = []
    if isinstance(value, dict):
        for key, child in value.items():
            path = f"{prefix}.{key}" if prefix else str(key)
            out.extend(leaf_paths(child, path))
    elif isinstance(value, list):
        out.append(prefix)
    else:
        out.append(prefix)
    return out


def get_path(obj, path):
    cur = obj
    for part in path.split("."):
        if not isinstance(cur, dict) or part not in cur:
            raise KeyError(path)
        cur = cur[part]
    return cur


def has_path(obj, path):
    try:
        get_path(obj, path)
        return True
    except KeyError:
        return False


def set_path(obj, path, value):
    parts = path.split(".")
    cur = obj
    for part in parts[:-1]:
        cur = cur.setdefault(part, {})
    cur[parts[-1]] = value


def minimize_call(payload, spec):
    allowed = set(spec.get("allowed_paths", []))
    required = set(spec.get("required_paths", []))
    forbidden = set(spec.get("forbidden_paths", []))
    secret_transport = set(spec.get("secret_transport_paths", []))

    missing = sorted(path for path in required if not has_path(payload, path))
    if missing:
        raise ValueError("missing required path(s): " + ", ".join(missing))

    forbidden_present = sorted(path for path in forbidden if has_path(payload, path))
    if forbidden_present:
        raise ValueError("forbidden path(s) present: " + ", ".join(forbidden_present))

    for path in leaf_paths(payload):
        if SECRET_KEY_RE.search(path) and path not in secret_transport:
            raise ValueError(f"secret-like field blocked from modeled payload: {path}")

    result = {}
    copied = []
    for path in sorted(allowed):
        if has_path(payload, path):
            value = get_path(payload, path)
            set_path(result, path, value)
            copied.append(path)

    all_leaves = set(leaf_paths(payload))
    dropped = sorted(all_leaves - set(copied))

    audit = {
        "copied_paths": copied,
        "dropped_paths": dropped,
        "input_estimated_tokens": estimate_tokens(payload),
        "output_estimated_tokens": estimate_tokens(result),
        "input_sha256": sha256_text(json.dumps(payload, sort_keys=True, separators=(",", ":"))),
    }
    if secret_transport:
        audit["secret_transport_paths"] = sorted(secret_transport)
    return {"payload": result, "audit": audit}


def audit_scopes(doc):
    requested = set(doc.get("requested_scopes", []))
    required = set(doc.get("required_scopes", []))
    already = set(doc.get("already_granted_scopes", []))
    incremental = bool(doc.get("incremental_authorization_supported", False))

    missing = sorted(required - requested)
    excess = sorted(requested - required)
    result = {
        "required_scopes": sorted(required),
        "requested_scopes": sorted(requested),
        "missing_required_scopes": missing,
        "excess_requested_scopes": excess,
        "scope_request_is_minimal": not missing and not excess,
    }
    if incremental:
        result["incremental_new_scopes"] = sorted(required - already)
        result["already_granted_required_scopes"] = sorted(required & already)
    return result


def compress_bundle(bundle):
    budget = int(bundle.get("budget_tokens", 4000))
    critical_categories = set(bundle.get("critical_categories", [])) | CRITICAL_CATEGORIES
    items = list(bundle.get("items", []))

    normalized = []
    for item in items:
        text = str(item.get("text", ""))
        source_ref = item.get("source_ref")
        if not source_ref:
            raise ValueError(f"item {item.get('id')} missing source_ref")
        normalized.append({
            **item,
            "text": text,
            "estimated_tokens": estimate_tokens(text),
            "must_preserve": bool(item.get("must_preserve", False)) or item.get("category") in critical_categories,
        })

    total = sum(i["estimated_tokens"] for i in normalized)
    if total <= budget:
        return {
            "budget_tokens": budget,
            "estimated_input_tokens": total,
            "estimated_active_tokens": total,
            "triggered": False,
            "semantic_compaction_required": False,
            "active_items": normalized,
            "recovery_index": [],
        }

    critical = [i for i in normalized if i["must_preserve"]]
    optional = [i for i in normalized if not i["must_preserve"]]
    active = list(critical)
    active_tokens = sum(i["estimated_tokens"] for i in active)

    semantic_required = active_tokens > budget
    if not semantic_required:
        for item in sorted(optional, key=lambda x: (-int(x.get("priority", 0)), x["estimated_tokens"], str(x.get("id", "")))):
            if active_tokens + item["estimated_tokens"] <= budget:
                active.append(item)
                active_tokens += item["estimated_tokens"]

    active_ids = {i.get("id") for i in active}
    cold = []
    for item in normalized:
        if item.get("id") in active_ids:
            continue
        cold.append({
            "id": item.get("id"),
            "category": item.get("category"),
            "source_ref": item.get("source_ref"),
            "content_sha256": sha256_text(item["text"]),
            "estimated_tokens": item["estimated_tokens"],
            "reason": "recoverable context moved out of active window",
        })

    return {
        "budget_tokens": budget,
        "estimated_input_tokens": total,
        "estimated_active_tokens": active_tokens,
        "triggered": True,
        "semantic_compaction_required": semantic_required,
        "active_items": active,
        "recovery_index": cold,
    }


def validate_entry_list(name, value, errors):
    if not isinstance(value, list):
        errors.append(f"{name} must be a list")
        return
    for idx, item in enumerate(value):
        if not isinstance(item, dict):
            errors.append(f"{name}[{idx}] must be an object")
            continue
        if not str(item.get("text", "")).strip():
            errors.append(f"{name}[{idx}] missing text")
        if not str(item.get("source_ref", "")).strip():
            errors.append(f"{name}[{idx}] missing source_ref")


def validate_checkpoint(checkpoint):
    errors = []
    if checkpoint.get("schema_version") != 1:
        errors.append("schema_version must equal 1")
    for key in ("active_goal", "scope_boundary", "next_action"):
        if not str(checkpoint.get(key, "")).strip():
            errors.append(f"{key} is required")

    source_snapshot = checkpoint.get("source_snapshot")
    if not isinstance(source_snapshot, dict):
        errors.append("source_snapshot must be an object")
    else:
        refs = source_snapshot.get("authority_refs")
        if not isinstance(refs, list) or not refs:
            errors.append("source_snapshot.authority_refs must be a non-empty list")

    coverage = set(checkpoint.get("coverage_manifest", []))
    missing_coverage = sorted(CRITICAL_CATEGORIES - coverage)
    if missing_coverage:
        errors.append("coverage_manifest missing: " + ", ".join(missing_coverage))

    for name in ("facts", "decisions", "open_obligations", "exact_identifiers", "security_boundaries"):
        validate_entry_list(name, checkpoint.get(name), errors)

    recovery = checkpoint.get("recovery_index")
    if not isinstance(recovery, list):
        errors.append("recovery_index must be a list")
        recovery_ids = set()
    else:
        recovery_ids = set()
        for idx, item in enumerate(recovery):
            if not isinstance(item, dict):
                errors.append(f"recovery_index[{idx}] must be an object")
                continue
            if not str(item.get("source_ref", "")).strip():
                errors.append(f"recovery_index[{idx}] missing source_ref")
            if item.get("id"):
                recovery_ids.add(item["id"])

    discarded = set(checkpoint.get("discarded_ids", []))
    unrecoverable = sorted(discarded - recovery_ids)
    if unrecoverable:
        errors.append("discarded_ids without recovery_index entry: " + ", ".join(unrecoverable))

    return {"valid": not errors, "errors": errors}


def main():
    parser = argparse.ArgumentParser(description="Context Governor deterministic helpers")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("minimize-call")
    p.add_argument("payload")
    p.add_argument("spec")

    p = sub.add_parser("audit-scopes")
    p.add_argument("request")

    p = sub.add_parser("compress-bundle")
    p.add_argument("bundle")

    p = sub.add_parser("validate-checkpoint")
    p.add_argument("checkpoint")

    args = parser.parse_args()
    try:
        if args.cmd == "minimize-call":
            dump_json(minimize_call(load_json(args.payload), load_json(args.spec)))
        elif args.cmd == "audit-scopes":
            dump_json(audit_scopes(load_json(args.request)))
        elif args.cmd == "compress-bundle":
            dump_json(compress_bundle(load_json(args.bundle)))
        elif args.cmd == "validate-checkpoint":
            result = validate_checkpoint(load_json(args.checkpoint))
            dump_json(result)
            if not result["valid"]:
                sys.exit(2)
    except (ValueError, KeyError, json.JSONDecodeError) as exc:
        print(json.dumps({"valid": False, "error": str(exc)}, indent=2), file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
