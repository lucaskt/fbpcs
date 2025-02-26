# Copyright (c) Facebook, Inc. and its affiliates.
#
# This source code is licensed under the MIT license found in the
# LICENSE file in the root directory of this source tree.

from __future__ import print_function

import base64
import json
import os

# initiate
print("Loading lambda function...")


def lambda_handler(event, context):
    output = []
    ##### NOTE: this script assume the schema is correct, no missing items
    for record in event["records"]:

        row = {}
        recordId = record["recordId"]
        row["recordId"] = recordId
        row["result"] = "Ok"
        decoded_data = json.loads(base64.b64decode(record["data"]))

        dic = dict(os.environ.items())
        debug = "DEBUG" in dic.keys() and dic["DEBUG"] == "true"

        if debug:
            print(
                f"Processing record for recordId: {recordId}, payload: {decoded_data}"
            )

        # if loaded as str, load again
        if type(decoded_data) is str:
            decoded_data = json.loads(decoded_data)

        if "serverSideEvent" not in decoded_data.keys():
            msg = f"Error: serverSideEvent does not exist for recordId: {recordId}"
            print(msg)
            continue
        row_data = decoded_data["serverSideEvent"]
        # as of H2 2021, only it should only be "website".
        action_source = row_data.get("action_source")
        timestamp = row_data.get("event_time")
        event_type = row_data.get("event_name")
        dummy_dict = {}
        currency_type = row_data.get("custom_data", dummy_dict).get("currency")
        conversion_value = row_data.get("custom_data", dummy_dict).get("value")
        email = row_data.get("user_data", dummy_dict).get("em")

        # make sure not all values are None
        if all(
            value is None
            for value in [timestamp, currency_type, conversion_value, event_type, email]
        ):
            msg = f"All essential columns are None/Null. Skip recordId: f{recordId}"
            print(msg)
            continue

        data = {}
        data["timestamp"] = timestamp
        data["currency_type"] = currency_type
        data["conversion_value"] = conversion_value
        data["event_type"] = event_type
        data["email"] = email
        data["action_source"] = action_source
        # firehose need data to be b64-encoded
        data = json.dumps(data) + "\n"
        data = data.encode("utf-8")
        row["data"] = base64.b64encode(data)
        output.append(row)

    print("finished data transformation.")
    return {"records": output}
