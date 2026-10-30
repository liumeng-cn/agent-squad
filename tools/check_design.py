"""Static checks for the design artifacts; never connect to a database or service."""

import json
import base64
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def check():
    spec = json.loads((ROOT / "api/openapi.json").read_text())
    assert spec["openapi"] == "3.0.3"
    operations = []

    def visit(node):
        if isinstance(node, dict):
            if "$ref" in node:
                value = spec
                for part in node["$ref"].removeprefix("#/").split("/"):
                    value = value[part.replace("~1", "/").replace("~0", "~")]
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    visit(spec)
    for path, methods in spec["paths"].items():
        for method, operation in methods.items():
            operations.append(operation["operationId"])
            expected = set(re.findall(r"{([^}]+)}", path))
            actual = {p["name"] for p in operation["parameters"] if p["in"] == "path"}
            assert expected == actual, path
            assert any(
                status.startswith("2") for status in operation["responses"]
            ), path
    assert len(operations) == len(set(operations))

    for path in ROOT.rglob("*.md"):
        if any(part in path.parts for part in (".git", "node_modules", "target", "dist")):
            continue
        text = path.read_text()
        assert text.count("```") % 2 == 0, path
        for target in re.findall(r"\]\(([^)]+)\)", text):
            if "://" in target or target.startswith(("#", "/")):
                continue
            assert (path.parent / target.split("#")[0]).exists(), (path, target)

    migration_files = sorted(
        (ROOT / "server" / "src" / "main" / "resources" / "db" / "migration").glob("V*.sql")
    )
    java_migrations = sorted((ROOT / "server/src/main/java/db/migration").glob("V*__*.java"))
    all_migrations = migration_files + java_migrations
    migration_versions = [int(re.match(r"V(\d+)__", path.name).group(1)) for path in all_migrations]
    assert len(migration_versions) == len(set(migration_versions)), "SQL/Java 迁移版本重复"
    sql = "\n".join(path.read_text() for path in migration_files)
    definitions = re.findall(r"CREATE TABLE (\w+)\s*\((.*?)\n\) ENGINE", sql, re.S)
    business = [(name, body) for name, body in definitions if name.startswith("sq_")]
    non_column_tokens = {
        "PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "CHECK", "FOREIGN", "INDEX",
        "REFERENCES", "ON", "ADD", "DROP", "ALTER",
    }

    def column_names(body):
        names = set()
        for line in body.splitlines():
            match = re.match(r"    (\w+)\s+", line)
            if match and match.group(1).upper() not in non_column_tokens:
                names.add(match.group(1))
        return names

    columns = {name: column_names(body) for name, body in business}
    assert len(business) == len(columns)
    # CREATE 定义之外，共享运行归属由增量迁移补齐；检查最终字段集。
    effective_columns = {name: set(fields) for name, fields in columns.items()}
    for table, column in re.findall(
        r"ALTER TABLE (\w+) ADD COLUMN (\w+)\b", sql, re.I
    ):
        assert table in effective_columns, (table, column)
        assert column not in effective_columns[table], (table, column, "重复字段")
        effective_columns[table].add(column)
    runtime_fields = {
        "sq_conversation": {"title_source", "title_version", "last_activity_at"},
        "sq_goal_task": {"scope_id", "goal_revision"},
        "sq_execution_scope": {"scope_id", "account_id"},
        "sq_budget_reservation": {"scope_id", "account_id"},
        "sq_runtime_binding": {"scope_id"},
        "sq_command": {"scope_id"},
        "sq_operation": {"scope_id"},
        "sq_interaction": {"scope_id"},
        "sq_context_snapshot": {"scope_id", "goal_revision"},
        "sq_plan": {"goal_revision"},
    }
    for table, fields in runtime_fields.items():
        assert fields <= effective_columns[table], (table, "缺少运行归属字段")
    for name, body in business:
        assert {
            "id",
            "creator_id",
            "creator_name",
            "create_time",
            "last_updater_id",
            "last_updater_name",
            "last_update_time",
            "del_flag",
        } <= columns[name], (name, "缺少统一审计字段")
        for column in columns[name]:
            assert re.search(rf"^    {column}\s+.*COMMENT", body, re.M), (name, column)

    bodies = dict(business)
    foreign_keys = []
    for name, body in business:
        for match in re.finditer(
            r"CONSTRAINT (\w+)\s+FOREIGN KEY\s*\(([^)]*)\)\s*REFERENCES\s+(\w+)\s*\(([^)]*)\)",
            body,
        ):
            foreign_keys.append(
                (name, match.group(1), match.group(2), match.group(3), match.group(4))
            )
    assert len({row[1] for row in foreign_keys}) == len(foreign_keys)
    for source, _, source_columns, target, target_columns in foreign_keys:
        source_cols = set(re.findall(r"\w+", source_columns))
        target_cols = set(re.findall(r"\w+", target_columns))
        assert source_cols <= columns[source], (source, source_columns)
        assert target_cols <= columns[target], (target, target_columns)
        assert len(source_cols) == len(target_cols)
        assert any(
            set(re.findall(r"\w+", key_columns)) == target_cols
            for key_columns in re.findall(r"UNIQUE KEY \w+ \(([^)]*)\)", bodies[target])
        ), (source, target, target_columns)
    assert not re.search(r"^\s*(DROP(?!\s+INDEX\b)|TRUNCATE|CREATE DATABASE)\b", sql, re.M | re.I)

    # Optional dependency for field-level request example checks; not an app dependency.
    try:
        from jsonschema import Draft4Validator, Draft202012Validator, FormatChecker
    except ImportError as error:
        raise SystemExit(
            "Install jsonschema in a design tooling environment to validate examples"
        ) from error
    def nullable_schema(value):
        """Translate OpenAPI 3.0 nullable into JSON Schema for example validation."""
        if isinstance(value, list):
            return [nullable_schema(item) for item in value]
        if not isinstance(value, dict):
            return value
        result = {key: nullable_schema(item) for key, item in value.items() if key != "nullable"}
        return {"anyOf": [result, {"type": "null"}]} if value.get("nullable") else result

    example_components = nullable_schema(spec["components"])
    examples = json.loads((ROOT / "api/examples.json").read_text())["examples"]
    for example in examples:
        schema = {
            "$ref": "#/components/schemas/" + example["schema"],
            "components": example_components,
        }
        Draft4Validator(schema, format_checker=FormatChecker()).validate(
            example["value"]
        )
    invalid_examples = json.loads(
        (ROOT / "api/invalid-examples.json").read_text()
    )["examples"]
    for example in invalid_examples:
        schema = {
            "$ref": "#/components/schemas/" + example["schema"],
            "components": example_components,
        }
        assert not Draft4Validator(schema, format_checker=FormatChecker()).is_valid(
            example["value"]
        ), example["reason"]

    manifest = json.loads((ROOT / "api/design-manifest.json").read_text())
    assert manifest["plannedOperationCount"] == len(operations)
    assert manifest["plannedSchemaCount"] == len(spec["components"]["schemas"])
    assert manifest["plannedBusinessTableCount"] == len(columns)
    owned_tables = [
        table for tables in manifest["modules"].values() for table in tables
    ]
    assert len(owned_tables) == len(set(owned_tables))
    assert set(owned_tables) == set(columns)
    traceability = json.loads((ROOT / "api/traceability.json").read_text())
    assert set(traceability) == set(manifest["requirements"])
    spec_files = sorted((ROOT / "openspec" / "specs").glob("*/spec.md"))
    specs_text = "\n".join(p.read_text() for p in spec_files)
    cases = specs_text
    overview = specs_text
    for requirement, trace in traceability.items():
        assert requirement in overview
        assert trace["modules"] and trace["cases"] and trace["schemas"]
        assert set(trace["modules"]) <= set(manifest["modules"])
        assert set(trace["schemas"]) <= set(spec["components"]["schemas"])
        assert set(trace["operations"]) <= set(operations)
        assert all(case in cases for case in trace["cases"])
    assert not any(path.startswith("/internal/") for path in spec["paths"])
    assert not {"sq_run", "sq_step", "sq_checkpoint"} & set(columns)

    ui_manifest = json.loads((ROOT / "api/ui-manifest.json").read_text())
    web_design = (ROOT / "openspec" / "specs" / "web-console" / "spec.md").read_text()
    pages = ui_manifest["routes"]
    assert len({page["page"] for page in pages}) == len(pages)
    assert len({page["route"] for page in pages}) == len(pages)
    for page in pages:
        assert page["route"] in web_design
        assert set(page["operations"]) <= set(operations), page["page"]
        assert all(case in web_design for case in page["cases"])
    covered_ui_operations = {
        operation for page in pages for operation in page["operations"]
    } | set(ui_manifest["nonPageActions"])
    assert set(operations) == covered_ui_operations
    for path in (
        "/api/v1/tasks/{taskId}/events",
        "/api/v1/conversations/{conversationId}/events",
    ):
        sse = spec["paths"][path]["get"]
        assert {("query", "after"), ("header", "Last-Event-ID")} <= {
            (parameter["in"], parameter["name"]) for parameter in sse["parameters"]
        }, path
    workspace = next(page for page in pages if page["page"] == "workspace")
    assert {
        "submitFirstMessage", "submitConversationMessage", "getConversationSnapshot",
        "observeConversationEvents", "cancelResponseExecution", "retryResponseExecution",
        "respondInteraction", "downloadArtifact", "reconcileOperation",
    } <= set(workspace["operations"])
    assert "createTask" not in workspace["operations"]

    identities = json.loads((ROOT / "api/runtime-identifiers.json").read_text())

    def encode_identity(value):
        return "s_" + base64.urlsafe_b64encode(value.encode()).decode().rstrip("=")

    prefix = (
        "agents/"
        + encode_identity(identities["agentId"])
        + "/sessions/"
        + encode_identity(identities["userId"])
        + "/"
        + encode_identity(identities["sessionId"])
    )
    key_lengths = [
        len(prefix + inbox + suffix)
        for inbox in ("", "/inbox")
        for suffix in identities["suffixes"]
    ]
    assert max(key_lengths) <= identities["maxItemKeyCharacters"]
    assert (
        len("/".join(identities["namespace"])) <= identities["maxNamespaceCharacters"]
    )
    config_schema = json.loads(
        (ROOT / "api/runtime-config.schema.json").read_text()
    )
    config = json.loads((ROOT / "api/runtime-config.example.json").read_text())
    Draft202012Validator.check_schema(config_schema)
    Draft202012Validator(config_schema, format_checker=FormatChecker()).validate(config)
    database = config["database"]
    assert (
        database["platformPool"]
        + database["sdkPool"]
        + database["sandboxLockPool"]
        + database["reserveConnections"]
        <= database["connectionBudget"]
    )
    runtime = config["runtime"]
    assert 2 * runtime["guardRenewSeconds"] < runtime["guardTtlSeconds"]
    assert runtime["globalModelConcurrency"] <= runtime["maxOwnedBindings"]
    assert (
        config["profile"]["interactionTtlSeconds"]
        <= config["profile"]["absoluteDeadlineSeconds"]
    )
    if config["app"]["mode"] == "OFFLINE":
        assert config["model"]["provider"] == "MOCK"
        assert not config["sandbox"]["enabled"]
        assert config["model"]["pricing"]["currency"] == "TEST_UNIT"
    else:
        assert config["model"]["provider"] != "MOCK"
        assert config["model"]["baseUrl"].startswith("https://")
        assert config["model"]["apiKeyRef"].startswith("env:")
        assert config["model"]["pricing"]["currency"] != "TEST_UNIT"
    if config["app"]["mode"] == "PRODUCTION":
        assert config["security"]["cookieSecure"]
        assert config["app"]["publicOrigin"].startswith("https://")
        assert config["storage"]["backend"] == "S3_COMPATIBLE"
    assert (
        len({value for key, value in config["security"].items() if key.endswith("Ref")})
        == 4
    )
    print(
        f'PASS: {len(operations)} operations, {len(spec["components"]["schemas"])} schemas, '
        f"{len(columns)} table definitions, {len(foreign_keys)} FK references, "
        f"{len(examples)} valid / {len(invalid_examples)} invalid schema examples; "
        f"{len(traceability)} requirements traced; {len(pages)} UI routes cover all APIs; "
        f"SDK key fixture max {max(key_lengths)}/255; Markdown links valid"
    )
    print("Configuration schema, sample and cross-field design limits valid")
    print("Conversation SSE, workspace actions and migrated runtime ownership fields valid")
    print(f"Flyway inventory: {len(migration_files)} SQL + {len(java_migrations)} Java migrations; latest V{max(migration_versions)}")
    print("Static design validation only; no SQL execution or runtime verification.")


if __name__ == "__main__":
    check()
