# AI-assisted development

[← Project overview](../README.md)

The project was developed for an AI agents course. Its development plan separated requirements, Android implementation and extraction-rule review.

| Role in the project plan | Focus |
| --- | --- |
| ChatGPT planning assistant | Requirements, scope, stages, feedback and coordination |
| GPT Astra 6.0 implementation role | Kotlin/Android, camera/OCR, PDF handling and debugging |
| GPT Sol 6.1 review role | Extraction rules, literature review and test scenarios |

These describe the project's development roles, not additional runtime services in the Android app.

## Representative prompts

1. Design an Android flow that extracts a requested value, table or graph together with its source page.
2. Generate technical-criterion matching rules that keep Recommended Operating Conditions separate from Absolute Maximum Ratings.
3. Create edge cases and test scenarios from an example datasheet and its expected results.

Generated suggestions were to be checked against real datasheets, official library documentation and test results. The repository keeps source evidence, integrity manifests and historical verification records so a reviewer can examine the delivered work.

The repository presentation work does not claim a new model experiment or a fresh Android test run. Its verification scope is described in [VALIDATION.md](VALIDATION.md).
