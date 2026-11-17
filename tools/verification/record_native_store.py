"""通过正式 v2 HTTP 接口记录原生 Journal；不创建替代 Agent、不操作业务数据库。"""
import argparse
import base64
import collections
import hashlib
import json
import struct
import time
import urllib.request
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / ".local/store-research-20261116"
REPORT = ROOT / "docs/research/agentscope-store-20261116"
WORKSPACE = ROOT / "server/v2-server/.agentscope/v2"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def segment(value):
    return "s_" + base64.urlsafe_b64encode(value.encode()).decode().rstrip("=")


def shape(value):
    if isinstance(value, dict):
        return {key: shape(item) for key, item in value.items()}
    if isinstance(value, list):
        return {"type": "array", "size": len(value), "firstItem": shape(value[0]) if value else None}
    if isinstance(value, str):
        return {"type": "string", "length": len(value)}
    return {"type": type(value).__name__}


def journal(session_id, user_id="1"):
    prefix = Path("agents") / segment("squad-master") / "sessions" / segment(user_id) / segment(session_id)
    candidates = list(WORKSPACE.rglob(str(prefix / "head.json"))) if WORKSPACE.exists() else []
    if not candidates:
        return {"found": False, "userId": user_id, "sessionId": session_id}
    head_file = candidates[0]
    directory = head_file.parent
    objects = []
    events = []
    payload_by_path = {}
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.suffix == ".lock":
            continue
        raw = path.read_bytes()
        try:
            version = struct.unpack(">q", raw[:8])[0]
            data = json.loads(raw[8:])
        except (ValueError, struct.error, UnicodeDecodeError):
            objects.append({"path": str(path.relative_to(directory)), "bytes": len(raw), "decoded": False})
            continue
        relative = str(path.relative_to(directory))
        payload_by_path[relative] = data
        item = {"path": relative, "bytes": len(raw), "storageVersion": version,
                "sha256": hashlib.sha256(raw).hexdigest(), "fields": list(data) if isinstance(data, dict) else []}
        if relative in ("head.json", "session.json", "inbox/head.json", "inbox/session.json"):
            item["decoded"] = data
        elif relative.startswith("commits/"):
            item["decoded"] = {key: value for key, value in data.items() if key != "events"}
            item["eventTypes"] = [event["type"] for event in data.get("events", [])]
        objects.append(item)
    # 只遍历 head 可达提交链；不把未提交对象或 inbox 的独立序号混进执行事实。
    head = payload_by_path["head.json"]
    cursor = head.get("commitId")
    while cursor:
        commit = payload_by_path["commits/" + cursor + ".json"]
        for event in commit.get("events", []):
            payload = json.loads(event.get("payloadJson", "{}"))
            entry = {key: value for key, value in event.items() if key != "payloadJson"}
            entry["payloadShape"] = shape(payload)
            entry["identifiers"] = {key: payload[key] for key in
                                    ("model", "purpose", "status", "modelCallId", "toolCallId",
                                     "childLogAgentId", "childAgentId", "childSessionId", "agentKey")
                                    if key in payload}
            if event["type"] == "state/checkpoint":
                state = json.loads(payload["stateJson"])
                entry["checkpoint"] = {"fields": list(state), "contextMessages": len(state.get("context", [])),
                                       "stateShape": shape(state), "reason": payload.get("reason")}
            events.append(entry)
        cursor = commit.get("parent")
    events.sort(key=lambda item: item["seq"])
    return {"found": True, "userId": user_id, "sessionId": session_id,
            "directory": str(directory.relative_to(ROOT)), "head": head,
            "eventCounts": dict(collections.Counter(event["type"] for event in events)),
            "contiguous": [event["seq"] for event in events] == list(range(1, head["seq"] + 1)),
            "objects": objects, "events": events}


def send(base_url, session_id, request_id, message, user_id="1"):
    request = urllib.request.Request(base_url + "/api/v2/chat/messages",
                                    data=json.dumps({"sessionId": session_id, "requestId": request_id,
                                                     "message": message}).encode(),
                                    headers={"Content-Type": "application/json", "Accept": "text/event-stream",
                                             "X-User-Id": user_id})
    started = time.monotonic()
    events = []
    with urllib.request.urlopen(request, timeout=180) as response:
        status = response.status
        for raw in response:
            line = raw.decode().strip()
            if line.startswith("data:"):
                event = json.loads(line[5:])
                # 思考正文只记录长度；合成输入产生的公开回复保留，便于人工核对。
                if event.get("type", "").startswith("REASONING") and "delta" in event:
                    event["deltaLength"] = len(event.pop("delta"))
                events.append(event)
    text = "".join(event.get("delta", "") for event in events if event.get("type") == "TEXT_MESSAGE_CONTENT")
    result = {"sessionId": session_id, "requestId": request_id, "userId": user_id,
              "httpStatus": status, "elapsedSeconds": round(time.monotonic() - started, 3),
              "reply": text, "eventCounts": dict(collections.Counter(event["type"] for event in events)),
              "events": events}
    print(json.dumps({"requestId": request_id, "httpStatus": status, "elapsedSeconds": result["elapsedSeconds"],
                      "eventCounts": result["eventCounts"], "reply": text}, ensure_ascii=False), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["seed", "reopen", "capture"])
    parser.add_argument("--base-url", default="http://127.0.0.1:18082")
    args = parser.parse_args()
    metadata_path = PRIVATE / "metadata.json"
    if args.phase == "seed":
        metadata = {"sessionId": "store-" + uuid.uuid4().hex[:16], "marker": "PERSIST-" + uuid.uuid4().hex[:10]}
        write(metadata_path, metadata)
    else:
        metadata = json.loads(metadata_path.read_text())
    session_id = metadata["sessionId"]
    marker = metadata["marker"]
    results = []
    if args.phase == "capture":
        write(REPORT / "reopen-journal.json", journal(session_id))
        return
    if args.phase == "seed":
        results.append(send(args.base_url, session_id, "remember-1",
                            "这次持久化验证编号是 " + marker + "。请记住它，只回复已记住，不调用工具。"))
        tool_prompt = "请实际调用一次 test_event_tool 测试工具，完成后简短报告结果，不调用其他工具。"
        results.append(send(args.base_url, session_id, "tool-1", tool_prompt))
        before_duplicate = journal(session_id)
        results.append(send(args.base_url, session_id, "tool-1", tool_prompt))
        after_duplicate = journal(session_id)
        write(REPORT / "duplicate-submit.json", {
            "requestId": "tool-1", "beforeHead": before_duplicate.get("head"),
            "afterHead": after_duplicate.get("head"),
            "beforeEventCounts": before_duplicate.get("eventCounts"),
            "afterEventCounts": after_duplicate.get("eventCounts")})
        results.append(send(args.base_url, session_id, "child-1",
                            "请将任务委派给 java-tester 子Agent，让它回复：子Agent持久化验证完成。不要调用其他工具。"))
    else:
        write(REPORT / "after-restart-before-request.json", journal(session_id))
        results.append(send(args.base_url, session_id, "recall-after-restart",
                            "之前告诉你的持久化验证编号是什么？只回复编号，不调用任何工具。"))
        results[-1]["recallMatched"] = marker in results[-1]["reply"]
        results.append(send(args.base_url, session_id, "other-user-1",
                            "之前告诉你的持久化验证编号是什么？如果不知道就回复不知道，不调用工具。", "2"))
        results[-1]["leakedMarker"] = marker in results[-1]["reply"]
        results.append(send(args.base_url, session_id + "-new", "new-session-1",
                            "之前告诉你的持久化验证编号是什么？如果不知道就回复不知道，不调用工具。"))
        results[-1]["leakedMarker"] = marker in results[-1]["reply"]
    write(REPORT / (args.phase + "-http.json"), results)
    write(REPORT / (args.phase + "-journal.json"), journal(session_id))
    print("Recorded " + args.phase, flush=True)


if __name__ == "__main__":
    main()
