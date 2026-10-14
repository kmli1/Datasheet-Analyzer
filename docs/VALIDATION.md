# Validation and known limitations

[← Project overview](../README.md)

There are two different records: archived release checks and the checks performed while preparing the repository presentation. They must not be read as the same test run.

## Archived release: 1.0.10

The source package includes [`DOGRULAMA-1.0.10.json`](../DOGRULAMA-1.0.10.json), dated **2026-10-09**.

| Recorded check | Result in the archived record |
| --- | --- |
| Unit test records | 365 total; 358 passed; 7 skipped; 0 failures/errors |
| Lint | 0 errors; 9 warnings |
| Debug APK | Built; checksum recorded |
| Device test APK | Built; device tests not executed |
| Sensor catalogue | 160 profiles; 138 added profiles |
| Physical phone/camera | Not tested in that release check |

The seven skipped tests require optional real datasheet inputs. They are not counted as passing. See [`SURUM_KONTROLU.txt`](../SURUM_KONTROLU.txt) for the original scope.

## Repository presentation: 2026-10-13

- Existing application source files are checked against the starting Git revision.
- The 144 protected main files are checked using `ALGORITMA_KORUMA_1.0.10.sha256`.
- The missing cache sources and synthetic security PDF fixtures are restored from the previously prepared 1.0.10 Git bundle.
- Catalogue JSON and generated Kotlin are checked with `python3 scripts/generate_sensor_catalog.py --check`.
- Documentation targets, SVG structure, repository file inventory and whitespace are checked locally; the banner is rendered for visual inspection.
- Android/Gradle build, instrumentation tests and physical-device checks are **not rerun** for this presentation update. No new passing Android-build claim is made.

A completed static check does not establish runtime behaviour on a phone. The [Stage 3 report](VERIFICATION.md) and [original 1.0.10 overview](SURUM_1.0.10.md) are historical records.

## Practical limits

Document structure, source access, OCR quality and page coverage affect extraction. A missing result does not prove that the parameter is absent from the original datasheet. Source graphs are page images, not newly reconstructed measurement data.

Catalogue alternatives carry conditions. Check power rails, I/O levels, measurement range, package and pin layout before selecting hardware.

The current repository has no published GitHub Release as of this update. Production signing, physical camera testing and Android 16 KB runtime validation remain release checks. [Signing instructions](../signing/README.md)
