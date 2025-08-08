import time, json, csv, os, random
from azure.iot.device import IoTHubDeviceClient, Message
from sensors import simulate_sensor_reading, SENSOR_TYPES

CONNECTION_STRING = "HostName=agriculture.azure-devices.net;DeviceId=simulateModule;SharedAccessKey=LOxTgIjKs+6RLoEXRkJHxvDLl3dpzd7KHmZiiatTfxk="
# main.py ------------------------------------------------------------

NUM_FARMS           = 2
GATEWAYS_PER_FARM   = 2
SENSORS_PER_GATEWAY = 10
CSV_FILE            = "smartagri_telemetry.csv"

def init_csv():
    if not os.path.exists(CSV_FILE):
        with open(CSV_FILE, "w", newline="") as f:
            csv.writer(f).writerow(
                ["timestamp","deviceId","sensorType",
                 "gatewayId","value"])

def append_csv(rec):
    with open(CSV_FILE, "a", newline="") as f:
        csv.writer(f).writerow(
            [rec["timestamp"], rec["deviceId"],
             rec["sensorType"], rec["gatewayId"], rec["value"]])

def main():
    client = IoTHubDeviceClient.create_from_connection_string(CONNECTION_STRING)
    init_csv()
    print("Smart-Agri telemetry simulation started…  Ctrl-C to stop.")

    try:
        while True:
            for f in range(NUM_FARMS):
                for g in range(GATEWAYS_PER_FARM):
                    for idx in range(SENSORS_PER_GATEWAY):
                        for s_type in SENSOR_TYPES:
                            reading = simulate_sensor_reading(f, g, idx, s_type)
                            append_csv(reading)
                            msg = Message(json.dumps(reading))

                            # example custom property: drought alert
                            if s_type == "SOIL_MOISTURE" and reading["value"] < 12:
                                msg.custom_properties["dry"] = "true"
                            client.send_message(msg)

                    # simple actuator demo: random irrigation trigger
                    if random.random() < 0.05:      # 5 % chance every loop
                        irrig_cmd = {
                            "timestamp": reading["timestamp"],
                            "deviceId":  f"irrig-{f}-{g}",
                            "gatewayId": f"gw-{f}-{g}",
                            "command":   "START",
                            "duration":  180   # seconds
                        }
                        client.send_message(Message(json.dumps(irrig_cmd)))

            time.sleep(0.5)      # half-second cadence (per iFogSim workload)

    except KeyboardInterrupt:
        print("Cancelled by user.")
    finally:
        client.shutdown()

if __name__ == "__main__":
    main()
