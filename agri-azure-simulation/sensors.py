# sensors.py ---------------------------------------------------------
import random, uuid
from datetime import datetime

SENSOR_TYPES = ["SOIL_MOISTURE", "AIR_TEMP", "HUMIDITY", "LIGHT"]

def simulate_sensor_reading(farm, gateway, idx, s_type):
    """Return a dict representing one environmental measurement."""
    if s_type == "SOIL_MOISTURE":             # volumetric % (VWC)
        value = random.uniform(8.0, 45.0)
    elif s_type == "AIR_TEMP":                # °C
        value = random.uniform(12.0, 38.0)
    elif s_type == "HUMIDITY":                # %
        value = random.uniform(30.0, 100.0)
    elif s_type == "LIGHT":                   # kLux
        value = random.uniform(5.0, 90.0)
    else:
        value = None

    return {
        "id":         str(uuid.uuid4()),
        "timestamp":  datetime.utcnow()
                          .isoformat(timespec="milliseconds") + "Z",
        "deviceId":   f"sensor-{farm}-{gateway}-{idx}",
        "gatewayId":  f"gw-{farm}-{gateway}",
        "sensorType": s_type,
        "value":      round(value, 2)
    }
