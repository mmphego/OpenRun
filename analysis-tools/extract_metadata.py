"""Offline metadata inspection; requires the separate hermes-dec analysis package.
Does not contact or execute Runna. Original APK is supplied separately by the user.
"""
import hashlib
import json
import sys
import zipfile
from io import BytesIO
from hermes_dec.parsers.hbc_file_parser import HBCReader

path = sys.argv[1]
with zipfile.ZipFile(path) as package:
    bundle = BytesIO(package.read('assets/index.android.bundle'))
reader = HBCReader()
reader.file_buffer = bundle
for method in ('read_header_from_buffer', 'read_functions', 'read_string_kinds',
               'read_identifier_hashes', 'read_small_string_table',
               'read_overflow_string_table', 'read_string_storage'):
    getattr(reader, method)()
routes = [{'index': i, 'value': value} for i, value in enumerate(reader.strings)
          if value.startswith('ROUTE_')]
endpoints = [{'index': i, 'value': value} for i, value in enumerate(reader.strings)
             if value.startswith('https://') and any(part in value for part in (
                 'coaching.', 'cognito-idp', 'prod.api.therunbuddy',
                 'api.revenuecat', 'api.mixpanel', 'api-eu.mixpanel', 'api.statsigcdn'))]
with open(path, 'rb') as data:
    digest = hashlib.file_digest(data, 'sha256').hexdigest()
json.dump(dict(apk_sha256=digest, hermes_version=reader.header.version,
               string_count=len(reader.strings), route_count=len(routes),
               routes=routes, endpoints=endpoints), sys.stdout, indent=2)
