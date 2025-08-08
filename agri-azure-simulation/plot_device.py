# plot_device.py  ── Smart-Agri edition
import pandas as pd
import matplotlib.pyplot as plt

CSV        = "smartagri_telemetry.csv"
DEVICE_ID  = "sensor-0-0-0"          # eg. sensor-<farm>-<gw>-<idx>
SENSOR_TYPE = "SOIL_MOISTURE"

df = pd.read_csv(CSV, parse_dates=["timestamp"])
mask   = (df["deviceId"] == DEVICE_ID) & (df["sensorType"] == SENSOR_TYPE)
series = df.loc[mask].sort_values("timestamp")

plt.figure(figsize=(10, 5))
plt.plot(series["timestamp"], series["value"], marker="o", linewidth=1.5)
plt.title(f"{SENSOR_TYPE} – {DEVICE_ID}")
plt.xlabel("Timestamp")
plt.ylabel("Value")
plt.xticks(rotation=45)
plt.grid(True)
plt.tight_layout()
plt.savefig(f"{DEVICE_ID}_{SENSOR_TYPE}.png")
plt.show()
