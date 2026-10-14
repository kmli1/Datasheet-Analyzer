# Contributing

[Project overview](README.md) · [Architecture](docs/ARCHITECTURE.md) · [Validation](docs/VALIDATION.md)

## A focused change

1. Describe the bug or proposal in an issue. For extraction problems, include the part number, parameter, expected source page and observed behaviour.
2. Create a branch from `main`, such as `fix/table-header` or `docs/source-guide`.
3. Keep the change focused and preserve unrelated PDF/OCR rules.
4. Run the relevant checks below and describe what was actually tested.
5. Open a pull request using the included template.

## Checks

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebug
python3 scripts/generate_sensor_catalog.py --check
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. Device tests require a configured phone or emulator. [Local signing](signing/README.md)

For documentation-only changes, check relative links and render any new SVGs. A documentation update does not require an invented Android test result.

## Extraction and catalogue rules

- Preserve source evidence, page numbers and operating conditions.
- Keep recommended operation separate from absolute maximum ratings.
- Preserve empty cells; do not fabricate missing values.
- Support catalogue changes with manufacturer references and reviewed compatibility conditions.
- Add regression coverage when a parsing change affects previously supported documents.

## Submission

Explain the trigger, resulting behaviour, checks and remaining limitations. Add real screenshots for UI changes when available. Do not include private PDFs, signing keys, credentials or identifiable logs. Third-party files require appropriate rights and notices.

The project-wide licence remains undecided; third-party licences are recorded separately.
