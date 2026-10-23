"""Static checks for the design artifacts; never connect to a database or service."""

import json
import base64
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def check():
    spec = json.loads((ROOT / "contracts/openapi.json").read_text())
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

    sql = (ROOT / "data/schema.sql").read_text()
    definitions = re.findall(r"CREATE TABLE `(\w+)` \((.*?)\n\) ENGINE", sql, re.S)
    columns = {
        name: set(re.findall(r"^    `(\w+)` ", body, re.M))
        for name, body in definitions
    }
    assert len(definitions) == len(columns)
    for name, body in definitions:
        assert {
            "id",
            "creator_id",
            "creator_name",
            "create_time",
            "last_updater_id",
            "last_updater_name",
            "last_update_time",
            "del_flag",
        } <= columns[name]
        for line in body.splitlines():
            if re.match(r"    `\w+` ", line):
                assert "COMMENT " in line, (name, line)
    foreign_keys = re.findall(
        r"ALTER TABLE `(\w+)` ADD CONSTRAINT `([^`]+)` FOREIGN KEY \(([^)]+)\) REFERENCES `(\w+)` \(([^)]+)\)",
        sql,
    )
    assert len({row[1] for row in foreign_keys}) == len(foreign_keys)
    for source, _, source_columns, target, target_columns in foreign_keys:
        assert set(re.findall(r"`(\w+)`", source_columns)) <= columns[source]
        assert set(re.findall(r"`(\w+)`", target_columns)) <= columns[target]
        assert len(re.findall(r"`(\w+)`", source_columns)) == len(
            re.findall(r"`(\w+)`", target_columns)
        )
        target_keys = dict(definitions)[target]
        assert any(
            re.findall(r"`(\w+)`", key_columns)
            == re.findall(r"`(\w+)`", target_columns)
            for key_columns in re.findall(
                r"UNIQUE KEY `[^`]+` \(([^)]+)\)", target_keys
            )
        ), (source, target, target_columns)
    assert not re.search(r"^\s*(DROP|TRUNCATE|CREATE DATABASE)\b", sql, re.M | re.I)

    # Optional dependency for field-level request example checks; not an app dependency.
    try:
        from jsonschema import Draft4Validator, Draft202012Validator, FormatChecker
    except ImportError as error:
        raise SystemExit(
            "Install jsonschema in a design tooling environment to validate examples"
        ) from error
    examples = json.loads((ROOT / "contracts/examples.json").read_text())["examples"]
    for example in examples:
        schema = {
            "$ref": "#/components/schemas/" + example["schema"],
            "components": spec["components"],
        }
        Draft4Validator(schema, format_checker=FormatChecker()).validate(
            example["value"]
        )
    invalid_examples = json.loads(
        (ROOT / "contracts/invalid-examples.json").read_text()
    )["examples"]
    for example in invalid_examples:
        schema = {
            "$ref": "#/components/schemas/" + example["schema"],
            "components": spec["components"],
        }
        assert not Draft4Validator(schema, format_checker=FormatChecker()).is_valid(
            example["value"]
        ), example["reason"]

    manifest = json.loads((ROOT / "contracts/design-manifest.json").read_text())
    assert manifest["plannedOperationCount"] == len(operations)
    assert manifest["plannedSchemaCount"] == len(spec["components"]["schemas"])
    assert manifest["plannedBusinessTableCount"] == len(columns)
    owned_tables = [
        table for tables in manifest["modules"].values() for table in tables
    ]
    assert len(owned_tables) == len(set(owned_tables))
    assert set(owned_tables) == set(columns)
    traceability = json.loads((ROOT / "contracts/traceability.json").read_text())
    assert set(traceability) == set(manifest["requirements"])
    cases = (ROOT / "docs/acceptance-cases.md").read_text()
    overview = (ROOT / "docs/overview.md").read_text()
    for requirement, trace in traceability.items():
        assert requirement in overview
        assert trace["modules"] and trace["cases"] and trace["schemas"]
        assert set(trace["modules"]) <= set(manifest["modules"])
        assert set(trace["schemas"]) <= set(spec["components"]["schemas"])
        assert set(trace["operations"]) <= set(operations)
        assert all(case in cases for case in trace["cases"])
    assert not any(path.startswith("/internal/") for path in spec["paths"])
    assert not {"sq_run", "sq_step", "sq_checkpoint"} & set(columns)

    ui_manifest = json.loads((ROOT / "contracts/ui-manifest.json").read_text())
    web_design = (ROOT / "docs/web-console.md").read_text()
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
    sse = spec["paths"]["/api/v1/tasks/{taskId}/events"]["get"]
    assert {("query", "after"), ("header", "Last-Event-ID")} <= {
        (parameter["in"], parameter["name"]) for parameter in sse["parameters"]
    }

    identities = json.loads((ROOT / "contracts/runtime-identifiers.json").read_text())

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
        (ROOT / "contracts/runtime-config.schema.json").read_text()
    )
    config = json.loads((ROOT / "contracts/runtime-config.example.json").read_text())
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
    print("Static design validation only; no SQL execution or runtime verification.")


if __name__ == "__main__":
    check()
