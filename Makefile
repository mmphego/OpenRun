SHELL := /usr/bin/env bash

APP_ID := app.rwun
GRADLEW := ./gradlew
ADB ?= adb
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: help assemble test check device-status connect-wifi install-wifi launch-wifi run-wifi instrumentation evidence

help:
	@printf '%s\n' \
		'OpenRun developer commands:' \
		'  make assemble                      Build the debug APK.' \
		'  make test                          Run host and Android unit tests.' \
		'  make check                         Run the full local validation suite.' \
		'  make device-status                 List ADB devices; makes no device changes.' \
		'  ADB_ENDPOINT=<ip:port> make connect-wifi' \
		'                                      Connect to a previously paired Wi-Fi device.' \
		'  ANDROID_SERIAL=<ip:port> make install-wifi' \
		'                                      Build and update a Wi-Fi device without clearing app data.' \
		'  ANDROID_SERIAL=<ip:port> make launch-wifi' \
		'                                      Start OpenRun on that verified device.' \
		'  ANDROID_SERIAL=<ip:port> make run-wifi' \
		'                                      Install the debug APK, then launch it.' \
		'  ANDROID_SERIAL=<serial> make instrumentation' \
		'                                      Install debug/instrumentation APKs and run the custom smoke suite.' \
		'  ANDROID_SERIAL=<serial> RWUN_EVIDENCE_DIR=<private-dir> make evidence' \
		'                                      Capture local device diagnostics; review before sharing.'

assemble:
	$(GRADLEW) --no-daemon :app:assembleDebug

test:
	python3 scripts/run-host-tests.py
	$(GRADLEW) --no-daemon :app:testDebugUnitTest

check:
	bash scripts/check.sh

device-status:
	$(ADB) devices -l

connect-wifi:
	@if [[ -z "$${ADB_ENDPOINT:-}" ]]; then \
		echo 'Set ADB_ENDPOINT to the paired device address, for example 192.168.1.50:43211.' >&2; \
		exit 2; \
	fi
	$(ADB) connect "$$ADB_ENDPOINT"

install-wifi: assemble
	@$(MAKE) --no-print-directory verify-wifi-device
	$(ADB) -s "$(ANDROID_SERIAL)" install -r "$(DEBUG_APK)"

launch-wifi:
	@$(MAKE) --no-print-directory verify-wifi-device
	$(ADB) -s "$(ANDROID_SERIAL)" shell am start -n "$(APP_ID)/.MainActivity"

run-wifi: install-wifi launch-wifi

instrumentation:
	@$(MAKE) --no-print-directory verify-device
	$(GRADLEW) --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
	$(ADB) -s "$(ANDROID_SERIAL)" install -r "$(DEBUG_APK)"
	$(ADB) -s "$(ANDROID_SERIAL)" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
	$(ADB) -s "$(ANDROID_SERIAL)" shell am instrument -w app.rwun.test/app.rwun.SmokeInstrumentation

evidence:
	@$(MAKE) --no-print-directory verify-device
	@if [[ -z "$${RWUN_EVIDENCE_DIR:-}" ]]; then \
		echo 'Set RWUN_EVIDENCE_DIR to a private local directory before collecting diagnostics.' >&2; \
		exit 2; \
	fi
	ANDROID_SERIAL="$(ANDROID_SERIAL)" RWUN_EVIDENCE_DIR="$$RWUN_EVIDENCE_DIR" bash scripts/capture-device-evidence.sh

.PHONY: verify-device verify-wifi-device

verify-device:
	@if [[ -z "$(ANDROID_SERIAL)" ]]; then \
		echo 'Set ANDROID_SERIAL from `adb devices -l`; an explicit device prevents accidental deployment.' >&2; \
		exit 2; \
	fi
	@if [[ "$$($(ADB) -s "$(ANDROID_SERIAL)" get-state 2>&1 || true)" != 'device' ]]; then \
		echo "$(ANDROID_SERIAL) is not an authorised ADB device. Run `make device-status` and approve/pair it first." >&2; \
		exit 2; \
	fi

verify-wifi-device: verify-device
	@if [[ "$(ANDROID_SERIAL)" != *:* && "$(ANDROID_SERIAL)" != *'_tcp' ]]; then \
		echo "$(ANDROID_SERIAL) does not look like a Wi-Fi ADB serial. Use install/launch tooling in tests/device for USB QA." >&2; \
		exit 2; \
	fi
