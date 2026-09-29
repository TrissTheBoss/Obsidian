#!/usr/bin/env python3
import json
from context_governor import actions_poll_decision, audit_scopes, compress_bundle, minimize_call, validate_checkpoint


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def test_compression_and_recovery():
    bundle = {
        "budget_tokens": 260,
        "items": [
            {"id":"u1","category":"user_requirements","priority":100,"text":"User requires Phase 4 P4.1 to remain draft until real-machine runtime and visual validation pass.","source_ref":"ai/CURRENT_STATE.md#Phase-4"},
            {"id":"s1","category":"current_state","priority":100,"text":"Active branch is phase4/p4.1-persistent-scene-visibility and active draft PR is #57.","source_ref":"ai/CURRENT_STATE.md#Canonical-repository"},
            {"id":"d1","category":"durable_decisions","priority":100,"text":"Native Vulkan graphics ownership must not expand without a separately justified decision.","source_ref":"ai/DECISIONS.md#D-0025"},
            {"id":"o1","category":"open_obligations","priority":100,"text":"Run the P4.1 dev1 reference-machine exercise and obtain an explicit human visual verdict.","source_ref":"ai/CURRENT_STATE.md#Current-handoff"},
            {"id":"i1","category":"exact_identifiers","priority":100,"text":"Preview tag v0.4.0-phase4-dev1 targets continuity head 6af5174f054b272c65174fbf0c37d981a66aff31.","source_ref":"ai/attempts/A-0207-repository-hygiene-activation-and-cleanup.md#Preview-release-proof"},
            {"id":"b1","category":"security_boundaries","priority":100,"text":"Never store credentials, tokens, cookies, private keys, or passwords in ai continuity files.","source_ref":"ai/README.md#Log-discipline"},
            {"id":"n1","category":"tool_output","priority":1,"text":"x"*1600,"source_ref":"github:workflow-run/36401088635"},
            {"id":"n2","category":"tool_output","priority":1,"text":"y"*1600,"source_ref":"github:branches-snapshot/2026-09-28"}
        ]
    }
    result = compress_bundle(bundle)
    check(result["triggered"], "compression should trigger")
    active_ids = {x["id"] for x in result["active_items"]}
    for critical in ("u1","s1","d1","o1","i1","b1"):
        check(critical in active_ids, f"critical item {critical} lost")
    cold_ids = {x["id"] for x in result["recovery_index"]}
    check("n1" in cold_ids and "n2" in cold_ids, "bulk items should be pointerized")
    check(all(x.get("source_ref") for x in result["recovery_index"]), "cold item lacks recovery pointer")


def test_semantic_compaction_fail_closed():
    bundle = {
        "budget_tokens": 10,
        "items": [
            {"id":"critical","category":"user_requirements","text":"z"*200,"source_ref":"source:critical"}
        ]
    }
    result = compress_bundle(bundle)
    check(result["semantic_compaction_required"], "critical overflow must require semantic compaction")
    check(result["active_items"][0]["id"] == "critical", "critical item must not be dropped")


def test_call_minimization():
    payload = {
        "repository_full_name":"TrissTheBoss/Obsidian",
        "path":"ai/CURRENT_STATE.md",
        "content":"new content",
        "message":"docs: update state",
        "branch":"phase4/example",
        "conversation_history":"huge unrelated transcript",
        "unrelated_runtime_log":"many megabytes"
    }
    spec = {
        "allowed_paths":["repository_full_name","path","content","message","branch"],
        "required_paths":["repository_full_name","path","content","message","branch"]
    }
    result = minimize_call(payload, spec)
    check(set(result["payload"]) == {"repository_full_name","path","content","message","branch"}, "unexpected payload field survived")
    check("conversation_history" in result["audit"]["dropped_paths"], "conversation history was not dropped")


def test_secret_block():
    payload = {"endpoint":"x","access_token":"secret"}
    spec = {"allowed_paths":["endpoint","access_token"],"required_paths":["endpoint"]}
    try:
        minimize_call(payload, spec)
    except ValueError as exc:
        check("secret-like field blocked" in str(exc), "wrong secret error")
    else:
        raise AssertionError("secret-like field should be blocked")


def test_nonsecret_token_parameter_allowed():
    payload = {"model":"example","max_tokens":4096}
    spec = {"allowed_paths":["model","max_tokens"],"required_paths":["model"]}
    result = minimize_call(payload, spec)
    check(result["payload"]["max_tokens"] == 4096, "ordinary token-count parameter was misclassified as a secret")


def test_scope_audit():
    result = audit_scopes({
        "requested_scopes":["repo","read:user","gist"],
        "required_scopes":["repo"],
        "already_granted_scopes":["read:user"],
        "incremental_authorization_supported":True
    })
    check(result["excess_requested_scopes"] == ["gist","read:user"], "excess scopes not detected")
    check(not result["scope_request_is_minimal"], "broad scope request should fail minimality")
    check(result["incremental_new_scopes"] == ["repo"], "incremental scope delta incorrect")



def test_actions_poll_guard():
    moved = actions_poll_decision({
        "expected_head_sha":"aaa",
        "current_head_sha":"bbb",
        "poll_count":1,
        "run":{"status":"in_progress","conclusion":None},
    })
    check(moved["action"] == "switch_head", "moving head must abandon old run")

    done = actions_poll_decision({
        "expected_head_sha":"aaa",
        "current_head_sha":"aaa",
        "poll_count":1,
        "run":{"status":"completed","conclusion":"success"},
    })
    check(done["action"] == "terminal" and done["terminal"], "completed run must terminate polling")

    stale_first = actions_poll_decision({
        "expected_head_sha":"aaa",
        "current_head_sha":"aaa",
        "poll_count":2,
        "run":{"status":"in_progress","conclusion":None},
        "jobs":[{"status":"completed","conclusion":"success"}],
    })
    check(stale_first["action"] == "cross_check_once", "terminal jobs with nonterminal run require one cross-check")

    stale_second = actions_poll_decision({
        "expected_head_sha":"aaa",
        "current_head_sha":"aaa",
        "poll_count":3,
        "cross_check_performed":True,
        "run":{"status":"in_progress","conclusion":None},
        "jobs":[{"status":"completed","conclusion":"success"}],
    })
    check(stale_second["action"] == "stop_waiting" and stale_second["terminal"], "persistent stale wrapper must stop polling")

    capped = actions_poll_decision({
        "expected_head_sha":"aaa",
        "current_head_sha":"aaa",
        "poll_count":3,
        "run":{"status":"in_progress","conclusion":None},
        "jobs":[{"status":"in_progress","conclusion":None}],
    })
    check(capped["action"] == "stop_waiting" and not capped["terminal"], "poll cap must stop blocking session")

    unchanged = actions_poll_decision({
        "expected_head_sha":"aaa",
        "current_head_sha":"aaa",
        "poll_count":2,
        "unchanged_polls":2,
        "run":{"status":"queued","conclusion":None},
    })
    check(unchanged["action"] == "stop_waiting", "two unchanged nonterminal snapshots must stop waiting")


def valid_checkpoint():
    return {
        "schema_version":1,
        "active_goal":"Validate P4.1 runtime without changing renderer source.",
        "scope_boundary":"Phase 4 P4.1 runtime handoff only.",
        "source_snapshot":{"created_at":"2026-09-28T09:00:00Z","repo_or_system":"TrissTheBoss/Obsidian","authority_refs":["ai/CURRENT_STATE.md","ai/attempts/A-0205-phase4-p4.1-dev1-ci-package-runtime-handoff.md"]},
        "coverage_manifest":["user_requirements","current_state","durable_decisions","open_obligations","exact_identifiers","security_boundaries"],
        "facts":[{"id":"F1","text":"P4.1 is active and shadow-only.","source_ref":"ai/CURRENT_STATE.md#Phase-4"}],
        "decisions":[{"id":"D1","text":"P3.10 remains production draw owner in P4.1.","source_ref":"ai/attempts/A-0203-phase4-p4.1-persistent-scene-gpu-visibility-contract.md"}],
        "open_obligations":[{"id":"O1","text":"Reference runtime and visual PASS still required.","source_ref":"ai/CURRENT_STATE.md#Current-handoff"}],
        "exact_identifiers":[{"id":"I1","text":"Active PR #57.","source_ref":"ai/CURRENT_STATE.md#Canonical-repository"}],
        "security_boundaries":[{"id":"S1","text":"Credentials never enter ai continuity files.","source_ref":"ai/README.md#Log-discipline"}],
        "recovery_index":[{"id":"bulk-1","topic":"full runtime handoff","source_ref":"ai/attempts/A-0205-phase4-p4.1-dev1-ci-package-runtime-handoff.md","locator":"entire attempt"}],
        "discarded_ids":["bulk-1"],
        "next_action":"Run tester exercise and inspect full PrismLauncher log."
    }


def test_checkpoint_validation():
    cp = valid_checkpoint()
    result = validate_checkpoint(cp)
    check(result["valid"], f"valid checkpoint rejected: {result['errors']}")
    broken = json.loads(json.dumps(cp))
    broken["coverage_manifest"].remove("exact_identifiers")
    broken["facts"][0].pop("source_ref")
    result = validate_checkpoint(broken)
    check(not result["valid"], "broken checkpoint should fail")
    check(any("coverage_manifest missing" in e for e in result["errors"]), "missing coverage not detected")
    check(any("missing source_ref" in e for e in result["errors"]), "missing provenance not detected")


def main():
    tests = [
        test_compression_and_recovery,
        test_semantic_compaction_fail_closed,
        test_call_minimization,
        test_secret_block,
        test_nonsecret_token_parameter_allowed,
        test_scope_audit,
        test_actions_poll_guard,
        test_checkpoint_validation,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    print(f"PASS all {len(tests)} Context Governor tests")


if __name__ == "__main__":
    main()
